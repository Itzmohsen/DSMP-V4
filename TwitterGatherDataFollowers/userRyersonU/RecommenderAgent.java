package TwitterGatherDataFollowers.userRyersonU;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.UnreadableException;


import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.io.FileWriter;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.RoundingMode;
// import java.sql.Connection; fully qualified in code due to conflict with Neuroph Connection
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.StringJoiner;
import java.util.Arrays;
import java.util.Random;   //Sepide
import java.lang.String;   //Sepide
import java.util.Collections; //Sepide
import weka.filters.Filter;   // Sepide
import java.io.*;     // Sepide
import java.lang.Object; // Sepide
import java.util.HashMap;  //Sepide
import java.util.Map.Entry;  //Sepide


import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import weka.classifiers.Classifier;
import weka.classifiers.AbstractClassifier;    //Sepide
import weka.classifiers.Evaluation;
import weka.classifiers.evaluation.NominalPrediction;
import weka.classifiers.functions.SMO;
import weka.classifiers.functions.LibSVM;
import weka.classifiers.functions.MultilayerPerceptron;    //Sepide
import weka.classifiers.meta.FilteredClassifier;
import weka.core.FastVector;
import weka.core.Instances;
import weka.filters.unsupervised.attribute.Remove;
import weka.filters.unsupervised.attribute.NumericToNominal;  //Sepide
import weka.filters.unsupervised.attribute.NominalToBinary;   //Sepide
// import weka.classifiers.functions.activation.Sigmoid;   //Sepide
// import weka.classifiers.functions.activation.ActivationFunction;   //Sepide
// import weka.classifiers.functions.activation.ApproximateSigmoid;   //Sepide


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
import org.neuroph.core.learning.LearningRule;
import org.neuroph.nnet.learning.BackPropagation;
import org.neuroph.eval.CrossValidation;  //Sepide
import org.neuroph.eval.ClassifierEvaluator; //Sepide
import org.neuroph.eval.CrossValidationResult;  //Sepide
import org.neuroph.eval.classification.ConfusionMatrix; // Sepide
//import org.neuroph.eval.Evaluation;   // Sepide 
import java.util.concurrent.ExecutionException;
import weka.filters.Filter;   //Sepide 
import weka.filters.unsupervised.attribute.StringToWordVector;   //Sepide
import java.util.logging.Logger;  //Sepide
import weka.core.SerializationHelper;  //Sepide
import weka.core.Utils;        // Sepide
import org.apache.commons.io.FileUtils;  //Sepide
import org.apache.commons.io.FileUtils.*; //Sepide
import java.io.InputStream;      //Sepide
import java.io.InputStreamReader;   //Sepide

import java.awt.AWTException;
import java.awt.Robot;
import com.aspose.cells.Workbook;  //Sepide
import com.opencsv.CSVWriter; //sepide
import java.nio.file.Files;  //Sepide

public class RecommenderAgent extends Agent 
{	
	private final long serialVersionUID = 1L;
	private static final int HASH_TAGS = 1;
	private static final int RE_TWEETS = 1;
	private static final int STOP_WORDS = 1;
	public static final int COS_SIM = 0;
	public static final int K_MEANS = 1;
	public static final int SVM = 2;
	public static final int MLP = 3;
	public static final int Doc2Vec = 4;     //added by Sepide
	public static final int CommonNeighbors = 5;        // added by Sepide
	public static final int K_MEANSEUCLIDEAN = 6;     // added by Sepide 
	public static final int SCALABLE_SVM = 7;
	public static final int TEMPORAL_GRAPH_FOUNDATION = 8;
	private static final String TWEET_BATCH_ONTOLOGY = "Tweet Batch From User Agent";
	private static final String TWEET_BATCH_PRIMARY_REC_SERVER_PREFIX = "__PRIMARY_REC_SERVER__\t";
	private static final String INPUT_DELIVERY_COMPLETE_ONTOLOGY =
			"Input Delivery Complete";
	private static final long INPUT_DELIVERY_GRACE_MILLIS = Math.max(
			30000L, Long.getLong("dsmp.input.delivery.grace.ms", 300000L));
	private static final boolean VERBOSE_TEXT_PROCESSING_DEBUG = false;
	private static final boolean WRITE_TFIDF_DEBUG_FILES = false;
	private static final boolean WRITE_LEGACY_DOC2VEC_COMPATIBILITY_FILES = false;
	private static final int COS_SIM_LOCAL_TOP_MIN_RESULTS = 20;
	private static final int COS_SIM_LOCAL_TOP_MULTIPLIER = 10;
	private static final Pattern RETWEET_PATTERN = Pattern.compile("RT @");
	private static final Pattern PUNCTUATION_PATTERN = Pattern.compile("[\\p{P}]");
	private static final Pattern LINK_PATTERN = Pattern.compile("http[a-zA-Z0-9]*|bitly[a-zA-Z0-9]*|www[a-zA-Z0-9]*");
	private static final Pattern SPECIAL_CHARACTER_PATTERN = Pattern.compile("[^a-zA-Z\\p{Z}]");
	private static final Pattern NON_ALPHA_PATTERN = Pattern.compile("[^a-zA-Z ]");
	private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile(" +");
	private static final double TEST_SET_PERCENT = 0.30;
	private static final double TRAIN_SET_PERCENT = 0.70;
	public static final int HIDDEN_NEURONS = 10;
	public static final double LEARNING_RATE_MLP = 0.1;
	public static final double MAX_ERROR_MLP = 0.01; 
	

	private AID controllerAgent;

	private String referenceUser; 
	private String tweetNum_temp2;
	private String dc1string_temp;
	private String dc2string_temp;
	private int    hashtags_temp;

	private int    retweetedby_temp;
	private int    stopWordFlag_temp;
	private int    stemFlag_temp;
	private int    numberofAgents_temp;	  
	private int    numberofAgents_tempint;
	private int    temp=0;


	private int connectedtoTfidfservernumber_temp;	  
	private int connectedtoTfidfservernumber;
	private int connectedtoRecservernumber_temp;	  
	private int connectedtoRecservernumber;

	private AID[] allRecommenderAgents;	
	private AID[] allUserAgentConnectedtoThisServer;		
	private AID   AID_agent_name;
	private String agentName;



	private int tweetCount = 0; //Number of tweets currently received from user agents
	private int tweetsToReceive = 100; //Total number of tweets the recommender is supposed to receive from user agents
	private int lastNodeTelemetryTweetCount = -1;
	private int usableTweetCount;
	private long streamingTextProcessingNanos;
	private static final String TWEET_BATCH_ACK_ONTOLOGY =
			"Tweet Batch Received By Recommender";
	private final TweetBatchDeliveryLedger processedTweetBatches =
			new TweetBatchDeliveryLedger();

	private int numRecAgents =0;

	static String serverName = DatabaseSettings.host();
	static String portNumber = DatabaseSettings.port();
	static String sid = DatabaseSettings.database();

	private java.sql.Connection con;
	private Statement stmt = null;
	private PreparedStatement preparedStatement = null;
	private ResultSet resultSet = null;

	static String user = DatabaseSettings.user();
	static String pass = DatabaseSettings.password();
	
	private ArrayList<String> textprocessing_wb_or_tfidf_Data = new ArrayList<String>();		

	//My Own Variables 
	static final String[] stopWordsArray = {"a","able","about","above","abst","accordance","according","accordingly","across","act",
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


	public String nodeNumber ="";

	//@Jason added algorithmRec
	private int algorithmRec = 0;

	//@Jason added convId
	private String convId = "";

	//@Jason added boolean to only calculate once
	private boolean calculateAlready=false;

	//@Jason added list of completed users for recommendation
	private ArrayList<String> completedUsers = new ArrayList<String>();
	private int countUsersCosim = 0;
	private int countScores = 0;
	private long firstTweetTime;
	

	//Necessary @Jason
	//private LinkedHashMap<String,ArrayList<String>> allUserDocuments = new LinkedHashMap<String,ArrayList<String>>();
	private LinkedHashMap<String,Double> aggregatedUserTweets = new LinkedHashMap<String,Double>();
	private LinkedHashMap<String,LinkedHashMap<String,Double>> allUserDocuments = new LinkedHashMap<String,LinkedHashMap<String,Double>>();
	private LinkedHashMap<String,LinkedHashMap<String,Double>> allUserDocumentsTFIDF =
			new LinkedHashMap<String,LinkedHashMap<String,Double>>();
	private LinkedHashMap<String,Integer> allTermsDocumentFreq = new LinkedHashMap<String,Integer>();
	public TreeSet<String> allUniqueDocTerms = new TreeSet<String>();  // Sepide changed the private access modifier to public
	private int totalUsers=0,totalWords=0,totalDocuments=0;
	private ArrayList<LinkedHashMap<String,Double>> userDocumentVectorsList = new ArrayList<LinkedHashMap<String,Double>>();
	private LinkedHashMap<String,ArrayList<LinkedHashMap<String,Double>>> allUserDocumentVectors = new LinkedHashMap<String,ArrayList<LinkedHashMap<String,Double>>>();

	private long startTimeTextProcessing,endTimeTextProcessing,completionTimeTextProcessing;
	private long startTimeTFIDF,endTimeTFIDF,completionTimeTFIDF;
	private long startTimeAlgorithm,endTimeAlgorithm,completionTimeAlgorithm;
	private long startTimeTrain,endTimeTrain,completionTimeTrain;
	private long startTimeTest,endTimeTest,completionTimeTest;

	private LinkedHashMap<Long,String> tweetIdUser = new LinkedHashMap<Long,String>();
	private LinkedHashMap<Long,String> tweetIdText = new LinkedHashMap<Long,String>();
	private LinkedHashMap<Long,LinkedHashMap<String,Double>> tweetIdDocumentVector = new LinkedHashMap<Long,LinkedHashMap<String,Double>>();
	// private LinkedHashMap<String,ArrayList<Long>> usersTweetIdsList = new LinkedHashMap<String,ArrayList<Long>>();
	
	private ArrayList<String> userRegisteredInRecAgent = new ArrayList<String>();
	private Map<String,Integer> userPrimaryRecServer = new LinkedHashMap<String,Integer>();
	private ArrayList<String> usersRec; //Users to be given recommendations
	private int[] usersRecTweetCountsReceived;
	
	private Map<String,TreeMap<String,Double>> allUserScores = new TreeMap<String,TreeMap<String,Double>>();
	private Map<String,TreeMap<String,String>> temporalGraphCandidateDetails =
			new TreeMap<String,TreeMap<String,String>>();
	private RecommendationExperimentConfig recommendationConfig;
	private Map<String,String> datasetUserFollowee = new LinkedHashMap<String,String>();
	private Map<String,TreeMap<String,RecommendationCandidateEvidence>>
			precomputedClassifierEvidence =
			new TreeMap<String,TreeMap<String,RecommendationCandidateEvidence>>();

	transient protected ControllerAgentGui myGui;
	transient protected FileSplitterService fsv;
	transient protected SplitterParams params;
	private boolean getUserRecList;

	private int totalMessageBytes = 0;
	private final RecommendationRunLifecycle runLifecycle =
			new RecommendationRunLifecycle();

	private long systemTimeName;
	
	public Map<String,String> userFollowee = new LinkedHashMap<String,String>(); //list of users and their followee names before processing (may have extra since processing can remove users) // private access modifier was changed to public by Sepide 
	private Map<String,Integer> followeeFollowerCounts = new LinkedHashMap<String,Integer>(); //number of followers for each followee/class
	private Map<String,List<String>> followeeFollowers = new LinkedHashMap<String,List<String>>(); //list of followees and their followers
	private List<String> testSetUsers; //list of users in test set
	private List<String> trainSetUsers; //list of users in training set
	private List<String> dataSetUsers;  //list of users in data set
	

	
	private Classifier trainedCentralSVM;
	private TreeSet<String> centralUniqueDocTerms = new TreeSet<String>();
	
	private DataSet dataSet;  // Data Set for Cross Validation Sepide
	private DataSet trainMLP; //training set for MLP
	private DataSet testMLP; //test set for MLP
	private DataSet recMLP; //user getting recommendation set for MLP
	private Map<String,Integer> followeeIndex; //index of array for followee
	private Map<String,Integer> followerIndex; //index of array for followers
	private MultiLayerPerceptron multiLayer;  // MLP for Cross Validation Sepide
	private MultiLayerPerceptron nodeMLP; //initial MLP for node;
	private MultilayerPerceptron mlp; //added by Sepide
	private MultiLayerPerceptron averagedMLP; //MLP with averaged weights
	private NeuralNetwork averagedNN; //NN with averaged weights
	private boolean sparseFederatedMlpRun;
	private List<SparseFederatedMlpModelSupport.TrainingExample> sparseMlpTestExamples;
	private List<SparseFederatedMlpModelSupport.TrainingExample> sparseMlpRecExamples;
	private RecommendationMemoryCoordinator.Lease activeMlpLease;
	private RecommendationMemoryCoordinator.Lease activeKMeansLease;
	private RecommendationMemoryCoordinator.Lease activeSvmLease;
	private boolean exactSvmDiskBackedWaves;
	private long estimatedExactSvmNodeTermEntries;
	private ExactSvmCorpusSpool exactSvmCorpusSpool;
	private final LinkedHashSet<String> exactSvmUsableUsers =
			new LinkedHashSet<String>();
	private String[] followeeNames;
	private String[] followerNames;    // Added by Sepide
	public List<String> datasetFollowees; //followees of whole dataset Sepide changed the private access modifier to public
	private List<String> centralTrainSetUsers;
	private List<String> centralTestSetUsers;
	//public File fileFromGui = myGui.fileChooser.getSelectedFile();  // added by Sepide
	//public String filePath = fileFromGui.getPath();   // added by Sepide 
    //public String userREC = usersRec.get(0).toString();    // added by Sepide 
	
	protected void setup() 
	{


		Object[] args = getArguments();
		controllerAgent = (AID) args[0];

		referenceUser = (String) args[1]; 
		tweetNum_temp2    = (String) args[2];
		dc1string_temp    = (String) args[3];
		dc2string_temp    = (String) args[4];
		hashtags_temp     = Integer.parseInt(args[5].toString());

		retweetedby_temp  = (Integer) args[7];
		stopWordFlag_temp = (Integer) args[8];


		connectedtoTfidfservernumber = (Integer) args[11];	  
		connectedtoRecservernumber   = (Integer) args[12];

		tweetsToReceive		 = (Integer) args[13];		

		System.out.println(getLocalName()+" tweetsToReceive: "+tweetsToReceive);

		numRecAgents = (Integer) args[15];

		//@Jason added algorithmRec argument
		algorithmRec = (Integer) args[16];
		recommendationConfig = args.length > 17
				&& args[17] instanceof RecommendationExperimentConfig
				? (RecommendationExperimentConfig) args[17]
				: RecommendationExperimentConfig.legacyDefault(algorithmRec, ControllerAgent.FROM_TEXT);

		//usersRec = (ArrayList<String>) args[17];

		myGui = (ControllerAgentGui) args[18];

		if (RecommendationModeSupport.isSvmFamilyAlgorithm(algorithmRec)
				&& numRecAgents > 1)
		{
			trainedCentralSVM = (Classifier) args[19];
		}
		
		// if (algorithmRec == MLP && numRecAgents > 1)
		if (algorithmRec == MLP)
		{
			centralUniqueDocTerms = (TreeSet<String>) args[20];
			centralTrainSetUsers = (List<String>) args[22];
			centralTestSetUsers = (List<String>) args[23];
		}
		
		datasetFollowees = args.length > 21 && args[21] instanceof List
				? (List<String>) args[21] : Collections.<String>emptyList();
		if (args.length > 24 && args[24] instanceof Map)
		{
			datasetUserFollowee.putAll((Map<String,String>) args[24]);
		}
		if (args.length > 25 && args[25] instanceof Map)
		{
			precomputedClassifierEvidence.putAll(
					(Map<String,TreeMap<String,RecommendationCandidateEvidence>>) args[25]);
		}
		exactSvmDiskBackedWaves = args.length > 26
				&& Boolean.TRUE.equals(args[26]);
		estimatedExactSvmNodeTermEntries = args.length > 27
				&& args[27] instanceof Number
				? Math.max(0L, ((Number)args[27]).longValue()) : 0L;
		System.out.println(getLocalName()+" datasetFollowees: "+datasetFollowees);
		
		try {
			DFAgentDescription dfd = new DFAgentDescription();
			dfd.setName(getAID());
			ServiceDescription sd = new ServiceDescription();
			sd.setName("Distributed Recommender System");
			sd.setType("Recommender Agent");
			dfd.addServices(sd);
			JadeDfRegistrationGate.register(this, dfd);
			RecommenderServiceBehaviour RecommenderServiceBehaviour = new RecommenderServiceBehaviour(this);
			addBehaviour(RecommenderServiceBehaviour);

			System.out.println(getLocalName()+" REGISTERED WITH THE DF");
		} catch (FIPAException e) {
			e.printStackTrace();
		}		
		agentName = getLocalName();
		AID_agent_name = getAID();

		//@Jason checking AID_agent_name
		System.out.println(this.getLocalName()+" AID_agent_name: "+AID_agent_name);


		nodeNumber = agentName.split("ServiceAgent", 2)[1].trim();
		int nodeNumInt = Integer.parseInt(nodeNumber);  // added by Sepide
		NodeExecutionMonitor.nodeReady(getLocalName(), tweetsToReceive);

		System.out.println("Hello! I am " + getAID().getLocalName()+ " and is setup properly.");

		DFAgentDescription template = new DFAgentDescription();
		ServiceDescription sd = new ServiceDescription();
		sd.setType("Recommender Agent");
		template.addServices(sd);
		try {
			DFAgentDescription[] result = DFService.search(this, template);
			allRecommenderAgents = new AID[result.length];
			for (int i = 0; i < result.length; ++i) {
				allRecommenderAgents[i] = result[i].getName();
			}
		}
		catch (FIPAException fe) {
			fe.printStackTrace();
		}

		setQueueSize(0);
		getUserRecList = false;
		
		systemTimeName = System.currentTimeMillis();
		
		System.out.println(getLocalName()+" currentQueueSize: "+getQueueSize());
		
//		usersRec = myGui.getUsersRec();
//		System.out.println(getLocalName()+" usersRec: "+usersRec);
//		usersRecTweetCountsReceived = new int[usersRec.size()];

	}

	private void refreshRecommendationTargets()
	{
		myGui.reselectRecommendee();
		if (recommendationConfig != null
				&& !recommendationConfig.getTargetUsers().isEmpty())
		{
			usersRec = new ArrayList<String>(recommendationConfig.getTargetUsers());
		}
		else
		{
			usersRec = myGui.getUsersRec();
		}
		System.out.println(getLocalName()+" usersRec: "+usersRec);
		usersRecTweetCountsReceived = new int[usersRec.size()];
		getUserRecList = true;
	}

	private void resetForRecommendationRun(String runId)
	{
		System.out.println(getLocalName()+" resetting per-run state for "+runId);
		RecommendationRunAbortRegistry.prepare(runId);
		NodeExecutionMonitor.beginNodeRun(
				getLocalName(), runId, tweetsToReceive);
		tweetCount = 0;
		usableTweetCount = 0;
		streamingTextProcessingNanos = 0L;
		lastNodeTelemetryTweetCount = -1;
		processedTweetBatches.clear();
		totalMessageBytes = 0;
		firstTweetTime = 0L;
		calculateAlready = false;
		getUserRecList = false;
		completedUsers.clear();
		countUsersCosim = 0;
		countScores = 0;

		aggregatedUserTweets.clear();
		allUserDocuments.clear();
		allUserDocumentsTFIDF.clear();
		allTermsDocumentFreq.clear();
		allUniqueDocTerms.clear();
		totalUsers = 0;
		totalWords = 0;
		totalDocuments = 0;
		userDocumentVectorsList.clear();
		allUserDocumentVectors.clear();

		tweetIdUser.clear();
		tweetIdText.clear();
		tweetIdDocumentVector.clear();
		userRegisteredInRecAgent.clear();
		userPrimaryRecServer.clear();
		allUserScores.clear();
		temporalGraphCandidateDetails.clear();
		userFollowee.clear();
		followeeFollowerCounts.clear();
		followeeFollowers.clear();

		testSetUsers = null;
		trainSetUsers = null;
		dataSetUsers = null;
		dataSet = null;
		trainMLP = null;
		testMLP = null;
		recMLP = null;
		followeeIndex = null;
		followerIndex = null;
		multiLayer = null;
		nodeMLP = null;
		mlp = null;
		averagedMLP = null;
		averagedNN = null;
		sparseFederatedMlpRun = false;
		sparseMlpTestExamples = null;
		sparseMlpRecExamples = null;
		releaseActiveMlpLease();
		releaseActiveKMeansLease();
		releaseActiveSvmLease();
		closeExactSvmCorpusSpool();
		exactSvmUsableUsers.clear();
		followeeNames = null;
		followerNames = null;

		startTimeTextProcessing = 0L;
		endTimeTextProcessing = 0L;
		completionTimeTextProcessing = 0L;
		startTimeTFIDF = 0L;
		endTimeTFIDF = 0L;
		completionTimeTFIDF = 0L;
		startTimeAlgorithm = 0L;
		endTimeAlgorithm = 0L;
		completionTimeAlgorithm = 0L;
		startTimeTrain = 0L;
		endTimeTrain = 0L;
		completionTimeTrain = 0L;
		startTimeTest = 0L;
		endTimeTest = 0L;
		completionTimeTest = 0L;
		textprocessing_wb_or_tfidf_Data.clear();

		refreshRecommendationTargets();
	}

	private void reportNodeInputProgress()
	{
		boolean runtimeInput = runtimeControlledInputQueueActive();
		int reportInterval = tweetsToReceive > 0
				? Math.max(1, tweetsToReceive / 100)
				: 1000;
		boolean finalUpdate = !runtimeInput
				&& tweetsToReceive > 0
				&& tweetCount >= tweetsToReceive;
		if (lastNodeTelemetryTweetCount < 0
				|| finalUpdate
				|| tweetCount - lastNodeTelemetryTweetCount >= reportInterval)
		{
			lastNodeTelemetryTweetCount = tweetCount;
			if (runtimeInput)
			{
				NodeExecutionMonitor.updateRuntimeInput(
						getLocalName(), tweetCount);
				return;
			}
			NodeExecutionMonitor.updateTweets(
					getLocalName(), tweetCount, tweetsToReceive);
			if (finalUpdate && !runLifecycle.isInputDeliveryComplete())
			{
				NodeExecutionMonitor.waitingForInputCompletion(
						getLocalName(), tweetCount, tweetsToReceive);
			}
		}
	}

	private boolean runtimeControlledInputQueueActive()
	{
		return RecommendationWorkQueueRegistry.snapshotInputBatchQueue(
				runLifecycle.getActiveRunId(),
				Math.max(1, numRecAgents)).getTotalShards() > 0;
	}

	private void releaseActiveMlpLease()
	{
		if (activeMlpLease != null)
		{
			activeMlpLease.close();
			activeMlpLease = null;
		}
	}

	private void releaseActiveKMeansLease()
	{
		if (activeKMeansLease != null)
		{
			activeKMeansLease.close();
			activeKMeansLease = null;
		}
	}

	private void releaseActiveSvmLease()
	{
		if (activeSvmLease != null)
		{
			activeSvmLease.close();
			activeSvmLease = null;
		}
	}

	private void closeExactSvmCorpusSpool()
	{
		if (exactSvmCorpusSpool == null)
		{
			return;
		}
		try
		{
			exactSvmCorpusSpool.close();
		}
		catch (IOException exception)
		{
			System.err.println(getLocalName()
					+" could not remove its Exact-SVM corpus spool: "
					+exception.getMessage());
		}
		finally
		{
			exactSvmCorpusSpool = null;
		}
	}

	private void releaseExactSvmPreparationState()
	{
		if (algorithmRec != SVM)
		{
			return;
		}
		allUserDocuments.clear();
		allUserDocumentsTFIDF.clear();
		allTermsDocumentFreq.clear();
		allUniqueDocTerms.clear();
		trainSetUsers = null;
		testSetUsers = null;
		dataSetUsers = null;
		closeExactSvmCorpusSpool();
		exactSvmUsableUsers.clear();
	}

	private class RecommenderServiceBehaviour extends CyclicBehaviour {
		private static final long serialVersionUID = 1L;

		public RecommenderServiceBehaviour(Agent a) {
			super(a);
		}

		private boolean waitForLivePhaseBoundary(String phaseLabel)
		{
			try
			{
				LiveWorkloadAdjustmentRegistry.waitUntilNodeEligible(
						runLifecycle.getActiveRunId(),
						currentNodeIndex(),
						phaseLabel);
				return true;
			}
			catch (InterruptedException exception)
			{
				Thread.currentThread().interrupt();
				handleAlgorithmFailure(new IllegalStateException(
						"Interrupted while waiting at live workload phase boundary "
								+ phaseLabel,
						exception));
				return false;
			}
		}

		private int currentNodeIndex()
		{
			try
			{
				return Math.max(1, Integer.parseInt(nodeNumber));
			}
			catch (NumberFormatException exception)
			{
				return 1;
			}
		}

		public void action() {
			
			if (!getUserRecList)
			{
				refreshRecommendationTargets();
			}

			ACLMessage msg= myAgent.receive();
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

			if (msg!=null && msg.getOntology() == "Update Connected UserAgent List for this Rec Server" && msg.getPerformative() == ACLMessage.REQUEST)
			{
				DFAgentDescription template = new DFAgentDescription();
				ServiceDescription sd = new ServiceDescription();
				sd.setName("Distributed Recommender System");
				sd.setType("User-Agent");
				sd.setOwnership(nodeNumber);
				template.addServices(sd);
				try {
					DFAgentDescription[] result = DFService.search(myAgent, template);

					//@Jason checking how many connected agents to recommender agents
					System.out.println(myAgent.getLocalName()+" RESULT LENGTH: "+result.length);

					allUserAgentConnectedtoThisServer = new AID[result.length];
					for (int i = 0; i < result.length; ++i) {
						allUserAgentConnectedtoThisServer[i] = result[i].getName();
						// System.out.println(getLocalName()+" user: "+allUserAgentConnectedtoThisServer[i].getLocalName());
					}
				}
				catch (FIPAException fe) {
					fe.printStackTrace();
				}
			}

				boolean receivedTweetData = false;
				boolean inputDeliveryCompleted = false;
				if (INPUT_DELIVERY_COMPLETE_ONTOLOGY.equals(msg.getOntology())
						&& msg.getPerformative() == ACLMessage.INFORM
						&& runLifecycle.isActive(msg.getConversationId()))
				{
					inputDeliveryCompleted =
							runLifecycle.markInputDeliveryComplete(
									msg.getConversationId());
					if (inputDeliveryCompleted)
					{
						if (runtimeControlledInputQueueActive())
						{
							NodeExecutionMonitor.inputClosedByRuntime(
									getLocalName(),
									tweetCount,
									tweetsToReceive);
						}
						scheduleInputDeliveryVerification(
								msg.getConversationId());
					}
				}
				boolean isTweetMessage = "Tweet From User Agent".equals(msg.getOntology())
						|| TWEET_BATCH_ONTOLOGY.equals(msg.getOntology());
				if (isTweetMessage)
				{
					RecommendationRunLifecycle.Admission admission =
							runLifecycle.admit(msg.getConversationId());
					if (admission == RecommendationRunLifecycle.Admission.RETIRED_RUN
							|| runLifecycle.isTextProcessingComplete())
					{
						if (TWEET_BATCH_ONTOLOGY.equals(msg.getOntology()))
						{
							sendTweetBatchAcknowledgement(msg);
						}
						return;
					}
					if (admission == RecommendationRunLifecycle.Admission.NEW_RUN)
					{
						resetForRecommendationRun(runLifecycle.getActiveRunId());
					}
					if (RecommendationRunAbortRegistry.isAborted(
							runLifecycle.getActiveRunId()))
					{
						if (TWEET_BATCH_ONTOLOGY.equals(msg.getOntology()))
						{
							sendTweetBatchAcknowledgement(msg);
						}
						return;
					}
				}
				
				if (msg!=null && "Tweet From User Agent".equals(msg.getOntology()))
				{
					receivedTweetData = recordTweetFromUserAgent(msg.getContent());
					if (receivedTweetData)
					{
						reportNodeInputProgress();
					}
				}
				
				if (msg!=null && TWEET_BATCH_ONTOLOGY.equals(msg.getOntology()))
				{
					if (!isProcessedTweetBatch(msg))
					{
						receivedTweetData = recordTweetBatchFromUserAgent(msg);
						markTweetBatchProcessed(msg);
					}
					sendTweetBatchAcknowledgement(msg);
					if (receivedTweetData)
					{
						reportNodeInputProgress();
					}
				}
				
				boolean runtimeInputClosed =
						inputDeliveryCompleted
						&& runtimeControlledInputQueueActive();
				int telemetryExpectedTweets = runtimeInputClosed
						? tweetCount : tweetsToReceive;
				if((receivedTweetData || inputDeliveryCompleted)
							&& runLifecycle.markTextProcessingCompleteIfReady(
									tweetCount,
									tweetsToReceive,
									runtimeInputClosed))
				{
					if (!waitForLivePhaseBoundary(
							"INPUT_BATCH -> TEXT_PREPROCESS_USER"))
					{
						return;
					}
					if (runtimeInputClosed)
					{
						NodeExecutionMonitor.preprocessingRetainedInput(
								getLocalName(), tweetCount);
					}
					else
					{
						NodeExecutionMonitor.preprocessing(
								getLocalName(), tweetCount, telemetryExpectedTweets);
					}
					long lastTweetTime = System.nanoTime();
					/*System.out.println("tweetIdText: "+tweetIdText);
					System.out.println("tweetIdUser: "+tweetIdText);
					System.out.println("usersTweetIdsList: "+usersTweetIdsList);
					 */
					
					ACLMessage msgMessagePassing = new ACLMessage( ACLMessage.INFORM );
					msgMessagePassing.addReceiver( new AID("Starter Agent", AID.ISLOCALNAME) );
					msgMessagePassing.setPerformative( ACLMessage.INFORM );
					msgMessagePassing.setConversationId(runLifecycle.getActiveRunId());

					msgMessagePassing.setContent(String.valueOf(totalMessageBytes));
					msgMessagePassing.setOntology("Message Passing Cost");
					send(msgMessagePassing);
					
					msgMessagePassing.setContent(String.valueOf(convertMs(lastTweetTime-firstTweetTime)));
					msgMessagePassing.setOntology("Message Passing Time");
					send(msgMessagePassing);
					NodeExecutionMonitor.recordMessageStats(
							getLocalName(),
							totalMessageBytes,
							convertMs(lastTweetTime-firstTweetTime));
					

					System.out.println(convertMs(lastTweetTime-firstTweetTime) + " ms");
					System.out.println("############### tweetCount: "+tweetCount);
					System.out.println("@@@@@@@@@@@@@@@ usableTweetCount: "
							+ usableTweetCount);

						startTimeTextProcessing = System.nanoTime();

					//					for (String curName: usersTweetIdsList.keySet())
					//					{
					//						userDocumentVectorsList = new ArrayList<LinkedHashMap<String,Double>>();
					//
					//						for (Long currTweetId : usersTweetIdsList.get(curName))
					//						{
					//							LinkedHashMap<String,Double> userDocumentVector = new LinkedHashMap<String,Double>();
					//							String currentText = tweetIdText.get(currTweetId);
					//
					//							//pad out spaces before and after word for parsing 
					//							currentText = String.format(" %s ",currentText);
					//
					//							//System.out.println("Original text: "+currentText);
					//
					//
					//							//Remove Photo: tweets
					//							if (currentText.contains("Photo:"))
					//								currentText = currentText.substring(0,currentText.indexOf("Photo:"));
					//
					//							//Remove Photoset: tweets
					//							if (currentText.contains("Photoset:"))
					//								currentText = currentText.substring(0,currentText.indexOf("Photoset:"));
					//
					//							//Remove all retweets if flagged
					//							Matcher matcher; //a matcher
					//							if (currentText.contains("RT @") && retweetedby_temp == RE_TWEETS)
					//								currentText="";
					//							else
					//							{
					//								//Remove RT @, conserve the text from retweets
					//								Pattern retweet = Pattern.compile("RT @");
					//
					//								matcher = retweet.matcher(currentText);
					//								currentText = matcher.replaceAll("RT ");
					//							}
					//							//System.out.println("After RT @: " + currentText);
					//
					//							//Remove punctuations
					//							Pattern punctuations = Pattern.compile("[\\p{P}]");
					//							matcher = punctuations.matcher(currentText);
					//							currentText = matcher.replaceAll("");
					//
					//							//System.out.println("After punctuations: "+ currentText);
					//
					//							//Remove url links
					//							Pattern links = Pattern.compile("http[a-zA-Z0-9]*|bitly[a-zA-Z0-9]*|www[a-zA-Z0-9]*");
					//							matcher = links.matcher(currentText);
					//							currentText = matcher.replaceAll(" ");
					//
					//							//System.out.println("After url links: "+ currentText);
					//
					//							//Remove special characters including hash tags if flagged
					//							if (hashtags_temp == HASH_TAGS)
					//							{
					//								Pattern specialCharacters = Pattern.compile("[^a-zA-Z\\p{Z}]");
					//								matcher = specialCharacters.matcher(currentText);
					//								currentText = matcher.replaceAll(" ");
					//							}
					//							//System.out.println("After special characters: " + currentText);
					//
					//							//Remove stop words if flagged
					//							if (stopWordFlag_temp == STOP_WORDS)
					//							{
					//								for (String stopWord : stopWordsArray){	
					//									if (currentText.toLowerCase().contains(" "+stopWord+" "))
					//										//System.out.println("FOUND STOPWORD: "+stopWord);
					//										currentText = currentText.toLowerCase().replaceAll(" "+stopWord+" "," ");
					//								}
					//							}
					//							//Change all text to lowercase
					//							currentText=currentText.toLowerCase();
					//
					//							//Trim leading and ending white space
					//							currentText=currentText.trim();
					//							//Remove any non-alphabetical characters
					//							currentText=currentText.replaceAll("[^a-zA-Z ]","");
					//							//Shorten any spaces to just 1 single space
					//							currentText=currentText.replaceAll(" +", " ");
					//							//currentText=currentText.trim().replaceAll(" +", " ");
					//
					//							//System.out.println(getLocalName()+" Removed junk: "+currentText);
					//
					//							//******@Begin making vectors*************************************************
					//							//Add processed texts to a list
					//							Scanner sc = new Scanner(currentText);
					//							List<String> list = new ArrayList<String>();
					//							String stringToken;
					//							double wordFreq = 0.0;
					//							int wordCount = 0;
					//							wordCount = currentText.split("\\s+").length;
					//
					//							//System.out.println("currentText: "+ currentText);
					//							//If processed text is a blank line with 1 single space or less than 3 words
					//							if (wordCount < 3)
					//							{
					//								//System.out.println("DO NOT ADD");
					//								tweetIdsToRemove.add(currTweetId);
					//							}
					//							else
					//							{
					//								while (sc.hasNext()){
					//									stringToken = sc.next();
					//									list.add(stringToken);
					//
					//									//Add all unique terms to allUniqueDocTerms
					//									allUniqueDocTerms.add(stringToken);
					//
					//									//Count frequency of words in a document
					//									if (userDocumentVector.get(stringToken)!=null) //already exists in vector
					//										wordFreq = userDocumentVector.get(stringToken)+1;
					//									else //does not exist in vector yet
					//										wordFreq = 1.0;
					//
					//									userDocumentVector.put(stringToken, wordFreq);
					//								}
					//								/*for (String s : list){
					//									System.out.print(s+" ");
					//								}
					//								System.out.println();*/
					//								sc.close();
					//								//System.out.println();
					//								//System.out.println("The length of string: "+currentText.length());
					//
					//								tweetIdDocumentVector.put(currTweetId, userDocumentVector);
					//								userDocumentVectorsList.add(userDocumentVector);
					//								//System.out.println("currTweetId: "+currTweetId);
					//								//System.out.println(userDocumentVector);
					//							}
					//
					//						} //end for (Long currTweetId : usersTweetIdsList.get(curName))
					//						//Case where after processing, some users may have no more useful words left in every document, only add > 0
					//						if (userDocumentVectorsList.size() > 0)
					//							allUserDocumentVectors.put(curName,userDocumentVectorsList);	
					//
					//					} //end for (String curName: usersTweetIdsList.keySet())

					//System.out.println("tweetIdText.size(): "+tweetIdText.size());

						System.out.println("XXXXXXXXXXXXX usableTweetCount: "
								+ usableTweetCount);

						long beforeAggregateTime = System.nanoTime();
						
						//Write to file  code added by Sepide
						if (algorithmRec == Doc2Vec
								&& WRITE_LEGACY_DOC2VEC_COMPATIBILITY_FILES)
						{
							writeDoc2VecUserDocumentFile();
						}
	                    
						// End of code added by Sepide 

						try
						{
							finishExactSvmCorpusSpoolWriting();
						}
						catch (Throwable failure)
						{
							handleAlgorithmFailure(failure);
							return;
						}
						
						if (VERBOSE_TEXT_PROCESSING_DEBUG)
						{
							for (String u : textProcessedUsers())
							{
								System.out.println(getLocalName()+" u: "+u);
							}
						}
					int followerCount = 0;
					String followeeName;
					List<String> followerNames;
					
					//adds only usable users after text processing and aggregating user documents to follower list					
					for (String currUser : textProcessedUsers())
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
							if (VERBOSE_TEXT_PROCESSING_DEBUG)
							{
								System.out.println(fNames);
							}
						}
					
					//-------------PRINTING OUT TF TO FILE***********************
					// FileWriter writer;
					// try {
						// writer = new FileWriter("tf_matrix_1000.txt", true); //append
						// BufferedWriter bufferedWriter = new BufferedWriter(writer);
						// bufferedWriter.write("\t\t");
						// for (String userNames : allUserDocuments.keySet())
						// {
							// bufferedWriter.write(userNames+"\t");
						// }
						// bufferedWriter.newLine();
						// for (String uniqueTerm: allUniqueDocTerms)
						// {
							// bufferedWriter.write(uniqueTerm+"\t\t");
							// for (String userNames : allUserDocuments.keySet())
							// {
								// double tfValue = 0.0;
								// if (allUserDocuments.get(userNames).containsKey(uniqueTerm))
									// tfValue = allUserDocuments.get(userNames).get(uniqueTerm);
								// bufferedWriter.write(tfValue+"\t");
							// }
							// bufferedWriter.newLine();
						// }
						// bufferedWriter.close();
					// } catch (IOException e) {
						// TODO Auto-generated catch block
						// e.printStackTrace();
					// }

					//					System.out.println("usersTweetIdsList.keySet(): "+usersTweetIdsList.keySet());
					//					for (String currentUser: usersTweetIdsList.keySet())
					//					{
					//						aggregatedUserTweets = new LinkedHashMap<String,Double>();
					//						for (long currTweetId: usersTweetIdsList.get(currentUser))
					//						{
					//							LinkedHashMap<String,Double> currTweetIdDocumentVector = tweetIdDocumentVector.get(currTweetId);
					//							for (String currTerm : currTweetIdDocumentVector.keySet())
					//							{
					//								double termFreq = 0.0;
					//								if (aggregatedUserTweets.containsKey(currTerm))
					//								{
					//									termFreq = aggregatedUserTweets.get(currTerm);
					//								}
					//
					//								termFreq += currTweetIdDocumentVector.get(currTerm);
					//								aggregatedUserTweets.put(currTerm,termFreq);
					//							}
					//						}
					//						allUserDocuments.put(currentUser, aggregatedUserTweets);
					//					}

					/*FileWriter writer;
					try {
						writer = new FileWriter("processedVectors.txt", true); //append
						BufferedWriter bufferedWriter = new BufferedWriter(writer);
						for (String currentUser: usersTweetIdsList.keySet())
						{
							bufferedWriter.write("---------------"+currentUser+"---------------");
							bufferedWriter.newLine();
							for (long currTweetId: usersTweetIdsList.get(currentUser))
							{
								LinkedHashMap<String,Double> currTweetIdDocumentVector = tweetIdDocumentVector.get(currTweetId);
								bufferedWriter.write(currTweetIdDocumentVector.toString());
								bufferedWriter.newLine();
							}
						}
						bufferedWriter.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}*/

					long aggregatedTime = System.nanoTime();
					System.out.println("##################################allUserDocuments*******************************");
					System.out.println(convertMs(aggregatedTime-beforeAggregateTime)+" ms");
//					for (String user: allUserDocuments.keySet())
//					{
//						System.out.print("user: "+user+" ");
//						System.out.println(allUserDocuments.get(user));
//						try {
//							FileWriter writer = new FileWriter("verification_docs/"+user+".txt", true); //append
//							BufferedWriter bufferedWriter = new BufferedWriter(writer);
//							for (String terms : allUserDocuments.get(user).keySet())
//							{
//								for (int i = 0; i < allUserDocuments.get(user).get(terms); i++)
//								{
//									bufferedWriter.write(terms);
//									bufferedWriter.newLine();
//								}
//							}
//							bufferedWriter.close();
//						} catch (IOException e) {
//							// TODO Auto-generated catch block
//							e.printStackTrace();
//						}
//					}

					//					try {
					//						FileWriter writer = new FileWriter("all_unique_terms.txt", true); //append
					//						BufferedWriter bufferedWriter = new BufferedWriter(writer);
					//						for (String terms : allUniqueDocTerms)
					//						{
					//							bufferedWriter.write(terms);
					//							bufferedWriter.newLine();
					//						}
					//						bufferedWriter.close();
					//					} catch (IOException e) {
					//						// TODO Auto-generated catch block
					//						e.printStackTrace();
					//					}
					//					System.out.println("tweetIdDocumentVector");
					//					System.out.println(tweetIdDocumentVector);


					//					int countDb2 = 0;
					//   					FileWriter writer;
					//					try {
					//						writer = new FileWriter("myOwnDBText.txt", true); //append
					//						BufferedWriter bufferedWriter = new BufferedWriter(writer);
					//						bufferedWriter.write("tweetIdText.size(): "+tweetIdText.size());
					//						bufferedWriter.newLine();
					//						for (Long l: tweetIdText.keySet())
					//						{
					//							countDb2++;
					//							bufferedWriter.write("TweetId: "+l);
					//							bufferedWriter.write("\t Text: "+tweetIdText.get(l));
					//							bufferedWriter.newLine();
					//						}
					//
					//						bufferedWriter.write("countDb2: "+countDb2);
					//						bufferedWriter.newLine();
					//						bufferedWriter.close();
					//					} catch (IOException e) {
					//						// TODO Auto-generated catch block
					//						e.printStackTrace();
					//					}


						endTimeTextProcessing = System.nanoTime();
						completionTimeTextProcessing =
								streamingTextProcessingNanos
								+ endTimeTextProcessing - startTimeTextProcessing;
						int processedTweetCountForDisplay = usableTweetCount;
						tweetIdText.clear();
						tweetIdUser.clear();
						tweetIdDocumentVector.clear();
						NodeExecutionMonitor.recordTextProcessingStats(
								getLocalName(),
								processedTweetCountForDisplay,
								allUserDocuments.size(),
								userRegisteredInRecAgent.size(),
								completionTimeTextProcessing);
						System.out.println(getLocalName()+" completionTimeTextProcessing: "+convertMs(completionTimeTextProcessing)+" ms");
					System.out.println(getLocalName()+ " After processing, tweets: "+ processedTweetCountForDisplay);

					myGui.appendResult(getLocalName()+"completionTimeTextProcessing: "+convertMs(completionTimeTextProcessing)+" ms");
					myGui.appendResult(getLocalName()+ "After processing, tweets: "+ processedTweetCountForDisplay);
					
					//@Jason added code to deny querying for any user who have no tweet in database after text processing and tweeting simulation is complete 					

					//System.out.println(getLocalName()+" userRegisteredInRecAgent: "+userRegisteredInRecAgent);

					ArrayList<String> usersToRemove = new ArrayList<String>();
					
					//System.out.println(getLocalName()+" allUserDocuments.keySet: "+allUserDocuments.keySet());
					//System.out.println(getLocalName()+" userRegisteredinRecAgent: "+userRegisteredInRecAgent);
					
					for(String currUser : userRegisteredInRecAgent){

						if (!hasTextProcessedUser(currUser)) {
							System.out.println(currUser+" DOES NOT EXIST IN DB");
							usersToRemove.add(currUser);

							String userAgent = currUser+"-UserAgent";
							System.out.println("userAgent: "+userAgent);
							ACLMessage stopUserQueryMsg = new ACLMessage (ACLMessage.REQUEST);
							stopUserQueryMsg.addReceiver(new AID(userAgent,AID.ISLOCALNAME));
							stopUserQueryMsg.setPerformative(ACLMessage.REQUEST);
							stopUserQueryMsg.setConversationId(runLifecycle.getActiveRunId());
							stopUserQueryMsg.setContent("Denied Querying");
							stopUserQueryMsg.setOntology("Denied Querying");
							send(stopUserQueryMsg);
							System.out.println(getLocalName()+" Sent out Denied Querying for "+userAgent);
						}
					}

					
					try {
						FileWriter writer10;
						writer10 = new FileWriter("numTextProcessed.txt", true); //append
						BufferedWriter bufferedWriter = new BufferedWriter(writer10);
						bufferedWriter.write(getLocalName()+ " Tweets After Processing: "+ processedTweetCountForDisplay+" Tweets Before Processing: "+ tweetsToReceive);
						bufferedWriter.newLine();
						bufferedWriter.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} 
					
					
					
					//   					int countDb = 0;
					//   					FileWriter writer2;
					//					try {
					//						writer2 = new FileWriter("myOwnDB.txt", true); //append
					//						BufferedWriter bufferedWriter = new BufferedWriter(writer2);
					//						bufferedWriter.write("usersTweetIdsList.size(): "+usersTweetIdsList.size());
					//						bufferedWriter.write("\tusersTweetIdsList.keySet(): "+usersTweetIdsList.keySet().size());
					//						bufferedWriter.newLine();
					//						for (String curUsername: usersTweetIdsList.keySet())
					//						{
					//							bufferedWriter.write("username: "+curUsername);
					//							bufferedWriter.write("\t tweetIds: "+usersTweetIdsList.get(curUsername).size());
					//							bufferedWriter.newLine();
					//							for (Long l : usersTweetIdsList.get(curUsername))
					//							{
					//								bufferedWriter.write("\ttweetId: "+l);
					//								bufferedWriter.newLine();
					//								countDb++;
					//							}
					//						}
					//
					//						bufferedWriter.write("countDb: "+countDb);
					//						bufferedWriter.newLine();
					//						bufferedWriter.close();
					//					} catch (IOException e) {
					//						// TODO Auto-generated catch block
					//						e.printStackTrace();
					//					} 

					//@Jason remove users from Starter Agent's counter
					//Only send remove message if there is a user to remove

					System.out.println(getLocalName()+" usersToRemove: "+usersToRemove);
					
					if (usersToRemove.size() > 0)
					{

						ACLMessage removeUsersMessage = new ACLMessage( ACLMessage.INFORM );
						removeUsersMessage.addReceiver(new AID("Starter Agent",AID.ISLOCALNAME));
						removeUsersMessage.setPerformative(ACLMessage.INFORM);
						removeUsersMessage.setConversationId(runLifecycle.getActiveRunId());
						try {
							removeUsersMessage.setContentObject(usersToRemove);
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						removeUsersMessage.setOntology("Remove Users From Total");
						send(removeUsersMessage);
						
						System.out.println(getLocalName()+" sent removeUserMessage");

					}
					//					Sent each user individually instead of the arraylist, more proper to agent fipa
					//					if (usersToRemove.size() > 0)
					//					{
					//
					//	   					ACLMessage removeUsersMessage = new ACLMessage( ACLMessage.INFORM );
					//	   					removeUsersMessage.addReceiver(new AID("Starter Agent",AID.ISLOCALNAME));
					//	   					removeUsersMessage.setPerformative(ACLMessage.INFORM);
					//	   					removeUsersMessage.setContent(String.valueOf(usersToRemove.size()));
					//	   					removeUsersMessage.setOntology("Remove Users From Total");
					//
					//
					//
					//   						send(removeUsersMessage);
					//
					//   					//@Jason remove users from allUserAgents list in Starter Agent
					//	   					removeUsersMessage.setOntology("Remove This User From List of Agents");
					//	   					for (String currentUser : usersToRemove){
					//	   						System.out.println(myAgent.getLocalName()+" send to Starter Agent to remove: "+currentUser);
					//	   						removeUsersMessage.setContent(currentUser+"-UserAgent");
					//	   						send(removeUsersMessage);
					//	   					}
					//					}  					


					//@Jason tell Starter Agent recommender agent is done text processing and only valid tweets available
					ACLMessage textProcessMessage = new ACLMessage( ACLMessage.INFORM );
					textProcessMessage.addReceiver(new AID("Starter Agent",AID.ISLOCALNAME));
					textProcessMessage.setPerformative(ACLMessage.INFORM);
					textProcessMessage.setConversationId(runLifecycle.getActiveRunId());
					textProcessMessage.setContent("Text Processing Complete");
					textProcessMessage.setOntology("Text Processing Complete");
					send(textProcessMessage);

					System.out.println(getLocalName()+ " Text Processing Complete");
					NodeExecutionMonitor.waitingForPeers(
							getLocalName(), processedTweetCountForDisplay);

					}

				//@Jason added new message to start recommending
			if (msg!=null
					&& "Start Recommend Algorithms".equals(msg.getOntology())
					&& msg.getPerformative()==ACLMessage.REQUEST
					&& runLifecycle.isActive(msg.getConversationId())
					&& calculateAlready==false)
			{

				calculateAlready=true;

				System.out.println(myAgent.getLocalName()+" Starting Algorithm: "+algorithmRec);

				System.out.println(myAgent.getLocalName()+" received Start Recommend Algorithms message");
				if (!waitForLivePhaseBoundary(
						"TEXT_PREPROCESS_USER -> FEATURE_BUILD"))
				{
					return;
				}
				NodeExecutionMonitor.buildingFeatures(
						getLocalName(),
						algorithmRec == Doc2Vec
								? "Preparing the shared document-vector model."
								: "Building TF-IDF and model-specific inputs.");

				try
				{
				if (algorithmRec == SVM && exactSvmDiskBackedWaves)
				{
					activeSvmLease =
							RecommendationMemoryCoordinator.acquire(
									getLocalName(),
									"Exact SVM disk-backed corpus wave and model execution",
									RecommendationMemoryCoordinator.Workload.SVM,
									estimateExactSvmWaveReservationBytes());
					materializeExactSvmCorpusSpool();
					RecommendationRunAbortRegistry.requireActive(
							runLifecycle.getActiveRunId());
				}
				if (algorithmRec == SVM && activeSvmLease == null)
				{
					boolean targetSpecificPreparation =
							isSvmBatchMode() || isClassifierGuidedUserMode();
					activeSvmLease =
							RecommendationMemoryCoordinator.acquire(
									getLocalName(),
									"Exact SVM feature preparation and isolated Weka worker",
									RecommendationMemoryCoordinator.Workload.SVM,
									RecommendationMemoryCoordinator
											.estimateSvmPreparationBytes(
													allUniqueDocTerms.size(),
													allUserDocuments.size(),
													countDocumentTerms(
															allUserDocuments),
													usersRec == null
															? 1 : usersRec.size(),
													datasetFollowees == null
															? 1 : datasetFollowees.size(),
													targetSpecificPreparation));
					RecommendationRunAbortRegistry.requireActive(
							runLifecycle.getActiveRunId());
				}
				if (algorithmRec == K_MEANS
						|| algorithmRec == K_MEANSEUCLIDEAN)
				{
					if (activeKMeansLease == null)
					{
						activeKMeansLease =
								RecommendationMemoryCoordinator.acquire(
										getLocalName(),
										algorithmRec == K_MEANS
												? "K-Means features and clustering"
												: "Euclidean K-Means features and clustering",
										RecommendationMemoryCoordinator.Workload.KMEANS,
										RecommendationMemoryCoordinator
												.estimateKMeansBytes(
														allUniqueDocTerms.size(),
														allUserDocuments.size(),
														countDocumentTerms(
																allUserDocuments)));
					}
				}

				/*//@Jason see users in local db
					int c=0;
					for(Entry<String,Double> currentUser : User_in_Server.entrySet()){
   						String currentUserID = currentUser.getKey();
   						c++;
   						System.out.println(myAgent.getLocalName()+" user "+c+": "+currentUserID);
					}
				 */

				startTimeTFIDF = System.nanoTime();

				System.out.println(getLocalName()+" Get Document Freq of Terms");
				int docFreq=0;
				boolean calculateGlobalTfidf = algorithmRec != Doc2Vec
						&& !(RecommendationModeSupport.isSvmFamilyAlgorithm(algorithmRec)
								&& isSvmBatchMode()
								&& !isClassifierGuidedUserMode());
				//Get the document frequency of terms
				
				//Make all unique terms from central for MLP inputs and num nodes > 1; Creates proper train/test files with correct num of attributes
				if (algorithmRec == MLP && numRecAgents > 1)  // Sepide changed MLP to CommonNeighbors
				//Make all unique terms from central training when SVM and num nodes > 1; Creates proper test files with correct num of attributes
				// if (algorithmRec == SVM  && numRecAgents > 1)
				{
					allUniqueDocTerms.clear();
					allUniqueDocTerms.addAll(centralUniqueDocTerms);
				}
				
				if (calculateGlobalTfidf)
				{
					for (String term : allUniqueDocTerms)
					{
						//Initialize allTermsDocumentFreq to 0
						allTermsDocumentFreq.put(term, 0);
					}

					for (String curName : allUserDocuments.keySet())
					{
						LinkedHashMap<String,Double> curDoc = allUserDocuments.get(curName);
						if (VERBOSE_TEXT_PROCESSING_DEBUG)
						{
							System.out.println("============================================");
						}
						//System.out.println(" Sepide testing if the document includes the users with their aggregated tweets" + allUserDocuments.get(curName));  // added by Sepide
						for (String docTerm : curDoc.keySet())
						{
							docFreq = allTermsDocumentFreq.get(docTerm);
							docFreq++;
							allTermsDocumentFreq.put(docTerm,docFreq);
						}
					}
				}
				
								// FileWriter writer11;
				// try {
					// writer11 = new FileWriter("df_freq.txt", true); //append
					// BufferedWriter bufferedWriter = new BufferedWriter(writer11);
						// for (String docTerm : allTermsDocumentFreq.keySet())
					// {
						// bufferedWriter.write(docTerm+"\t"+allTermsDocumentFreq.get(docTerm));
						// bufferedWriter.newLine();
					// }
					// bufferedWriter.close();
				// } catch (IOException e) {
					// e.printStackTrace();
				// }
				
				
				
//				for (String term : allUniqueDocTerms)
//				{
//					//Initialize allTermsDocumentFreq to 0
//					allTermsDocumentFreq.put(term, 0);
//
//					//for (String curName: allUserDocumentVectors.keySet())
//					for (String curName: allUserDocuments.keySet())
//					{
//						//for (LinkedHashMap<String,Double> curDoc : allUserDocumentVectors.get(curName))
//						//{
//						LinkedHashMap<String,Double> curDoc = allUserDocuments.get(curName);
//						for (String docTerm : curDoc.keySet())
//						{
//							if (term.equals(docTerm))
//							{
//								docFreq++;
//								break;
//							}
//
//						}
//						//}
//					}
//
//					allTermsDocumentFreq.put(term,docFreq);
//					docFreq=0;
//
//				}

//				System.out.println(getLocalName()+ " allTermsDocumentFreq: "+allTermsDocumentFreq);

				//*******************************@CALCULATE THE TF-IDF ************************************
				//tf-idf = tf * idf
				//tf log normalization= allUserDocumentVectors ; tf raw frequency = allUserDocumentVectors
				//idf smooth = log((totalDocuments/df)+1) //adjust for zero log(1); idf = log(totalDocuments/df)
				//df = allTermsDocumentFreq

				//Put tf-idf weights into documents
				double tf,df,idf,tfidf;
				//LinkedHashMap<String,ArrayList<LinkedHashMap<String,Double>>> allUserDocumentsTFIDF = new LinkedHashMap<String,ArrayList<LinkedHashMap<String,Double>>>();
				allUserDocumentsTFIDF = new LinkedHashMap<String,LinkedHashMap<String,Double>>();
				ArrayList<LinkedHashMap<String,Double>> userDocumentsTFIDFList = new ArrayList<LinkedHashMap<String,Double>>();
				ArrayList<LinkedHashMap<String,Double>> userDocumentsTFList = new ArrayList<LinkedHashMap<String,Double>>();
				//totalDocuments = tweetIdDocumentVector.size();
				totalDocuments = allUserDocuments.size();
				System.out.println("User Documents size: " + totalDocuments);
				System.out.println(getLocalName()+" Calculating TF-IDF");

				double vectorMagnitude=0.0;
				boolean preserveRawDocuments =
						algorithmRec == SCALABLE_SVM
						|| RecommendationModeSupport.isSvmFamilyAlgorithm(algorithmRec)
						&& (isSvmBatchMode() || isClassifierGuidedUserMode());
				/*
				//TF-IDF for each individual tweets
				for (String curName : usersTweetIdsList.keySet())
				{

					userDocumentsTFIDFList = new ArrayList<LinkedHashMap<String,Double>>();


					for (Long currTweetId: usersTweetIdsList.get(curName))
					{
						vectorMagnitude = 0.0;
						LinkedHashMap<String,Double> tweetIdDoc = tweetIdDocumentVector.get(currTweetId);

						LinkedHashMap<String,Double> userDocumentTFIDF = new LinkedHashMap<String,Double>();
						for (String docTerm : tweetIdDoc.keySet())
						{
							//tf=1+Math.log10(tweetIdDoc.get(docTerm)); //tf log normalization
							tf=tweetIdDoc.get(docTerm); //tf raw frequency
							df=allTermsDocumentFreq.get(docTerm);
							//idf=Math.log10((totalDocuments/df)+1); //idf smooth, adjust for zero log(1)
							//idf=Math.log10(totalDocuments/df); //log base 10
							idf=(double)Math.log(totalDocuments/df) / Math.log(2); //log base 2
							tfidf=tf*idf;
							userDocumentTFIDF.put(docTerm, tfidf);
							//System.out.println("docTerm: "+docTerm+"\ttf: "+tf+"\tdf: "+df+"\tidf: "+idf+"\ttfidf: "+tfidf);					
							vectorMagnitude+=tfidf*tfidf;
						}

						//System.out.println("tweetId: "+currTweetId+","+userDocumentTFIDF);

						vectorMagnitude = Math.sqrt(vectorMagnitude);

						//precalculate the magnitude of vectors and element-wise division of documents to the magnitude x./|x| ie. normalize the document to unit vectors
						double docSumMag = 0.0;
						for (String docTerm : tweetIdDoc.keySet())
						{
							tfidf = userDocumentTFIDF.get(docTerm);
							tfidf = tfidf / vectorMagnitude;
							userDocumentTFIDF.put(docTerm,tfidf);

							docSumMag += tfidf*tfidf;
						}

						//System.out.println("Normalized_tweetId: "+currTweetId+","+userDocumentTFIDF);

						//System.out.println(Math.sqrt(docSumMag));

						userDocumentsTFIDFList.add(userDocumentTFIDF);

						tweetIdTFIDF.put(currTweetId, userDocumentTFIDF);

					} //end for (Long currTweetId: usersTweetIdsList.get(curName)) 

					allUserDocumentsTFIDF.put(curName,userDocumentsTFIDFList);
				} //end for (String curName : usersTweetIdsList.keySet())
				 */
				//Tf-idf for aggregated tweets
				if (calculateGlobalTfidf)
				{
				for (String curName : allUserDocuments.keySet())
				{
					vectorMagnitude = 0.0;
					LinkedHashMap<String,Double> userDoc = allUserDocuments.get(curName);
					LinkedHashMap<String,Double> userDocumentTFIDF =
							preserveRawDocuments
									? new LinkedHashMap<String,Double>() : userDoc;
					LinkedHashMap<String,Double> userDocumentDOC2VEC = new LinkedHashMap<String,Double>();  // added by Sepide

					for (String docTerm : userDoc.keySet())
					{
						//tf=1+Math.log10(tweetIdDoc.get(docTerm)); //tf log normalization
						tf=userDoc.get(docTerm); //tf raw frequency
						//tf = tf/userDoc.keySet().size(); //tf normalized by document
						df=allTermsDocumentFreq.get(docTerm);
						//idf=Math.log10((totalDocuments/df)+1); //idf smooth, adjust for zero log(1)
						//idf=Math.log10(totalDocuments/df); //log base 10
						idf=(double)Math.log10(totalDocuments/df) / Math.log10(2); //log base 2
						tfidf=tf*idf;
						
						//Case for distributed SVM
						if (df == 0 || Double.isNaN(tfidf))
							tfidf=0.0;
						
						userDocumentTFIDF.put(docTerm, tfidf);
//						System.out.println("docTerm: "+docTerm+"\ttf: "+tf+"\tdf: "+df+"\tidf: "+idf+"\ttfidf: "+tfidf);
						vectorMagnitude+=tfidf*tfidf;
					}

					//System.out.println("tweetId: "+currTweetId+","+userDocumentTFIDF);

					vectorMagnitude = Math.sqrt(vectorMagnitude);

					// FileWriter writer20;
					// try {
						// writer20 = new FileWriter("tfidf_before_norm.txt",true);
						// BufferedWriter bufferedWriter = new BufferedWriter(writer20);
						// bufferedWriter.write(curName+"\t");
						// for (String docTerm : userDoc.keySet())
						// {
							// bufferedWriter.write(docTerm+" "+userDocumentTFIDF.get(docTerm)+"\t");
						// }
						// bufferedWriter.newLine();
						// bufferedWriter.close();
					// }	catch (IOException e) {
							// e.printStackTrace();
					// }
					
					//precalculate the magnitude of vectors and element-wise division of documents to the magnitude x./|x| ie. normalize the document to unit vectors
					double docSumMag = 0.0;
					for (String docTerm : userDoc.keySet())
					{
						tfidf = userDocumentTFIDF.get(docTerm);
						tfidf = tfidf / vectorMagnitude;
						if (Double.isNaN(tfidf))
						{
							tfidf = 0.0;
							System.out.println("ENTERED A NAN");
						}
							
						
						userDocumentTFIDF.put(docTerm,tfidf);
						docSumMag += tfidf*tfidf;
						
					}

					//System.out.println("Normalized_tweetId: "+currTweetId+","+userDocumentTFIDF);

					//System.out.println(Math.sqrt(docSumMag));

					allUserDocumentsTFIDF.put(curName,userDocumentTFIDF);
				// } //end for (String curName : usersTweetIdsList.keySet())
				} //String curName : allUserDocuments.keySet()
				}
				System.out.println();

				endTimeTFIDF = System.nanoTime();
				completionTimeTFIDF = endTimeTFIDF - startTimeTFIDF;
				System.out.println(getLocalName()+" completionTimeTFIDF: "+convertMs(completionTimeTFIDF)+" ms");
				myGui.appendResult(getLocalName()+" completionTimeTFIDF: "+convertMs(completionTimeTFIDF)+" ms");
				NodeExecutionMonitor.recordFeatureStats(
						getLocalName(),
						allUserDocumentsTFIDF.size(),
						allUniqueDocTerms.size(),
						allTermsDocumentFreq.size(),
						completionTimeTFIDF);

					if (VERBOSE_TEXT_PROCESSING_DEBUG)
					{
						for (String userHere: allUserDocumentsTFIDF.keySet())
						{
							System.out.println(getLocalName()+" userHere: "+userHere+" "+allUserDocumentsTFIDF.get(userHere).size());
						}
					}
				
				//WORD CHECKING
				// FileWriter writerWords;
				// try{
					// String filename;
					// File wordDir = new File("Dataset/WordCheck/GEN_14kSet/");
					// if (!wordDir.exists())
					// {
							// wordDir.mkdirs();
					// }
					// for (String user : allUserDocumentsTF.keySet())
					// {
						// filename = "Dataset/WordCheck/GEN_14kSet/"+user+"_wordsTF.txt";
						// writerWords = new FileWriter(filename,true);
						// Map<String,Double> userDocTF = allUserDocumentsTF.get(user);
						// BufferedWriter bwWords = new BufferedWriter(writerWords);
						// bwWords.write(user);
						// bwWords.newLine();
						// bwWords.newLine();
						// for (String word : userDocTF.keySet())
						// {
							// bwWords.write(word+"\t"+userDocTF.get(word));
							// bwWords.newLine();
						// }
						// bwWords.close();
					// }
					
				// }
				// catch (IOException e)
				// {
					// e.printStackTrace();
				// }
				
				// try{
					// String filename;
					// File wordDir = new File("Dataset/WordCheck/GEN_14kSet/");
					// if (!wordDir.exists())
					// {
							// wordDir.mkdirs();
					// }
					// for (String user : allUserDocumentsTFIDF.keySet())
					// {
						// filename = "Dataset/WordCheck/GEN_14kSet/"+user+"_wordsTFIDF.txt";
						// writerWords = new FileWriter(filename,true);
						// Map<String,Double> userDocTFIDF = allUserDocumentsTFIDF.get(user);
						// BufferedWriter bwWords = new BufferedWriter(writerWords);
						// bwWords.write(user);
						// bwWords.newLine();
						// bwWords.newLine();
						// for (String word : userDocTFIDF.keySet())
						// {
							// bwWords.write(word+"\t"+userDocTFIDF.get(word));
							// bwWords.newLine();
						// }
						// bwWords.close();
					// }
					
				// }
				// catch (IOException e)
				// {
					// e.printStackTrace();
				// }
				
				//-------------PRINTING OUT TF-IDF TO FILE***********************
				

				
				if (WRITE_TFIDF_DEBUG_FILES)
				{
					FileWriter writer11;
				    try {   // from line 1602 to 1626 is uncommented  
						 writer11 = new FileWriter("tfidf_matrix.txt", true); //append
						 BufferedWriter bufferedWriter = new BufferedWriter(writer11);
						 bufferedWriter.write(totalDocuments+"\t");
						 for (String userNames : allUserDocumentsTFIDF.keySet())
						 {
							 bufferedWriter.write(userNames+"\t");
						  }
						   bufferedWriter.newLine();
						   for (String uniqueTerm: allUniqueDocTerms)
						  {
							  bufferedWriter.write(uniqueTerm+"\t");
							  for (String userNames : allUserDocumentsTFIDF.keySet())
							  {
								  double tfidfValue = 0.0;
								  if (allUserDocumentsTFIDF.get(userNames).containsKey(uniqueTerm))
									  tfidfValue = allUserDocumentsTFIDF.get(userNames).get(uniqueTerm);
								  bufferedWriter.write(tfidfValue+"\t");
							  }
							   bufferedWriter.newLine();
						   }
						   bufferedWriter.close();
					   } catch (IOException e) {
						  e.printStackTrace();
					  } 

					try {
						int uniqueWordsSize = allUniqueDocTerms.size();
						writer11 = new FileWriter("uniqueWords.txt", true); //append
						BufferedWriter bufferedWriter = new BufferedWriter(writer11);
						bufferedWriter.write(String.valueOf(uniqueWordsSize));
						bufferedWriter.newLine();
						bufferedWriter.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
					
					try {
						writer11 = new FileWriter("docFreqWords.txt", true); //append
						BufferedWriter bufferedWriter = new BufferedWriter(writer11);
						for (String docTerm: allTermsDocumentFreq.keySet())
						{
							bufferedWriter.write(docTerm+"\t"+allTermsDocumentFreq.get(docTerm));
							bufferedWriter.newLine();
						}
						
						bufferedWriter.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			
				//-------------PRINTING OUT TF TO FILE***********************
				
//				try {
//					writer = new FileWriter("tf_matrix.txt", true); //append
//					BufferedWriter bufferedWriter = new BufferedWriter(writer);
//					bufferedWriter.write("\t\t");
//					for (String userNames : allUserDocumentsTF.keySet())
//					{
//						bufferedWriter.write(userNames+"\t");
//					}
//					bufferedWriter.newLine();
//					for (String uniqueTerm: allUniqueDocTerms)
//					{
//						bufferedWriter.write(uniqueTerm+"\t\t");
//						for (String userNames : allUserDocumentsTF.keySet())
//						{
//							double tfValue = 0.0;
//							if (allUserDocumentsTF.get(userNames).containsKey(uniqueTerm))
//								tfValue = allUserDocumentsTF.get(userNames).get(uniqueTerm);
//							bufferedWriter.write(tfValue+"\t");
//						}
//						bufferedWriter.newLine();
//					}
//					bufferedWriter.close();
//				} catch (IOException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}

//-----------------------------------Original spot for usersRec----------------------------------------
//				usersRec = myGui.getUsersRec();
//				System.out.println(getLocalName()+" usersRec: "+usersRec);

				//System.out.println(getLocalName()+" allUserDocumentsTFIDF: "+allUserDocumentsTFIDF);

				//@Jason added conditions to determine what algorithms to use for recommender

				//Only used for classifiers for Weka
				String trainSetFilePath = "";
				String testSetFilePath = "";
				String recommendSetFilePath = "";
				String dataSetFilePath = "";
				String dataSetFilePath1 = "";
				String trainSetFilePath1 = "";
				
				
				//Create Arff files for Weka, *INCORRECT*Only determineTrainingTestSet when it is a single node and algorithmRec == SVM
				 if (algorithmRec == SVM
						 && !isSvmBatchMode()
						 && !isClassifierGuidedUserMode())
				{
					
					System.out.println("Test Sepide to see if the arff file is created");
					String arffDirName = "Dataset/Arff_files/";
					File arffDir = new File(arffDirName);
					if (!arffDir.exists())
					{
							arffDir.mkdirs();
					}
					
					// if (numRecAgents < 2)
					// {
					determineTrainingTestSet();
					dataSetUsers = new ArrayList<String>(trainSetUsers);
					dataSetUsers.addAll(testSetUsers);
					System.out.println(getLocalName()+" test set "+ testSetUsers.size() +": "+testSetUsers);
					System.out.println(getLocalName()+" train set "+ trainSetUsers.size() +": "+trainSetUsers);
					System.out.println(getLocalName()+" data set "+ dataSetUsers.size() +": "+dataSetUsers);  // added by Sepide
											
					//Create the arff training file attributes
					FileWriter trainWriter;	
                    FileWriter dataWriter;					
					BufferedWriter bufferedWriterTrain;
					BufferedWriter bufferedWriterData;
					
					int uniqueWordCountTrain = 0;
					trainSetFilePath = arffDirName + "train_set_rec"+nodeNumber+".txt";
					trainSetFilePath = arffDirName + "train_set_rec"+nodeNumber+".txt";
					trainSetFilePath1 = arffDirName + "train_set_rec1"+nodeNumber+".txt";  // added by Sepide 
					dataSetFilePath = arffDirName + "data_set_rec"+nodeNumber+".txt";
					dataSetFilePath1 = arffDirName + "data_set_rec1"+nodeNumber+".txt";    // added by Sepide 
									
					try {
							trainWriter = new FileWriter(trainSetFilePath, false);
                            dataWriter = new FileWriter(dataSetFilePath, false);							
							bufferedWriterTrain = new BufferedWriter(trainWriter);
							bufferedWriterData = new BufferedWriter(dataWriter);
										
							bufferedWriterTrain.write("@relation trainingSet");
							bufferedWriterData.write("@relation dataSet");
							bufferedWriterTrain.newLine();
							bufferedWriterTrain.newLine();
							bufferedWriterData.newLine();
							bufferedWriterData.newLine();
														
							for (String uniqueWord: allUniqueDocTerms)
							{
								bufferedWriterTrain.write("@attribute word"+ uniqueWordCountTrain +" numeric");
								bufferedWriterData.write("@attribute word"+ uniqueWordCountTrain +" numeric");
								bufferedWriterTrain.newLine();
                                bufferedWriterData.newLine();								
								uniqueWordCountTrain++;
							}
							
							String attributeClass = buildArffClassAttribute(followeeFollowers.keySet());
							
							bufferedWriterTrain.write(attributeClass);
							bufferedWriterData.write(attributeClass);
							bufferedWriterTrain.newLine();
							bufferedWriterData.newLine();
							bufferedWriterTrain.newLine();
							bufferedWriterData.newLine();
							bufferedWriterTrain.newLine();
							bufferedWriterData.newLine();
							bufferedWriterTrain.write("@data");
							bufferedWriterData.write("@data");
							bufferedWriterTrain.newLine();
                            bufferedWriterData.newLine();							
							
							bufferedWriterTrain.close();
							bufferedWriterData.close();
							
					}
					catch (IOException e) {
						e.printStackTrace();
					}
					
					//Write the vector data of each user for training, test, recommend set to arff files
					try {
							trainWriter = new FileWriter(trainSetFilePath, true);
							dataWriter = new FileWriter(dataSetFilePath, true);
							bufferedWriterTrain = new BufferedWriter(trainWriter);
							bufferedWriterData = new BufferedWriter(dataWriter);

							StringJoiner tfidfJoiner;
							double currTfidf;
															
							 for (String currUser : trainSetUsers)
							//S for (String currUser : dataSetUsers)
							{
								// tfidfJoiner = new StringJoiner(",");
								Map<String,Double> currDocTfidf = allUserDocumentsTFIDF.get(currUser);
								
								// for (String uniqueWord: allUniqueDocTerms)
								// {
									// if (currDocTfidf.keySet().contains(uniqueWord))
										// currTfidf = currDocTfidf.get(uniqueWord);
									// else
										// currTfidf = 0.0;
									
									// tfidfJoiner.add(String.valueOf(currTfidf));
								// }
								SparseArffWriter.writeRow(bufferedWriterTrain, currDocTfidf,
										allUniqueDocTerms, userFollowee.get(currUser));
								SparseArffWriter.writeRow(bufferedWriterData, currDocTfidf,
										allUniqueDocTerms, userFollowee.get(currUser));
								//S bufferedWriterData.write(tfidfJoiner.toString());
								
							}
							bufferedWriterTrain.close();
                            							

					
					
					 for (String currUser : dataSetUsers)
							{
								// tfidfJoiner = new StringJoiner(",");
								Map<String,Double> currDocTfidf = allUserDocumentsTFIDF.get(currUser);
								
								// for (String uniqueWord: allUniqueDocTerms)
								// {
									// if (currDocTfidf.keySet().contains(uniqueWord))
										// currTfidf = currDocTfidf.get(uniqueWord);
									// else
										// currTfidf = 0.0;
									
									// tfidfJoiner.add(String.valueOf(currTfidf));
								// }
								SparseArffWriter.writeRow(bufferedWriterData, currDocTfidf,
										allUniqueDocTerms, userFollowee.get(currUser));
								//S bufferedWriterData.write(tfidfJoiner.toString());
								
							}
							bufferedWriterData.close();
							
						}
					catch (IOException e) {
						e.printStackTrace();
					}
					
					/* Code added by Sepide for Word embedding part   */

                     //try {
						//Map<Integer,String> mapEmbedding = new HashMap<Integer,String>();
						//Scanner scanner = new Scanner(new FileReader("vectors-corrected.txt"));
						//int i =0;
						//while (scanner.hasNextLine()) {
								
								//String[] columns = scanner.nextLine().split("\t");

                                //mapEmbedding.put(i,columns[0]);
								//i++;
							            
                         //}
							//trainWriter = new FileWriter(trainSetFilePath1, true);
							//dataWriter = new FileWriter(dataSetFilePath1, true);
							//bufferedWriterTrain = new BufferedWriter(trainWriter);
							//bufferedWriterData = new BufferedWriter(dataWriter);
							//File file=new File("vectors-corrected.txt");    //creates a new file instance
                            //FileReader fr=new FileReader(file);   //reads the file
                            //BufferedReader br=new BufferedReader(fr);  //creates a buffering character input stream   							
                            //String line; 
							//line=br.readLine();
							
							//StringJoiner tfidfJoiner;
							//double currTfidf;
															
							//S for (String currUser : trainSetUsers)
							//for (String currUser : dataSetUsers)
							//{
								// tfidfJoiner = new StringJoiner(",");
								//Map<String,Double> currDocTfidf = allUserDocumentsTFIDF.get(currUser);
								
								// for (String uniqueWord: allUniqueDocTerms)
								// {
									// if (currDocTfidf.keySet().contains(uniqueWord))
										// currTfidf = currDocTfidf.get(uniqueWord);
									// else
										// currTfidf = 0.0;
									
									// tfidfJoiner.add(String.valueOf(currTfidf));
								// }
								//tfidfJoiner = vectorArffFormat(mapEmbedding,allUniqueDocTerms);
								
								//bufferedWriterTrain.write(tfidfJoiner.toString() + "," + userFollowee.get(currUser));
								//bufferedWriterData.write(tfidfJoiner.toString() + "," + userFollowee.get(currUser));
								//S bufferedWriterData.write(tfidfJoiner.toString());
								//bufferedWriterTrain.newLine();
								//bufferedWriterData.newLine();
								//line=br.readLine();
								
							//}
							//bufferedWriterTrain.close();
                            //bufferedWriterData.close();						
                            
							
                            //line=br.readLine();
							//double input1_vector[] = new double[70];
							//double input2_vector[] = new double[70];
							//if (line != null) {
								//input1_vector.add(line);
								//line=br.readLine();
								//input2_vector.add(line);
							//}
								
                            //double dot_product = Nd4j.getBlasWrapper().dot(input1_vector[].class, input2_vector[].class);
                            
							 //double cosine_similarity(double[] input1_vector, double[] input2_vector, double dot_product){
									//double norm_a = 0.0;
									//double norm_b = 0.0;
								  //Its assumed input1_vector and input2_vector have same length (300 dimensions)
									//for (int i = 0; i < input1_vector.length; i++) 
								   // {
										//norm_a += Math.pow(input1_vector[i], 2);
										//norm_b += Math.pow(input2_vector[i], 2);
									//}   
								  //double cosine_sim = (dot_product / (Math.sqrt(norm_a) * Math.sqrt(norm_b)));
								  ///return cosine_sim;
							     // }
							
							//double cosine_sim = cosine_similarity(input1_vector[].class, input2_vector[].class, dot_product);

							 

					//}
					//catch (IOException e) {
						//e.printStackTrace(); 
					//}					
					
					/* End of code added by Sepide for Word embedding part */
					
					// }
					// else
					// {
						// trainSetFilePath = arffDirName + "train_set_controller.txt";
						// testSetUsers = new ArrayList<String>();
						// /*for (String testUser : allUserDocumentsTFIDF.keySet())*/
						// for (AID testUser : allUserAgentConnectedtoThisServer)
						// {
							// String testUserName = testUser.getLocalName().split("-")[0];
							// testSetUsers.add(testUserName);
							// System.out.println(getLocalName()+" testUser: "+testUserName);
						// }
						/*testset is list of original users connected to rec agent (does not include duplicated recommended user)*/
						
						// System.out.println(getLocalName()+" testSetUsers.size: "+testSetUsers.size());
					// }
					
					
					FileWriter testWriter;
					FileWriter recommendWriter;
					BufferedWriter bufferedWriterTest;
					BufferedWriter bufferedWriterRecommend;
					int uniqueWordCountTest = 0;
					testSetFilePath = arffDirName + "test_set_rec"+nodeNumber+".txt";
					recommendSetFilePath = arffDirName + "recommend_set_rec"+nodeNumber+".txt";
					try{
						testWriter = new FileWriter(testSetFilePath, false);
						recommendWriter = new FileWriter(recommendSetFilePath, false);
						
						bufferedWriterTest = new BufferedWriter(testWriter);
						bufferedWriterTest.write("@relation testSet");
						bufferedWriterTest.newLine();
						bufferedWriterTest.newLine();
						
						bufferedWriterRecommend = new BufferedWriter(recommendWriter);
						bufferedWriterRecommend.write("@relation recommendSet");
						bufferedWriterRecommend.newLine();
						bufferedWriterRecommend.newLine();
						
						for (String uniqueWord: allUniqueDocTerms)
						{
							bufferedWriterTest.write("@attribute word"+ uniqueWordCountTest +" numeric");
							bufferedWriterTest.newLine();
							bufferedWriterRecommend.write("@attribute word"+ uniqueWordCountTest +" numeric");
							bufferedWriterRecommend.newLine();		
							uniqueWordCountTest++;
						}
						
						String attributeClass = buildArffClassAttribute(followeeFollowers.keySet());
						
						
						bufferedWriterTest.write(attributeClass);
						bufferedWriterTest.newLine();
						bufferedWriterTest.newLine();
						bufferedWriterTest.newLine();
						bufferedWriterTest.write("@data");
						bufferedWriterTest.newLine();
						
						bufferedWriterRecommend.write(attributeClass);
						bufferedWriterRecommend.newLine();
						bufferedWriterRecommend.newLine();
						bufferedWriterRecommend.newLine();
						bufferedWriterRecommend.write("@data");
						bufferedWriterRecommend.newLine();
						
						bufferedWriterTest.close();
						bufferedWriterRecommend.close();
						
					}
					catch (IOException e)
					{
						e.printStackTrace();
					}
					
					//Write the vector data of each user for training, test, recommend set to arff files
					try {
						testWriter = new FileWriter(testSetFilePath, true);
						recommendWriter = new FileWriter(recommendSetFilePath, true);
						bufferedWriterTest = new BufferedWriter(testWriter);
						bufferedWriterRecommend = new BufferedWriter(recommendWriter);

						StringJoiner tfidfJoiner;
						double currTfidf;
						
						for (String currUser : testSetUsers)
						{
							System.out.println(getLocalName()+" currUser: "+currUser);
							Map<String,Double> currDocTfidf = allUserDocumentsTFIDF.get(currUser);
							// System.out.println(getLocalName()+" currUser tfidf: ");
							// StringJoiner tfidfString = new StringJoiner(",");
							// for (String wordTfidf: currDocTfidf.keySet())
							// {
								// tfidfString.add(String.valueOf(currDocTfidf.get(wordTfidf)));
							// }
							// System.out.println(tfidfString.toString());
							
							SparseArffWriter.writeRow(bufferedWriterTest, currDocTfidf,
									allUniqueDocTerms, userFollowee.get(currUser));
						}
						bufferedWriterTest.close();

						for (String currUser : usersRec)
						{
							Map<String,Double> currDocTfidf = allUserDocumentsTFIDF.get(currUser);							
							SparseArffWriter.writeRow(bufferedWriterRecommend, currDocTfidf,
									allUniqueDocTerms, userFollowee.get(currUser));
						}
						bufferedWriterRecommend.close();	
					}
					catch (IOException e) {
						e.printStackTrace();
					}
					
				}
				
				// Code added for Doc2Vec by Sepide on Nov.3 
				String dataSetFilePath4 = "";
				if (algorithmRec == Doc2Vec
						&& WRITE_LEGACY_DOC2VEC_COMPATIBILITY_FILES)  {
					
					String importantStuffDirName = "important-stuff/";
					File importantStuffDir = new File(importantStuffDirName);
					if (!importantStuffDir.exists())
					{
							importantStuffDir.mkdirs();
					}
					
					String doc2vecDirName = "Dataset/424k/";
					File doc2vecDir = new File(doc2vecDirName);
					if (!doc2vecDir.exists())
					{
							doc2vecDir.mkdirs();
					}
					
					// commenetd out on Nov. 17 
					/*File followeeRecName = new File(importantStuffDirName+ "followeeRec.txt");
					if(followeeRecName.exists() && !followeeRecName.isDirectory()){
						
						followeeRecName.delete();
					}  */
					
						int numFiles = Integer.parseInt(myGui.numNodesField.getText());
						try {
							if ("1".equals(nodeNumber))
							{
								writeDoc2VecCompatibilityPartFiles(
										getConfiguredDatasetFile(),
										doc2vecDirName,
										numFiles,
										usersRec.get(0).toString());
							}
							else
							{
								System.out.println(getLocalName()+" skipping Doc2Vec compatibility file split; Recommender-ServiceAgent1 handles it");
							}
						}
						catch (IOException e) {
						  System.out.println("An error occurred.");
						  e.printStackTrace();
						}
					
				    determineTrainingTestSet();
					dataSetUsers = new ArrayList<String>(trainSetUsers);
					dataSetUsers.addAll(testSetUsers);	
					System.out.println(getLocalName()+" data set "+ dataSetUsers.size() +": "+dataSetUsers);
					FileWriter dataWriter;
					BufferedWriter bufferedWriterData = null;
					
					dataSetFilePath4 = doc2vecDirName + "data_set_doc2vec_"+nodeNumber+".txt";
					
					try {
					
					    dataWriter = new FileWriter(dataSetFilePath4, false);
						bufferedWriterData = new BufferedWriter(dataWriter);
						
						for (Map.Entry<String,LinkedHashMap<String,Double>> entry : allUserDocuments.entrySet()) 
							{
								LinkedHashMap<String,Double> entry2 = entry.getValue();
								
								for (Map.Entry<String,Double> entry3 : entry2.entrySet()){
								bufferedWriterData.write(entry3.getKey()+ " ");
								
							   }
							   bufferedWriterData.write("\t");
							   bufferedWriterData.write(entry.getKey());
							   bufferedWriterData.newLine();
							}
							
								
					     bufferedWriterData.flush();
					}
					
					catch (IOException e) {
					  System.out.println("An error occurred.");
					  e.printStackTrace();
					}
					
					finally {

					try {

						// always close the writer
						bufferedWriterData.close();
					}
					catch (Exception e) {
					}
				  }
				  
          }
				
				// End of Code added for Doc2Vec by Sepide on Nov. 3

				int preparedModelFeatureCount = allUniqueDocTerms.size();
				if (algorithmRec != SCALABLE_SVM
						&& !(RecommendationModeSupport.isSvmFamilyAlgorithm(algorithmRec)
						&& (isSvmBatchMode() || isClassifierGuidedUserMode())))
				{
					allUserDocuments.clear();
				}
				allTermsDocumentFreq.clear();
				if (algorithmRec != MLP)
				{
					allUniqueDocTerms.clear();
				}

				NodeExecutionMonitor.recordModelShape(
						getLocalName(),
						RecommendationModeSupport.algorithmName(algorithmRec),
						preparedModelFeatureCount,
						allUserDocumentsTFIDF.size(),
						followeeFollowers == null ? -1 : followeeFollowers.size(),
						usersRec == null ? -1 : usersRec.size(),
						"Features: " + preparedModelFeatureCount
								+ ", documents: " + allUserDocumentsTFIDF.size()
								+ ", targets: "
								+ (usersRec == null ? 0 : usersRec.size()));
				if (!waitForLivePhaseBoundary(
						"FEATURE_BUILD -> MODEL_STAGE"))
				{
					return;
				}
				NodeExecutionMonitor.modelRunning(
						getLocalName(),
						"Executing "
								+ RecommendationModeSupport.algorithmName(algorithmRec)
								+ " for this node's assigned shard.");

				//Cosine Similarity
				if (algorithmRec == COS_SIM)
				{
					//@Jason prevent sleep from windows
					/*try {
					        new Robot().mouseMove(new Random().nextInt(1920),new Random().nextInt(1080));		         
					    } catch (AWTException e) {
					        e.printStackTrace();
					    }
					 */
					//-----------------------------CALCULATE COS-SIM SCORES-----------------------------

					startTimeAlgorithm = System.nanoTime();

					//setup array of users
					String[] users = new String[allUserDocumentsTFIDF.keySet().size()];
					String[] usersForRec = new String[usersRec.size()];
					users = allUserDocumentsTFIDF.keySet().toArray(users);
					usersForRec = usersRec.toArray(usersForRec);

					System.out.println(getLocalName()+ "CALCULATING COS-SIM SCORES");

					int localResultLimit = getCosSimLocalResultLimit();
					if (isExperimentalCandidateSchedulingEnabled(COS_SIM))
					{
						allUserScores = runExperimentalCandidateShardScheduling(
								users,
								usersForRec,
								false,
								localResultLimit);
					}
					else
					{
						allUserScores = initializeTargetScoreRows(usersRec, users);
						for (int i = 0; i < usersForRec.length; i++)
						{
							String recUser = usersForRec[i];
							LinkedHashMap<String, Double> recDoc = allUserDocumentsTFIDF.get(recUser);
							TreeMap<String,Double> userScores = new TreeMap<String,Double>();

							if (recDoc != null)
							{
								for (int j = 0; j < users.length; j++)
								{
									String candidateUser = users[j];
									if (!recUser.equals(candidateUser))
									{
										LinkedHashMap<String, Double> candidateDoc = allUserDocumentsTFIDF.get(candidateUser);
										double score = calculateSparseCosineScore(recDoc, candidateDoc);
										userScores.put(candidateUser, score);
									}
								}
							}

							if (numRecAgents > 1 && !isUserRankerFolloweeMode())
							{
								userScores = keepTopCosSimScores(userScores, localResultLimit);
							}
							allUserScores.put(recUser, userScores);
						}
					}


					// System.out.println(getLocalName()+" After COS SIM scores: "+allUserScores);

					endTimeAlgorithm = System.nanoTime();
					completionTimeAlgorithm = endTimeAlgorithm - startTimeAlgorithm;

					//Output for cosSIM
					textprocessing_wb_or_tfidf_Data.add("CosSim=TP+TFIDF+CosSim" + "\t" + agentName + "\t" + tweetCount + "\t" + completionTimeTextProcessing + "\t" + completionTimeTFIDF    + "\t" + completionTimeAlgorithm + "\t" + System.getProperty("line.separator"));
					System.out.println(agentName+"- Total Tweets Processed: " + tweetCount + " TP: " + convertMs(completionTimeTextProcessing) + " TFIDF: " + convertMs(completionTimeTFIDF) + " CosSim: " + convertMs(completionTimeAlgorithm) + " Total: " + convertMs(completionTimeTextProcessing+completionTimeTFIDF+completionTimeAlgorithm));
//					myGui.appendResult(agentName+"\nTotal Tweets Processed: " + tweetCount + " TP:" + round(completionTimeTextProcessing/1000000.00,2) + "ms TFIDF:" + round(completionTimeTFIDF/1000000.00,2) + "ms CosSim:" + round(completionTimeAlgorithm/1000000.00,2) + "ms Total:" + round((completionTimeTextProcessing+completionTimeTFIDF+completionTimeAlgorithm)/1000000.00,2)+"ms");
					myGui.appendResult(agentName+"\nTotal Tweets Processed: " + tweetCount + " TP: " + convertMs(completionTimeTextProcessing) + " ms TFIDF: " + convertMs(completionTimeTFIDF) + " ms CosSim: " + convertMs(completionTimeAlgorithm) + " ms Total: " + convertMs(completionTimeTextProcessing+completionTimeTFIDF+completionTimeAlgorithm)+" ms");
				
				   // Code added by Sepide
				   
				  //ArrayList<String> allUsersSep = MobileAgent.sepArray;
				   
				   /* try {
					   
				   //ArrayList<String> followeeNameSep = userFollowee.get(usersRec);
				   System.out.println(getLocalName()+" usersRec: "+usersRec);
				   BufferedWriter writerSepFollowee = new BufferedWriter(new FileWriter("D:/important-stuff/outPutSepFollowee.txt",true));
				   for(String str: usersRec) {
						  writerSepFollowee.write(str);
						  //String followeeNameSep = followeeFollowers.getKey("Germany");
							String followeeSep = userFollowee.get(str);
							// Commented out Oct 23 writerSepFollowee.newLine();
							writerSepFollowee.write(" Followes ");
							writerSepFollowee.write(followeeSep);							
						    writerSepFollowee.newLine();
					}
				   
				   writerSepFollowee.close();
					   
				   }
				   
				   catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}  */  
					
					// End of code added by Sepide 
				   
				
					}

					// Common Neighbors over the user-term graph.
					else if (algorithmRec == CommonNeighbors)
					{
						startTimeAlgorithm = System.nanoTime();

						String[] users = new String[allUserDocumentsTFIDF.keySet().size()];
						String[] usersForRec = new String[usersRec.size()];
						users = allUserDocumentsTFIDF.keySet().toArray(users);
						usersForRec = usersRec.toArray(usersForRec);

						System.out.println(getLocalName()
								+ " CALCULATING COMMON-NEIGHBOR SCORES");

						int localResultLimit = getCosSimLocalResultLimit();
						if (isExperimentalCandidateSchedulingEnabled(CommonNeighbors))
						{
							allUserScores = runExperimentalCandidateShardScheduling(
									users,
									usersForRec,
									true,
									localResultLimit);
						}
						else
						{
							allUserScores = initializeTargetScoreRows(usersRec, users);
							int currentNode = Integer.parseInt(nodeNumber);

							for (int i = 0; i < usersForRec.length; i++)
							{
								String recUser = usersForRec[i];
								LinkedHashMap<String, Double> recDoc =
										allUserDocumentsTFIDF.get(recUser);
								TreeMap<String,Double> userScores =
										new TreeMap<String,Double>();

								if (recDoc != null)
								{
									for (int j = 0; j < users.length; j++)
									{
										String candidateUser = users[j];
										if (!recUser.equals(candidateUser))
										{
											Integer primaryServer =
													userPrimaryRecServer.get(candidateUser);
											if (primaryServer != null
													&& primaryServer.intValue() != currentNode)
											{
												continue;
											}
											LinkedHashMap<String, Double> candidateDoc =
													allUserDocumentsTFIDF.get(candidateUser);
											double score = calculateCommonNeighborScore(
													recDoc, candidateDoc);
											userScores.put(candidateUser, score);
										}
									}
								}

								if (numRecAgents > 1 && !isUserRankerFolloweeMode())
								{
									userScores = keepTopCosSimScores(
											userScores, localResultLimit);
								}
								allUserScores.put(recUser, userScores);
							}
						}

						endTimeAlgorithm = System.nanoTime();
						completionTimeAlgorithm = endTimeAlgorithm - startTimeAlgorithm;

						textprocessing_wb_or_tfidf_Data.add(
								"CommonNeighbors=TP+TFIDF+CommonNeighbors"
										+ "\t" + agentName
										+ "\t" + tweetCount
										+ "\t" + completionTimeTextProcessing
										+ "\t" + completionTimeTFIDF
										+ "\t" + completionTimeAlgorithm
										+ "\t" + System.getProperty("line.separator"));
						System.out.println(agentName
								+"- Total Tweets Processed: " + tweetCount
								+ " TP: " + convertMs(completionTimeTextProcessing)
								+ " ms TFIDF: " + convertMs(completionTimeTFIDF)
								+ " ms CommonNeighbors: "
								+ convertMs(completionTimeAlgorithm)
								+ " ms Total: "
								+ convertMs(completionTimeTextProcessing
										+ completionTimeTFIDF
										+ completionTimeAlgorithm)
								+ " ms");
						myGui.appendResult(agentName
								+"\nTotal Tweets Processed: " + tweetCount
								+ " TP: " + convertMs(completionTimeTextProcessing)
								+ " ms TFIDF: " + convertMs(completionTimeTFIDF)
								+ " ms CommonNeighbors: "
								+ convertMs(completionTimeAlgorithm)
								+ " ms Total: "
								+ convertMs(completionTimeTextProcessing
										+ completionTimeTFIDF
										+ completionTimeAlgorithm)
								+ " ms");
					}

					else if (algorithmRec == TEMPORAL_GRAPH_FOUNDATION)
					{
						startTimeAlgorithm = System.nanoTime();
						System.out.println(getLocalName()
								+ " CALCULATING TEMPORAL GRAPH FOUNDATION SCORES");

						TemporalGraphFoundationModelSupport.Result result =
								TemporalGraphFoundationModelSupport.score(
										new TemporalGraphFoundationModelSupport.Input(
												runLifecycle.getActiveRunId(),
												getConfiguredDatasetFile(),
												allUserDocumentsTFIDF,
												userFollowee,
												datasetUserFollowee,
												usersRec,
												userPrimaryRecServer,
												Math.max(1, Integer.parseInt(nodeNumber)),
												numRecAgents,
												getCosSimLocalResultLimit()));
						allUserScores = result.getScores();
						temporalGraphCandidateDetails.clear();
						temporalGraphCandidateDetails.putAll(result.getDetails());
						TemporalGraphFoundationRegistry.record(result);

						endTimeAlgorithm = System.nanoTime();
						completionTimeAlgorithm =
								endTimeAlgorithm - startTimeAlgorithm;
						textprocessing_wb_or_tfidf_Data.add(
								"TGF=TP+TFIDF+TemporalGraphFoundation"
										+ "\t" + agentName
										+ "\t" + tweetCount
										+ "\t" + completionTimeTextProcessing
										+ "\t" + completionTimeTFIDF
										+ "\t" + completionTimeAlgorithm
										+ "\t" + System.getProperty("line.separator"));
						String timing = agentName
								+ "- Total Tweets Processed: " + tweetCount
								+ " TP: " + convertMs(completionTimeTextProcessing)
								+ " ms TFIDF: " + convertMs(completionTimeTFIDF)
								+ " ms Temporal Graph Foundation: "
								+ convertMs(completionTimeAlgorithm)
								+ " ms Total: "
								+ convertMs(completionTimeTextProcessing
										+ completionTimeTFIDF
										+ completionTimeAlgorithm)
								+ " ms";
						System.out.println(timing);
						System.out.println(result.getSummary());
						myGui.appendResult(timing + "\n" + result.getSummary());
					}

					//K-Means 
					else if (algorithmRec == K_MEANS)
					{
					//@Jason prevent sleep from windows
					/*try {
					        new Robot().mouseMove(new Random().nextInt(1920),new Random().nextInt(1080));		         
					    } catch (AWTException e) {
					        e.printStackTrace();
					    }
					 */
					//**************CALCULATE K-Means SCORES******************
					/*
					//k-means on every individual tweets 
					int kClusters = 3; //number of k clusters
					long initialTweetId;
					boolean notDuplicateInitialTweetId = false; 
					boolean convergence = false; //documents remain in the same clusters
					int maxIterations = 10;
					ArrayList<Long> initialTweetIds = new ArrayList<Long>();
					List<Long> allTweetIds = new ArrayList<Long>(tweetIdDocumentVector.keySet());
					List<Long> usableTweetIds = new ArrayList<Long>(allTweetIds); //Tweet ids excluding initial tweet ids
					List<Cluster> allClusters = new ArrayList<Cluster>();
					LinkedHashMap<String,Double> centroidTFIDF = new LinkedHashMap<String,Double>();
					LinkedHashMap<String,Double> baseCentroidTFIDF = new LinkedHashMap<String,Double>(); //tfidf of 0.0 for all unique doc terms
					List<List<Point>> prevListPoints = new ArrayList<List<Point>>();

					if (kClusters > allTweetIds.size())
						kClusters = allTweetIds.size();
					if (kClusters < 2)
						kClusters = 2;

					for (String term : allUniqueDocTerms)
					{
						baseCentroidTFIDF.put(term, 0.0);
					}


					startTimeAlgorithm = System.nanoTime();

					System.out.println("Calculating k-means");

					//Choose random initial points for cluster centroid
					for (int i = 0; i < kClusters; i++)
					{
						Collections.shuffle(allTweetIds);
						initialTweetId = allTweetIds.get(0);
						if (initialTweetIds.contains(initialTweetId))
						{
							notDuplicateInitialTweetId = true;
							while (notDuplicateInitialTweetId)
							{
								System.out.println("Duplicated");
								Collections.shuffle(allTweetIds);
								initialTweetId = allTweetIds.get(0);
								if (!initialTweetIds.contains(initialTweetId))
									notDuplicateInitialTweetId = false;
							}
						}

						initialTweetIds.add(initialTweetId);

						Cluster cluster = new Cluster(i);
						LinkedHashMap<String,Double> currTFIDF = tweetIdTFIDF.get(initialTweetId);
						Point initialPoint = new Point(initialTweetId,currTFIDF);
						initialPoint.setCluster(i);
						cluster.addPoint(initialPoint);

						centroidTFIDF = new LinkedHashMap<String,Double>(baseCentroidTFIDF);

						for (String term : currTFIDF.keySet())
						{
							centroidTFIDF.put(term,currTFIDF.get(term));
						}

						Point centroid = new Point(-1,centroidTFIDF);
						cluster.setCentroid(centroid);

						allClusters.add(cluster);

					}

					//remove initial tweet ids from remaining tweet ids
					usableTweetIds.removeAll(initialTweetIds);

					//	System.out.println("allTweetIds: "+allTweetIds);
					//	System.out.println("Initial Tweet Ids: "+initialTweetIds);
					//	System.out.println("usableTweetIds: "+usableTweetIds);

					//Create the initial clusters
					for (Cluster currCluster: allClusters)
					{
						List<Point> points = currCluster.getPoints();
						//System.out.println(currCluster.getPoints());
						//	for (Point p : points)
						//	{
						//		System.out.println(p.getTweetId());
						//	}
						//	System.out.println("currCluster centroid: "+currCluster.getCentroid());

					}

					//for (int i = 0; i < kClusters; i++) {
				    //		Cluster c = allClusters.get(i);
				    //		c.plotClusterTweets();
				    //	}


					//Assign remaining points to the closest cluster
					//assignCluster();
					double highestCosSim = 0.0; 
					int cluster = 0;                 
					double cosSim = 0.0; 

					for(Long currTweetId : usableTweetIds) {

						LinkedHashMap<String,Double> currTFIDF = tweetIdTFIDF.get(currTweetId);
						Point currPoint = new Point(currTweetId,currTFIDF);
						Cluster c;

						for(int i = 0; i < kClusters; i++) {
							c = allClusters.get(i);
							cosSim = Point.cosSimDistance(currPoint, c.getCentroid());
							if(cosSim >= highestCosSim){
								highestCosSim = cosSim;
								cluster = i;
							}
						}
						currPoint.setCluster(cluster);
						allClusters.get(cluster).addPoint(currPoint);

						highestCosSim = 0.0;
					}


					//Calculate new centroids.
					//calculateCentroids();
					for(Cluster clusterI : allClusters) {

						List<Point> listOfPoints = clusterI.getPoints();
						int numPoints = listOfPoints.size();

						Point centroid = clusterI.getCentroid();
						if(numPoints > 0) {
							LinkedHashMap<String,Double> newCentroidTFIDF = new LinkedHashMap<String,Double>(baseCentroidTFIDF);
							for (Point p : listOfPoints)
							{
								LinkedHashMap<String,Double> currTFIDF = p.getTfidf();
								for (String term : currTFIDF.keySet())
								{
									double pointTFIDFValue = 0.0;
									double centroidTFIDFValue = 0.0;
									pointTFIDFValue = currTFIDF.get(term);
									centroidTFIDFValue = newCentroidTFIDF.get(term);
									newCentroidTFIDF.put(term, pointTFIDFValue+centroidTFIDFValue);
								}
							}

							double newClusterMag = 0.0;
							double currTermTFIDF = 0.0;

							for (String term : newCentroidTFIDF.keySet())
							{
								currTermTFIDF = 0.0;

								currTermTFIDF = newCentroidTFIDF.get(term);
								if (currTermTFIDF > 0.0)
								{
									currTermTFIDF = currTermTFIDF / numPoints;
									newClusterMag += currTermTFIDF * currTermTFIDF;
									newCentroidTFIDF.put(term, currTermTFIDF);
								}

							}

							//Normalize the new cluster centroid as it might not be normalized

							newClusterMag = Math.sqrt(newClusterMag);
							for (String term : newCentroidTFIDF.keySet())
							{
								currTermTFIDF = newCentroidTFIDF.get(term);

								if (currTermTFIDF > 0.0)
								{
									currTermTFIDF = currTermTFIDF / newClusterMag;
									newCentroidTFIDF.put(term, currTermTFIDF);
								}

							}

							centroid.setTfidf(newCentroidTFIDF);
							clusterI.setCentroid(centroid);
						}
					}

					System.out.println("~~~~~~~~~~~~~~~~~~~TESTING IF CENTROID UNIT VECTOR~~~~~~~~~~~~~~~~~~~~~~~");
					Double sumOfMags;
					Point cent;
					for(Cluster clusterI : allClusters) {
						sumOfMags = 0.0;
						cent = clusterI.getCentroid();
						LinkedHashMap<String,Double> centtfidf = cent.getTfidf();
						for (String term : centtfidf.keySet())
						{
							sumOfMags += centtfidf.get(term)*centtfidf.get(term);
						}
						sumOfMags = Math.sqrt(sumOfMags);
						System.out.println("cluster #"+clusterI.getId()+" sumOfMags: "+sumOfMags);
						System.out.println(cent.getTfidf());
					}


					System.out.println("#################");
					System.out.println("Iteration: " + 0);


					for (int i = 0; i < kClusters; i++) {
						Cluster c = allClusters.get(i);
						//c.plotClusterTweets();
						prevListPoints.add(c.getPoints());
						//System.out.println("prevListPoints: "+prevListPoints.get(i));
					}




					//Calculate k-means calculate()
					int iteration = 0;

					//Iterate k-means ********************************************************************************
					while(!convergence && iteration < maxIterations) {

						//Clear cluster state
						//clearClusters();
						for(Cluster clusterK : allClusters) {
							clusterK.clear();
							//System.out.println("clusterK: "+clusterK.getPoints());
						}


						//getCentroids()
						List centroids = new ArrayList(kClusters);
						for(Cluster clusterH : allClusters) {
							Point currCentroid = clusterH.getCentroid();
							Point point = new Point(currCentroid.getTweetId(),currCentroid.getTfidf());
							centroids.add(point);
						}
						List<Point> lastCentroids = centroids;

						//Assign points to the closer cluster
						//assignCluster();
						highestCosSim = 0.0; 
						cluster = 0;                 
						cosSim = 0.0; 

						for(Long currTweetId : allTweetIds) {

							LinkedHashMap<String,Double> currTFIDF = tweetIdTFIDF.get(currTweetId);
							Point currPoint = new Point(currTweetId,currTFIDF);

							for(int i = 0; i < kClusters; i++) {
								Cluster c = allClusters.get(i);
								cosSim = Point.cosSimDistance(currPoint, c.getCentroid());
								if(cosSim >= highestCosSim){
									highestCosSim = cosSim;
									cluster = i;
								}
							}
							currPoint.setCluster(cluster);
							allClusters.get(cluster).addPoint(currPoint);

							highestCosSim = 0.0;
						}


						//Get the current list of points
						List<List<Point>> currListPoints = new ArrayList<List<Point>>();

						//Calculate new centroids.
						//calculateCentroids();

						for(Cluster clusterI : allClusters) {

							List<Point> listOfPoints = clusterI.getPoints();
							currListPoints.add(listOfPoints);

							int numPoints = listOfPoints.size();

							Point centroid = clusterI.getCentroid();
							if(numPoints > 0) {
								LinkedHashMap<String,Double> newCentroidTFIDF = new LinkedHashMap<String,Double>(baseCentroidTFIDF);
								for (Point p : listOfPoints)
								{
									LinkedHashMap<String,Double> currTFIDF = p.getTfidf();
									for (String term : currTFIDF.keySet())
									{
										double pointTFIDFValue = 0.0;
										double centroidTFIDFValue = 0.0;
										pointTFIDFValue = currTFIDF.get(term);
										centroidTFIDFValue = newCentroidTFIDF.get(term);
										newCentroidTFIDF.put(term, pointTFIDFValue+centroidTFIDFValue);
									}
								}

								double newClusterMag = 0.0;
								double currTermTFIDF = 0.0;

								for (String term : newCentroidTFIDF.keySet())
								{
									currTermTFIDF = 0.0;
									currTermTFIDF = newCentroidTFIDF.get(term);
									if (currTermTFIDF > 0.0)
									{
										currTermTFIDF = currTermTFIDF / numPoints;
										newClusterMag += currTermTFIDF * currTermTFIDF;
										newCentroidTFIDF.put(term, currTermTFIDF);
									}

								}

								//Normalize the new cluster centroid as it might not be normalized

								newClusterMag = Math.sqrt(newClusterMag);
								for (String term : newCentroidTFIDF.keySet())
								{
									currTermTFIDF = newCentroidTFIDF.get(term);

									if (currTermTFIDF > 0.0)
									{
										currTermTFIDF = currTermTFIDF / newClusterMag;
										newCentroidTFIDF.put(term, currTermTFIDF);
									}

								}

								centroid.setTfidf(newCentroidTFIDF);
								clusterI.setCentroid(centroid);
							}
						}


						System.out.println("~~~~~~~~~~~~~~~~~~~TESTING IF CENTROID UNIT VECTOR AFTER ITERATION "+iteration+"~~~~~~~~~~~~~~~~~~~~~~~");

						for(Cluster clusterI : allClusters) {
							sumOfMags = 0.0;
							cent = clusterI.getCentroid();
							LinkedHashMap<String,Double> centtfidf = cent.getTfidf();
							for (String term : centtfidf.keySet())
							{
								sumOfMags += centtfidf.get(term)*centtfidf.get(term);
							}
							sumOfMags = Math.sqrt(sumOfMags);
							System.out.println("cluster #"+clusterI.getId()+" sumOfMags: "+sumOfMags);
							System.out.println(cent.getTfidf());
						}

						iteration++;


						//Check if convergence
						convergence = true;

						for (int i = 0; i < kClusters; i++) {
							List<Point> prevList = prevListPoints.get(i);
							List<Point> currList = currListPoints.get(i);
							//System.out.println("prevList "+prevList);
							//System.out.println("currList "+currList);
							for (Point p : prevList)
							{

								if (!currList.contains(p))
								{
									convergence = false;
									break;
								}
							}
							if (convergence == false)
								break;

						}

						if (convergence == false)
							prevListPoints = currListPoints;

						System.out.println("#################");
						System.out.println("Iteration: " + iteration);


						//	for (int i = 0; i < kClusters; i++) {
					    //		Cluster c = allClusters.get(i);
					    //		c.plotClusterTweets();
					    //	}

					}

					//Output the clusters and the tweets
					System.out.println("THE CLUSTERS");
					for (Cluster c : allClusters)
					{
						c.plotClusterTweets(tweetIdUser);
					}




					String[] users = new String[allUserDocumentsTFIDF.keySet().size()];
					allUserDocumentsTFIDF.keySet().toArray(users);
						allUserScores = initializeTargetScoreRows(usersRec, users);
						Map<String,Double> userScore1 = new TreeMap<String,Double>();
					Map<String,Double> userScore2 = new TreeMap<String,Double>();
					double dpVectors = 0.0, score = 0.0, prevScore = 0.0;

					System.out.println("Initialized Scores to 0.0");
					for (int i = 0; i < users.length; i++)
					{
						for (int j = 0; j < users.length; j++)
						{
							if (!users[i].equals(users[j]))
							{
								userScore1.put(users[j], 0.0);
								allUserScores.put(users[i],(TreeMap<String,Double>) userScore1);
							}
						}
						//userScore1 = new LinkedHashMap<String,Double>();
						userScore1 = new TreeMap<String,Double>();
					}


					//System.out.println("allUserScores:" +allUserScores);

					for (int i = 0; i < allClusters.size(); i++)
					{
						//Not comparing where clusters have a size of 1 or 0
						List<Point> pointsInCluster = allClusters.get(i).getPoints();
						if (pointsInCluster.size() > 1)
						{
							for (int j = 0; j < pointsInCluster.size()-1; j++)
							{
								long tweetId1 = pointsInCluster.get(j).getTweetId();
								LinkedHashMap<String,Double> tweetId1Tfidf = pointsInCluster.get(j).getTfidf();
								Set<String> terms1 = tweetId1Tfidf.keySet();

								for (int k = j+1; k < pointsInCluster.size(); k++)
								{
									long tweetId2 = pointsInCluster.get(k).getTweetId();
									LinkedHashMap<String,Double> tweetId2Tfidf = pointsInCluster.get(k).getTfidf();
									Set<String> terms2 = tweetId2Tfidf.keySet();

									String user1, user2;
									user1 = tweetIdUser.get(tweetId1);
									user2 = tweetIdUser.get(tweetId2);

									if (!user1.equals(user2))
									{
										for (String term1 : terms1)
										{
											for (String term2 : terms2)
											{

												if (term1.equals(term2))
												{
													dpVectors+=tweetId1Tfidf.get(term1)*tweetId2Tfidf.get(term2);
												}
											}
										}

										//System.out.println("user1: "+user1+" user2: "+user2);
										//Update the scores of the users
										//System.out.println(allUserScores.get(user1));

										//prevScore = allUserScores.get(user1).get(user2);
					        			//	score = prevScore + dpVectors;
					        			//	userScore1 = allUserScores.get(user1);
					        			//	userScore1.put(user2, score);
					        			//	allUserScores.put(user1,(TreeMap<String,Double>)userScore1);
					        			//	userScore2 = allUserScores.get(user2);
					        			//	userScore2.put(user1, score);
					        			//	allUserScores.put(user2,(TreeMap<String,Double>)userScore2);

										prevScore = allUserScores.get(user1).get(user2);
										score = prevScore + dpVectors;
										allUserScores.get(user1).put(user2, score);
										allUserScores.get(user2).put(user1, score);

									}
									dpVectors=0.0;
									score=0.0;
								} //end for (int k = j+1; k < pointsInCluster.size(); k++)
							} //end for (int j = 0; j < pointsInCluster.size()-1; j++)
						} //end if (pointsInCluster.size() > 1)
					} //for (int i = 0; i < allClusters.size(); i++)

					//for (String s : allUserScores.keySet())
					//	{
					//		System.out.print("user: "+s+"\t");
					//		System.out.println(allUserScores.get(s));
					//	}
					 */
					//k-means on aggregated tweets as document vectors
					AlgorithmParameterSettings algorithmSettings = algorithmSettings();
					int kClusters = algorithmSettings.getKMeansClusters(); //number of k clusters
					boolean convergence = false; //documents remain in the same clusters
					int maxIterations = algorithmSettings.getKMeansMaxIterations();

					List<Cluster> allClusters = new ArrayList<Cluster>();
					ArrayList<String> allUserNames = new ArrayList<String>(allUserDocumentsTFIDF.keySet());
					ArrayList<String> remainingUserNames = new ArrayList<String>(allUserDocumentsTFIDF.keySet()); //Remaining usernames after initial usernames chosen for seeds

					LinkedHashMap<String,Double> centroidTFIDF = new LinkedHashMap<String,Double>();
					LinkedHashMap<String,Double> baseCentroidTFIDF = new LinkedHashMap<String,Double>(); //tfidf of 0.0 for all unique doc terms
					List<List<Point>> prevListPoints = new ArrayList<List<Point>>();
					
					LinkedHashMap<String,Double> centroidTF = new LinkedHashMap<String,Double>();
					LinkedHashMap<String,Double> baseCentroidTF = new LinkedHashMap<String,Double>(); //tf of 0.0 for all unique doc terms



//					if (kClusters > allUserDocumentsTF.size())
//						kClusters = allUserDocumentsTF.size();
					if (kClusters > allUserDocumentsTFIDF.size())
						kClusters = allUserDocumentsTFIDF.size();
					if (kClusters < 2)
						kClusters = 2;

					startTimeAlgorithm = System.nanoTime();

					System.out.println("Calculating k-means");

					//Choose random initial points for cluster centroid
					for (int i = 0; i < kClusters; i++)
					{
						System.out.println("remainingUserNames: "+remainingUserNames);
						String initialUserName;
						Collections.shuffle(remainingUserNames);
						System.out.println("shuffled remainingUserNames: "+remainingUserNames);
//						initialUserName = remainingUserNames.get(0);
//						remainingUserNames.remove(0);
						// if (remainingUserNames.contains("Simon_Pella"))
						// {
							// initialUserName = "Simon_Pella";
							// remainingUserNames.remove(initialUserName);
						// }
						// else if (remainingUserNames.contains("Simon_Pella_generated"))
						// {
							// initialUserName = "Simon_Pella_generated";
							// remainingUserNames.remove(initialUserName);
						// }
						// else if (remainingUserNames.contains("styleofthesix"))
						// {
							// initialUserName = "styleofthesix";
							// remainingUserNames.remove(initialUserName);
						// }
						// else if (remainingUserNames.contains("styleofthesix_generated"))
						// {
							// initialUserName = "styleofthesix_generated";
							// remainingUserNames.remove(initialUserName);
						// }
						// else if (remainingUserNames.contains("AHKru"))
						// {
							// initialUserName = "AHKru";
							// remainingUserNames.remove(initialUserName);
						// }
						// else if (remainingUserNames.contains("AHKru_generated"))
						// {
							// initialUserName = "AHKru_generated";
							// remainingUserNames.remove(initialUserName);
						// }
						// else if (remainingUserNames.contains("Steve"))
						// {
							// initialUserName = "Steve";
							// remainingUserNames.remove(initialUserName);
						// }
						// else if (remainingUserNames.contains("Alice"))
						// {
							// initialUserName = "Alice";
							// remainingUserNames.remove(initialUserName);
						// }
						// else if (remainingUserNames.contains("Fred"))
						// {
							// initialUserName = "Fred";
							// remainingUserNames.remove(initialUserName);
						// }
						// else
						// {
							// initialUserName = remainingUserNames.get(0);
							// remainingUserNames.remove(0);
						// }
						
						if (remainingUserNames.contains("RyersonU"))
						{
							initialUserName = "RyersonU";
							remainingUserNames.remove(initialUserName);
						}
						else if (remainingUserNames.contains("TheCatTweeting"))
						{
							initialUserName = "TheCatTweeting";
							remainingUserNames.remove(initialUserName);
						}
						else if (remainingUserNames.contains("weathernetwork"))
						{
							initialUserName = "weathernetwork";
							remainingUserNames.remove(initialUserName);
						}
						else if (remainingUserNames.contains("TorontoStar"))
						{
							initialUserName = "TorontoStar";
							remainingUserNames.remove(initialUserName);
						}
						else if (remainingUserNames.contains("Doggos"))
						{
							initialUserName = "Doggos";
							remainingUserNames.remove(initialUserName);
						}
						else if (remainingUserNames.contains("MoviesGuy"))
						{
							initialUserName = "MoviesGuy";
							remainingUserNames.remove(initialUserName);
						}
						else if (remainingUserNames.contains("SportsGuy"))
						{
							initialUserName = "SportsGuy";
							remainingUserNames.remove(initialUserName);
						}
						else
						{
							initialUserName = remainingUserNames.get(0);
							remainingUserNames.remove(0);
						}
						
						
//						boolean isGenerated = true;
//						while (isGenerated)
//						{
//							String pattern = "_generated";
//							System.out.println("currentChoice: "+remainingUserNames.get(0));
//							
//							if (remainingUserNames.get(0).contains(pattern))
//							{
//								remainingUserNames.remove(0);
//								System.out.println("Removed generated");
//							}
//							else
//							{
//								isGenerated = false;
//							}
//						}
//						initialUserName = remainingUserNames.get(0);
//						remainingUserNames.remove(0);
						System.out.println("initialUserName: "+initialUserName);
						
						Cluster cluster = new Cluster(i);
						LinkedHashMap<String,Double> currTFIDF = allUserDocumentsTFIDF.get(initialUserName);
//						LinkedHashMap<String,Double> currTF = allUserDocumentsTF.get(initialUserName);
						Point initialPoint = new Point(initialUserName,currTFIDF);
//						Point initialPoint = new Point(initialUserName,currTF);
						initialPoint.setCluster(i);
						cluster.addPoint(initialPoint);

						centroidTFIDF = new LinkedHashMap<String,Double>(baseCentroidTFIDF);
//						centroidTF = new LinkedHashMap<String,Double>(baseCentroidTF);

						for (String term : currTFIDF.keySet())
//						for (String term : currTF.keySet())
						{
							centroidTFIDF.put(term,currTFIDF.get(term));
//							centroidTF.put(term,currTF.get(term));
						}

						Point centroid = new Point(-1,centroidTFIDF);
//						Point centroid = new Point(-1,centroidTF);
						cluster.setCentroid(centroid);

						allClusters.add(cluster);

					}



					//Create the initial clusters
					// for (Cluster currCluster: allClusters)
					// {
						// List<Point> points = currCluster.getPoints();
						//System.out.println(currCluster.getPoints());
						//	for (Point p : points)
						//	{
						//		System.out.println(p.getTweetId());
						//	}
						//	System.out.println("currCluster centroid: "+currCluster.getCentroid());

					// }

					//for (int i = 0; i < kClusters; i++) {
					//		Cluster c = allClusters.get(i);
					//		c.plotClusterTweets();
					//	}


					//Assign remaining points to the closest cluster
					//assignCluster();
					double highestCosSim = 0.0; 
					int cluster = 0;                 
					double cosSim = 0.0; 

					for(String currUserName : remainingUserNames) {

						LinkedHashMap<String,Double> currTFIDF = allUserDocumentsTFIDF.get(currUserName);
//						LinkedHashMap<String,Double> currTF = allUserDocumentsTF.get(currUserName);
						Point currPoint = new Point(currUserName,currTFIDF);
//						Point currPoint = new Point(currUserName,currTF);
						Cluster c;

						for(int i = 0; i < kClusters; i++) {
							c = allClusters.get(i);
							cosSim = currPoint.cosSimDistance(c.getCentroid());
							//if(cosSim >= highestCosSim){
							if(cosSim > highestCosSim){
								highestCosSim = cosSim;
								cluster = i;
							}
						}
						currPoint.setCluster(cluster);
						allClusters.get(cluster).addPoint(currPoint);

						highestCosSim = 0.0;
					}


					//Calculate new centroids.
					//calculateCentroids();
					for(Cluster clusterI : allClusters) {

						List<Point> listOfPoints = clusterI.getPoints();
						// System.out.println("Sepide points in cluster "+ clusterI + "is" + listOfPoints);
						int numPoints = listOfPoints.size();

						Point centroid = clusterI.getCentroid();
						if(numPoints > 0) {
							LinkedHashMap<String,Double> newCentroidTFIDF = new LinkedHashMap<String,Double>(baseCentroidTFIDF);
//							LinkedHashMap<String,Double> newCentroidTF = new LinkedHashMap<String,Double>(baseCentroidTF);
							for (Point p : listOfPoints)
							{
								Map<String,Double> currTFIDF = p.getTfidf_or_Tf();
//								LinkedHashMap<String,Double> currTF = p.getTfidf_or_Tf();
								for (String term : currTFIDF.keySet())
//								for (String term : currTF.keySet())
								{
									double pointTFIDFValue = 0.0;
									double centroidTFIDFValue = 0.0;
									pointTFIDFValue = currTFIDF.get(term);
									Double existingCentroidValue = newCentroidTFIDF.get(term);
									centroidTFIDFValue = existingCentroidValue == null
											? 0.0 : existingCentroidValue.doubleValue();
									newCentroidTFIDF.put(term, pointTFIDFValue+centroidTFIDFValue);
//									double pointTFValue = 0.0;
//									double centroidTFValue = 0.0;
//									pointTFValue = currTF.get(term);
//									centroidTFValue = newCentroidTF.get(term);
//									newCentroidTF.put(term, pointTFValue+centroidTFValue);
								}
							}

							double newClusterMag = 0.0;
							double currTermTFIDF = 0.0;
//							double currTermTF = 0.0;

							for (String term : newCentroidTFIDF.keySet())
//							for (String term : newCentroidTF.keySet())
							{
								currTermTFIDF = 0.0;
//								currTermTF = 0.0;

								currTermTFIDF = newCentroidTFIDF.get(term);
//								currTermTF = newCentroidTF.get(term);
								
								if (currTermTFIDF > 0.0)
//								if (currTermTF > 0.0)
								{
									currTermTFIDF = currTermTFIDF / numPoints;
									newClusterMag += currTermTFIDF * currTermTFIDF;
									newCentroidTFIDF.put(term, currTermTFIDF);
//									currTermTF = currTermTF / numPoints;
//									newClusterMag += currTermTF * currTermTF;
//									newCentroidTF.put(term, currTermTF);
								}

							}

							//Normalize the new cluster centroid as it might not be normalized

							newClusterMag = Math.sqrt(newClusterMag);
							for (String term : newCentroidTFIDF.keySet())
//							for (String term : newCentroidTF.keySet())
							{
								currTermTFIDF = newCentroidTFIDF.get(term);
//								currTermTF = newCentroidTF.get(term);
								
								if (currTermTFIDF > 0.0)
//								if (currTermTF > 0.0)
								{
									currTermTFIDF = currTermTFIDF / newClusterMag;
									newCentroidTFIDF.put(term, currTermTFIDF);
//									currTermTF = currTermTF / newClusterMag;
//									newCentroidTF.put(term, currTermTF);
								}

							}

							centroid.setTfidf_or_Tf(newCentroidTFIDF);
//							centroid.setTfidf_or_Tf(newCentroidTF);
							clusterI.setCentroid(centroid);
						}
					}

//					System.out.println("~~~~~~~~~~~~~~~~~~~TESTING IF CENTROID UNIT VECTOR~~~~~~~~~~~~~~~~~~~~~~~");
					Double sumOfMags;
					Point cent;
					for(Cluster clusterI : allClusters) {
						sumOfMags = 0.0;
						cent = clusterI.getCentroid();
						Map<String,Double> centtfidf = cent.getTfidf_or_Tf();
//						LinkedHashMap<String,Double> centtf = cent.getTfidf_or_Tf();
						for (String term : centtfidf.keySet())
//						for (String term : centtf.keySet())
						{
							sumOfMags += centtfidf.get(term)*centtfidf.get(term);
//							sumOfMags += centtf.get(term)*centtf.get(term);
						}
						sumOfMags = Math.sqrt(sumOfMags);
//						System.out.println("cluster #"+clusterI.getId()+" sumOfMags: "+sumOfMags);
////						System.out.println(cent.getTfidf_or_Tf());
//						System.out.println(cent.getTfidf_or_Tf());
					}


					System.out.println("#################");
					System.out.println("Iteration: " + 0);


					for (int i = 0; i < kClusters; i++) {
						Cluster c = allClusters.get(i);
						//c.plotClusterTweets();
						prevListPoints.add(c.getPoints());
						//System.out.println("prevListPoints: "+prevListPoints.get(i));
					}




					//Calculate k-means calculate()
					int iteration = 0;

					//Iterate k-means ********************************************************************************
					while(!convergence && iteration < maxIterations) {

						//Clear cluster state
						//clearClusters();
						for(Cluster clusterK : allClusters) {
							clusterK.clear();
							//System.out.println("clusterK: "+clusterK.getPoints());
						}


						//getCentroids()
						List centroids = new ArrayList(kClusters);
						for(Cluster clusterH : allClusters) {
							Point currCentroid = clusterH.getCentroid();
							Point point = new Point(currCentroid.getTweetId(),currCentroid.getTfidf_or_Tf());
							centroids.add(point);
						}
						List<Point> lastCentroids = centroids;

						//Assign points to the closer cluster
						//assignCluster();
						highestCosSim = 0.0; 
						cluster = 0;                 
						cosSim = 0.0; 

						for(String currUserName : allUserNames) {

							LinkedHashMap<String,Double> currTFIDF = allUserDocumentsTFIDF.get(currUserName);
//							LinkedHashMap<String,Double> currTF = allUserDocumentsTF.get(currUserName);
							Point currPoint = new Point(currUserName,currTFIDF);
//							Point currPoint = new Point(currUserName,currTF);

							for(int i = 0; i < kClusters; i++) {
								Cluster c = allClusters.get(i);
								cosSim = currPoint.cosSimDistance(c.getCentroid());
								if(cosSim >= highestCosSim){
									highestCosSim = cosSim;
									cluster = i;
								}
							}
							currPoint.setCluster(cluster);
							allClusters.get(cluster).addPoint(currPoint);

							highestCosSim = 0.0;
						}


						//Get the current list of points
						List<List<Point>> currListPoints = new ArrayList<List<Point>>();

						//Calculate new centroids.
						//calculateCentroids();

						for(Cluster clusterI : allClusters) {

							List<Point> listOfPoints = clusterI.getPoints();
							currListPoints.add(listOfPoints);

							int numPoints = listOfPoints.size();
							// System.out.println("Sepide Number of points: " + numPoints);

							Point centroid = clusterI.getCentroid();
							if(numPoints > 0) {
								LinkedHashMap<String,Double> newCentroidTFIDF = new LinkedHashMap<String,Double>(baseCentroidTFIDF);
//								LinkedHashMap<String,Double> newCentroidTF = new LinkedHashMap<String,Double>(baseCentroidTF);
								for (Point p : listOfPoints)
								{
									Map<String,Double> currTFIDF = p.getTfidf_or_Tf();
//									LinkedHashMap<String,Double> currTF = p.getTfidf_or_Tf();
									for (String term : currTFIDF.keySet())
//									for (String term : currTF.keySet())
									{
										double pointTFIDFValue = 0.0;
										double centroidTFIDFValue = 0.0;
										pointTFIDFValue = currTFIDF.get(term);
										Double existingCentroidValue = newCentroidTFIDF.get(term);
										centroidTFIDFValue = existingCentroidValue == null
												? 0.0 : existingCentroidValue.doubleValue();
										newCentroidTFIDF.put(term, pointTFIDFValue+centroidTFIDFValue);
//										double pointTFValue = 0.0;
//										double centroidTFValue = 0.0;
//										pointTFValue = currTF.get(term);
//										centroidTFValue = newCentroidTF.get(term);
//										newCentroidTF.put(term, pointTFValue+centroidTFValue);
									}
								}

								double newClusterMag = 0.0;
								double currTermTFIDF = 0.0;
//								double currTermTF = 0.0;

								for (String term : newCentroidTFIDF.keySet())
//								for (String term : newCentroidTF.keySet())
								{
									currTermTFIDF = 0.0;
									currTermTFIDF = newCentroidTFIDF.get(term);
									if (currTermTFIDF > 0.0)
//									currTermTF = 0.0;
//									currTermTF = newCentroidTF.get(term);
//									if (currTermTF > 0.0)
									{
										currTermTFIDF = currTermTFIDF / numPoints;
										newClusterMag += currTermTFIDF * currTermTFIDF;
										newCentroidTFIDF.put(term, currTermTFIDF);
//										currTermTF = currTermTF / numPoints;
//										newClusterMag += currTermTF * currTermTF;
//										newCentroidTF.put(term, currTermTF);
									}

								}

								//Normalize the new cluster centroid as it might not be normalized

								newClusterMag = Math.sqrt(newClusterMag);
								for (String term : newCentroidTFIDF.keySet())
//								for (String term : newCentroidTF.keySet())
								{
									currTermTFIDF = newCentroidTFIDF.get(term);
//									currTermTF = newCentroidTF.get(term);

									if (currTermTFIDF > 0.0)
//									if (currTermTF > 0.0)
									{
										currTermTFIDF = currTermTFIDF / newClusterMag;
										newCentroidTFIDF.put(term, currTermTFIDF);
//										currTermTF = currTermTF / newClusterMag;
//										newCentroidTF.put(term, currTermTF);
									}

								}

								centroid.setTfidf_or_Tf(newCentroidTFIDF);
//								centroid.setTfidf_or_Tf(newCentroidTF);
								clusterI.setCentroid(centroid);
							}
						}


						System.out.println("~~~~~~~~~~~~~~~~~~~TESTING IF CENTROID UNIT VECTOR AFTER ITERATION "+iteration+"~~~~~~~~~~~~~~~~~~~~~~~");

						for(Cluster clusterI : allClusters) {
							sumOfMags = 0.0;
							cent = clusterI.getCentroid();
							Map<String,Double> centtfidf = cent.getTfidf_or_Tf();
//							LinkedHashMap<String,Double> centtf = cent.getTfidf_or_Tf();
							for (String term : centtfidf.keySet())
//							for (String term : centtf.keySet())
							{
								sumOfMags += centtfidf.get(term)*centtfidf.get(term);
//								sumOfMags += centtf.get(term)*centtf.get(term);
							}
							sumOfMags = Math.sqrt(sumOfMags);
//							System.out.println("cluster #"+clusterI.getId()+" sumOfMags: "+sumOfMags);
//							System.out.println(cent.getTfidf_or_Tf());
						}

						iteration++;


						//Check if convergence
						convergence = true;

						for (int i = 0; i < kClusters; i++) {
							List<Point> prevList = prevListPoints.get(i);
							List<Point> currList = currListPoints.get(i);
							// System.out.println("sepide list of points in each cluster" + currList);
							//System.out.println("prevList "+prevList);
							//System.out.println("currList "+currList);
							for (Point p : prevList)
							{

								if (!currList.contains(p))
								{
									convergence = false;
									break;
								}
							}
							if (convergence == false)
								break;

						}

						if (convergence == false)
							prevListPoints = currListPoints;

//						System.out.println("#################");
//						System.out.println("Iteration: " + iteration);


						//	for (int i = 0; i < kClusters; i++) {
						//		Cluster c = allClusters.get(i);
						//		c.plotClusterTweets();
						//	}

					}

					//Output the clusters and the tweets
					System.out.println("THE FINAL CLUSTERS "+ getLocalName());
					  for (Cluster c : allClusters)
					    {
						   c.plotCluster();
					    }

					String[] users = new String[allUserDocumentsTFIDF.keySet().size()];
					users = allUserDocumentsTFIDF.keySet().toArray(users);
						allUserScores = initializeTargetScoreRows(usersRec, users);
					Map<String,Double> userScore1 = new TreeMap<String,Double>();
					Map<String,Double> userScore2 = new TreeMap<String,Double>();
					double dpVectors = 0.0, score = 0.0, prevScore = 0.0;

						System.out.println("kmeans clustering completed Initialized target Scores to 0.0");

					System.out.println(getLocalName() + " All scores initialized to 0");
					//System.out.println("allUserScores:" +allUserScores);
					
					int scoreTimes = 0;
					int expectedTotalScores = 0;
					int clusterSize = 0;
					
					for (int i = 0; i < allClusters.size(); i++)
					{
						clusterSize = allClusters.get(i).getPoints().size();
						if (clusterSize > 1)
						{
							expectedTotalScores += ((clusterSize-1)*clusterSize)/2;
						}
					}
					
					System.out.println(getLocalName()+ " expectedTotalScores: "+expectedTotalScores);
					System.out.println(getLocalName() + " Calculating Scores");
					
					for (int i = 0; i < allClusters.size(); i++)
					{
						//Not comparing where clusters have a size of 1 or 0
						List<Point> pointsInCluster = allClusters.get(i).getPoints();
						// System.out.println("pointsInCluster.size(): "+pointsInCluster.size());
						if (pointsInCluster.size() > 1)
						{
							for (int j = 0; j < pointsInCluster.size()-1; j++)
							{
								String userName1 = pointsInCluster.get(j).getUserName();
								Map<String,Double> userName1Tfidf = pointsInCluster.get(j).getTfidf_or_Tf();
									Set<String> terms1 = userName1Tfidf.keySet();
//								LinkedHashMap<String,Double> userName1Tf = pointsInCluster.get(j).getTfidf_or_Tf();
//								Set<String> terms1 = userName1Tf.keySet();

								for (int k = j+1; k < pointsInCluster.size(); k++)
								{
									String userName2 = pointsInCluster.get(k).getUserName();
									Map<String,Double> userName2Tfidf = pointsInCluster.get(k).getTfidf_or_Tf();
									Set<String> terms2 = userName2Tfidf.keySet();
//									LinkedHashMap<String,Double> userName2Tf = pointsInCluster.get(k).getTfidf_or_Tf();
//									Set<String> terms2 = userName2Tf.keySet();

									dpVectors = calculateSparseCosineScore(
											userName1Tfidf, userName2Tfidf);

									//System.out.println("user1: "+user1+" user2: "+user2);
									//Update the scores of the users
									//System.out.println(allUserScores.get(user1));

									//prevScore = allUserScores.get(user1).get(user2);
									//	score = prevScore + dpVectors;
									//	userScore1 = allUserScores.get(user1);
									//	userScore1.put(user2, score);
									//	allUserScores.put(user1,(TreeMap<String,Double>)userScore1);
									//	userScore2 = allUserScores.get(user2);
									//	userScore2.put(user1, score);
									//	allUserScores.put(user2,(TreeMap<String,Double>)userScore2);

										score = dpVectors;
										putScoreForTrackedTargets(
												allUserScores, userName1, userName2, score);

									scoreTimes++;
									
									if (scoreTimes % 1000 == 0 || scoreTimes == expectedTotalScores)
										System.out.println(getLocalName()+" scoreTimes: "+scoreTimes);
									
									dpVectors=0.0;
									score=0.0;

								} //end for (int k = j+1; k < pointsInCluster.size(); k++)
							} //end for (int j = 0; j < pointsInCluster.size()-1; j++)
						} //end if (pointsInCluster.size() > 1)
					} //for (int i = 0; i < allClusters.size(); i++)

					//for (String s : allUserScores.keySet())
					//	{
					//		System.out.print("user: "+s+"\t");
					//		System.out.println(allUserScores.get(s));
					//	}

					System.out.println(getLocalName() + " Completed the scores Total Score Times: "+scoreTimes);

					
					


					endTimeAlgorithm = System.nanoTime();
					completionTimeAlgorithm = endTimeAlgorithm - startTimeAlgorithm;						

					//Output for K-means	   					
					textprocessing_wb_or_tfidf_Data.add("K-means=TP+TFIDF+K-means" + "\t" + agentName + "\t" + tweetCount + "\t" + completionTimeTextProcessing + "\t" + completionTimeTFIDF    + "\t" + completionTimeAlgorithm + "\t" + System.getProperty("line.separator"));
//					System.out.println(agentName+"- Total Tweets Processed: " + tweetCount + " TP:" + convertMs(completionTimeTextProcessing) + "ms TFIDF:" + convertMs(completionTimeTFIDF) + "ms K-means:" + convertMs(completionTimeAlgorithm) + "ms Total:" + convertMs(completionTimeTextProcessing+completionTimeTFIDF+completionTimeAlgorithm)+"ms");
					System.out.println("Mapper"+nodeNumber+"- Total Tweets Processed: " + tweetCount + " TP: " + convertMs(completionTimeTextProcessing) + " ms TFIDF: " + convertMs(completionTimeTFIDF) + " ms Reducer"+ nodeNumber+ " K-means: " + convertMs(completionTimeAlgorithm) + " ms Total: " + convertMs(completionTimeTextProcessing+completionTimeTFIDF+completionTimeAlgorithm)+" ms");
//					myGui.appendResult(agentName+"\nTotal Tweets Processed: " + tweetCount + " TP:" + round(completionTimeTextProcessing/1000000.00,2) + "ms TFIDF:" + round(completionTimeTFIDF/1000000.00,2) + "ms K-means:" + round(completionTimeAlgorithm/1000000.00,2) + "ms Total:" + round((completionTimeTextProcessing+completionTimeTFIDF+completionTimeAlgorithm)/1000000.00,2)+"ms");
					myGui.appendResult("Mapper"+nodeNumber+"- Total Tweets Processed: " + tweetCount + " TP: " + convertMs(completionTimeTextProcessing) + " ms TFIDF: " + convertMs(completionTimeTFIDF) + " ms Reducer"+ nodeNumber+ " K-means: " + convertMs(completionTimeAlgorithm) + " ms Total: " + convertMs(completionTimeTextProcessing+completionTimeTFIDF+completionTimeAlgorithm)+" ms");

				}
				
				// K-means clustering with Euclidean Distance added by Sepide
				
				else if (algorithmRec == K_MEANSEUCLIDEAN)
				{
					AlgorithmParameterSettings algorithmSettings = algorithmSettings();
					startTimeAlgorithm = System.nanoTime();
					System.out.println("Calculating deterministic Euclidean K-means");

					EuclideanKMeansRecommender.Result result =
							EuclideanKMeansRecommender.score(
									allUserDocumentsTFIDF,
									usersRec,
									algorithmSettings.getKMeansClusters(),
									algorithmSettings.getKMeansMaxIterations());
					allUserScores = result.scores;

					System.out.println(getLocalName()
							+ " Euclidean K-means requestedClusters="
							+ result.requestedClusters
							+ " effectiveClusters=" + result.effectiveClusters
							+ " iterations=" + result.iterations
							+ " converged=" + result.converged);
					for (Cluster clusterResult : result.clusters)
					{
						System.out.println(getLocalName()
								+ " Euclidean K-means cluster "
								+ clusterResult.getId()
								+ " size=" + clusterResult.getPoints().size());
					}

					endTimeAlgorithm = System.nanoTime();
					completionTimeAlgorithm = endTimeAlgorithm - startTimeAlgorithm;

					textprocessing_wb_or_tfidf_Data.add(
							"K-meansEuclidean=TP+TFIDF+K-meansEuclidean"
									+ "\t" + agentName
									+ "\t" + tweetCount
									+ "\t" + completionTimeTextProcessing
									+ "\t" + completionTimeTFIDF
									+ "\t" + completionTimeAlgorithm
									+ "\t" + System.getProperty("line.separator"));
					System.out.println("Mapper" + nodeNumber
							+ "- Total Tweets Processed: " + tweetCount
							+ " TP: " + convertMs(completionTimeTextProcessing)
							+ " ms TFIDF: " + convertMs(completionTimeTFIDF)
							+ " ms Reducer" + nodeNumber
							+ " K-means Euclidean: "
							+ convertMs(completionTimeAlgorithm)
							+ " ms Total: "
							+ convertMs(completionTimeTextProcessing
									+ completionTimeTFIDF
									+ completionTimeAlgorithm)
							+ " ms");
					myGui.appendResult("Mapper" + nodeNumber
							+ "- Total Tweets Processed: " + tweetCount
							+ " TP: " + convertMs(completionTimeTextProcessing)
							+ " ms TFIDF: " + convertMs(completionTimeTFIDF)
							+ " ms Reducer" + nodeNumber
							+ " K-means Euclidean: "
							+ convertMs(completionTimeAlgorithm)
							+ " ms Total: "
							+ convertMs(completionTimeTextProcessing
									+ completionTimeTFIDF
									+ completionTimeAlgorithm)
							+ " ms");
				}
				else if (false && algorithmRec == K_MEANSEUCLIDEAN)
				{
					//k-means on aggregated tweets as document vectors
					AlgorithmParameterSettings algorithmSettings = algorithmSettings();
					int kClusters = algorithmSettings.getKMeansClusters(); //number of k clusters
					boolean convergence = false; //documents remain in the same clusters
					int maxIterations = algorithmSettings.getKMeansMaxIterations();

					List<Cluster> allClusters = new ArrayList<Cluster>();
					ArrayList<String> allUserNames = new ArrayList<String>(allUserDocumentsTFIDF.keySet());
					ArrayList<String> remainingUserNames = new ArrayList<String>(allUserDocumentsTFIDF.keySet()); //Remaining usernames after initial usernames chosen for seeds

					LinkedHashMap<String,Double> centroidTFIDF = new LinkedHashMap<String,Double>();
					LinkedHashMap<String,Double> baseCentroidTFIDF = new LinkedHashMap<String,Double>(); //tfidf of 0.0 for all unique doc terms
					List<List<Point>> prevListPoints = new ArrayList<List<Point>>();
					
					LinkedHashMap<String,Double> centroidTF = new LinkedHashMap<String,Double>();
					LinkedHashMap<String,Double> baseCentroidTF = new LinkedHashMap<String,Double>(); //tf of 0.0 for all unique doc terms



//					if (kClusters > allUserDocumentsTF.size())
//						kClusters = allUserDocumentsTF.size();
					if (kClusters > allUserDocumentsTFIDF.size())
						kClusters = allUserDocumentsTFIDF.size();
					if (kClusters < 2)
						kClusters = 2;

					startTimeAlgorithm = System.nanoTime();

					System.out.println("Calculating k-means Euclidean");

					//Choose random initial points for cluster centroid
					for (int i = 0; i < kClusters; i++)
					{
						System.out.println("remainingUserNames: "+remainingUserNames);
						String initialUserName;
						Collections.shuffle(remainingUserNames);
						System.out.println("shuffled remainingUserNames: "+remainingUserNames);
						
						if (remainingUserNames.contains("RyersonU"))
						{
							initialUserName = "RyersonU";
							remainingUserNames.remove(initialUserName);
						}
						else if (remainingUserNames.contains("TheCatTweeting"))
						{
							initialUserName = "TheCatTweeting";
							remainingUserNames.remove(initialUserName);
						}
						else if (remainingUserNames.contains("weathernetwork"))
						{
							initialUserName = "weathernetwork";
							remainingUserNames.remove(initialUserName);
						}
						else if (remainingUserNames.contains("TorontoStar"))
						{
							initialUserName = "TorontoStar";
							remainingUserNames.remove(initialUserName);
						}
						else if (remainingUserNames.contains("Doggos"))
						{
							initialUserName = "Doggos";
							remainingUserNames.remove(initialUserName);
						}
						else if (remainingUserNames.contains("MoviesGuy"))
						{
							initialUserName = "MoviesGuy";
							remainingUserNames.remove(initialUserName);
						}
						else if (remainingUserNames.contains("SportsGuy"))
						{
							initialUserName = "SportsGuy";
							remainingUserNames.remove(initialUserName);
						}
						else
						{
							initialUserName = remainingUserNames.get(0);
							remainingUserNames.remove(0);
						}
						
//						initialUserName = remainingUserNames.get(0);
//						remainingUserNames.remove(0);
						System.out.println("initialUserName: "+initialUserName);
						
						Cluster cluster = new Cluster(i);
						LinkedHashMap<String,Double> currTFIDF = allUserDocumentsTFIDF.get(initialUserName);
//						LinkedHashMap<String,Double> currTF = allUserDocumentsTF.get(initialUserName);
						Point initialPoint = new Point(initialUserName,currTFIDF);
//						Point initialPoint = new Point(initialUserName,currTF);
						initialPoint.setCluster(i);
						cluster.addPoint(initialPoint);

						centroidTFIDF = new LinkedHashMap<String,Double>(baseCentroidTFIDF);
//						centroidTF = new LinkedHashMap<String,Double>(baseCentroidTF);

						for (String term : currTFIDF.keySet())
//						for (String term : currTF.keySet())
						{
							centroidTFIDF.put(term,currTFIDF.get(term));
//							centroidTF.put(term,currTF.get(term));
						}

						Point centroid = new Point(-1,centroidTFIDF);
//						Point centroid = new Point(-1,centroidTF);
						cluster.setCentroid(centroid);

						allClusters.add(cluster);

					}


					//Assign remaining points to the closest cluster
					//assignCluster();
					double lowestEuclidean = Double.POSITIVE_INFINITY; 
					int cluster = 0;                 
					double eucliDista = 0.0; 

					for(String currUserName : remainingUserNames) {

						LinkedHashMap<String,Double> currTFIDF = allUserDocumentsTFIDF.get(currUserName);
//						LinkedHashMap<String,Double> currTF = allUserDocumentsTF.get(currUserName);
						Point currPoint = new Point(currUserName,currTFIDF);
//						Point currPoint = new Point(currUserName,currTF);
						Cluster c;

						for(int i = 0; i < kClusters; i++) {
							c = allClusters.get(i);
							eucliDista = currPoint.eucliDistance(c.getCentroid());
							//if(cosSim >= highestCosSim){
							if(eucliDista < lowestEuclidean){
								lowestEuclidean = eucliDista;
								cluster = i;
							}
						}
						currPoint.setCluster(cluster);
						allClusters.get(cluster).addPoint(currPoint);

						lowestEuclidean = Double.POSITIVE_INFINITY;
					}
					//Calculate new centroids.
					//calculateCentroids();
					for(Cluster clusterI : allClusters) {

						List<Point> listOfPoints = clusterI.getPoints();
						// System.out.println("Sepide points in cluster "+ clusterI + "is" + listOfPoints);
						int numPoints = listOfPoints.size();

						Point centroid = clusterI.getCentroid();
						if(numPoints > 0) {
							LinkedHashMap<String,Double> newCentroidTFIDF = new LinkedHashMap<String,Double>(baseCentroidTFIDF);
//							LinkedHashMap<String,Double> newCentroidTF = new LinkedHashMap<String,Double>(baseCentroidTF);
							for (Point p : listOfPoints)
							{
								Map<String,Double> currTFIDF = p.getTfidf_or_Tf();
//								LinkedHashMap<String,Double> currTF = p.getTfidf_or_Tf();
								for (String term : currTFIDF.keySet())
//								for (String term : currTF.keySet())
								{
									double pointTFIDFValue = 0.0;
									double centroidTFIDFValue = 0.0;
									pointTFIDFValue = currTFIDF.get(term);
									Double existingCentroidValue = newCentroidTFIDF.get(term);
									centroidTFIDFValue = existingCentroidValue == null
											? 0.0 : existingCentroidValue.doubleValue();
									newCentroidTFIDF.put(term, pointTFIDFValue+centroidTFIDFValue);
//									double pointTFValue = 0.0;
//									double centroidTFValue = 0.0;
//									pointTFValue = currTF.get(term);
//									centroidTFValue = newCentroidTF.get(term);
//									newCentroidTF.put(term, pointTFValue+centroidTFValue);
								}
							}

							double newClusterMag = 0.0;
							double currTermTFIDF = 0.0;
//							double currTermTF = 0.0;

							for (String term : newCentroidTFIDF.keySet())
//							for (String term : newCentroidTF.keySet())
							{
								currTermTFIDF = 0.0;
//								currTermTF = 0.0;

								currTermTFIDF = newCentroidTFIDF.get(term);
//								currTermTF = newCentroidTF.get(term);
								
								if (currTermTFIDF > 0.0)
//								if (currTermTF > 0.0)
								{
									currTermTFIDF = currTermTFIDF / numPoints;
									newClusterMag += currTermTFIDF * currTermTFIDF;
									newCentroidTFIDF.put(term, currTermTFIDF);
//									currTermTF = currTermTF / numPoints;
//									newClusterMag += currTermTF * currTermTF;
//									newCentroidTF.put(term, currTermTF);
								}

							}

							//Normalize the new cluster centroid as it might not be normalized

							newClusterMag = Math.sqrt(newClusterMag);
							for (String term : newCentroidTFIDF.keySet())
//							for (String term : newCentroidTF.keySet())
							{
								currTermTFIDF = newCentroidTFIDF.get(term);
//								currTermTF = newCentroidTF.get(term);
								
								if (currTermTFIDF > 0.0)
//								if (currTermTF > 0.0)
								{
									currTermTFIDF = currTermTFIDF / newClusterMag;
									newCentroidTFIDF.put(term, currTermTFIDF);
//									currTermTF = currTermTF / newClusterMag;
//									newCentroidTF.put(term, currTermTF);
								}

							}

							centroid.setTfidf_or_Tf(newCentroidTFIDF);
//							centroid.setTfidf_or_Tf(newCentroidTF);
							clusterI.setCentroid(centroid);
						}
					}

//					System.out.println("~~~~~~~~~~~~~~~~~~~TESTING IF CENTROID UNIT VECTOR~~~~~~~~~~~~~~~~~~~~~~~");
					Double sumOfMags;
					Point cent;
					for(Cluster clusterI : allClusters) {
						sumOfMags = 0.0;
						cent = clusterI.getCentroid();
						Map<String,Double> centtfidf = cent.getTfidf_or_Tf();
//						LinkedHashMap<String,Double> centtf = cent.getTfidf_or_Tf();
						for (String term : centtfidf.keySet())
//						for (String term : centtf.keySet())
						{
							sumOfMags += centtfidf.get(term)*centtfidf.get(term);
//							sumOfMags += centtf.get(term)*centtf.get(term);
						}
						sumOfMags = Math.sqrt(sumOfMags);
//						System.out.println("cluster #"+clusterI.getId()+" sumOfMags: "+sumOfMags);
////						System.out.println(cent.getTfidf_or_Tf());
//						System.out.println(cent.getTfidf_or_Tf());
					}


					System.out.println("#################");
					System.out.println("Iteration: " + 0);


					for (int i = 0; i < kClusters; i++) {
						Cluster c = allClusters.get(i);
						//c.plotClusterTweets();
						prevListPoints.add(c.getPoints());
						//System.out.println("prevListPoints: "+prevListPoints.get(i));
					}




					//Calculate k-means calculate()
					int iteration = 0;

					//Iterate k-means ********************************************************************************
					while(!convergence && iteration < maxIterations) {

						//Clear cluster state
						//clearClusters();
						for(Cluster clusterK : allClusters) {
							clusterK.clear();
							//System.out.println("clusterK: "+clusterK.getPoints());
						}


						//getCentroids()
						List centroids = new ArrayList(kClusters);
						for(Cluster clusterH : allClusters) {
							Point currCentroid = clusterH.getCentroid();
							Point point = new Point(currCentroid.getTweetId(),currCentroid.getTfidf_or_Tf());
							centroids.add(point);
						}
						List<Point> lastCentroids = centroids;

						//Assign points to the closer cluster
						//assignCluster();
						lowestEuclidean = Double.POSITIVE_INFINITY; 
						cluster = 0;                 
						eucliDista = 0.0; 

						for(String currUserName : allUserNames) {

							LinkedHashMap<String,Double> currTFIDF = allUserDocumentsTFIDF.get(currUserName);
//							LinkedHashMap<String,Double> currTF = allUserDocumentsTF.get(currUserName);
							Point currPoint = new Point(currUserName,currTFIDF);
//							Point currPoint = new Point(currUserName,currTF);

							for(int i = 0; i < kClusters; i++) {
								Cluster c = allClusters.get(i);
								eucliDista = currPoint.eucliDistance(c.getCentroid());
								if(eucliDista < lowestEuclidean){
									lowestEuclidean = eucliDista;
									cluster = i;
								}
							}
							currPoint.setCluster(cluster);
							allClusters.get(cluster).addPoint(currPoint);

							lowestEuclidean = Double.POSITIVE_INFINITY;
						}


						//Get the current list of points
						List<List<Point>> currListPoints = new ArrayList<List<Point>>();

						//Calculate new centroids.
						//calculateCentroids();

						for(Cluster clusterI : allClusters) {

							List<Point> listOfPoints = clusterI.getPoints();
							currListPoints.add(listOfPoints);

							int numPoints = listOfPoints.size();
							// System.out.println("Sepide Number of points: " + numPoints);

							Point centroid = clusterI.getCentroid();
							if(numPoints > 0) {
								LinkedHashMap<String,Double> newCentroidTFIDF = new LinkedHashMap<String,Double>(baseCentroidTFIDF);
//								LinkedHashMap<String,Double> newCentroidTF = new LinkedHashMap<String,Double>(baseCentroidTF);
								for (Point p : listOfPoints)
								{
									Map<String,Double> currTFIDF = p.getTfidf_or_Tf();
//									LinkedHashMap<String,Double> currTF = p.getTfidf_or_Tf();
									for (String term : currTFIDF.keySet())
//									for (String term : currTF.keySet())
									{
										double pointTFIDFValue = 0.0;
										double centroidTFIDFValue = 0.0;
										pointTFIDFValue = currTFIDF.get(term);
										Double existingCentroidValue = newCentroidTFIDF.get(term);
										centroidTFIDFValue = existingCentroidValue == null
												? 0.0 : existingCentroidValue.doubleValue();
										newCentroidTFIDF.put(term, pointTFIDFValue+centroidTFIDFValue);
//										double pointTFValue = 0.0;
//										double centroidTFValue = 0.0;
//										pointTFValue = currTF.get(term);
//										centroidTFValue = newCentroidTF.get(term);
//										newCentroidTF.put(term, pointTFValue+centroidTFValue);
									}
								}

								double newClusterMag = 0.0;
								double currTermTFIDF = 0.0;
//								double currTermTF = 0.0;

								for (String term : newCentroidTFIDF.keySet())
//								for (String term : newCentroidTF.keySet())
								{
									currTermTFIDF = 0.0;
									currTermTFIDF = newCentroidTFIDF.get(term);
									if (currTermTFIDF > 0.0)
//									currTermTF = 0.0;
//									currTermTF = newCentroidTF.get(term);
//									if (currTermTF > 0.0)
									{
										currTermTFIDF = currTermTFIDF / numPoints;
										newClusterMag += currTermTFIDF * currTermTFIDF;
										newCentroidTFIDF.put(term, currTermTFIDF);
//										currTermTF = currTermTF / numPoints;
//										newClusterMag += currTermTF * currTermTF;
//										newCentroidTF.put(term, currTermTF);
									}

								}

								//Normalize the new cluster centroid as it might not be normalized

								newClusterMag = Math.sqrt(newClusterMag);
								for (String term : newCentroidTFIDF.keySet())
//								for (String term : newCentroidTF.keySet())
								{
									currTermTFIDF = newCentroidTFIDF.get(term);
//									currTermTF = newCentroidTF.get(term);

									if (currTermTFIDF > 0.0)
//									if (currTermTF > 0.0)
									{
										currTermTFIDF = currTermTFIDF / newClusterMag;
										newCentroidTFIDF.put(term, currTermTFIDF);
//										currTermTF = currTermTF / newClusterMag;
//										newCentroidTF.put(term, currTermTF);
									}

								}

								centroid.setTfidf_or_Tf(newCentroidTFIDF);
//								centroid.setTfidf_or_Tf(newCentroidTF);
								clusterI.setCentroid(centroid);
							}
						}


						System.out.println("~~~~~~~~~~~~~~~~~~~TESTING IF CENTROID UNIT VECTOR AFTER ITERATION "+iteration+"~~~~~~~~~~~~~~~~~~~~~~~");

						for(Cluster clusterI : allClusters) {
							sumOfMags = 0.0;
							cent = clusterI.getCentroid();
							Map<String,Double> centtfidf = cent.getTfidf_or_Tf();
//							LinkedHashMap<String,Double> centtf = cent.getTfidf_or_Tf();
							for (String term : centtfidf.keySet())
//							for (String term : centtf.keySet())
							{
								sumOfMags += centtfidf.get(term)*centtfidf.get(term);
//								sumOfMags += centtf.get(term)*centtf.get(term);
							}
							sumOfMags = Math.sqrt(sumOfMags);
//							System.out.println("cluster #"+clusterI.getId()+" sumOfMags: "+sumOfMags);
//							System.out.println(cent.getTfidf_or_Tf());
						}

						iteration++;


						//Check if convergence
						convergence = true;

						for (int i = 0; i < kClusters; i++) {
							List<Point> prevList = prevListPoints.get(i);
							List<Point> currList = currListPoints.get(i);
							// System.out.println("sepide list of points in each cluster" + currList);
							//System.out.println("prevList "+prevList);
							//System.out.println("currList "+currList);
							for (Point p : prevList)
							{

								if (!currList.contains(p))
								{
									convergence = false;
									break;
								}
							}
							if (convergence == false)
								break;

						}

						if (convergence == false)
							prevListPoints = currListPoints;

//						System.out.println("#################");
//						System.out.println("Iteration: " + iteration);


						//	for (int i = 0; i < kClusters; i++) {
						//		Cluster c = allClusters.get(i);
						//		c.plotClusterTweets();
						//	}

					}

					//Output the clusters and the tweets
					System.out.println("THE FINAL CLUSTERS "+ getLocalName());
					  for (Cluster c : allClusters)
					    {
						   c.plotCluster();
					    }

					String[] users = new String[allUserDocumentsTFIDF.keySet().size()];
					users = allUserDocumentsTFIDF.keySet().toArray(users);
						allUserScores = initializeTargetScoreRows(usersRec, users);
						Map<String,Double> userScore1 = new TreeMap<String,Double>();
					Map<String,Double> userScore2 = new TreeMap<String,Double>();
					double dpVectors = 0.0, score = 0.0, prevScore = 0.0;

						System.out.println("kmeans clustering completed Initialized target Scores to 0.0");

					System.out.println(getLocalName() + " All scores initialized to 0");
					//System.out.println("allUserScores:" +allUserScores);
					
					int scoreTimes = 0;
					int expectedTotalScores = 0;
					int clusterSize = 0;
					
					for (int i = 0; i < allClusters.size(); i++)
					{
						clusterSize = allClusters.get(i).getPoints().size();
						if (clusterSize > 1)
						{
							expectedTotalScores += ((clusterSize-1)*clusterSize)/2;
						}
					}
					
					System.out.println(getLocalName()+ " expectedTotalScores: "+expectedTotalScores);
					System.out.println(getLocalName() + " Calculating Scores");
					
					for (int i = 0; i < allClusters.size(); i++)
					{
						//Not comparing where clusters have a size of 1 or 0
						List<Point> pointsInCluster = allClusters.get(i).getPoints();
						// System.out.println("pointsInCluster.size(): "+pointsInCluster.size());
						if (pointsInCluster.size() > 1)
						{
							for (int j = 0; j < pointsInCluster.size()-1; j++)
							{
								String userName1 = pointsInCluster.get(j).getUserName();
								Map<String,Double> userName1Tfidf = pointsInCluster.get(j).getTfidf_or_Tf();
								Set<String> terms1 = userName1Tfidf.keySet();
//								LinkedHashMap<String,Double> userName1Tf = pointsInCluster.get(j).getTfidf_or_Tf();
//								Set<String> terms1 = userName1Tf.keySet();

								for (int k = j+1; k < pointsInCluster.size(); k++)
								{
									String userName2 = pointsInCluster.get(k).getUserName();
									Map<String,Double> userName2Tfidf = pointsInCluster.get(k).getTfidf_or_Tf();
									Set<String> terms2 = userName2Tfidf.keySet();
//									LinkedHashMap<String,Double> userName2Tf = pointsInCluster.get(k).getTfidf_or_Tf();
//									Set<String> terms2 = userName2Tf.keySet();

									dpVectors = calculateEuclideanSimilarityScore(
											userName1Tfidf, userName2Tfidf);

									//System.out.println("user1: "+user1+" user2: "+user2);
									//Update the scores of the users
									//System.out.println(allUserScores.get(user1));

									//prevScore = allUserScores.get(user1).get(user2);
									//	score = prevScore + dpVectors;
									//	userScore1 = allUserScores.get(user1);
									//	userScore1.put(user2, score);
									//	allUserScores.put(user1,(TreeMap<String,Double>)userScore1);
									//	userScore2 = allUserScores.get(user2);
									//	userScore2.put(user1, score);
									//	allUserScores.put(user2,(TreeMap<String,Double>)userScore2);

										score = dpVectors;
										putScoreForTrackedTargets(
												allUserScores, userName1, userName2, score);

									scoreTimes++;
									
									if (scoreTimes % 1000 == 0 || scoreTimes == expectedTotalScores)
										System.out.println(getLocalName()+" scoreTimes: "+scoreTimes);
									
									dpVectors=0.0;
									score=0.0;

								} //end for (int k = j+1; k < pointsInCluster.size(); k++)
							} //end for (int j = 0; j < pointsInCluster.size()-1; j++)
						} //end if (pointsInCluster.size() > 1)
					} //for (int i = 0; i < allClusters.size(); i++)

					//for (String s : allUserScores.keySet())
					//	{
					//		System.out.print("user: "+s+"\t");
					//		System.out.println(allUserScores.get(s));
					//	}

					System.out.println(getLocalName() + " Completed the scores Total Score Times: "+scoreTimes);

					
					


					endTimeAlgorithm = System.nanoTime();
					completionTimeAlgorithm = endTimeAlgorithm - startTimeAlgorithm;						

					//Output for K-means	   					
					textprocessing_wb_or_tfidf_Data.add("K-means=TP+TFIDF+K-means" + "\t" + agentName + "\t" + tweetCount + "\t" + completionTimeTextProcessing + "\t" + completionTimeTFIDF    + "\t" + completionTimeAlgorithm + "\t" + System.getProperty("line.separator"));
//					System.out.println(agentName+"- Total Tweets Processed: " + tweetCount + " TP:" + convertMs(completionTimeTextProcessing) + "ms TFIDF:" + convertMs(completionTimeTFIDF) + "ms K-means:" + convertMs(completionTimeAlgorithm) + "ms Total:" + convertMs(completionTimeTextProcessing+completionTimeTFIDF+completionTimeAlgorithm)+"ms");
					System.out.println("Mapper"+nodeNumber+"- Total Tweets Processed: " + tweetCount + " TP: " + convertMs(completionTimeTextProcessing) + " ms TFIDF: " + convertMs(completionTimeTFIDF) + " ms Reducer"+ nodeNumber+ " K-means: " + convertMs(completionTimeAlgorithm) + " ms Total: " + convertMs(completionTimeTextProcessing+completionTimeTFIDF+completionTimeAlgorithm)+" ms");
//					myGui.appendResult(agentName+"\nTotal Tweets Processed: " + tweetCount + " TP:" + round(completionTimeTextProcessing/1000000.00,2) + "ms TFIDF:" + round(completionTimeTFIDF/1000000.00,2) + "ms K-means:" + round(completionTimeAlgorithm/1000000.00,2) + "ms Total:" + round((completionTimeTextProcessing+completionTimeTFIDF+completionTimeAlgorithm)/1000000.00,2)+"ms");
					myGui.appendResult("Mapper"+nodeNumber+"- Total Tweets Processed: " + tweetCount + " TP: " + convertMs(completionTimeTextProcessing) + " ms TFIDF: " + convertMs(completionTimeTFIDF) + " ms Reducer"+ nodeNumber+ " K-means: " + convertMs(completionTimeAlgorithm) + " ms Total: " + convertMs(completionTimeTextProcessing+completionTimeTFIDF+completionTimeAlgorithm)+" ms");
	
					
				}	
				
				//SVM Recommendation?
				else if (RecommendationModeSupport.isSvmFamilyAlgorithm(algorithmRec))
				{
					if (algorithmRec == SCALABLE_SVM)
					{
						startTimeAlgorithm = System.nanoTime();
						ScalableSvmSharedCoordinator.Outcome outcome =
								ScalableSvmSharedCoordinator.score(
										getLocalName(),
										runLifecycle.getActiveRunId(),
										numRecAgents,
										Integer.parseInt(nodeNumber),
										allUserDocuments,
										userFollowee,
										datasetUserFollowee,
										usersRec,
										isClassifierGuidedUserMode(),
										algorithmSettings());
						if (isSharedScalablePublisher())
						{
							allUserScores = outcome.getScores();
							if (isClassifierGuidedUserMode())
							{
								precomputedClassifierEvidence.clear();
								precomputedClassifierEvidence.putAll(
										outcome.getEvidence());
							}
						}
						else
						{
							allUserScores =
									new TreeMap<String,TreeMap<String,Double>>();
							precomputedClassifierEvidence.clear();
						}
						endTimeAlgorithm = System.nanoTime();
						completionTimeAlgorithm =
								endTimeAlgorithm - startTimeAlgorithm;
						String summary = getLocalName()
								+ " shared Scalable Linear SVM completed: "
								+ outcome.describe()
								+ (Double.isFinite(outcome.getAccuracyPercent())
										? ", test accuracy "
												+ String.format("%.2f%%",
														outcome.getAccuracyPercent())
										: "");
						System.out.println(summary);
						myGui.appendResult(summary);
					}
					else if (isSvmBatchMode() || isClassifierGuidedUserMode())
					{
						startTimeAlgorithm = System.nanoTime();
						allUserScores = runIndependentSvmBatchRecommendations();
						endTimeAlgorithm = System.nanoTime();
						completionTimeAlgorithm = endTimeAlgorithm - startTimeAlgorithm;
							String svmName = RecommendationModeSupport.algorithmName(algorithmRec);
							textprocessing_wb_or_tfidf_Data.add(svmName+"-Batch=TP+TFIDF+"+svmName + "\t" + agentName + "\t" + tweetCount + "\t" + completionTimeTextProcessing + "\t" + completionTimeTFIDF    + "\t" + completionTimeAlgorithm + "\t" + System.getProperty("line.separator"));
							System.out.println("Mapper"+nodeNumber+"- Total Tweets Processed: " + tweetCount + " TP: " + convertMs(completionTimeTextProcessing) + " ms TFIDF: " + convertMs(completionTimeTFIDF) + " ms Reducer"+ nodeNumber+ " "+svmName+" Batch: " + convertMs(completionTimeAlgorithm) + " ms Total: " + convertMs(completionTimeTextProcessing+completionTimeTFIDF+completionTimeAlgorithm)+" ms");
							myGui.appendResult("Mapper"+nodeNumber+"- Total Tweets Processed: " + tweetCount + " TP: " + convertMs(completionTimeTextProcessing) + " ms TFIDF: " + convertMs(completionTimeTFIDF) + " ms Reducer"+ nodeNumber+ " "+svmName+" Batch: " + convertMs(completionTimeAlgorithm) + " ms Total: " + convertMs(completionTimeTextProcessing+completionTimeTFIDF+completionTimeAlgorithm)+" ms");
					}
						else
						{
							startTimeAlgorithm = System.nanoTime();
							allUserScores = runExactSvmWorkerRecommendations(
									trainSetFilePath,
									testSetFilePath,
									recommendSetFilePath,
									usersRec,
									preparedModelFeatureCount,
									trainSetUsers == null ? 0 : trainSetUsers.size(),
									followeeFollowers.size(),
									algorithmRec == SCALABLE_SVM
											? "single-target scalable linear SVM"
											: "single-target exact SVM");
							endTimeAlgorithm = System.nanoTime();
							completionTimeAlgorithm =
									endTimeAlgorithm - startTimeAlgorithm;

							String svmName = RecommendationModeSupport.algorithmName(
									algorithmRec);
							textprocessing_wb_or_tfidf_Data.add(
									svmName + "=TP+TFIDF+" + svmName + "\t" + agentName
									+ "\t" + tweetCount + "\t"
									+ completionTimeTextProcessing + "\t"
									+ completionTimeTFIDF + "\t"
									+ completionTimeAlgorithm + "\t"
									+ System.getProperty("line.separator"));
							String timing = "Mapper" + nodeNumber
									+ "- Total Tweets Processed: " + tweetCount
									+ " TP: " + convertMs(completionTimeTextProcessing)
									+ " ms TFIDF: " + convertMs(completionTimeTFIDF)
									+ " ms Reducer" + nodeNumber + " " + svmName + ": "
									+ convertMs(completionTimeAlgorithm)
									+ " ms Total: "
									+ convertMs(completionTimeTextProcessing
											+ completionTimeTFIDF
											+ completionTimeAlgorithm)
									+ " ms";
							System.out.println(timing);
							myGui.appendResult(timing);
						}
					}

				// Code added by Sepide

					else if (algorithmRec == Doc2Vec)
				{
					startTimeAlgorithm = System.nanoTime();
					int topn = Integer.parseInt(myGui.recommendationField.getText());
					if (isUserRankerFolloweeMode())
					{
						topn = Math.max(topn, datasetUserFollowee.size());
					}
					allUserScores = runDoc2VecBatchRecommendations(
							topn, !isUserRankerFolloweeMode());
					endTimeAlgorithm = System.nanoTime();
					completionTimeAlgorithm = endTimeAlgorithm - startTimeAlgorithm;

					System.out.println("Mapper"+nodeNumber+"- Total Tweets Processed: " + tweetCount + " TP: " + convertMs(completionTimeTextProcessing) + " ms Reducer"+ nodeNumber+ " Doc2Vec Batch: " + convertMs(completionTimeAlgorithm) + " ms Total: " + convertMs(completionTimeTextProcessing+completionTimeAlgorithm)+" ms");
					myGui.appendResult("Mapper"+nodeNumber+"- Total Tweets Processed: " + tweetCount + " TP: " + convertMs(completionTimeTextProcessing) + " ms Reducer"+ nodeNumber+ " Doc2Vec Batch: " + convertMs(completionTimeAlgorithm) + " ms Total: " + convertMs(completionTimeTextProcessing+completionTimeAlgorithm)+" ms");
				}
				else if (false && algorithmRec == Doc2Vec)
				{
					startTimeAlgorithm = System.nanoTime();
					Map<String,Double> predictedFollowerScore = new LinkedHashMap<String,Double>();
					int topn = Integer.parseInt(myGui.recommendationField.getText());
					String[] wordsF = null;
					String[] wordsLine = null;
					Workbook workbook = null;
					File myCSV = null;
					String out_file_pattern = null;
					int nodeNumInt = 0;
					String[] usersForRec = new String[usersRec.size()];
					usersForRec = usersRec.toArray(usersForRec);
					allUserScores = new TreeMap<String,TreeMap<String,Double>>();
					Map<String,Double> userScore1 = new TreeMap<String,Double>();
					double followeeScore = 0.0;
					
					try {
						File fileFromGui = getConfiguredDatasetFile();
						if (fileFromGui == null)
						{
							throw new IOException(
									"No dataset file is configured for Doc2Vec.");
						}
						//System.out.println("File path" + fileFromGui);
						//System.out.println("File path" + myGui.fileChooser.getSelectedFile());
						//File txtFile = new File((myGui.fileChooser.getSelectedFile()));
						//String contents = FileUtils.readFileToString(fileFromGui);
						String filePath = fileFromGui.getAbsolutePath();
						System.out.println("File Path for Sepide:" + filePath);
                        String doc2vecDirName = "Dataset/424k/";
					    File doc2vecDir = new File(doc2vecDirName);
						if (!doc2vecDir.exists())
						{
								doc2vecDir.mkdirs();
						}	
                        
						//myCSV = myGui.fileChooser.getSelectedFile();
						//nodeNumInt = Integer.parseInt((myGui.numNodesField.getText()));
						//out_file_pattern = doc2vecDirName + "data_set_doc2vec_"+nodeNumber+".txt";
						out_file_pattern = filePath;
						//out_file_pattern = dataSetFilePath;
						//out_file_pattern = doc2vecDirName+"data_set_doc2vec_"+getLocalName()+".txt";
						//System.out.println("File path: "+ txtFile);
						
					}
					
					catch(Exception e)
						{
						  e.printStackTrace();
						}

                        String doc2vecDirLoc = "TwitterGatherDataFollowers/userRyersonU/";
					    File doc2vecLocDir = new File(doc2vecDirLoc);
						if (!doc2vecLocDir.exists())
						{
								doc2vecLocDir.mkdirs();
						}						
						
				  try {
					  
						
					//usersRec = myGui.getUsersRec();

					//java.lang.ProcessBuilder pb = new ProcessBuilder("C:/Program Files/Python39/python.exe","D:/Simulator-S-15-May-2020/TwitterGatherDataFollowers/userRyersonU/doc2vec.py",""+usersRec.get(0).toString(),""+topn,""+myCSV).inheritIO();
					//java.lang.ProcessBuilder pb = new ProcessBuilder("C:/Program Files/Python39/python.exe",doc2vecDirLoc + "doc2vec.py",""+usersRec.get(0).toString(),""+topn,""+out_file_pattern,""+nodeNumber).inheritIO();
					int doc2vecWorkers = Math.min(Math.max(Runtime.getRuntime().availableProcessors() - 1, 1), 12);
					java.lang.ProcessBuilder pb = new ProcessBuilder(
							getPythonCommand(),
						doc2vecDirLoc + "doc2vec_shared.py",
						""+usersRec.get(0).toString(),
						""+topn,
						""+out_file_pattern,
						""+nodeNumber,
						""+numRecAgents,
						""+doc2vecWorkers
					);
					//java.lang.ProcessBuilder pb = new ProcessBuilder("C:/Program Files/Python39/python.exe","D:/Simulator-S-15-May-2020/TwitterGatherDataFollowers/userRyersonU/doc2vec3.py",""+usersRec.get(0).toString(),""+topn,""+myGui.fileChooser.getSelectedFile()).inheritIO();
                    
					Process p = pb.start();
					InputStream errorStream = p.getErrorStream();
					BufferedReader errorReader = new BufferedReader(new InputStreamReader(errorStream));
					BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
					BufferedReader r = new BufferedReader(reader);
					//System.out.println("I am a line here");
					String line;
					int counter = 0;
					if ((line = r.readLine()) == null) {
						System.out.println("it is null");
					}
				    
					  while ((line = r.readLine()) != null) {
						counter = counter+1;
						//line = r.readLine();
						if ((line != null) &&  line.contains("OpenBLAS")){
							System.out.println("I'm coming from OpenBLAS : " + line);
						}
						else if ((line != null) &&  line.contains("completionTime")){
							System.out.println("I'm coming from Time : " + line);
						}
						else if ((line != null) &&  line.contains("cores")){
							System.out.println("I'm coming from cores : " + line);
						}
						else if ((line != null) &&  line.contains("model")){
							System.out.println("I'm coming from model : " + line);
						}
						else if ((line != null) &&  line.contains("python")){
						System.out.println("I'm coming from python : " + line);
					    line = line.replace("python", "");
						line = line.replace("(", "");
						line = line.replace(")", "");
						line = line.replaceAll("'", "");
						wordsLine = line.split(",");
						predictedFollowerScore.put(wordsLine[0],Double.parseDouble(wordsLine[1]));
					    userScore1.put((wordsLine[0]).trim(),Double.parseDouble(wordsLine[1]));
						}
						
						else if ((line != null) &&  line.contains("Testing accuracy for movie plots MLPClassifier%s")){
							System.out.println("Testing accuracy for movie plots MLPClassifier%s : " + line);
						}
						else if ((line != null) &&  line.contains("Testing F1 score for movie plots MLPClassifier: {}")){
							System.out.println("Testing F1 score for movie plots MLPClassifier: {} : " + line);
						}
						else if ((line != null) &&  line.contains("Similarity after MLP")){
							System.out.println("Similarity after MLP : " + line);
						}
						
						/*if ((line != null) &&  line.contains("cores:")) {
							System.out.println("I'm coming from python : " + line);
						}*/
					}
					
					endTimeAlgorithm = System.nanoTime();
					completionTimeAlgorithm = endTimeAlgorithm - startTimeAlgorithm;
					 
					System.out.println("counter: " + counter);
					reader.close();
					
					
					for (int i = 0; i < usersForRec.length; i++) {
						allUserScores.put(usersForRec[i],(TreeMap<String,Double>) userScore1);
					}
					userScore1 = new TreeMap<String,Double>();
					
					String line1;
					while ((line1 = errorReader.readLine()) != null) {
					System.out.println(line1); // Display the error message
					// You can also store the error message in a variable or perform any other desired error handling.
					}
					
					int exitCode = p.waitFor();
					if (exitCode != 0)
					{
						throw new IOException(
								"Doc2Vec subprocess exited with code " + exitCode);
					}
					
					             try 
									 {
										  Thread.sleep(400);
									 } 
								  catch(InterruptedException e)
									{
									  e.printStackTrace();
									}
						
						// loop through the linkedHashMap 
						
						Set entrySet = predictedFollowerScore.entrySet();
						Iterator it = entrySet.iterator();
						
						for (int i = 0; i < usersForRec.length; i++) {
							while (it.hasNext()){
							System.out.println("test print: " + it.next());
							
						  }
						  
						}
						
						/*for (int i = 0; i < usersForRec.length; i++) {
							
							for (Entry<String, Double> entry: predictedFollowerScore.entrySet()){
								userScore1.put(entry.getKey(),entry.getValue());
						        //lineF = readerFollowee.readLine();
					            allUserScores.put(usersForRec[i],(TreeMap<String,Double>) userScore1);
							}
							
							userScore1 = new TreeMap<String,Double>();
						} */  //comment on Dec. 9
						
						// loop over the allUserScores
						for (Map.Entry<String,TreeMap<String,Double>> entry: allUserScores.entrySet()) {
							
							System.out.println("Key in allUserScores " + entry.getKey() + " Value in allUserScores: " + entry.getValue());
						}  
						
						  
					    System.out.println("Doc2Vec Implementation is finished");
						Map<String,String> userPredictedFollowee5 = new LinkedHashMap<String,String>();
						//System.out.println("test 1");
						String importantStuffDirName = "important-stuff/";
						File importantStuffDir = new File(importantStuffDirName);
						if (!importantStuffDir.exists())
						{
								importantStuffDir.mkdirs();
						}
						// reading from a text file to output it
						//File myFile = new File(importantStuffDirName + nodeNumber + "_followeeRec.txt");
						//BufferedReader readerFollowee = new BufferedReader(new FileReader(importantStuffDirName + nodeNumber + "_followeeRec.txt"));
						
						//Integer.parseInt(myGui.recommendationField.getText())
						
						// end of reading from a file to output it
						
						/*for (String name : userPredictedFollowee5.keySet()) {
							System.out.println("names of Predicted Followees:" + userPredictedFollowee5.get(name));
						}  */  //commented out on Nov. 17
						
					/*for (String recUser : userPredictedFollowee5.keySet())
					   {
						 for (String followeeUser: followeeFollowers.keySet())
						 {
							  if (userPredictedFollowee5.get(recUser).equals(followeeUser))
							  {followeeScore = 1.0;}
							  else
							  {followeeScore = 0.0;}
								  //followeeScore = Double.parseDouble(wordsF[1]);
							
							  userScore1.put(followeeUser,followeeScore);
						 }
						allUserScores.put(recUser,(TreeMap<String,Double>)userScore1);
						userScore1 = new TreeMap<String,Double>();
					  }  */ // commented out on Nov. 18
					  
					  // Getting the Scores
					String lineF = null;
					System.out.println("usersForRec.length: " + usersForRec.length);
					/* for (int i = 0; i < usersForRec.length; i++) { // comment on Dec. 8 
					inner:	for (int j = 0; j < myFile.length()-1; j++)  {
							  lineF = readerFollowee.readLine();
						      System.out.println("line in the file: " + lineF);
							  if (lineF == null) {
								  break inner;
							  }
							  lineF = lineF.replace("(", "");
							  lineF = lineF.replace(")", "");
							  lineF = lineF.replaceAll("'", "");
							  wordsF = lineF.split(",");
							  
							  userScore1.put(wordsF[0],Double.parseDouble(wordsF[1])); 
							  //lineF = readerFollowee.readLine();
							  allUserScores.put(usersForRec[i],(TreeMap<String,Double>) userScore1);
						}
						userScore1 = new TreeMap<String,Double>();
					}
					readerFollowee.close(); */
					  
					  // End of code for getting the scores
					  
					  // Code for loop through the allUserScores
					  
					   for (Map.Entry<String,TreeMap<String,Double>> entry :allUserScores.entrySet()) {
						   System.out.println("Sepide looping through this allUserScores second time from file " + entry.getKey() + " , " + entry.getValue());
					   }  //commented out on Nov. 18 
					 
					  // end of code for loop through the allUserScores
					
					//endTimeAlgorithm = System.nanoTime();
					//completionTimeAlgorithm = endTimeAlgorithm - startTimeAlgorithm;
					myGui.appendResult("Mapper"+nodeNumber+"- Total Tweets Processed: " + tweetCount + " TP: " + convertMs(completionTimeTextProcessing) + " ms Reducer"+ nodeNumber+ " Doc2Vec: " + convertMs(completionTimeAlgorithm) + " ms Total: " + convertMs(completionTimeTextProcessing+completionTimeAlgorithm)+" ms");
					try 
									 {
										  Thread.sleep(1000);
									 } 
								  catch(InterruptedException e)
									{
									  e.printStackTrace();
									}
						
						// read from the text file and add it to userPredictedFollowee
						
						/*for (int i = 0; i < Integer.parseInt(myGui.recommendationField.getText()); i++) {
							//userPredictedFollowee.put(usersRec.get(i),predictedClass);
							
							// predicted label should be the one predicted by Doc2Vec
						} */
						
						
					
						} 	
						 /* try {
						    Process p2 = Runtime.getRuntime().exec("C:\\Program Files\\Python39\\python.exe D:\\Simulator-S-15-May-2020\\TwitterGatherDataFollowers\\userRyersonU\\doc2vec.py"+usersRec);
						
						        try 
									 {
										  Thread.sleep(5000);
									 } 
								  catch(InterruptedException e)
									{
									  e.printStackTrace();
									}
									
									System.out.println("Sepide is done with Doc2Vec");
									
							} */ 
								
						catch (IOException e) {
						      // TODO Auto-generated catch block
						       e.printStackTrace();
					        } 
					    catch(InterruptedException e)
							{
								 // this part is executed when an exception (in this example InterruptedException) occurs
							}


					/*ACLMessage toMergeMsg = new ACLMessage(ACLMessage.INFORM);
					toMergeMsg.addReceiver( new AID("Organizing Agent1", AID.ISLOCALNAME) );
					toMergeMsg.setPerformative(ACLMessage.INFORM);
					toMergeMsg.setOntology("Merge Lists");
					try {
						toMergeMsg.setContentObject((Serializable) allUserScores);
						send(toMergeMsg);
						// System.out.println(getLocalName()+" sent toMergeMsg");
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}  */  
								
							
				}
				
				// Code added by Sepide for Multi-Node Doc2Vec
				
				/* else if (algorithmRec == Doc2Vec && numRecAgents > 1 ) {
					  
					  System.out.println("Doc2vec for multi nodessss");
					  
					  int topn = Integer.parseInt(myGui.recommendationField.getText());
					  startTimeAlgorithm = System.nanoTime();
					  String[] wordsF = null;
					  String doc2vecDirName = "D:/Simulator-S-15-May-2020/Dataset/424k/";
					  File doc2vecDir = new File(doc2vecDirName);
						if (!doc2vecDir.exists())
						{
								doc2vecDir.mkdirs();
						}
						//int nodeNumInt = Integer.parseInt(nodeNumber);
						//String userREC = usersRec.get(0).toString();
					  
					  try {
						  
						  File fileFromGui = myGui.fileChooser.getSelectedFile();
						  String filePath = fileFromGui.getPath();
						  System.out.println("File Path for Sepideeeeeeeeeeee:" + filePath);
                          String contents = FileUtils.readFileToString(fileFromGui);
						  
						  //App applic = new App();
						  
						  // Code added on Nov. 20
						  
						  
						int nodeNumInt = Integer.parseInt((myGui.numNodesField.getText()));
						System.out.println("Number of nodes for sepide: " + nodeNumInt);
						usersRec = myGui.getUsersRec(); 
						String userREC = usersRec.get(0).toString();
						fileFromGui = myGui.fileChooser.getSelectedFile();  // added by Sepide
						filePath = fileFromGui.getPath();
						
						//FileSplitterService svc = new FileSplitterService();
						//SplitterParams params = new SplitterParams();
						params.setParts(nodeNumInt);
						params.setFileName(filePath);
						params.setCopyString(userREC);

						try {
							fsv.splitFile(params);
						} catch (IOException e) {
							e.printStackTrace();
						}
						  
						  // end of code added on Nov. 2
					  }
					  
					   catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					  
				  }  */
				// End of code added by Sepide for Multi-Node Doc2Vec 
				
				else if (algorithmRec == MLP)
				{
					AlgorithmParameterSettings algorithmSettings = algorithmSettings();
					int mlpHiddenNeurons = algorithmSettings.getMlpHiddenNeurons();
					int mlpHiddenLayers = algorithmSettings.getMlpHiddenLayers();
					double mlpLearningRate = algorithmSettings.getMlpLearningRate();
					double mlpMaxError = algorithmSettings.getMlpMaxError();
					boolean sparseFederatedMlp =
							algorithmSettings.isSparseFederatedMlpEnabled();

					startTimeAlgorithm = System.nanoTime();

					// determineTrainingTestSet();
					determineCentralTrainingTestSet();
					System.out.println(getLocalName()+" test set "+ testSetUsers.size() +": "+testSetUsers);
					System.out.println(getLocalName()+" train set "+ trainSetUsers.size() +": "+trainSetUsers);
					System.out.println(getLocalName()+" data set "+ dataSetUsers.size() +": "+dataSetUsers);
					
					//Start of creating the training set 		
		
					int uniqueWordCountTrain = 0;
					// int numFollowees = followeeFollowers.keySet().size();
					int numFollowees = datasetFollowees.size();
					int numUniqueDocTerms = allUniqueDocTerms.size();
					System.out.println(getLocalName()+ " numUniqueDocTerms: " + numUniqueDocTerms);
					int mlpDenseRows =
							(trainSetUsers == null ? 0 : trainSetUsers.size())
							+ (testSetUsers == null ? 0 : testSetUsers.size())
							+ Math.max(1, usersRec.size());
					if (!sparseFederatedMlp)
					{
						validateLegacyNeurophMlpConfiguration(
								numUniqueDocTerms,
								mlpHiddenNeurons,
								mlpHiddenLayers,
								numFollowees,
								trainSetUsers == null ? 0 : trainSetUsers.size(),
								testSetUsers == null ? 0 : testSetUsers.size(),
								usersRec == null ? 0 : usersRec.size());
					}
					long mlpReservation;
					if (sparseFederatedMlp)
					{
						int sparseRows = mlpDenseRows;
						long nonZeroFeatureCount =
								countSparseFeatureEntries(trainSetUsers)
								+ countSparseFeatureEntries(testSetUsers)
								+ countSparseFeatureEntries(usersRec);
						mlpReservation =
								RecommendationMemoryCoordinator.estimateSparseMlpTrainingBytes(
										numUniqueDocTerms,
										mlpHiddenNeurons,
										mlpHiddenLayers,
										numFollowees,
										nonZeroFeatureCount,
										sparseRows);
					}
					else
					{
						mlpReservation =
								RecommendationMemoryCoordinator.estimateMlpTrainingBytes(
										numUniqueDocTerms,
										mlpHiddenNeurons,
										mlpHiddenLayers,
										numFollowees,
										mlpDenseRows);
					}
					activeMlpLease = RecommendationMemoryCoordinator.acquire(
							getLocalName(),
							"MLP dataset construction and training",
							RecommendationMemoryCoordinator.Workload.MLP_TRAIN,
							mlpReservation);
					if (!sparseFederatedMlp)
					{
						reportLegacyNeurophMlpConfiguration(
								numUniqueDocTerms,
								mlpHiddenNeurons,
								mlpHiddenLayers,
								numFollowees,
								trainSetUsers == null ? 0 : trainSetUsers.size(),
								testSetUsers == null ? 0 : testSetUsers.size(),
								usersRec == null ? 0 : usersRec.size(),
								mlpReservation);
					}


					//Create the class(followee) vectors
					double[][] followeeClassVectors = new double[numFollowees][numFollowees];
					followeeNames = new String[numFollowees];
					// followeeNames = followeeFollowers.keySet().toArray(followeeNames);
					followeeNames = datasetFollowees.toArray(followeeNames);
					followeeIndex = new LinkedHashMap<String,Integer>();
					
					for (int i = 0; i < followeeNames.length; i++)
					{
						followeeIndex.put(followeeNames[i],i);
					} 
		
					for (int i = 0; i < followeeClassVectors.length; i++)
					{
						for (int j = 0; j < followeeClassVectors[0].length; j++)
						{
							if (i == j)
								followeeClassVectors[i][j] = 1;
							else
								followeeClassVectors[i][j] = 0;
						}
					}
					if (sparseFederatedMlp)
					{
						runSparseFederatedMlp(
								algorithmSettings,
								numUniqueDocTerms,
								numFollowees);
					}
					else
					{
					
					// Create MultiLayerPerceptron for 10-fold Cross-Validation with Weka added by Sepide
					/* int folds = 10;     //commented out on May-1 2023
					mlp = new MultilayerPerceptron();
					mlp.setLearningRate(LEARNING_RATE_MLP);
					mlp.setMomentum(0.2);
					mlp.setHiddenLayers("10");  */
					
                   /* try {   //commented out on May-1 2023 
					
                    
					//S int runs  = 10;
					 
					FileReader datareader = new FileReader(dataSetFilePath); 
                    Instances data = new Instances(datareader);					
					data.setClassIndex((data.numAttributes())-1);
					
					 //S for (int r = 0; r < runs; r++) {
						
						// randomize data
						//S int seed = r + 1;
						//S String[] args;
						//S int seed  = Integer.parseInt(Utils.getOption("s", args));
						int seed = 1;
						Random random = new Random(seed);
					    
						data.randomize(random);
						
						 if (data.classAttribute().isNominal())
                         data.stratify(folds);
					 
					    // Perform Cross-Validation
						System.out.println();
						System.out.println("=== Setup ===");
						System.out.println("Classifier: " + mlp.getClass().getName() + " " + Utils.joinOptions(mlp.getOptions()));
						System.out.println("Dataset: " + data.relationName());
						System.out.println("Folds: " + folds);
						System.out.println("Seed: " + seed);
                        System.out.println();
					 
						Evaluation evalAll = new Evaluation(data);
					    for (int i = 0; i < folds; i++) { 
					           
							   Evaluation eval = new Evaluation(data);
							   Instances train = data.trainCV(folds, i, random);
							   //S eval.setPriors(train);
							   
							   Instances test = data.testCV(folds, i);
							   Classifier mlpCopy = AbstractClassifier.makeCopy(mlp);
							   mlpCopy.buildClassifier(train);
                               mlp.buildClassifier(data);							   
							   eval.evaluateModel(mlpCopy, test);
							   evalAll.evaluateModel(mlpCopy, test);
							   
							   // output evaluation
							   System.out.println();
							   System.out.println(eval.toMatrixString("=== Confusion matrix for fold " + (i+1) + "/" + folds + " ===\n"));
							   
						}
							   //S System.out.println("Test for Weka Cross Validation for fold " + (i+1));
							   /* System.out.println();
							   System.out.println("=== Setup run " + (r+1) + " ===");
							   System.out.println("Classifier: " + Utils.toCommandLine(mlp));
							   System.out.println("Dataset: " + data.relationName());
							   System.out.println("Folds: " + folds);
							   System.out.println("Seed: " + seed);  */
							   /* System.out.println();  // commented out on May-1 2023 
							   evalAll.crossValidateModel(mlp, data, folds, new Random(seed));
                               System.out.println(evalAll.toSummaryString("=== " + folds + "-fold Cross-validation ===", false));
							   System.out.println("Mean Root Squared Error: "+evalAll.errorRate());   //Printing Training Mean root squared Error
							   System.out.println("Estimated Accuracy: "+Double.toString(evalAll.pctCorrect()));
							   System.out.println("Estimated Incorrectly classified: "+Double.toString(evalAll.pctIncorrect()));
							   System.out.println("===================================================");
					           System.out.println(evalAll.toMatrixString());  */
							   //S System.out.println(evalAll.toSummaryString());
                               //S System.out.println(evalAll.toMatrixString("=== Confusion matrix for fold " + (r+1) + "/" + folds + " ===\n"));
							    
                        //S }
																					
					
					//int cls = Instances.classIndex();
					
					//Apply K-fold cross validation
					/* System.out.println("test for line 3690");
                    //S System.out.println(evalAll.toSummaryString("=== " + folds + "-fold Cross-validation ===", false));
					Evaluation eval = new Evaluation(data); 
					eval.crossValidateModel(mlp, data, folds, new Random(1));
					System.out.println("\n10-fold CV:\n" + eval.toSummaryString());
					System.out.println("Mean Root Squared Error: "+eval.errorRate()); //Printing Training Mean root squared Error
					System.out.println("Estimated Accuracy: "+Double.toString(eval.pctCorrect()));
					System.out.println("Estimated Incorrectly classified: "+Double.toString(eval.pctIncorrect()))
					*/
					
					//S System.out.println("Precision: "+Double.toString(eval.precision(cls)));
					
					/* }  // commented out on May-1 2023 
					
					catch(Exception ex){
                    ex.printStackTrace();
                    } */	
					
					// Comment-out by Sepide - Neuroph NN
					//Sep 30-2023 MultiLayerPerceptron multiLayer = new MultiLayerPerceptron(TransferFunctionType.TANH, numUniqueDocTerms, HIDDEN_NEURONS, HIDDEN_NEURONS, numFollowees);
					//Sep 30-2023 SoftMax softMaxAct = new SoftMax(multiLayer.getLayers().get(multiLayer.getLayers().size()-1));
					//Sep30-2023 int hiddenLayers = multiLayer.getLayersCount() - 2;
					//Sep30-2023 System.out.println("Number of Hidden Layers in Neuroph: "+ hiddenLayers);
					//Sep3-2023 List<Layer> mlpLayers = multiLayer.getLayers();
					//Sep30-2023 List<Neuron> outputNeurons = mlpLayers.get(mlpLayers.size()-1).getNeurons();
					/* for (Neuron outputNeuron : outputNeurons)
					{
						outputNeuron.setTransferFunction(softMaxAct);
					} */  //Sep30-2023 
					
					   //Sep30-2023 BackPropagation nodeLearningRule = (BackPropagation) multiLayer.getLearningRule();
					   //Sep30-2023 nodeLearningRule.setLearningRate(LEARNING_RATE_MLP);
					   //Sep30-2023 nodeLearningRule.setMaxError(MAX_ERROR_MLP); 
					   //CrossValidation crossValidation = new CrossValidation(multiLayer, dataSet, 10);
					   //crossValidation.addEvaluator(new ClassifierEvaluator.MultiClass(followeeNames));
					   
					   
					  /* try { // commented out on May-1 2023
	  
	                         crossValidation.run(); 
	                    } 
					   
					    catch(InterruptedException e) {
		  
		                         System.out.println("sleep time finished");
		  
	                       }
			 
			           catch(ExecutionException ee) {
		  
		                    System.out.println("execution time finished");
		             
		  
	                   }
					   System.out.println("Test of cross Validation implementation");
					   CrossValidationResult results = crossValidation.getResult();
					   results.printResult();
					   System.out.println("End of cross Validation implementation"); */ 

                    //End of Code added by Sepide for Cross-Validation
					
										
					 trainMLP = new DataSet(numUniqueDocTerms,numFollowees);			
					
					//Setup the vector data of each user for training
					for (String currUser : trainSetUsers)
					{
						Map<String,Double> currDocTfidf = allUserDocumentsTFIDF.get(currUser);
						double[] currUserTfidfVector = vectorArrayFormat(currDocTfidf,allUniqueDocTerms);
						int indexFollowee = followeeIndex.get(userFollowee.get(currUser));
						
						trainMLP.addRow(new DataSetRow(currUserTfidfVector, followeeClassVectors[indexFollowee]));
					} 
										
					// try {
						// FileWriter writer = new FileWriter("testVectors.txt", true); //append
						// BufferedWriter bufferedWriter = new BufferedWriter(writer);
									
						// System.out.println("trainSet");
						// bufferedWriter.write("trainSet");
						// bufferedWriter.newLine();
						
						// List<DataSetRow> setRows = trainMLP.getRows();
						// for (int i = 0; i < setRows.size(); i++)
						// {
							// double[] inputVector = setRows.get(i).getInput();
							// double[] outputVector = setRows.get(i).getDesiredOutput();
							// bufferedWriter.write("User: "+trainSetUsers.get(i)+" Followee: "+userFollowee.get(trainSetUsers.get(i)));
							// bufferedWriter.newLine();
							// bufferedWriter.write("Input: "+ Arrays.toString(inputVector) + " Output: " + Arrays.toString(outputVector) );
							// bufferedWriter.newLine();
						// }
						// bufferedWriter.newLine();
						// bufferedWriter.close();
					// } catch (IOException e) {
						// e.printStackTrace();
					// }
					
					
					//Start of creating the test set and recommend set
					
					testMLP = new DataSet(numUniqueDocTerms,numFollowees);
					
					//Setup the vector data of each user for test
					for (String currUser : testSetUsers)
					{
						Map<String,Double> currDocTfidf = allUserDocumentsTFIDF.get(currUser);
						double[] currUserTfidfVector = vectorArrayFormat(currDocTfidf,allUniqueDocTerms);
						int indexFollowee = followeeIndex.get(userFollowee.get(currUser));
						
						testMLP.addRow(new DataSetRow(currUserTfidfVector, followeeClassVectors[indexFollowee]));
					  
					}
					// try {
						// FileWriter writer = new FileWriter("testVectors.txt", true); //append
						// BufferedWriter bufferedWriter = new BufferedWriter(writer);
						
						// System.out.println("testSet");
						// bufferedWriter.write("testSet");
						// bufferedWriter.newLine();
						
						// List<DataSetRow> setRows = testMLP.getRows();
						// for (int i = 0; i < setRows.size(); i++)
						// {
							// double[] inputVector = setRows.get(i).getInput();
							// double[] outputVector = setRows.get(i).getDesiredOutput();
							// bufferedWriter.write("User: "+testSetUsers.get(i)+" Followee: "+userFollowee.get(testSetUsers.get(i)));
							// bufferedWriter.newLine();
							// bufferedWriter.write("Input: "+ Arrays.toString(inputVector) + " Output: " + Arrays.toString(outputVector) );
							// bufferedWriter.newLine();
						// }
						// bufferedWriter.newLine();
						// bufferedWriter.close();
					// } catch (IOException e) {
						// e.printStackTrace();
					// }
					
					
					recMLP = new DataSet(numUniqueDocTerms,numFollowees);
					
					//Setup the vector data of each user for test
					for (String currUser : usersRec)
					{
						Map<String,Double> currDocTfidf = allUserDocumentsTFIDF.get(currUser);
						double[] currUserTfidfVector = vectorArrayFormat(currDocTfidf,allUniqueDocTerms);
						int indexFollowee = followeeIndex.get(userFollowee.get(currUser));
						
						recMLP.addRow(new DataSetRow(currUserTfidfVector, followeeClassVectors[indexFollowee]));
					} 
					
					// try {
						// FileWriter writer = new FileWriter("testVectors.txt", true); //append
						// BufferedWriter bufferedWriter = new BufferedWriter(writer);
						
						// System.out.println("recSet");
						// bufferedWriter.write("recSet");
						// bufferedWriter.newLine();
						
						// List<DataSetRow> setRows = recMLP.getRows();
						// for (int i = 0; i < setRows.size(); i++)
						// {
							// double[] inputVector = setRows.get(i).getInput();
							// double[] outputVector = setRows.get(i).getDesiredOutput();
							// bufferedWriter.write("User: "+usersRec.get(i)+" Followee: "+userFollowee.get(usersRec.get(i)));
							// bufferedWriter.newLine();
							// bufferedWriter.write("Input: "+ Arrays.toString(inputVector) + " Output: " + Arrays.toString(outputVector) );
							// bufferedWriter.newLine();
						// }
						// bufferedWriter.newLine();
						// bufferedWriter.close();
					// } catch (IOException e) {
						// e.printStackTrace();
					// }
					
					   
					try
					{
					nodeMLP = createLegacyNeurophMlp(
							numUniqueDocTerms,
							mlpHiddenNeurons,
							mlpHiddenLayers,
							numFollowees);
					SoftMax softMaxAct = new SoftMax(nodeMLP.getLayers().get(nodeMLP.getLayers().size()-1));
					int hiddenLayers = nodeMLP.getLayersCount() - 2;
					System.out.println("Number of Hidden Layers in Neuroph: "+ hiddenLayers);
					List<Layer> mlpLayers = nodeMLP.getLayers();
					List<Neuron> outputNeurons = mlpLayers.get(mlpLayers.size()-1).getNeurons();
					for (Neuron outputNeuron : outputNeurons)
					{
						outputNeuron.setTransferFunction(softMaxAct);
					}
					
					BackPropagation nodeLearningRule = (BackPropagation) nodeMLP.getLearningRule();
					nodeLearningRule.setLearningRate(mlpLearningRate);
					nodeLearningRule.setMaxError(mlpMaxError);
					//nodeLearningRule.setMaxIterations(100);

					System.out.println(getLocalName()+" training MLP");
					
					startTimeTrain = System.nanoTime();
					//System.out.println("The line before the training starts");
					nodeMLP.learn(trainMLP);
					trainMLP = null;
					//System.out.println(" This line is taking a lot of time");
					
					endTimeTrain = System.nanoTime();
					
					completionTimeTrain = endTimeTrain - startTimeTrain;
					}
					catch (Throwable failure)
					{
						nodeMLP = null;
						trainMLP = null;
						releaseActiveMlpLease();
						throw failure;
					}
					
					// try{
						// System.in.read();
					// }
					// catch (IOException e)
					// {
						// e.printStackTrace();
					// }
										
					if (numRecAgents < 2)
					{
						startTimeTest = System.nanoTime();
						testNeuralNetwork(nodeMLP,testMLP);
						endTimeTest = System.nanoTime();
						completionTimeTest = endTimeTest - startTimeTest;
						recNeuralNetwork(nodeMLP,recMLP);
						
						
						// Calculating the accuracy for MLP Neuroph
						
						String nnDirName = "Stored_NN/";
						File nnDir = new File(nnDirName);
						if (!nnDir.exists())
						{
								nnDir.mkdirs();
						}
						
						String nodeMLPFileName = nnDirName+getLocalName()+"_MLP.nnet";
						nodeMLP.save(nodeMLPFileName);
						NeuralNetwork neuralNet = nodeMLP;
						double accuracy = reportLegacyNeurophAccuracy(
								neuralNet,
								testMLP,
								"single-node local model");
						//S System.out.println("Accuracy: " + String.format("%.2f%%", accuracy));

						
						// End of code for calculating the accuracyfor MLP Neuroph
						
						
			   
						
						// Code for confusion Matrix Neuroph
						
						int numClasses = numFollowees;
						int[][] confusionMatrix = new int[numClasses][numClasses];
						
						try {
							
						for (DataSetRow row : dataSet.getRows()) {
							neuralNet.setInput(row.getInput());
							neuralNet.calculate();
							double[] networkOutput = neuralNet.getOutput();

							// Determine the actual class (assuming it's a one-hot encoded array)
							int actualClass = findIndexOfMaxValue(row.getDesiredOutput());

							// Determine the predicted class
							int predictedClass = findIndexOfMaxValue(networkOutput);

							// Update the confusion matrix
							confusionMatrix[actualClass][predictedClass]++;
                          }
						  
						  //S printConfusionMatrix(confusionMatrix);
						  
						  }
						  
						  catch (NullPointerException e) {
						      e.printStackTrace();
							  
						  }
						  
						// End of code for confusion Matrix Neuroph

						//FileReader trainreader2 = new FileReader("C:\\Users\\Sepide\\Desktop\\Simulator-S\\Dataset\\424k\\Newfolder\\dataset.csv");
                        //Instances train2 = new Instances(trainreader2);
						// recNeuralNetworkS(mlp2, recMLPS); // added by Sepide
											
						
					/* BufferedReader recommendFile = readDataFile(recommendSetFilePath);  // commented out on May-1 2023 
			        Instances recommendInstances = null;
			
			        try{
						
						recommendInstances = new Instances(recommendFile);
					}
					catch (IOException e)
					{
						e.printStackTrace();
					}  */
					
					// Use filter					
					/* Remove remove = new Remove();
					remove.setAttributeIndices("last");
					FilteredClassifier fc = new FilteredClassifier();
					fc.setFilter(remove);
					FilteredClassifier mlpFiltered = new FilteredClassifier(); */ 
					
					//S NumericToNominal filter = new NumericToNominal();
					//S NominalToBinary filter = new NominalToBinary();
					
					 
					
					/* try {
						filter.setInputFormat(recommendInstances2);
					}
					catch(Exception e){
								e.printStackTrace();            
						}
					try {
						recommendInstances2 = Filter.useFilter(recommendInstances2, filter);
					}
					catch(Exception e){
								e.printStackTrace();            
						} */ 
									               
					
			   /*  recommendInstances.setClassIndex(recommendInstances.numAttributes() - 1);  // commented out on May-1 2023 
			     Instances predictedLabelsRec2 = new Instances(recommendInstances);
			
				double clsLabel = 0.0;
				Map<String,String> userPredictedFollowee2 = new LinkedHashMap<String,String>();
				String predictedClass2;
				
			
				BufferedReader datafile = readDataFile(trainSetFilePath);
				Instances data = null;
					try{
						data = new Instances(datafile);
					}
					catch (IOException e)
					{
						e.printStackTrace();
					}
			
			         for (int i = 0; i < recommendInstances.numInstances(); i++) {
						
						try {
							clsLabel = mlp.classifyInstance(recommendInstances.instance(i));
						}
						catch (Exception e)
						{
							e.printStackTrace();
						} 
						
						predictedLabelsRec2.instance(i).setClassValue(clsLabel);
						
						if (i == recommendInstances.numInstances()-1)
						{
							predictedClass2 = predictedLabelsRec2.instance(i).stringValue(data.numAttributes()-1);
			//				System.out.println(predictedLabels.instance(i).stringValue(data.numAttributes()-2));
							System.out.println(getLocalName()+" Recommended Class: "+predictedLabelsRec2.instance(i).stringValue(data.numAttributes()-1));
							userPredictedFollowee2.put(usersRec.get(i),predictedClass2);
							System.out.println("User to print for Sepide:" + usersRec.get(i));
						}
			              }
			
			            allUserScores = new TreeMap<String,TreeMap<String,Double>>();
			            Map<String,Double> userScore1 = new TreeMap<String,Double>();
			            double followeeScore = 0.0;
			
						for (String recUser : userPredictedFollowee2.keySet())
								{
									for (String followeeUser: followeeFollowers.keySet())
									{
										if (userPredictedFollowee2.get(recUser).equals(followeeUser))
											followeeScore = 1.0;
										else
											followeeScore = 0.0;
										
										userScore1.put(followeeUser,followeeScore);
									}
									allUserScores.put(recUser,(TreeMap<String,Double>)userScore1);
									userScore1 = new TreeMap<String,Double>();
								}  */   // commented out on May-1 2023 
						
						endTimeAlgorithm = System.nanoTime();
						completionTimeAlgorithm = endTimeAlgorithm - startTimeAlgorithm;						
                        myGui.appendResult("Completion time: " + convertMs(completionTimeAlgorithm));
						//Output for MLP
						textprocessing_wb_or_tfidf_Data.add("MLP=TP+TFIDF+MLP" + "\t" + agentName + "\t" + tweetCount + "\t" + completionTimeTextProcessing + "\t" + completionTimeTFIDF    + "\t" + completionTimeAlgorithm + "\t" + System.getProperty("line.separator"));
	//					System.out.println(agentName+"- Total Tweets Processed: " + tweetCount + " TP:" + convertMs(completionTimeTextProcessing) + "ms TFIDF:" + convertMs(completionTimeTFIDF) + "ms K-means:" + convertMs(completionTimeAlgorithm) + "ms Total:" + convertMs(completionTimeTextProcessing+completionTimeTFIDF+completionTimeAlgorithm)+"ms");
						System.out.println("Mapper"+nodeNumber+"- Total Tweets Processed: " + tweetCount + " TP: " + convertMs(completionTimeTextProcessing) + " ms TFIDF: " + convertMs(completionTimeTFIDF) + " ms Reducer"+ nodeNumber+ " MLP: " + convertMs(completionTimeAlgorithm) + " ms MLP Train: "+ convertMs(completionTimeTrain) + " ms MLP Test: "+ convertMs(completionTimeTest) +" ms Total: " + convertMs(completionTimeTextProcessing+completionTimeTFIDF+completionTimeAlgorithm)+" ms");
	//					myGui.appendResult(agentName+"\nTotal Tweets Processed: " + tweetCount + " TP:" + round(completionTimeTextProcessing/1000000.00,2) + "ms TFIDF:" + round(completionTimeTFIDF/1000000.00,2) + "ms K-means:" + round(completionTimeAlgorithm/1000000.00,2) + "ms Total:" + round((completionTimeTextProcessing+completionTimeTFIDF+completionTimeAlgorithm)/1000000.00,2)+"ms");
					    myGui.appendResult("Mapper"+nodeNumber+"- Total Tweets Processed: " + tweetCount + " TP: " + convertMs(completionTimeTextProcessing) + " ms TFIDF: " + convertMs(completionTimeTFIDF) + " ms Reducer"+ nodeNumber+ " MLP: " + convertMs(completionTimeAlgorithm) + " ms MLP Train: "+ convertMs(completionTimeTrain) + " ms MLP Test: "+ convertMs(completionTimeTest) +" ms Total: " + convertMs(completionTimeTextProcessing+completionTimeTFIDF+completionTimeAlgorithm)+" ms");
						//S myGui.appendResult("MLP Accuracy: " + String.format("%.2f%%", accuracy));
						nodeMLP = null;
						dataSet = null;
						testMLP = null;
						recMLP = null;
						releaseActiveMlpLease();
					}
					
					//Stop  timing until averaged weights is returned
					else
					{
						endTimeAlgorithm = System.nanoTime();
						completionTimeAlgorithm = endTimeAlgorithm - startTimeAlgorithm;
					}
					}
					
				  }   

				// try {
					// FileWriter writer = new FileWriter("scores_Not_Normalized.txt", true); //append
					// BufferedWriter bufferedWriter = new BufferedWriter(writer);
					
					// for (String userRec: allUserScores.keySet())
					// {
						// bufferedWriter.write(getLocalName()+" "+ userRec+" Scores: [\t");
						
						// TreeMap<String,Double> otherUserScores = allUserScores.get(userRec);
						
						// for (String otherUser: otherUserScores.keySet())
						// {
							// double oldScore = otherUserScores.get(otherUser);
							
							// bufferedWriter.write(otherUser+":"+oldScore+"\t");				
						// }
						
					// }
					// bufferedWriter.write("]");
					// bufferedWriter.newLine();
					// bufferedWriter.close();
				// } catch (IOException e) {
					// TODO Auto-generated catch block
					// e.printStackTrace();
				// }
                   
                    if (algorithmRec == Doc2Vec && numRecAgents > 1 ) {
						
						System.out.println("test if algorithmRec == Doc2Vec && numRecAgents > 1 ");
						String nnModelName = "Stored_Doc2VecModel/";
						File nnModelDir = new File(nnModelName);
						if (!nnModelDir.exists())
						{
								nnModelDir.mkdirs();
						}
						
						String doc2vecModel = nnModelName + "md"+ nodeNumber +"_d2v.model";
						// there is no library to average the weights for Doc2Vec 
					}				   
				
					  if (algorithmRec == MLP && numRecAgents > 1
							  && sparseFederatedMlpRun)
					{
						return;
					}

					  if (algorithmRec == MLP && numRecAgents > 1
							  && !sparseFederatedMlpRun)
					{
						try
						{
					//System.out.println("test if algorithmRec == MLP && numRecAgents > 1 ");
					
					
					String nnDirName = "Stored_NN/";
					File nnDir = new File(nnDirName);
					if (!nnDir.exists())
					{
							nnDir.mkdirs();
					}
					//System.out.println("test for line 5311");
					// try {
					String nodeMLPFileName = nnDirName+getLocalName()+"_MLP.nnet";
					//S String nodeMLPFileName = nnDirName+getLocalName()+"_MLP.txt";
					//May-1 2023 String nodeMLPFileName = nnDirName+getLocalName()+"_MLP.model";
					//System.out.println("test for line 5316");
					nodeMLP.save(nodeMLPFileName);
					
					// Calculating the accuracy for MLP Neuroph
					
					NeuralNetwork neuralNet = nodeMLP;
						double accuracy = reportLegacyNeurophAccuracy(
								neuralNet,
								testMLP,
								"local model before averaging");
						//S System.out.println("Accuracy: " + accuracy + "%");
						//S myGui.appendResult("MLP Accuracy: " + String.format("%.2f%%", accuracy));
					
					// End of code for calculating the accuracy for MLP Neuroph
                    					
					//s FileReader datareader = new FileReader(dataSetFilePath);
                    // S Instances data = new Instances(datareader);
					//S weka.core.SerializationHelper.write("mlp.model", mlpNode);
					
					/* try {   // commented out on May-1 2023 
						  weka.core.SerializationHelper.write(nodeMLPFileName, mlp);
					}
					
					catch(Exception e) {
						
					}  */
					
					// code added by Sepide to loop through recommendations
                     /*  Set s = userPredictedFollowee2.entrySet();
                      Iterator it = s.ietartor();
                       while (it.hasNext()) {
						   System.out.println("The value of userPredictedFollowee2 in MLP: " + it.next());
					   }					  
					
					// end of code added by Sepide */
												
					ACLMessage toAverageWeightsMsg = new ACLMessage(ACLMessage.INFORM);
					toAverageWeightsMsg.addReceiver( new AID("Organizing Agent1", AID.ISLOCALNAME) );
					toAverageWeightsMsg.setPerformative(ACLMessage.INFORM);
					toAverageWeightsMsg.setConversationId(runLifecycle.getActiveRunId());
					toAverageWeightsMsg.setOntology("Average Weights MLP");
					
					toAverageWeightsMsg.setContent(nodeMLPFileName);
					//S toAverageWeightsMsg.setContent("mlp.model");
					send(toAverageWeightsMsg);
					System.out.println(getLocalName()+" sent toAverageWeightsMsg");
					nodeMLP = null;
					releaseActiveMlpLease();
					
					// try {
						// toAverageWeightsMsg.setContentObject((Serializable) nodeMLP);	
						// send(toAverageWeightsMsg);
						// System.out.println(getLocalName()+" sent toAverageWeightsMsg");
					// } catch (IOException e) {
						// e.printStackTrace();
					// }
					// }
					/* catch (FileNotFoundException ex){
                   } */ 
				   /* catch (IOException e)  {
                   } */ 
				   
				    
					/* catch(Exception e){
                     // return null;            // Always must return something
                         } */ 
					
					}
					finally
					{
						nodeMLP = null;
						releaseActiveMlpLease();
					}
				 }
				  else
				 { 
					//Added in @Jason display text processing wb/tfidf kmean/cosSIM time in ms
					for (String s : textprocessing_wb_or_tfidf_Data){
						System.out.print(s);
					}

					myGui.setTPTime(completionTimeTextProcessing/1000000.00);
					myGui.setTfidfTime(completionTimeTFIDF/1000000.00);
					myGui.setAlgorithmTime(completionTimeAlgorithm/1000000.00);


					// System.out.println(getLocalName()+" tweetCount: "+ tweetCount+"\ttweetreceived: "+tweetsToReceive);


					//OUTPUT TO TIMING, NEED TO EDIT

					String outputFilename = "Results/Timing/" + referenceUser + "/" + "Distributed_Server_TP_TFIDF_Algorithm" + numRecAgents + ".txt"; 
					try {
						saveToFile_array(outputFilename, textprocessing_wb_or_tfidf_Data, "append");
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					textprocessing_wb_or_tfidf_Data.clear();

					ACLMessage msg7 = new ACLMessage( ACLMessage.INFORM );
					msg7.addReceiver( new AID("Starter Agent", AID.ISLOCALNAME) );
					msg7.setPerformative( ACLMessage.INFORM );
					msg7.setContent("Tweeting TFIDF Algorithm Calculation Completed");
					msg7.setConversationId(runLifecycle.getActiveRunId());
					msg7.setOntology("Tweets TFIDF Algorithm Calculation Done");
					send(msg7);

					sendScoresToOrganizer();
				} 
				
				}
				catch (Throwable failure)
				{
					handleAlgorithmFailure(failure);
				}
				finally
				{
					releaseExactSvmPreparationState();
					releaseActiveSvmLease();
					releaseActiveKMeansLease();
				}
			}
			
			// Code added by Sepide 
			
			/* if (msg!=null && msg.getOntology() == "Sepide Testing")  {
				
  
                        try {
						
						BufferedReader readerSep = new BufferedReader(new FileReader("D:/important-stuff/outPutSep.txt"));
						String line = readerSep.readLine();
						//String words[];
						BufferedWriter writerSepFollowee2 = new BufferedWriter(new FileWriter("D:/important-stuff/outPutSepFolloweeResults.txt"));
						writerSepFollowee2.write("test");
						writerSepFollowee2.newLine();
						int count = 0;
							while (line != null) {
								
								if (count != 0) {
									
									String myWord = line.substring(18);
									writerSepFollowee2.write(myWord);
									String followeeSep2 = userFollowee.get(myWord);
									writerSepFollowee2.write(followeeSep2);
									writerSepFollowee2.newLine();
									
								}
								//words = line.split("\n");
								line = readerSep.readLine();								
								count++;
							}
							readerSep.close();
							writerSepFollowee2.close();
							
						}
						
						catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
							}
			
  
			} */ 
	
			// End of code added by Sepide

			  if (msg!=null
					  && "Averaged MLP Failed".equals(msg.getOntology())
					  && runLifecycle.isActive(msg.getConversationId()))
			{
				System.err.println(getLocalName()+" received MLP averaging failure: "
						+msg.getContent());
				handleAlgorithmFailure(new IllegalStateException(
						"MLP averaging failed: " + msg.getContent()));
			}

			  if (msg!=null
					  && "Averaged Sparse MLP Failed".equals(msg.getOntology())
					  && runLifecycle.isActive(msg.getConversationId()))
			{
				System.err.println(getLocalName()
						+" received sparse MLP averaging failure: "
						+msg.getContent());
				handleAlgorithmFailure(new IllegalStateException(
						"Sparse MLP averaging failed: " + msg.getContent()));
			}

			  if (msg!=null
					  && "Averaged Sparse MLP Complete".equals(msg.getOntology())
					  && runLifecycle.isActive(msg.getConversationId()))
			{
				System.out.println(getLocalName()+" received Averaged Sparse MLP Complete");
				try (RecommendationMemoryCoordinator.Lease ignored =
						RecommendationMemoryCoordinator.acquire(
								getLocalName(),
								"Sparse MLP averaged scoring",
								RecommendationMemoryCoordinator.Workload.MLP_SCORE,
								RecommendationMemoryCoordinator.estimateMlpModelBytes(
										new File(msg.getContent()), 5)))
				{
					SparseFederatedMlpModelSupport.SparseMlpModel averagedSparseMlp =
							SparseFederatedMlpModelSupport.SparseMlpModel.load(
									new File(msg.getContent()));
					startTimeAlgorithm = System.nanoTime();
					startTimeTest = System.nanoTime();
					testSparseFederatedMlp(averagedSparseMlp, sparseMlpTestExamples);
					endTimeTest = System.nanoTime();
					completionTimeTest = endTimeTest - startTimeTest;
					recSparseFederatedMlp(averagedSparseMlp, sparseMlpRecExamples);
					endTimeAlgorithm = System.nanoTime();
					completionTimeAlgorithm += (endTimeAlgorithm - startTimeAlgorithm);
					finishSparseMlpRecommendationOutput();
				}
				catch (Throwable failure)
				{
					System.err.println(getLocalName()
							+" failed while scoring with the averaged sparse MLP.");
					handleAlgorithmFailure(failure);
					return;
				}
			}


			  if (msg!=null
					  && "Averaged MLP Complete".equals(msg.getOntology())
					  && runLifecycle.isActive(msg.getConversationId()))
			{
				
				//May-1 2023 MultilayerPerceptron averagedMLP = new MultilayerPerceptron(); 
				System.out.println(getLocalName()+" received Averaged MLP Complete");
				
				// averagedMLP = null;
				// averagedNN = null;
				
				// try
				// {
					// averagedMLP = (MultiLayerPerceptron) msg.getContentObject();	
				// }
				// catch (UnreadableException e)
				// {
					// e.printStackTrace();
				// }
				
				try (RecommendationMemoryCoordinator.Lease ignored =
						RecommendationMemoryCoordinator.acquire(
								getLocalName(),
								"MLP averaged scoring",
								RecommendationMemoryCoordinator.Workload.MLP_SCORE,
								RecommendationMemoryCoordinator.estimateMlpModelBytes(
										new File(msg.getContent()), 5)))
				{
				// System.out.println(getLocalName()+" path to averagedNN : "+msg.getContent());
				averagedMLP = (MultiLayerPerceptron) NeuralNetwork.createFromFile(msg.getContent());
				
				// Code added by Sepide
				
				
			/*May-1 2023	try {    
				        averagedMLP = (MultilayerPerceptron) SerializationHelper.read(msg.getContent());
			           //S averagedMLP = SerializationHelper.read(msg.getContent());
				
				    }
				
				catch (Exception e) {
                        e.printStackTrace();
                       } */
					   
				// End of code added by S	   
				
				startTimeAlgorithm = System.nanoTime();
				
				startTimeTest = System.nanoTime();
				testNeuralNetwork(averagedMLP,testMLP);
				reportLegacyNeurophAccuracy(
						averagedMLP,
						testMLP,
						"averaged federated model");
				//System.out.println("test message by Sepide1");
				// testNeuralNetwork(averagedNN,testMLP);
				endTimeTest = System.nanoTime();
				completionTimeTest = endTimeTest - startTimeTest;
				recNeuralNetwork(averagedMLP,recMLP);
				averagedMLP = null;
				}
					catch (Throwable failure)
					{
						System.err.println(getLocalName()
								+" failed while scoring with the averaged MLP.");
						handleAlgorithmFailure(failure);
						return;
					}
				// recNeuralNetwork(averagedNN,recMLP);
				
				
				/* averagedMLP.setLearningRate(LEARNING_RATE_MLP);
				averagedMLP.setMomentum(0.2);
				averagedMLP.setHiddenLayers("10"); */
				
				
				/* String arffDirName = "Dataset/Arff_files/";
			    File arffDir = new File(arffDirName);
					 if (!arffDir.exists())
					{
							arffDir.mkdirs();
					} 
				
				   String recommendSetFilePath = "";
				   recommendSetFilePath = arffDirName + "recommend_set_rec"+nodeNumber+".txt";
				
				try {
					
					FileReader datareader = new FileReader(recommendSetFilePath);
					Instances data3 = new Instances(datareader);
					data3.setClassIndex((data3.numAttributes())-1);
					averagedMLP.buildClassifier(data3);
				}	
				catch (FileNotFoundException ex){
                           ex.printStackTrace();
                       }				     
				
				catch (IOException e) {
                        e.printStackTrace();
                       }
					   				
			         catch (Exception ee){
                          ee.printStackTrace();    
                    }
					
				
				BufferedReader recommendFile = readDataFile(recommendSetFilePath);
			    Instances recommendInstances = null;
					
					try{
						
						recommendInstances = new Instances(recommendFile);
					}
					catch (IOException e)
					{
						e.printStackTrace();
					}
					
				 recommendInstances.setClassIndex(recommendInstances.numAttributes() - 1);
			     Instances predictedLabelsRec = new Instances(recommendInstances);
			
				double clsLabel = 0.0;
				Map<String,String> userPredictedFollowee = new LinkedHashMap<String,String>();
				String predictedClass;
				
				String trainSetFilePath = "";
				trainSetFilePath = arffDirName + "train_set_rec"+nodeNumber+".txt";
				BufferedReader datafile = readDataFile(trainSetFilePath);
				
				Instances data = null;
					try{
						data = new Instances(datafile);
					}
					catch (IOException e)
					{
						e.printStackTrace();
					}
			
			         for (int i = 0; i < recommendInstances.numInstances(); i++) {
						
						try {
							
							clsLabel = averagedMLP.classifyInstance(recommendInstances.instance(i));
						}
						catch (Exception e)
						{
							e.printStackTrace();
						} 
						
						predictedLabelsRec.instance(i).setClassValue(clsLabel);
						
						if (i == recommendInstances.numInstances()-1)
						{
							predictedClass = predictedLabelsRec.instance(i).stringValue(data.numAttributes()-1);
			//				System.out.println(predictedLabels.instance(i).stringValue(data.numAttributes()-2));
							System.out.println(getLocalName()+" Recommended Class: "+predictedLabelsRec.instance(i).stringValue(data.numAttributes()-1));
							userPredictedFollowee.put(usersRec.get(i),predictedClass);
						}
			              }
			
			            allUserScores = new TreeMap<String,TreeMap<String,Double>>();
			            Map<String,Double> userScore1 = new TreeMap<String,Double>();
			            double followeeScore = 0.0;
			
						for (String recUser : userPredictedFollowee.keySet())
								{
									for (String followeeUser: followeeFollowers.keySet())
									{
										if (userPredictedFollowee.get(recUser).equals(followeeUser))
											followeeScore = 1.0;
										else
											followeeScore = 0.0;
										
										userScore1.put(followeeUser,followeeScore);
									}
									allUserScores.put(recUser,(TreeMap<String,Double>)userScore1);
									userScore1 = new TreeMap<String,Double>();
								}  */   // commented out on May-1 2023 
				
				endTimeAlgorithm = System.nanoTime();
				completionTimeAlgorithm += (endTimeAlgorithm - startTimeAlgorithm);						

				//Output for MLP
				textprocessing_wb_or_tfidf_Data.add("MLP=TP+TFIDF+MLP" + "\t" + agentName + "\t" + tweetCount + "\t" + completionTimeTextProcessing + "\t" + completionTimeTFIDF    + "\t" + completionTimeAlgorithm + "\t" + System.getProperty("line.separator"));
//					System.out.println(agentName+"- Total Tweets Processed: " + tweetCount + " TP:" + convertMs(completionTimeTextProcessing) + "ms TFIDF:" + convertMs(completionTimeTFIDF) + "ms K-means:" + convertMs(completionTimeAlgorithm) + "ms Total:" + convertMs(completionTimeTextProcessing+completionTimeTFIDF+completionTimeAlgorithm)+"ms");
				System.out.println("Mapper"+nodeNumber+"- Total Tweets Processed: " + tweetCount + " TP: " + convertMs(completionTimeTextProcessing) + " ms TFIDF: " + convertMs(completionTimeTFIDF) + " ms Reducer"+ nodeNumber+ " MLP: " + convertMs(completionTimeAlgorithm) + " ms MLP Train: "+ convertMs(completionTimeTrain) + " ms MLP Test: "+ convertMs(completionTimeTest) +" ms Total: " + convertMs(completionTimeTextProcessing+completionTimeTFIDF+completionTimeAlgorithm)+" ms");
//					myGui.appendResult(agentName+"\nTotal Tweets Processed: " + tweetCount + " TP:" + round(completionTimeTextProcessing/1000000.00,2) + "ms TFIDF:" + round(completionTimeTFIDF/1000000.00,2) + "ms K-means:" + round(completionTimeAlgorithm/1000000.00,2) + "ms Total:" + round((completionTimeTextProcessing+completionTimeTFIDF+completionTimeAlgorithm)/1000000.00,2)+"ms");
				myGui.appendResult("Mapper"+nodeNumber+"- Total Tweets Processed: " + tweetCount + " TP: " + convertMs(completionTimeTextProcessing) + " ms TFIDF: " + convertMs(completionTimeTFIDF) + " ms Reducer"+ nodeNumber+ " MLP: " + convertMs(completionTimeAlgorithm) + " ms MLP Train: "+ convertMs(completionTimeTrain) + " ms MLP Test: "+ convertMs(completionTimeTest) +" ms Total: " + convertMs(completionTimeTextProcessing+completionTimeTFIDF+completionTimeAlgorithm)+" ms");
				//Sep30-2023 myGui.appendResult("Mapper"+nodeNumber+"- Total Tweets Processed: " + tweetCount + " TP: " + convertMs(completionTimeTextProcessing) + " ms TFIDF: " + convertMs(completionTimeTFIDF) + " ms Reducer"+ nodeNumber+ " MLP: " + convertMs(completionTimeAlgorithm) +" ms Total: " + convertMs(completionTimeTextProcessing+completionTimeTFIDF+completionTimeAlgorithm)+" ms");  //Added by Sepide
                System.out.println("test for sepide to see the avaraged MLP");
				
				for (String s : textprocessing_wb_or_tfidf_Data){
						System.out.print(s);
				}

				myGui.setTPTime(completionTimeTextProcessing/1000000.00);
				myGui.setTfidfTime(completionTimeTFIDF/1000000.00);
				myGui.setAlgorithmTime(completionTimeAlgorithm/1000000.00);
                System.out.println("test message by Sepide2");

				// System.out.println(getLocalName()+" tweetCount: "+ tweetCount+"\ttweetreceived: "+tweetsToReceive);


				//OUTPUT TO TIMING, NEED TO EDIT

				String outputFilename = "Results/Timing/" + referenceUser + "/" + "Distributed_Server_TP_TFIDF_Algorithm" + numRecAgents + ".txt"; 
				try {
					saveToFile_array(outputFilename, textprocessing_wb_or_tfidf_Data, "append");
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				textprocessing_wb_or_tfidf_Data.clear();

				ACLMessage msg7 = new ACLMessage( ACLMessage.INFORM );
				msg7.addReceiver( new AID("Starter Agent", AID.ISLOCALNAME) );
				msg7.setPerformative( ACLMessage.INFORM );
				msg7.setContent("Tweeting TFIDF Algorithm Calculation Completed");
				msg7.setConversationId(runLifecycle.getActiveRunId());
				msg7.setOntology("Tweets TFIDF Algorithm Calculation Done");
				send(msg7);

				sendScoresToOrganizer();
			}

		}

		private void sendScoresToOrganizer()
		{
			if (RecommendationRunAbortRegistry.isAborted(
					runLifecycle.getActiveRunId()))
			{
				System.out.println(getLocalName()
						+" skipped score publication because this run was aborted.");
				return;
			}
			if (!waitForLivePhaseBoundary("MODEL_STAGE -> PUBLISH_MERGE"))
			{
				return;
			}
			NodeExecutionMonitor.publishing(getLocalName());
			ACLMessage toMergeMsg = new ACLMessage(ACLMessage.INFORM);
			toMergeMsg.addReceiver(new AID("Organizing Agent1", AID.ISLOCALNAME));
			toMergeMsg.setPerformative(ACLMessage.INFORM);
			toMergeMsg.setConversationId(runLifecycle.getActiveRunId());

			Serializable content;
			int scoreTargetsForTelemetry;
			int scoreCandidatesForTelemetry;
			if (isUserRankerFolloweeMode())
			{
				Map<String,TreeMap<String,String>> projectionDetails =
						new TreeMap<String,TreeMap<String,String>>();
				Map<String,TreeMap<String,Double>> projectedScores =
						buildFolloweeProjectionScores(projectionDetails);
				Map<String,String> errors = buildMissingScoreErrors(
						projectedScores,
						"No mapped followee accounts received a positive score on this node.",
						isSharedScalableNonPublisher());
				content = RecommendationScorePayload.directWithDetails(
						recommendationConfig.getTargetMode(),
						RecommendationCandidateType.FOLLOWEE,
						RecommendationMergePolicy.MAX,
						usersRec,
						projectedScores,
						projectionDetails,
						errors);
				toMergeMsg.setOntology("Merge Recommendation Scores V2");
				scoreTargetsForTelemetry = projectedScores.size();
				scoreCandidatesForTelemetry =
						countScoreCandidates(projectedScores);
			}
			else if (isClassifierGuidedUserMode())
			{
				Map<String,TreeMap<String,RecommendationCandidateEvidence>> evidence =
						buildClassifierCandidateEvidence();
				Map<String,String> errors = buildMissingScoreErrors(
						allUserScores,
						"The classifier did not produce a usable followee distribution on this node.",
						isSharedScalableNonPublisher());
				content = RecommendationScorePayload.classifierGuidedUsers(
						recommendationConfig.getTargetMode(),
						usersRec,
						allUserScores,
						evidence,
						RecommendationModeSupport.algorithmName(algorithmRec)
								+ "-guided users",
						errors);
				toMergeMsg.setOntology("Merge Recommendation Scores V2");
				scoreTargetsForTelemetry = allUserScores.size();
				scoreCandidatesForTelemetry =
						countScoreCandidates(allUserScores);
			}
			else if (algorithmRec == TEMPORAL_GRAPH_FOUNDATION)
			{
				Map<String,String> errors = buildMissingScoreErrors(
						allUserScores,
						"Temporal Graph Foundation found no positive text, graph, or temporal candidates on this node.",
						false);
				content = RecommendationScorePayload.directWithDetails(
						recommendationConfig.getTargetMode(),
						RecommendationCandidateType.USER,
						RecommendationMergePolicy.MAX,
						usersRec,
						allUserScores,
						temporalGraphCandidateDetails,
						errors);
				toMergeMsg.setOntology("Merge Recommendation Scores V2");
				scoreTargetsForTelemetry = allUserScores.size();
				scoreCandidatesForTelemetry =
						countScoreCandidates(allUserScores);
			}
			else
			{
				content = (Serializable) allUserScores;
				toMergeMsg.setOntology("Merge Lists");
				scoreTargetsForTelemetry = allUserScores.size();
				scoreCandidatesForTelemetry =
						countScoreCandidates(allUserScores);
			}

			try
			{
				NodeExecutionMonitor.recordAlgorithmTiming(
						getLocalName(),
						completionTimeAlgorithm,
						completionTimeTrain,
						completionTimeTest);
				NodeExecutionMonitor.recordPublishStats(
						getLocalName(),
						scoreTargetsForTelemetry,
						scoreCandidatesForTelemetry,
						toMergeMsg.getOntology());
				toMergeMsg.setContentObject(content);
				send(toMergeMsg);
				NodeExecutionMonitor.complete(getLocalName());
			}
			catch (IOException e)
			{
				e.printStackTrace();
				NodeExecutionMonitor.failed(getLocalName(), e);
			}
		}

		private int countScoreCandidates(
				Map<String,? extends Map<String,Double>> scores)
		{
			if (scores == null)
			{
				return 0;
			}
			int count = 0;
			for (Map<String,Double> targetScores : scores.values())
			{
				if (targetScores != null)
				{
					count += targetScores.size();
				}
			}
			return count;
		}

		private Map<String,TreeMap<String,Double>> buildFolloweeProjectionScores(
				Map<String,TreeMap<String,String>> projectionDetails)
		{
			Map<String,TreeMap<String,Double>> projected =
					new TreeMap<String,TreeMap<String,Double>>();
			for (String target : usersRec)
			{
				TreeMap<String,Double> followeeScores = new TreeMap<String,Double>();
				TreeMap<String,String> followeeDetails = new TreeMap<String,String>();
				Map<String,Double> candidateScores = allUserScores.get(target);
				if (candidateScores != null)
				{
					for (Map.Entry<String,Double> entry : candidateScores.entrySet())
					{
						String followee = getCandidateFollowee(entry.getKey());
						Double score = entry.getValue();
						if (followee == null || score == null
								|| !Double.isFinite(score) || score <= 0.0)
						{
							continue;
						}
						Double oldScore = followeeScores.get(followee);
						if (oldScore == null || score > oldScore)
						{
							followeeScores.put(followee, score);
							followeeDetails.put(
									followee, "Best-matching user: " + entry.getKey());
						}
					}
				}
				projected.put(target, followeeScores);
				projectionDetails.put(target, followeeDetails);
			}
			return projected;
		}

		private Map<String,TreeMap<String,RecommendationCandidateEvidence>>
				buildClassifierCandidateEvidence()
		{
			if (!precomputedClassifierEvidence.isEmpty())
			{
				return precomputedClassifierEvidence;
			}
			Map<String,TreeMap<String,RecommendationCandidateEvidence>> evidenceByTarget =
					new TreeMap<String,TreeMap<String,RecommendationCandidateEvidence>>();
			int currentNode = Integer.parseInt(nodeNumber);
			for (String target : usersRec)
			{
				TreeMap<String,RecommendationCandidateEvidence> targetEvidence =
						new TreeMap<String,RecommendationCandidateEvidence>();
				Map<String,LinkedHashMap<String,Double>> evidenceDocuments =
						allUserDocumentsTFIDF;
				LinkedHashMap<String,Double> targetDocument = evidenceDocuments.get(target);
				if (targetDocument != null)
				{
					for (Map.Entry<String,LinkedHashMap<String,Double>> candidateEntry
							: evidenceDocuments.entrySet())
					{
						String candidate = candidateEntry.getKey();
						if (target.equals(candidate))
						{
							continue;
						}
						Integer primaryServer = userPrimaryRecServer.get(candidate);
						if (primaryServer != null && primaryServer.intValue() != currentNode)
						{
							continue;
						}
						String followee = getCandidateFollowee(candidate);
						if (followee == null)
						{
							continue;
						}
						double similarity = calculateSparseCosineScore(
								targetDocument, candidateEntry.getValue());
						if (Double.isFinite(similarity) && similarity > 0.0)
						{
							targetEvidence.put(candidate,
									new RecommendationCandidateEvidence(followee, similarity));
						}
					}
				}
				evidenceByTarget.put(target, targetEvidence);
			}
			return evidenceByTarget;
		}

		private String getCandidateFollowee(String candidate)
		{
			String followee = datasetUserFollowee.get(candidate);
			if (followee == null)
			{
				followee = userFollowee.get(candidate);
			}
			return followee == null || followee.length() == 0 ? null : followee;
		}

		private Map<String,String> buildMissingScoreErrors(
				Map<String,? extends Map<?,Double>> scores,
				String message,
				boolean suppressErrors)
		{
			Map<String,String> errors = new TreeMap<String,String>();
			if (suppressErrors)
			{
				return errors;
			}
			for (String target : usersRec)
			{
				Map<?,Double> targetScores = scores == null ? null : scores.get(target);
				boolean hasPositive = false;
				if (targetScores != null)
				{
					for (Double score : targetScores.values())
					{
						if (score != null && Double.isFinite(score) && score > 0.0)
						{
							hasPositive = true;
							break;
						}
					}
				}
				if (!hasPositive)
				{
					errors.put(target, message);
				}
			}
			return errors;
		}

		private boolean isSharedScalablePublisher()
		{
			return algorithmRec == SCALABLE_SVM
					&& Integer.parseInt(nodeNumber) == 1;
		}

		private boolean isSharedScalableNonPublisher()
		{
			return algorithmRec == SCALABLE_SVM
					&& !isSharedScalablePublisher();
		}

		protected void saveToFile_array(String filename, ArrayList<String> result, String append) throws IOException
		{
			BufferedWriter writer = new BufferedWriter(new FileWriter(filename, true));
			if(append=="not_append")
			{
				writer = new BufferedWriter(new FileWriter(filename, false));
			}
			for(int i=0; i<result.size(); i++)
			{
				writer.write(result.get(i));
				writer.flush();
			}
			writer.close();
			return;
		}

	    // Method added by Sepide
		
		private boolean isCorrectPrediction(double[] networkOutput, double[] expectedOutput) {
		// Round the network's output to the nearest integer (0 or 1)
		int predicted = (int) Math.round(networkOutput[0]);

		// Check if the predicted value matches the expected value
		if(predicted == expectedOutput[0]) {
			return true;
				} else {
					return false;
				}
			}
		
		// End of method added by Sepide 
		
		// Methods added by Sepide
		private int findIndexOfMaxValue(double[] array) {
        int maxIndex = 0;
        double maxValue = array[0];
        for (int i = 1; i < array.length; i++) {
            if (array[i] > maxValue) {
                maxIndex = i;
                maxValue = array[i];
            }
        }
        return maxIndex;
    }
	
	// Method added by Sepide
	    private void printConfusionMatrix(int[][] matrix) {
        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix[i].length; j++) {
                System.out.print(matrix[i][j] + "\t");
            }
            System.out.println();
        }
    }
	
	// End of methods added by Sepide 
		
		private void determineCentralTrainingTestSet()
		{
			testSetUsers = new ArrayList<String>(); //list of users in test set
			trainSetUsers = new ArrayList<String>(); //list of users in training set
			for (String userInstance : allUserDocumentsTFIDF.keySet())
			{
				if (centralTrainSetUsers.contains(userInstance))
					trainSetUsers.add(userInstance);
				else if (centralTestSetUsers.contains(userInstance))
					testSetUsers.add(userInstance);
			}
			Collections.sort(trainSetUsers);
			Collections.sort(testSetUsers);
			dataSetUsers = new ArrayList<String>(trainSetUsers);
			dataSetUsers.addAll(testSetUsers);
		}

		private void determineTrainingTestSet()
		{
			int numUsers = allUserDocuments.keySet().size();
			System.out.println(getLocalName()+" classifier input users: "+numUsers);
			SvmDataSplit split = determineSvmDataSplit(followeeFollowers);
			trainSetUsers = split.trainUsers;
			testSetUsers = split.testUsers;
			String splitLabel = RecommendationModeSupport.isSvmFamilyAlgorithm(algorithmRec)
					? RecommendationModeSupport.algorithmName(algorithmRec) + " split"
					: "Classifier split";
			logSvmSplitSummary(splitLabel, trainSetUsers, testSetUsers);
		}

		private MultiLayerPerceptron createLegacyNeurophMlp(
				int inputCount,
				int hiddenNeurons,
				int hiddenLayers,
				int outputCount)
		{
			if (hiddenLayers <= 1)
			{
				return new MultiLayerPerceptron(
						TransferFunctionType.TANH,
						inputCount,
						hiddenNeurons,
						outputCount);
			}
			int[] layers = new int[hiddenLayers + 2];
			layers[0] = inputCount;
			for (int index = 1; index <= hiddenLayers; index++)
			{
				layers[index] = hiddenNeurons;
			}
			layers[layers.length - 1] = outputCount;
			return new MultiLayerPerceptron(TransferFunctionType.TANH, layers);
		}

		private void validateLegacyNeurophMlpConfiguration(
				int featureCount,
				int hiddenNeurons,
				int hiddenLayers,
				int outputCount,
				int trainRows,
				int testRows,
				int recRows)
		{
			if (featureCount <= 0)
			{
				throw new IllegalStateException(
						"Legacy Neuroph MLP has no TF-IDF features after preprocessing.");
			}
			if (hiddenNeurons <= 0)
			{
				throw new IllegalStateException(
						"Legacy Neuroph MLP hidden-neuron count must be positive.");
			}
			if (hiddenLayers <= 0)
			{
				throw new IllegalStateException(
						"Legacy Neuroph MLP hidden-layer count must be positive.");
			}
			if (outputCount <= 0)
			{
				throw new IllegalStateException(
						"Legacy Neuroph MLP has no followee/output classes.");
			}
			if (trainRows <= 0)
			{
				throw new IllegalStateException(
						"Legacy Neuroph MLP has no training rows after the central split.");
			}
			if (recRows <= 0)
			{
				throw new IllegalStateException(
						"Legacy Neuroph MLP has no selected recommendation targets.");
			}
			if (testRows <= 0)
			{
				String message = getLocalName()
						+ " Legacy Neuroph MLP has no local test rows; "
						+ "training and recommendation can continue, but accuracy "
						+ "reporting will be skipped for this node.";
				System.out.println(message);
				if (myGui != null)
				{
					myGui.appendResult(message);
				}
			}
		}

		private void reportLegacyNeurophMlpConfiguration(
				int featureCount,
				int hiddenNeurons,
				int hiddenLayers,
				int outputCount,
				int trainRows,
				int testRows,
				int recRows,
				long reservationBytes)
		{
			String detail = "Legacy Neuroph dense MLP: features="
					+ featureCount
					+ ", hiddenLayers=" + hiddenLayers
					+ ", hiddenNeurons=" + hiddenNeurons
					+ ", outputs=" + outputCount
					+ ", rows train/test/rec="
					+ trainRows + "/" + testRows + "/" + recRows
					+ ", memory lease=" + formatMlpMemoryEstimate(reservationBytes);
			String message = getLocalName() + " " + detail;
			System.out.println(message);
			if (myGui != null)
			{
				myGui.appendResult(message);
			}
			NodeExecutionMonitor.recordModelShape(
					getLocalName(),
					"Legacy Neuroph MLP",
					featureCount,
					trainRows + testRows + recRows,
					outputCount,
					recRows,
					detail);
		}

		private String formatMlpMemoryEstimate(long bytes)
		{
			long megabytes = Math.max(
					1L,
					(bytes + RecommendationMemoryCoordinator.MEBIBYTE - 1L)
					/ RecommendationMemoryCoordinator.MEBIBYTE);
			if (megabytes >= 1024L)
			{
				return megabytes + " MB ("
						+ String.format("%.1f", megabytes / 1024.0) + " GB)";
			}
			return megabytes + " MB";
		}

		private double reportLegacyNeurophAccuracy(
				NeuralNetwork neuralNet,
				DataSet testSet,
				String context)
		{
			if (neuralNet == null || testSet == null || testSet.size() <= 0)
			{
				String message = getLocalName()
						+ " Legacy Neuroph MLP accuracy skipped for "
						+ context + ": no test rows.";
				System.out.println(message);
				if (myGui != null)
				{
					myGui.appendResult(message);
				}
				return Double.NaN;
			}

			int correctPredictions = 0;
			int usableRows = 0;
			for (DataSetRow row : testSet.getRows())
			{
				if (row == null
						|| row.getInput() == null
						|| row.getDesiredOutput() == null
						|| row.getDesiredOutput().length == 0)
				{
					continue;
				}
				neuralNet.setInput(row.getInput());
				neuralNet.calculate();
				double[] networkOutput = neuralNet.getOutput();
				if (networkOutput == null || networkOutput.length == 0)
				{
					continue;
				}
				usableRows++;
				if (findIndexOfMaxValue(networkOutput)
						== findIndexOfMaxValue(row.getDesiredOutput()))
				{
					correctPredictions++;
				}
			}

			if (usableRows <= 0)
			{
				String message = getLocalName()
						+ " Legacy Neuroph MLP accuracy skipped for "
						+ context + ": no usable test rows.";
				System.out.println(message);
				if (myGui != null)
				{
					myGui.appendResult(message);
				}
				return Double.NaN;
			}

			double accuracy = (double)correctPredictions / usableRows * 100.0;
			String message = getLocalName()
					+ " Legacy Neuroph MLP accuracy for " + context + ": "
					+ String.format("%.2f%%", accuracy)
					+ " (" + correctPredictions + "/" + usableRows + ")";
			System.out.println(message);
			if (myGui != null)
			{
				myGui.appendResult(message);
			}
			return accuracy;
		}

		private long countSparseFeatureEntries(Collection<String> users)
		{
			if (users == null)
			{
				return 0L;
			}
			long count = 0L;
			for (String user : users)
			{
				Map<String,Double> document = allUserDocumentsTFIDF.get(user);
				if (document != null)
				{
					count += document.size();
				}
			}
			return count;
		}

		private Map<String,Integer> buildSparseMlpTermIndex(
				TreeSet<String> uniqueDocTerms)
		{
			Map<String,Integer> termIndex = new LinkedHashMap<String,Integer>();
			int index = 0;
			for (String term : uniqueDocTerms)
			{
				termIndex.put(term, index);
				index++;
			}
			return termIndex;
		}

		private List<SparseFederatedMlpModelSupport.TrainingExample>
				createSparseMlpExamples(
						Collection<String> users,
						Map<String,Integer> termIndex,
						boolean requireLabel)
		{
			List<SparseFederatedMlpModelSupport.TrainingExample> examples =
					new ArrayList<SparseFederatedMlpModelSupport.TrainingExample>();
			if (users == null)
			{
				return examples;
			}
			for (String user : users)
			{
				Integer labelIndex = null;
				if (requireLabel)
				{
					labelIndex = followeeIndex.get(userFollowee.get(user));
					if (labelIndex == null)
					{
						continue;
					}
				}
				Map<Integer,Double> sparseFeatures =
						new LinkedHashMap<Integer,Double>();
				Map<String,Double> document = allUserDocumentsTFIDF.get(user);
				if (document != null)
				{
					for (Map.Entry<String,Double> entry : document.entrySet())
					{
						Integer featureIndex = termIndex.get(entry.getKey());
						Double value = entry.getValue();
						if (featureIndex != null
								&& value != null
								&& Double.isFinite(value.doubleValue())
								&& value.doubleValue() != 0.0)
						{
							sparseFeatures.put(featureIndex, value);
						}
					}
				}
				examples.add(new SparseFederatedMlpModelSupport.TrainingExample(
						user,
						sparseFeatures,
						requireLabel ? labelIndex.intValue() : -1));
			}
			return examples;
		}

		private void runSparseFederatedMlp(
				AlgorithmParameterSettings settings,
				int featureCount,
				int outputCount)
				throws IOException
		{
			sparseFederatedMlpRun = true;
			Map<String,Integer> termIndex =
					buildSparseMlpTermIndex(allUniqueDocTerms);
			List<SparseFederatedMlpModelSupport.TrainingExample> trainExamples =
					createSparseMlpExamples(trainSetUsers, termIndex, true);
			sparseMlpTestExamples =
					createSparseMlpExamples(testSetUsers, termIndex, true);
			sparseMlpRecExamples =
					createSparseMlpExamples(usersRec, termIndex, false);
			if (trainExamples.isEmpty())
			{
				throw new IllegalStateException(
						"Sparse MLP has no labeled training examples on "
						+ getLocalName() + ".");
			}
			if (sparseMlpRecExamples.isEmpty())
			{
				throw new IllegalStateException(
						"Sparse MLP has no recommendation target examples on "
						+ getLocalName() + ".");
			}

				SparseFederatedMlpModelSupport.SparseMlpModel model =
						SparseFederatedMlpModelSupport.SparseMlpModel.create(
								featureCount,
								settings.getMlpHiddenLayers(),
								settings.getMlpHiddenNeurons(),
								outputCount,
								0x5A17EEDL);
			System.out.println(getLocalName()
					+" training Sparse Federated MLP examples="
					+trainExamples.size()+" features="+featureCount
					+" hiddenLayers="+settings.getMlpHiddenLayers()
					+" hiddenNeurons="+settings.getMlpHiddenNeurons());
			startTimeTrain = System.nanoTime();
			model.train(
					trainExamples,
						settings.getMlpSparseEpochs(),
						settings.getMlpLearningRate(),
						settings.getMlpSparseL2(),
						settings.getMlpFedProxMu());
			endTimeTrain = System.nanoTime();
			completionTimeTrain = endTimeTrain - startTimeTrain;
			endTimeAlgorithm = System.nanoTime();
			completionTimeAlgorithm = endTimeAlgorithm - startTimeAlgorithm;
			if (numRecAgents < 2)
			{
				startTimeTest = System.nanoTime();
				testSparseFederatedMlp(model, sparseMlpTestExamples);
				endTimeTest = System.nanoTime();
				completionTimeTest = endTimeTest - startTimeTest;
				recSparseFederatedMlp(model, sparseMlpRecExamples);
				recordSparseMlpTimingLine();
				releaseActiveMlpLease();
			}
			else
			{
				sendSparseMlpModelForAveraging(model);
			}
		}

		private void sendSparseMlpModelForAveraging(
				SparseFederatedMlpModelSupport.SparseMlpModel model)
				throws IOException
		{
			String nnDirName = "Stored_NN/";
			File nnDir = new File(nnDirName);
			if (!nnDir.exists())
			{
				nnDir.mkdirs();
			}
			String nodeMlpFileName =
					nnDirName + getLocalName() + "_SparseFedMLP.ser";
			model.save(new File(nodeMlpFileName));
			ACLMessage toAverageWeightsMsg = new ACLMessage(ACLMessage.INFORM);
			toAverageWeightsMsg.addReceiver(
					new AID("Organizing Agent1", AID.ISLOCALNAME));
			toAverageWeightsMsg.setPerformative(ACLMessage.INFORM);
			toAverageWeightsMsg.setConversationId(runLifecycle.getActiveRunId());
			toAverageWeightsMsg.setOntology("Average Sparse MLP");
			toAverageWeightsMsg.setContent(nodeMlpFileName);
			send(toAverageWeightsMsg);
			System.out.println(getLocalName()+" sent Sparse MLP model for averaging");
			releaseActiveMlpLease();
		}

		private void testSparseFederatedMlp(
				SparseFederatedMlpModelSupport.SparseMlpModel model,
				List<SparseFederatedMlpModelSupport.TrainingExample> examples)
		{
			if (examples == null || examples.isEmpty())
			{
				String message = getLocalName()
						+" Sparse Federated MLP has no test examples.";
				System.out.println(message);
				if (myGui != null)
				{
					myGui.appendResult(message);
				}
				return;
			}
			int correct = 0;
			for (SparseFederatedMlpModelSupport.TrainingExample example : examples)
			{
				double[] output = model.predict(example.getFeatures());
				if (findIndexOfMaxValue(output) == example.getLabelIndex())
				{
					correct++;
				}
			}
			double accuracy = (double)correct / examples.size() * 100.0;
			String message = getLocalName()
					+" Sparse Federated MLP accuracy: "
					+String.format("%.2f%%", accuracy)
					+" ("+correct+"/"+examples.size()+")";
			System.out.println(message);
			if (myGui != null)
			{
				myGui.appendResult(message);
			}
		}

		private void recSparseFederatedMlp(
				SparseFederatedMlpModelSupport.SparseMlpModel model,
				List<SparseFederatedMlpModelSupport.TrainingExample> examples)
		{
			allUserScores = new TreeMap<String,TreeMap<String,Double>>();
			if (examples == null)
			{
				return;
				}
				for (SparseFederatedMlpModelSupport.TrainingExample example : examples)
				{
					double[] output = model.predict(example.getFeatures());
					TreeMap<String,Double> userScores = new TreeMap<String,Double>();
					for (int index = 0; index < output.length; index++)
					{
						double score = output[index] < 0.0 ? 0.0 : output[index] * 100.0;
						userScores.put(followeeNames[index], score);
					}
					allUserScores.put(example.getUser(), userScores);
				}
			}

		private void recordSparseMlpTimingLine()
		{
			myGui.appendResult("Completion time: "
					+ convertMs(completionTimeAlgorithm));
			textprocessing_wb_or_tfidf_Data.add(
					"MLP=TP+TFIDF+SparseFedMLP" + "\t"
					+ agentName + "\t"
					+ tweetCount + "\t"
					+ completionTimeTextProcessing + "\t"
					+ completionTimeTFIDF + "\t"
					+ completionTimeAlgorithm + "\t"
					+ System.getProperty("line.separator"));
			System.out.println("Mapper"+nodeNumber
					+"- Total Tweets Processed: " + tweetCount
					+" TP: " + convertMs(completionTimeTextProcessing)
					+" ms TFIDF: " + convertMs(completionTimeTFIDF)
					+" ms Reducer"+ nodeNumber
					+" Sparse Federated MLP: "
					+ convertMs(completionTimeAlgorithm)
					+" ms MLP Train: "
					+ convertMs(completionTimeTrain)
					+" ms MLP Test: "
					+ convertMs(completionTimeTest)
					+" ms Total: "
					+ convertMs(completionTimeTextProcessing
							+ completionTimeTFIDF
							+ completionTimeAlgorithm)
					+" ms");
			myGui.appendResult("Mapper"+nodeNumber
					+"- Total Tweets Processed: " + tweetCount
					+" TP: " + convertMs(completionTimeTextProcessing)
					+" ms TFIDF: " + convertMs(completionTimeTFIDF)
					+" ms Reducer"+ nodeNumber
					+" Sparse Federated MLP: "
					+ convertMs(completionTimeAlgorithm)
					+" ms MLP Train: "
					+ convertMs(completionTimeTrain)
					+" ms MLP Test: "
					+ convertMs(completionTimeTest)
					+" ms Total: "
					+ convertMs(completionTimeTextProcessing
							+ completionTimeTFIDF
							+ completionTimeAlgorithm)
					+" ms");
		}

		private void finishSparseMlpRecommendationOutput()
		{
			recordSparseMlpTimingLine();
			for (String timingLine : textprocessing_wb_or_tfidf_Data)
			{
				System.out.print(timingLine);
			}
			myGui.setTPTime(completionTimeTextProcessing/1000000.00);
			myGui.setTfidfTime(completionTimeTFIDF/1000000.00);
			myGui.setAlgorithmTime(completionTimeAlgorithm/1000000.00);
			String outputFilename = "Results/Timing/" + referenceUser
					+ "/" + "Distributed_Server_TP_TFIDF_Algorithm"
					+ numRecAgents + ".txt";
			try
			{
				saveToFile_array(
						outputFilename,
						textprocessing_wb_or_tfidf_Data,
						"append");
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
			textprocessing_wb_or_tfidf_Data.clear();
			ACLMessage msg7 = new ACLMessage(ACLMessage.INFORM);
			msg7.addReceiver(new AID("Starter Agent", AID.ISLOCALNAME));
			msg7.setPerformative(ACLMessage.INFORM);
			msg7.setContent("Tweeting TFIDF Algorithm Calculation Completed");
			msg7.setConversationId(runLifecycle.getActiveRunId());
			msg7.setOntology("Tweets TFIDF Algorithm Calculation Done");
			send(msg7);
			sendScoresToOrganizer();
		}
		
		private double[] vectorArrayFormat(Map<String,Double> currDocTfidf, TreeSet<String> uniqueDocTerms)
		{
			double[] vectorArray = new double[uniqueDocTerms.size()];
			double currTfidf;
			
			int uniqueWordCount = 0;
				for (String uniqueWord: uniqueDocTerms)
				{
					
					// System.out.println(getLocalName()+" uniqueWordCount: "+uniqueWordCount);
					Double value = currDocTfidf == null ? null : currDocTfidf.get(uniqueWord);
					currTfidf = value == null ? 0.0 : value.doubleValue();
					
					vectorArray[uniqueWordCount] = currTfidf;
				
				uniqueWordCount++;
			}
			
			return vectorArray;
		}
		
		
			private StringJoiner vectorArffFormat(Map<String,Double> currDocTfidf, TreeSet<String> uniqueDocTerms)
			{
				StringJoiner tfidfJoinerTemp = new StringJoiner(",");
			double currTfidf;
			
			// int uniqueWordCount = 0;
			for (String uniqueWord: uniqueDocTerms)
			{
				// uniqueWordCount++;
				// System.out.println(getLocalName()+" uniqueWordCount: "+uniqueWordCount);
				if (currDocTfidf.keySet().contains(uniqueWord))
					currTfidf = currDocTfidf.get(uniqueWord);
				else
					currTfidf = 0.0;
				
				tfidfJoinerTemp.add(String.valueOf(currTfidf));
			}
				return tfidfJoinerTemp;
			}

			private void configureSvmModel(SMO svmModel)
			{
				ExactSvmModelSupport.configure(svmModel, algorithmSettings());
			}

			private double[] getSvmClassDistribution(SMO svmModel, Instances instances, int instanceIndex)
			{
				try
				{
					return svmModel.distributionForInstance(instances.instance(instanceIndex));
				}
				catch (Exception e)
				{
					e.printStackTrace();
					return null;
				}
			}

			private TreeMap<String,Double> buildFolloweeScoreMapFromDistribution(Instances instances, double[] distribution, String fallbackFollowee)
			{
				ArrayList<String> classNames = new ArrayList<String>();
				for (int index = 0; index < instances.classAttribute().numValues(); index++)
				{
					classNames.add(instances.classAttribute().value(index));
				}
				return buildFolloweeScoreMapFromDistribution(
						classNames, distribution, fallbackFollowee);
			}

			private TreeMap<String,Double> buildFolloweeScoreMapFromDistribution(
					List<String> classNames,
					double[] distribution,
					String fallbackFollowee)
			{
				TreeMap<String,Double> userScore = new TreeMap<String,Double>();
				for (String followeeUser : followeeFollowers.keySet())
				{
					userScore.put(followeeUser, 0.0);
				}

				if (distribution != null)
				{
					for (int i = 0; i < distribution.length
							&& i < classNames.size(); i++)
					{
						String className = classNames.get(i);
						if (userScore.containsKey(className))
						{
							double score = distribution[i];
							if (Double.isNaN(score) || Double.isInfinite(score))
							{
								score = 0.0;
							}
							userScore.put(className, score);
						}
					}
				}

				boolean hasPositiveScore = false;
				for (Double score : userScore.values())
				{
					if (score != null && score > 0.0)
					{
						hasPositiveScore = true;
						break;
					}
				}
				if (!hasPositiveScore && fallbackFollowee != null && userScore.containsKey(fallbackFollowee))
				{
					userScore.put(fallbackFollowee, 1.0);
				}

				return userScore;
				}
	
				private AlgorithmParameterSettings algorithmSettings()
				{
					return myGui == null
							? AlgorithmParameterSettings.defaults()
							: myGui.getAlgorithmParameterSettings();
				}

				private TreeMap<String,TreeMap<String,Double>>
						runExactSvmWorkerRecommendations(
							String trainPath,
							String testPath,
							String recommendPath,
							List<String> targets,
							int featureCount,
							int trainingInstanceCount,
							int trainingClassCount,
							String stage) throws Exception
			{
				ExactSvmWorkerResult result = algorithmRec == SCALABLE_SVM
						? ScalableSvmWorkerRunner.run(
								getLocalName(),
								stage,
								runLifecycle.getActiveRunId(),
								new File(trainPath),
								new File(testPath),
									new File(recommendPath),
									featureCount,
									trainingInstanceCount,
									trainingClassCount,
									algorithmSettings())
							: ExactSvmWorkerRunner.run(
									getLocalName(),
									stage,
								runLifecycle.getActiveRunId(),
								new File(trainPath),
								new File(testPath),
									new File(recommendPath),
									featureCount,
									trainingInstanceCount,
									trainingClassCount,
									algorithmSettings());

				if (Double.isFinite(result.getTestAccuracyPercent()))
				{
					String accuracy = getLocalName()
							+ " Accuracy of "
							+ RecommendationModeSupport.algorithmName(algorithmRec)
							+ ": "
							+ String.format("%.2f%%", result.getTestAccuracyPercent())
							+ "\n---------------------------------";
					System.out.println(accuracy);
					myGui.appendResult(accuracy);
				}
				if (result.getConfusionMatrix() != null
						&& result.getConfusionMatrix().length() > 0)
				{
					System.out.println(result.getConfusionMatrix());
				}

				TreeMap<String,TreeMap<String,Double>> scores =
						new TreeMap<String,TreeMap<String,Double>>();
				List<String> predictedClasses = result.getPredictedClasses();
				List<double[]> distributions = result.getDistributions();
				for (int index = 0; index < targets.size(); index++)
				{
					if (index >= predictedClasses.size()
							|| index >= distributions.size())
					{
						throw new IllegalStateException(
								RecommendationModeSupport.algorithmName(algorithmRec)
								+ " worker returned fewer target predictions than requested.");
					}
					String target = targets.get(index);
					String predictedClass = predictedClasses.get(index);
					System.out.println(
							getLocalName() + " Recommended Class: "
							+ predictedClass);
					scores.put(
							target,
							buildFolloweeScoreMapFromDistribution(
									result.getClassNames(),
									distributions.get(index),
									predictedClass));
				}
				return scores;
			}

			private boolean isDoc2VecBatchMode()
			{
				return algorithmRec == Doc2Vec && usersRec != null && usersRec.size() > 1;
			}

			private boolean isUserRankerFolloweeMode()
			{
				return recommendationConfig != null
						&& recommendationConfig.usesConvertedPipeline()
						&& RecommendationModeSupport.isUserRankingAlgorithm(algorithmRec)
						&& recommendationConfig.getCandidateType() == RecommendationCandidateType.FOLLOWEE;
			}

			private boolean isClassifierGuidedUserMode()
			{
				return recommendationConfig != null
						&& recommendationConfig.usesConvertedPipeline()
						&& RecommendationModeSupport.isClassifierAlgorithm(algorithmRec)
						&& recommendationConfig.getCandidateType() == RecommendationCandidateType.USER;
			}

			private TreeMap<String,TreeMap<String,Double>> runDoc2VecBatchRecommendations(
					int topn, boolean allowLegacyFallback)
			{
				TreeMap<String,TreeMap<String,Double>> batchScores = new TreeMap<String,TreeMap<String,Double>>();
				ArrayList<String> batchUsers = new ArrayList<String>(usersRec);
				File selectedDataset = getConfiguredDatasetFile();
				if (selectedDataset == null)
				{
					reportDoc2VecBatchFailure(
							"Doc2Vec batch cannot run because this experiment has no dataset file.");
					return allowLegacyFallback
							? buildDoc2VecBatchFailureScores(batchUsers, topn)
							: buildEmptyDoc2VecScores(batchUsers);
				}
				String datasetValidation = recommendationConfig == null
						|| recommendationConfig.getDatasetFile() == null
						? null : recommendationConfig.validateDatasetSnapshot();
				if (datasetValidation != null)
				{
					reportDoc2VecBatchFailure(datasetValidation);
					return allowLegacyFallback
							? buildDoc2VecBatchFailureScores(batchUsers, topn)
							: buildEmptyDoc2VecScores(batchUsers);
				}

				int doc2vecWorkers = Math.min(Math.max(Runtime.getRuntime().availableProcessors() - 1, 1), 12);
				File batchScript = new File("TwitterGatherDataFollowers/userRyersonU/doc2vec_batch.py");
				int doc2vecNodeNumber = Integer.parseInt(nodeNumber);
				int virtualShardCount =
						NodeCapacityScheduler.virtualCandidateShardCount(
								numRecAgents,
								batchUsers.size());
				List<Integer> assignedShards =
						NodeCapacityScheduler.assignedVirtualCandidateShards(
								doc2vecNodeNumber,
								numRecAgents,
								virtualShardCount);
				boolean dynamicQueueActive =
						RecommendationSchedulerRegistry.snapshot().isActive()
						&& virtualShardCount != numRecAgents;
				boolean allowShardFallback =
						allowLegacyFallback
						&& numRecAgents == 1
						&& virtualShardCount == 1;
				for (String recUser : batchUsers)
				{
					batchScores.put(recUser, new TreeMap<String,Double>());
				}
				if (dynamicQueueActive)
				{
					String queueDetail =
							NodeCapacityScheduler.virtualCandidateShardDetail(
									doc2vecNodeNumber,
									numRecAgents,
									assignedShards,
									virtualShardCount);
					System.out.println(
							getLocalName()
							+ " Dynamic Doc2Vec candidate queue: "
							+ queueDetail
							+ " | pull queue enabled");
					NodeExecutionMonitor.modelRunning(
							getLocalName(),
							"Dynamic Doc2Vec pull queue: " + queueDetail);
				}
				if (!dynamicQueueActive && assignedShards.isEmpty())
				{
					System.out.println(
							getLocalName()
							+ " Dynamic Doc2Vec candidate queue assigned no shards; "
							+ "publishing empty score rows for organizer merge.");
					NodeExecutionMonitor.modelRunning(
							getLocalName(),
							"No Doc2Vec candidate shards assigned to this node.");
					NodeExecutionMonitor.publishing(getLocalName());
					return batchScores;
				}
				try
				{
					if (dynamicQueueActive)
					{
						int claimedCount = 0;
						int deferredPolls = 0;
						while (true)
						{
							RecommendationWorkQueueRegistry.ShardClaim claim =
									RecommendationWorkQueueRegistry
											.claimDoc2VecCandidateShard(
													runLifecycle.getActiveRunId(),
													doc2vecNodeNumber,
													numRecAgents,
													virtualShardCount);
							if (!claim.hasShard())
							{
								if (claim.isDeferred())
								{
									deferredPolls++;
									if (deferredPolls == 1
											|| deferredPolls % 20 == 0)
									{
										NodeExecutionMonitor.modelRunning(
												getLocalName(),
												"Waiting for Doc2Vec scheduler turn: "
												+ claim.getReason());
									}
									Thread.sleep(Math.min(
											500L,
											50L + deferredPolls * 10L));
									continue;
								}
								break;
							}
							deferredPolls = 0;
							claimedCount++;
							int claimedShard = claim.getShardNumber();
							NodeExecutionMonitor.modelRunning(
									getLocalName(),
									"Claimed Doc2Vec candidate shard "
									+ claimedShard
									+ "/" + virtualShardCount
									+ " from the live scheduler queue; "
									+ claim.getRemaining()
									+ " shard(s) remain.");
							boolean shardScored;
							try
							{
								LiveWorkloadAdjustmentRegistry
										.applyClaimPacingDelay(
												runLifecycle.getActiveRunId());
								if (!RecommendationWorkQueueRegistry
										.isActiveDoc2VecCandidateShard(
												runLifecycle.getActiveRunId(),
												doc2vecNodeNumber,
												numRecAgents,
												virtualShardCount,
												claimedShard))
								{
									NodeExecutionMonitor.modelRunning(
											getLocalName(),
											"Doc2Vec shard " + claimedShard
											+ " was drained before scoring; "
											+ "skipping stale claim.");
									continue;
								}
								shardScored = scoreDoc2VecVirtualShard(
										batchScript,
										topn,
										selectedDataset,
										claimedShard,
										virtualShardCount,
										doc2vecWorkers,
										batchUsers,
										batchScores,
										true);
							}
							catch (IOException exception)
							{
								RecommendationWorkQueueRegistry
										.failDoc2VecCandidateShard(
												runLifecycle.getActiveRunId(),
												doc2vecNodeNumber,
												numRecAgents,
												virtualShardCount,
												claimedShard);
								throw exception;
							}
							catch (InterruptedException exception)
							{
								RecommendationWorkQueueRegistry
										.failDoc2VecCandidateShard(
												runLifecycle.getActiveRunId(),
												doc2vecNodeNumber,
												numRecAgents,
												virtualShardCount,
												claimedShard);
								throw exception;
							}
							if (!shardScored)
							{
								RecommendationWorkQueueRegistry
										.failDoc2VecCandidateShard(
												runLifecycle.getActiveRunId(),
												doc2vecNodeNumber,
												numRecAgents,
												virtualShardCount,
												claimedShard);
								return allowShardFallback
										? buildDoc2VecBatchFailureScores(batchUsers, topn)
										: buildEmptyDoc2VecScores(batchUsers);
							}
							if (!RecommendationWorkQueueRegistry
									.isActiveDoc2VecCandidateShard(
											runLifecycle.getActiveRunId(),
											doc2vecNodeNumber,
											numRecAgents,
											virtualShardCount,
											claimedShard))
							{
								NodeExecutionMonitor.modelRunning(
										getLocalName(),
										"Doc2Vec shard " + claimedShard
										+ " completed after drain/requeue; "
										+ "discarded stale completion.");
								continue;
							}
							RecommendationWorkQueueRegistry
									.completeDoc2VecCandidateShard(
											runLifecycle.getActiveRunId(),
											doc2vecNodeNumber,
											numRecAgents,
											virtualShardCount,
											claimedShard);
							NodeExecutionMonitor.modelRunning(
									getLocalName(),
									"Finished Doc2Vec candidate shard "
									+ claimedShard + "/" + virtualShardCount
									+ "; claiming another shard if the queue has work.");
						}
						if (claimedCount == 0)
						{
							System.out.println(
									getLocalName()
									+ " found the Doc2Vec scheduler queue empty; "
									+ "publishing empty score rows for organizer merge.");
							NodeExecutionMonitor.publishing(getLocalName());
						}
					}
					else
					{
						for (int shardIndex = 0;
								shardIndex < assignedShards.size();
								shardIndex++)
						{
							int virtualShardNumber = assignedShards.get(shardIndex);
							if (!scoreDoc2VecVirtualShard(
									batchScript,
									topn,
									selectedDataset,
									virtualShardNumber,
									virtualShardCount,
									doc2vecWorkers,
									batchUsers,
									batchScores,
									false))
							{
								return allowShardFallback
									? buildDoc2VecBatchFailureScores(batchUsers, topn)
									: buildEmptyDoc2VecScores(batchUsers);
							}
						}
					}

					int completedWithScores = 0;
					for (String recUser : batchUsers)
					{
						TreeMap<String,Double> userScores = batchScores.get(recUser);
						if (userScores != null && !userScores.isEmpty())
						{
							completedWithScores++;
						}
					}
					if (completedWithScores == 0)
					{
						reportDoc2VecBatchFailure(
								"Doc2Vec produced no recommendation candidates for any of "
								+ batchUsers.size() + " target users using "
								+ selectedDataset.getAbsolutePath() + ".");
					}
				}
				catch (IOException e)
				{
					e.printStackTrace();
					reportDoc2VecBatchFailure(
							"Doc2Vec batch could not start for "
							+ selectedDataset.getAbsolutePath() + ": "
							+ e.getMessage());
					return allowShardFallback
							? buildDoc2VecBatchFailureScores(batchUsers, topn)
							: buildEmptyDoc2VecScores(batchUsers);
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
					e.printStackTrace();
					reportDoc2VecBatchFailure(
							"Doc2Vec batch was interrupted for "
							+ selectedDataset.getAbsolutePath() + ".");
					return allowShardFallback
							? buildDoc2VecBatchFailureScores(batchUsers, topn)
							: buildEmptyDoc2VecScores(batchUsers);
				}

				return batchScores;
			}

			private boolean scoreDoc2VecVirtualShard(
					File batchScript,
					int topn,
					File selectedDataset,
					int virtualShardNumber,
					int virtualShardCount,
					int doc2vecWorkers,
					List<String> batchUsers,
					TreeMap<String,TreeMap<String,Double>> batchScores,
					boolean dynamicQueueActive)
					throws IOException, InterruptedException
			{
				if (dynamicQueueActive)
				{
					applyDoc2VecShardCapacityDelay(virtualShardNumber);
				}
				Doc2VecBatchRunner.Result result = Doc2VecBatchRunner.run(
						getPythonCommand(), batchScript, topn, selectedDataset,
							virtualShardNumber,
							virtualShardCount,
							doc2vecWorkers,
							batchUsers,
							algorithmSettings(),
							new Doc2VecBatchRunner.ProgressListener()
						{
							public void onProgress(
									String event, String detail)
							{
								handleDoc2VecProgress(event, detail);
							}
						});
				if (result.hasUnsafeNumericalDiagnostic())
				{
					reportDoc2VecBatchFailure(
							"Doc2Vec stopped because Gensim reported an unsafe "
							+ "numerical-kernel exception."
							+ result.getDiagnosticSummary());
					return false;
				}
				for (Map.Entry<String,String> error : result.getErrors().entrySet())
				{
					System.out.println(getLocalName()+" Doc2Vec batch warning for "+error.getKey()+": "+error.getValue());
				}
				if (result.getExitCode() != 0)
				{
					reportDoc2VecBatchFailure(
							"Doc2Vec batch process exited with code "
							+ result.getExitCode() + " for "
							+ selectedDataset.getAbsolutePath()
							+ " while scoring virtual shard "
							+ virtualShardNumber + "/" + virtualShardCount
							+ result.getDiagnosticSummary());
				}
				if (dynamicQueueActive
						&& !RecommendationWorkQueueRegistry
								.isActiveDoc2VecCandidateShard(
										runLifecycle.getActiveRunId(),
										Math.max(1, Integer.parseInt(nodeNumber)),
										numRecAgents,
										virtualShardCount,
										virtualShardNumber))
				{
					NodeExecutionMonitor.modelRunning(
							getLocalName(),
							"Doc2Vec shard " + virtualShardNumber
							+ " was drained while the subprocess was running; "
							+ "discarding stale shard output.");
					return true;
				}
				mergeDoc2VecShardScores(result, batchUsers, batchScores);
				return true;
			}

			private void applyDoc2VecShardCapacityDelay(
					int virtualShardNumber) throws InterruptedException
			{
				NodeCapacityScheduler.applyPerJobDelayOnly(
						NodeCapacityRegistry.profileForNode(
								Integer.parseInt(nodeNumber)),
						new NodeCapacityScheduler.CancellationCheck()
						{
							public void throwIfCancelled()
									throws InterruptedException
							{
								if (Thread.currentThread().isInterrupted())
								{
									throw new InterruptedException(
											"Doc2Vec virtual shard "
											+ virtualShardNumber
											+ " was interrupted.");
								}
							}
						});
			}

			private void mergeDoc2VecShardScores(
					Doc2VecBatchRunner.Result result,
					List<String> batchUsers,
					TreeMap<String,TreeMap<String,Double>> batchScores)
			{
				for (String recUser : batchUsers)
				{
					TreeMap<String,Double> userScores = result.getScores(recUser);
					if (result.isCompleted(recUser)
							&& !result.hasError(recUser)
							&& userScores != null)
					{
						TreeMap<String,Double> mergedScores =
								batchScores.get(recUser);
						if (mergedScores == null)
						{
							mergedScores = new TreeMap<String,Double>();
							batchScores.put(recUser, mergedScores);
						}
						for (Map.Entry<String,Double> scoreEntry : userScores.entrySet())
						{
							Double existing =
									mergedScores.get(scoreEntry.getKey());
							if (existing == null
									|| scoreEntry.getValue().doubleValue()
											> existing.doubleValue())
							{
								mergedScores.put(
										scoreEntry.getKey(),
										scoreEntry.getValue());
							}
						}
					}
					else
					{
						System.out.println(
								getLocalName()
								+ " Doc2Vec shard did not complete for "
								+ recUser + ".");
					}
				}
			}

			private File getConfiguredDatasetFile()
			{
				File configuredDataset = recommendationConfig == null
						? null : recommendationConfig.getDatasetFile();
				if (configuredDataset != null)
				{
					return configuredDataset;
				}
				return myGui == null || myGui.fileChooser == null
						? null : myGui.fileChooser.getSelectedFile();
			}

			private String getPythonCommand()
			{
				String configuredCommand =
						System.getProperty("recommender.python.command");
				return configuredCommand == null || configuredCommand.trim().length() == 0
						? "python" : configuredCommand.trim();
			}

			private void reportDoc2VecBatchFailure(String message)
			{
				String diagnostic = getLocalName() + " " + message;
				System.err.println(diagnostic);
				if (myGui != null)
				{
					myGui.appendResult(diagnostic);
				}
			}

			private void handleDoc2VecProgress(String event, String detail)
			{
				if ("WAITING".equals(event))
				{
					NodeExecutionMonitor.waitingForSharedModel(
							getLocalName(), detail);
				}
				else if ("TRAINING".equals(event)
						|| "MODEL_READY".equals(event)
						|| "CACHE".equals(event)
						|| "INDEX".equals(event)
						|| "SCORING".equals(event))
				{
					NodeExecutionMonitor.modelRunning(
							getLocalName(), detail);
				}
			}

			private TreeMap<String,TreeMap<String,Double>> buildDoc2VecBatchFailureScores(List<String> batchUsers, int topn)
			{
				TreeMap<String,TreeMap<String,Double>> failureScores = new TreeMap<String,TreeMap<String,Double>>();
				for (String recUser : batchUsers)
				{
					failureScores.put(recUser, buildDoc2VecBatchFailureScoreMap(recUser, topn));
				}
				return failureScores;
			}

			private TreeMap<String,TreeMap<String,Double>> buildEmptyDoc2VecScores(
					List<String> batchUsers)
			{
				TreeMap<String,TreeMap<String,Double>> emptyScores =
						new TreeMap<String,TreeMap<String,Double>>();
				for (String recUser : batchUsers)
				{
					emptyScores.put(recUser, new TreeMap<String,Double>());
				}
				return emptyScores;
			}

			private TreeMap<String,Double> buildDoc2VecBatchFailureScoreMap(String recUser, int topn)
			{
				return new TreeMap<String,Double>();
			}

			private boolean isSvmBatchMode()
			{
				return RecommendationModeSupport.isSvmFamilyAlgorithm(algorithmRec)
						&& usersRec != null && usersRec.size() > 1;
			}

			private TreeMap<String,TreeMap<String,Double>> runIndependentSvmBatchRecommendations()
			{
				if (isExperimentalTargetSchedulingEnabled())
				{
					return runScheduledSvmTargetQueue();
				}
				TreeMap<String,TreeMap<String,Double>> batchScores = new TreeMap<String,TreeMap<String,Double>>();
				ArrayList<String> assignedUsers = getAssignedSvmBatchUsers();
				System.out.println(getLocalName()+" SVM batch assigned users: "+assignedUsers);
				System.out.println(getLocalName()+" SVM reproducibility split seed: "
						+ SvmReproducibility.SPLIT_SEED + " model seed: " + SvmReproducibility.MODEL_SEED);
				for (String recUser : assignedUsers)
				{
					applyExperimentalTargetJobDelay(recUser);
					TreeMap<String,Double> userScores = runIndependentSvmForUser(recUser);
					if (userScores == null)
					{
						throw new IllegalStateException(
								RecommendationModeSupport.algorithmName(algorithmRec)
								+ " did not produce a valid prediction for "
								+ recUser + ".");
					}
					batchScores.put(recUser, userScores);
				}
				return batchScores;
			}

			private TreeMap<String,TreeMap<String,Double>> runScheduledSvmTargetQueue()
			{
				TreeMap<String,TreeMap<String,Double>> batchScores =
						new TreeMap<String,TreeMap<String,Double>>();
				if (usersRec == null || usersRec.isEmpty())
				{
					return batchScores;
				}
				ArrayList<String> targetUsers =
						new ArrayList<String>(usersRec);
				int currentNode = Math.max(1, Integer.parseInt(nodeNumber));
				int targetCount = targetUsers.size();
				String algorithmKey =
						RecommendationModeSupport.algorithmName(algorithmRec);
				NodeExecutionMonitor.modelRunning(
						getLocalName(),
						"Live " + algorithmKey
						+ " target-job queue active for "
						+ targetCount + " target user"
						+ (targetCount == 1 ? "" : "s") + ".");
				int claimedCount = 0;
				int deferredPolls = 0;
				while (true)
				{
					RecommendationWorkQueueRegistry.TargetClaim claim =
							RecommendationWorkQueueRegistry.claimTargetUserJob(
									runLifecycle.getActiveRunId(),
									algorithmKey,
									currentNode,
									numRecAgents,
									targetUsers);
					if (!claim.hasTarget())
					{
						if (claim.isDeferred())
						{
							deferredPolls++;
							if (deferredPolls == 1 || deferredPolls % 20 == 0)
							{
								NodeExecutionMonitor.modelRunning(
										getLocalName(),
										"Waiting for " + algorithmKey
										+ " target-job scheduler turn: "
										+ claim.getReason());
							}
							try
							{
								Thread.sleep(Math.min(
										500L,
										50L + deferredPolls * 10L));
							}
							catch (InterruptedException interrupted)
							{
								Thread.currentThread().interrupt();
								throw new IllegalStateException(
										algorithmKey
										+ " target scheduler was interrupted.",
										interrupted);
							}
							continue;
						}
						break;
					}

					deferredPolls = 0;
					claimedCount++;
					int jobNumber = claim.getJobNumber();
					String recUser = claim.getTargetUser();
					try
					{
						NodeExecutionMonitor.modelRunning(
								getLocalName(),
								"Claimed " + algorithmKey
								+ " target job " + jobNumber
								+ "/" + targetCount + " for " + recUser
								+ "; " + claim.getRemaining()
								+ " target job(s) remain.");
						applyExperimentalTargetJobDelay(recUser);
						LiveWorkloadAdjustmentRegistry.applyClaimPacingDelay(
								runLifecycle.getActiveRunId());
						if (!RecommendationWorkQueueRegistry.isActiveTargetUserJob(
								runLifecycle.getActiveRunId(),
								algorithmKey,
								currentNode,
								numRecAgents,
								targetCount,
								jobNumber))
						{
							NodeExecutionMonitor.modelRunning(
									getLocalName(),
									algorithmKey + " target job " + jobNumber
									+ " was drained before scoring; "
									+ "skipping stale claim.");
							continue;
						}
						TreeMap<String,Double> userScores =
								runIndependentSvmForUser(recUser);
						if (userScores == null)
						{
							throw new IllegalStateException(
									algorithmKey
									+ " did not produce a valid prediction for "
									+ recUser + ".");
						}
						if (!RecommendationWorkQueueRegistry.isActiveTargetUserJob(
								runLifecycle.getActiveRunId(),
								algorithmKey,
								currentNode,
								numRecAgents,
								targetCount,
								jobNumber))
						{
							NodeExecutionMonitor.modelRunning(
									getLocalName(),
									algorithmKey + " target job " + jobNumber
									+ " completed after drain/requeue; "
									+ "discarded stale score row.");
							continue;
						}
						batchScores.put(recUser, userScores);
						RecommendationWorkQueueRegistry.completeTargetUserJob(
								runLifecycle.getActiveRunId(),
								algorithmKey,
								currentNode,
								numRecAgents,
								targetCount,
								jobNumber);
					}
					catch (InterruptedException interrupted)
					{
						RecommendationWorkQueueRegistry.failTargetUserJob(
								runLifecycle.getActiveRunId(),
								algorithmKey,
								currentNode,
								numRecAgents,
								targetCount,
								jobNumber);
						Thread.currentThread().interrupt();
						throw new IllegalStateException(
								algorithmKey
								+ " target scheduler was interrupted.",
								interrupted);
					}
					catch (RuntimeException failure)
					{
						RecommendationWorkQueueRegistry.failTargetUserJob(
								runLifecycle.getActiveRunId(),
								algorithmKey,
								currentNode,
								numRecAgents,
								targetCount,
								jobNumber);
						throw failure;
					}
				}
				NodeExecutionMonitor.modelRunning(
						getLocalName(),
						"Live " + algorithmKey
						+ " target scheduler finished "
						+ claimedCount + " target job"
						+ (claimedCount == 1 ? "" : "s") + ".");
				return batchScores;
			}

			private boolean isExperimentalTargetSchedulingEnabled()
			{
				RecommendationSchedulerPlan plan =
						RecommendationSchedulerRegistry.snapshot();
				return plan != null
						&& plan.allowsExperimentalSideEffects()
						&& numRecAgents > 1
						&& usersRec != null
						&& usersRec.size() > 1
						&& RecommendationModeSupport.isSvmFamilyAlgorithm(
								algorithmRec);
			}

			private void applyExperimentalTargetJobDelay(
					final String recUser)
			{
				if (!isExperimentalTargetSchedulingEnabled())
				{
					return;
				}
				final int currentNode = Math.max(
						1,
						Integer.parseInt(nodeNumber));
				NodeCapacityProfile profile =
						NodeCapacityRegistry.profileForNode(currentNode);
				try
				{
					NodeCapacityScheduler.applyPerJobDelayOnly(
							profile,
							new NodeCapacityScheduler.CancellationCheck()
							{
								public void throwIfCancelled()
										throws InterruptedException
								{
									if (Thread.currentThread().isInterrupted())
									{
										throw new InterruptedException(
												"interrupted while scheduling "
												+ recUser);
									}
								}
							});
				}
				catch (InterruptedException interrupted)
				{
					Thread.currentThread().interrupt();
					throw new IllegalStateException(
							"Experimental target scheduler was interrupted.",
							interrupted);
				}
			}

			private ArrayList<String> getAssignedSvmBatchUsers()
			{
				ArrayList<String> assignedUsers = new ArrayList<String>();
				if (usersRec == null || usersRec.size() == 0)
				{
					return assignedUsers;
				}

				if (isExperimentalTargetSchedulingEnabled())
				{
					List<List<String>> assignments =
							NodeCapacityScheduler.assignTargetJobsDeterministic(
									usersRec,
									numRecAgents);
					int nodeIndex = Math.max(
							0,
							Math.min(
									assignments.size() - 1,
									Integer.parseInt(nodeNumber) - 1));
					assignedUsers.addAll(assignments.get(nodeIndex));
					NodeExecutionMonitor.modelRunning(
							getLocalName(),
							"Experimental "
									+ RecommendationModeSupport.algorithmName(
											algorithmRec)
									+ " target scheduler active: "
									+ NodeCapacityScheduler.assignmentDetail(
											nodeIndex + 1,
											assignedUsers.size()));
					return assignedUsers;
				}

				assignedUsers.addAll(usersRec);
				return assignedUsers;
			}

			private TreeMap<String,Double> runIndependentSvmForUser(String recUser)
			{
				if (recUser == null || !allUserDocuments.containsKey(recUser))
				{
					throw new IllegalArgumentException(
							RecommendationModeSupport.algorithmName(algorithmRec)
							+ " target has no prepared document: " + recUser);
				}

				LinkedHashMap<String,LinkedHashMap<String,Double>> documentsForTarget = buildSvmDocumentsForTarget(recUser);
				if (!documentsForTarget.containsKey(recUser))
				{
					throw new IllegalArgumentException(
							RecommendationModeSupport.algorithmName(algorithmRec)
							+ " target is not present on this node: " + recUser);
				}

				TreeSet<String> targetTerms = buildUniqueTermsForDocuments(documentsForTarget);
				LinkedHashMap<String,Integer> targetDocFreq = buildDocumentFrequencyForDocuments(documentsForTarget);
				LinkedHashMap<String,LinkedHashMap<String,Double>> targetTfidf = calculateTfidfForDocuments(documentsForTarget, targetDocFreq);
				Map<String,List<String>> targetFolloweeFollowers = buildFolloweeFollowersForDocuments(documentsForTarget);
				if (isClassifierGuidedUserMode())
				{
					for (List<String> followers : targetFolloweeFollowers.values())
					{
						followers.remove(recUser);
					}
				}
				SvmDataSplit split = determineSvmDataSplit(targetFolloweeFollowers);

				String arffDirName = "Dataset/Arff_files/";
				File arffDir = new File(arffDirName);
				if (!arffDir.exists())
				{
					arffDir.mkdirs();
				}

				String safeUser = sanitizeFileName(recUser);
				String trainPath = arffDirName + "svm_batch_train_rec" + nodeNumber + "_" + safeUser + ".txt";
				String testPath = arffDirName + "svm_batch_test_rec" + nodeNumber + "_" + safeUser + ".txt";
				String recommendPath = arffDirName + "svm_batch_recommend_rec" + nodeNumber + "_" + safeUser + ".txt";

				try
				{
					writeSvmBatchArffFiles(trainPath, testPath, recommendPath, targetTfidf, targetTerms, split, recUser);
					return trainAndPredictSvmForBatchUser(
							trainPath,
							testPath,
							recommendPath,
							recUser,
							targetTerms.size(),
							split.trainUsers.size()
									+ split.testUsers.size() + 1,
							followeeFollowers.size());
				}
				catch (IOException e)
				{
					throw new IllegalStateException(
							"Could not prepare exact-SVM input for " + recUser + ".", e);
				}
				finally
				{
					deleteTemporaryFile(trainPath);
					deleteTemporaryFile(testPath);
					deleteTemporaryFile(recommendPath);
				}
			}

			private LinkedHashMap<String,LinkedHashMap<String,Double>> buildSvmDocumentsForTarget(String targetUser)
			{
				LinkedHashMap<String,LinkedHashMap<String,Double>> documentsForTarget = new LinkedHashMap<String,LinkedHashMap<String,Double>>();
				for (Map.Entry<String,LinkedHashMap<String,Double>> entry : allUserDocuments.entrySet())
				{
					String userName = entry.getKey();
					if (usersRec.contains(userName) && !userName.equals(targetUser))
					{
						Integer primaryRecServer = userPrimaryRecServer.get(userName);
						if (primaryRecServer == null || !String.valueOf(primaryRecServer).equals(nodeNumber))
						{
							continue;
						}
					}
					documentsForTarget.put(userName, entry.getValue());
				}
				return documentsForTarget;
			}

			private TreeSet<String> buildUniqueTermsForDocuments(LinkedHashMap<String,LinkedHashMap<String,Double>> documents)
			{
				TreeSet<String> uniqueTerms = new TreeSet<String>();
				for (LinkedHashMap<String,Double> document : documents.values())
				{
					uniqueTerms.addAll(document.keySet());
				}
				return uniqueTerms;
			}

			private LinkedHashMap<String,Integer> buildDocumentFrequencyForDocuments(LinkedHashMap<String,LinkedHashMap<String,Double>> documents)
			{
				LinkedHashMap<String,Integer> docFreq = new LinkedHashMap<String,Integer>();
				for (LinkedHashMap<String,Double> document : documents.values())
				{
					for (String term : document.keySet())
					{
						Integer currentCount = docFreq.get(term);
						if (currentCount == null)
						{
							currentCount = 0;
						}
						docFreq.put(term, currentCount + 1);
					}
				}
				return docFreq;
			}

			private LinkedHashMap<String,LinkedHashMap<String,Double>> calculateTfidfForDocuments(LinkedHashMap<String,LinkedHashMap<String,Double>> documents, LinkedHashMap<String,Integer> docFreq)
			{
				LinkedHashMap<String,LinkedHashMap<String,Double>> tfidfDocuments = new LinkedHashMap<String,LinkedHashMap<String,Double>>();
				int targetTotalDocuments = documents.size();
				for (Map.Entry<String,LinkedHashMap<String,Double>> entry : documents.entrySet())
				{
					String userName = entry.getKey();
					LinkedHashMap<String,Double> userDoc = entry.getValue();
					LinkedHashMap<String,Double> userDocumentTFIDF = new LinkedHashMap<String,Double>();
					double vectorMagnitude = 0.0;
					for (String docTerm : userDoc.keySet())
					{
						double tf = userDoc.get(docTerm);
						Integer dfValue = docFreq.get(docTerm);
						double df = dfValue == null ? 0.0 : dfValue.doubleValue();
						double tfidf = 0.0;
						if (df > 0.0)
						{
							double idf = (double)Math.log10(targetTotalDocuments/df) / Math.log10(2);
							tfidf = tf * idf;
						}
						if (Double.isNaN(tfidf))
						{
							tfidf = 0.0;
						}
						userDocumentTFIDF.put(docTerm, tfidf);
						vectorMagnitude += tfidf * tfidf;
					}

					vectorMagnitude = Math.sqrt(vectorMagnitude);
					for (String docTerm : userDoc.keySet())
					{
						double tfidf = userDocumentTFIDF.get(docTerm);
						if (vectorMagnitude > 0.0)
						{
							tfidf = tfidf / vectorMagnitude;
						}
						else
						{
							tfidf = 0.0;
						}
						if (Double.isNaN(tfidf))
						{
							tfidf = 0.0;
						}
						userDocumentTFIDF.put(docTerm, tfidf);
					}
					tfidfDocuments.put(userName, userDocumentTFIDF);
				}
				return tfidfDocuments;
			}

			private Map<String,List<String>> buildFolloweeFollowersForDocuments(LinkedHashMap<String,LinkedHashMap<String,Double>> documents)
			{
				Map<String,List<String>> targetFolloweeFollowers = new LinkedHashMap<String,List<String>>();
				for (String userName : documents.keySet())
				{
					String followeeName = userFollowee.get(userName);
					if (followeeName == null)
					{
						continue;
					}
					List<String> followers = targetFolloweeFollowers.get(followeeName);
					if (followers == null)
					{
						followers = new ArrayList<String>();
						targetFolloweeFollowers.put(followeeName, followers);
					}
					followers.add(userName);
				}
				return targetFolloweeFollowers;
			}

			private SvmDataSplit determineSvmDataSplit(Map<String,List<String>> targetFolloweeFollowers)
			{
				SvmReproducibility.Split split = SvmReproducibility.stratifiedSplit(
						targetFolloweeFollowers, TEST_SET_PERCENT);
				return new SvmDataSplit(split.getTrainUsers(), split.getTestUsers());
			}

			private void logSvmSplitSummary(String label, List<String> trainUsers, List<String> testUsers)
			{
				System.out.println(getLocalName()+" "+label+" reproducibility split seed: "
						+ SvmReproducibility.SPLIT_SEED + " model seed: " + SvmReproducibility.MODEL_SEED);
				System.out.println(getLocalName()+" "+label+" train users: "+trainUsers.size()+" test users: "+testUsers.size());
				System.out.println(getLocalName()+" "+label+" train class counts: "+countUsersByFollowee(trainUsers));
				System.out.println(getLocalName()+" "+label+" test class counts: "+countUsersByFollowee(testUsers));
			}

			private Map<String,Integer> countUsersByFollowee(List<String> users)
			{
				Map<String,Integer> counts = new LinkedHashMap<String,Integer>();
				if (users == null)
				{
					return counts;
				}
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

			private void writeSvmBatchArffFiles(String trainPath, String testPath, String recommendPath, LinkedHashMap<String,LinkedHashMap<String,Double>> tfidfDocuments, TreeSet<String> uniqueTerms, SvmDataSplit split, String recUser) throws IOException
			{
				writeSvmBatchArffFile(trainPath, "trainingSet", split.trainUsers, tfidfDocuments, uniqueTerms);
				writeSvmBatchArffFile(testPath, "testSet", split.testUsers, tfidfDocuments, uniqueTerms);
				ArrayList<String> recommendUsers = new ArrayList<String>();
				recommendUsers.add(recUser);
				writeSvmBatchArffFile(recommendPath, "recommendSet", recommendUsers, tfidfDocuments, uniqueTerms);
			}

			private void writeSvmBatchArffFile(String filePath, String relationName, List<String> rowUsers, LinkedHashMap<String,LinkedHashMap<String,Double>> tfidfDocuments, TreeSet<String> uniqueTerms) throws IOException
			{
				Collection<String> orderedClasses =
						SvmReproducibility.sortedNonNullValues(followeeFollowers.keySet());
				try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath, false)))
				{
					SparseArffWriter.writeHeader(
							writer, relationName, uniqueTerms, orderedClasses);
					for (String currUser : rowUsers)
					{
						Map<String,Double> currDocTfidf = tfidfDocuments.get(currUser);
						if (currDocTfidf != null)
						{
							SparseArffWriter.writeRow(
									writer, currDocTfidf, uniqueTerms,
									userFollowee.get(currUser));
						}
					}
				}
			}

			private TreeMap<String,Double> trainAndPredictSvmForBatchUser(
					String trainPath,
					String testPath,
					String recommendPath,
					String recUser,
					int featureCount,
					int instanceCount,
					int trainingClassCount)
			{
				try
				{
					ArrayList<String> target = new ArrayList<String>();
					target.add(recUser);
					TreeMap<String,TreeMap<String,Double>> result =
							runExactSvmWorkerRecommendations(
									trainPath,
									testPath,
									recommendPath,
									target,
									featureCount,
									instanceCount - 1,
									trainingClassCount,
									(algorithmRec == SCALABLE_SVM
											? "scalable linear SVM target "
											: "exact SVM target ") + recUser);
					TreeMap<String,Double> userScores = result.get(recUser);
					System.out.println(getLocalName()+" "
							+ RecommendationModeSupport.algorithmName(algorithmRec)
							+ " scores for "
							+recUser+": "+userScores);
					return userScores;
				}
				catch (Exception e)
				{
					throw new IllegalStateException(
							RecommendationModeSupport.algorithmName(algorithmRec)
							+ " failed for target " + recUser + ".", e);
				}
			}

			private TreeMap<String,Double> buildFolloweeScoreMap(String predictedFollowee)
			{
				TreeMap<String,Double> userScore = new TreeMap<String,Double>();
				for (String followeeUser : followeeFollowers.keySet())
				{
					double followeeScore = 0.0;
					if (predictedFollowee != null && predictedFollowee.equals(followeeUser))
					{
						followeeScore = 1.0;
					}
					userScore.put(followeeUser, followeeScore);
				}
				return userScore;
			}

			private String sanitizeFileName(String value)
			{
				if (value == null)
				{
					return "unknown";
				}
				String safeValue = value.replaceAll("[^A-Za-z0-9._-]", "_");
				if (safeValue.length() == 0)
				{
					return "unknown";
				}
				return safeValue;
			}

			private void deleteTemporaryFile(String filePath)
			{
				if (filePath != null)
				{
					try
					{
						Files.deleteIfExists(new File(filePath).toPath());
					}
					catch (IOException e)
					{
						System.err.println(getLocalName()
								+" could not remove temporary file "+filePath);
					}
				}
			}

			private void scheduleInputDeliveryVerification(final String runId)
			{
				myAgent.addBehaviour(new WakerBehaviour(
						myAgent, INPUT_DELIVERY_GRACE_MILLIS)
				{
					private static final long serialVersionUID = 1L;

					protected void onWake()
					{
						if (!runLifecycle.isActive(runId)
								|| runLifecycle.isTextProcessingComplete()
								|| RecommendationRunAbortRegistry.isAborted(runId))
						{
							return;
						}
						handleAlgorithmFailure(new IllegalStateException(
								"Input delivery completed, but "
								+ getLocalName() + " retained only "
								+ tweetCount + " of " + tweetsToReceive
								+ " expected records after "
								+ INPUT_DELIVERY_GRACE_MILLIS
								+ " ms. The run was stopped before scoring."));
					}
				});
			}

			private Instances loadInstances(String filePath) throws IOException
			{
				try (BufferedReader reader = new BufferedReader(new FileReader(filePath)))
				{
					return new Instances(reader);
				}
			}

			private void handleAlgorithmFailure(Throwable failure)
			{
				NodeExecutionMonitor.failed(getLocalName(), failure);
				releaseActiveMlpLease();
				releaseActiveKMeansLease();
				releaseExactSvmPreparationState();
				releaseActiveSvmLease();
				nodeMLP = null;
				averagedMLP = null;
				trainMLP = null;
				testMLP = null;
				recMLP = null;
				System.err.println(getLocalName()+" recommendation stage failed: "
						+failure.getClass().getSimpleName()+": "+failure.getMessage());
				failure.printStackTrace();

				String message = "Recommendation stage failed on " + getLocalName()
						+ ": " + failure.getClass().getSimpleName()
						+ (failure.getMessage() == null
								? "" : ": " + failure.getMessage());
				RecommendationRunAbortRegistry.abort(
						runLifecycle.getActiveRunId(), message);
				ACLMessage failureMessage = new ACLMessage(ACLMessage.FAILURE);
				failureMessage.addReceiver(new AID("Starter Agent", AID.ISLOCALNAME));
				failureMessage.addReceiver(new AID("Organizing Agent1", AID.ISLOCALNAME));
				failureMessage.setConversationId(runLifecycle.getActiveRunId());
				failureMessage.setContent(message);
				failureMessage.setOntology("Recommendation Run Failed");
				send(failureMessage);

				if (myGui != null)
				{
					myGui.appendResult(getLocalName()
							+" failed safely. The run was stopped; no partial or zero-score "
							+"recommendations will be published.");
				}
			}

			private static class SvmDataSplit
			{
				private final ArrayList<String> trainUsers;
				private final ArrayList<String> testUsers;

				private SvmDataSplit(ArrayList<String> trainUsers, ArrayList<String> testUsers)
				{
					this.trainUsers = trainUsers;
					this.testUsers = testUsers;
				}
			}
	
			private String normalizeProcessedTweet(String tweetText)
			{
				String currentText = tweetText == null ? "" : tweetText;

			if (currentText.contains("Photo:"))
				currentText = currentText.substring(0,currentText.indexOf("Photo:"));

			if (currentText.contains("Photoset:"))
				currentText = currentText.substring(0,currentText.indexOf("Photoset:"));

			if (currentText.contains("RT @") && retweetedby_temp == RE_TWEETS)
				currentText="";
			else
				currentText = RETWEET_PATTERN.matcher(currentText).replaceAll("RT ");

			currentText = PUNCTUATION_PATTERN.matcher(currentText).replaceAll("");
			currentText = LINK_PATTERN.matcher(currentText).replaceAll(" ");

			if (hashtags_temp == HASH_TAGS)
				currentText = SPECIAL_CHARACTER_PATTERN.matcher(currentText).replaceAll(" ");

			currentText = currentText.toLowerCase();

			if (stopWordFlag_temp == STOP_WORDS)
				currentText = removeStopWords(currentText);

			currentText = currentText.trim();
			currentText = NON_ALPHA_PATTERN.matcher(currentText).replaceAll("");
			currentText = MULTI_SPACE_PATTERN.matcher(currentText).replaceAll(" ");
				currentText = currentText.trim();

				return currentText;
			}

			private int countNormalizedTokens(String normalizedText)
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

		private String removeStopWords(String currentText)
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

		private long countDocumentTerms(
				Map<String,? extends Map<String,Double>> documents)
		{
			long total = 0L;
			for (Map<String,Double> document : documents.values())
			{
				total += document.size();
			}
			return total;
		}

		private void writeDoc2VecUserDocumentFile()
		{
			BufferedWriter bf = null;
			String doc2vecDirName = "Dataset/424k/";
			File doc2vecDir = new File(doc2vecDirName);
			if (!doc2vecDir.exists())
			{
				doc2vecDir.mkdirs();
			}

			try
			{
				bf = new BufferedWriter(new FileWriter(doc2vecDirName+ "userdoc.txt"));
				for (Map.Entry<String,LinkedHashMap<String,Double>> entry : allUserDocuments.entrySet())
				{
					LinkedHashMap<String,Double> entry2 = entry.getValue();
					for (Map.Entry<String,Double> entry3 : entry2.entrySet())
					{
						bf.write(entry3.getKey()+ " ");
					}
					bf.write(entry.getKey());
					bf.newLine();
				}
				bf.flush();
			}
			catch (IOException e)
			{
				System.out.println("An error occurred.");
				e.printStackTrace();
			}
			finally
			{
				if (bf != null)
				{
					try
					{
						bf.close();
					}
					catch (Exception e)
					{
					}
				}
			}
		}
		
		private boolean recordTweetBatchFromUserAgent(ACLMessage msg)
		{
			Object batchObject;
			try
			{
				batchObject = msg.getContentObject();
			}
			catch (UnreadableException e)
			{
				e.printStackTrace();
				return false;
			}
			
			if (!(batchObject instanceof List<?>))
			{
				System.out.println(getLocalName()+" received invalid tweet batch payload from "+msg.getSender().getLocalName());
				return false;
			}
			
				boolean recordedAnyTweet = false;
				int primaryRecServer = -1;
				for (Object tweetObject : (List<?>) batchObject)
				{
					if (tweetObject instanceof String)
					{
						String tweetMessage = (String) tweetObject;
						if (tweetMessage.startsWith(TWEET_BATCH_PRIMARY_REC_SERVER_PREFIX))
						{
							try
							{
								primaryRecServer = Integer.parseInt(tweetMessage.substring(TWEET_BATCH_PRIMARY_REC_SERVER_PREFIX.length()).trim());
							}
							catch (NumberFormatException e)
							{
								primaryRecServer = -1;
							}
							continue;
						}
						recordedAnyTweet = recordTweetFromUserAgent(tweetMessage, primaryRecServer) || recordedAnyTweet;
					}
				}
			
			return recordedAnyTweet;
		}

		private void sendTweetBatchAcknowledgement(ACLMessage batchMessage)
		{
			if (batchMessage.getSender() == null)
			{
				return;
			}
			ACLMessage acknowledgement = batchMessage.createReply();
			acknowledgement.setPerformative(ACLMessage.INFORM);
			acknowledgement.setOntology(TWEET_BATCH_ACK_ONTOLOGY);
			acknowledgement.setConversationId(batchMessage.getConversationId());
			acknowledgement.setContent("Tweet batch retained");
			send(acknowledgement);
		}

		private boolean isProcessedTweetBatch(ACLMessage batchMessage)
		{
			return processedTweetBatches.wasProcessed(
					tweetBatchSender(batchMessage),
					batchMessage == null ? null : batchMessage.getReplyWith());
		}

		private void markTweetBatchProcessed(ACLMessage batchMessage)
		{
			processedTweetBatches.markProcessed(
					tweetBatchSender(batchMessage),
					batchMessage == null ? null : batchMessage.getReplyWith());
		}

		private String tweetBatchSender(ACLMessage batchMessage)
		{
			if (batchMessage == null || batchMessage.getSender() == null)
			{
				return null;
			}
			return batchMessage.getSender().getName();
		}
		
			private boolean recordTweetFromUserAgent(String tweetReceived)
			{
				return recordTweetFromUserAgent(tweetReceived, -1);
			}

			private boolean recordTweetFromUserAgent(String tweetReceived, int primaryRecServer)
			{
				if (tweetReceived == null)
				{
				return false;
			}
			
			String[] tweetFields = splitTweetMessage(tweetReceived);
			if (tweetFields.length < 5)
			{
				System.out.println(getLocalName()+" skipped malformed tweet message: "+tweetReceived);
				return false;
			}
			
				int totalTweetFromUser;
				long tweetIdReceived;
				try
				{
					totalTweetFromUser = Integer.parseInt(tweetFields[0]);
					tweetIdReceived = Long.valueOf(tweetFields[2]);
				}
				catch (NumberFormatException e)
				{
					System.out.println(getLocalName()+" skipped tweet message with invalid numeric fields: "+tweetReceived);
					return false;
				}
				
				String tweetUserReceived = tweetFields[1];
				String tweetFolloweeName = tweetFields[3];
				String tweetTextReceived = tweetFields[4];
				
				tweetCount++;
				if (tweetCount == 1)
					firstTweetTime = System.nanoTime();
				
				if (tweetUserReceived.equals("sageryereson"))
					System.out.println("sageryerson: "+tweetReceived);
				
				if (!userFollowee.containsKey(tweetUserReceived))
					userFollowee.put(tweetUserReceived,tweetFolloweeName);

				if (primaryRecServer > 0 && !userPrimaryRecServer.containsKey(tweetUserReceived))
				{
					userPrimaryRecServer.put(tweetUserReceived, primaryRecServer);
				}
				
				totalMessageBytes += tweetReceived.length() * 2;
				
				if (usersRec.contains(tweetUserReceived))
				{
					int userIndex = usersRec.indexOf(tweetUserReceived);
					usersRecTweetCountsReceived[userIndex]++;
					
					if (usersRecTweetCountsReceived[userIndex] == totalTweetFromUser)
					{
						ACLMessage msgLastTweetFromRecUser = new ACLMessage( ACLMessage.INFORM );
						msgLastTweetFromRecUser.addReceiver( new AID(tweetUserReceived+"-UserAgent", AID.ISLOCALNAME) );
						msgLastTweetFromRecUser.setPerformative( ACLMessage.INFORM );
						msgLastTweetFromRecUser.setConversationId(runLifecycle.getActiveRunId());
						msgLastTweetFromRecUser.setContent("Received Last Tweet");
						msgLastTweetFromRecUser.setOntology("Last Tweet Received From Rec Agent");
						send(msgLastTweetFromRecUser);
					}
				}
				
				if (!userRegisteredInRecAgent.contains(tweetUserReceived))
				{
					userRegisteredInRecAgent.add(tweetUserReceived);
				}

				try
				{
					aggregateReceivedTweet(tweetUserReceived, tweetTextReceived);
				}
				catch (Throwable failure)
				{
					handleAlgorithmFailure(failure);
					return false;
				}
				return true;
			}

			private void aggregateReceivedTweet(
					String tweetUserReceived,
					String tweetTextReceived)
			{
				long processingStart = System.nanoTime();
				try
				{
						String normalizedText =
								normalizeProcessedTweet(tweetTextReceived);
						if (countNormalizedTokens(normalizedText) < 3)
						{
							return;
						}

					usableTweetCount++;
					if (algorithmRec == SVM && exactSvmDiskBackedWaves)
					{
						appendExactSvmCorpusRecord(
								tweetUserReceived, normalizedText);
						exactSvmUsableUsers.add(tweetUserReceived);
						return;
					}

					aggregateNormalizedDocument(
							tweetUserReceived, normalizedText);
				}
				finally
				{
					streamingTextProcessingNanos +=
							System.nanoTime() - processingStart;
				}
			}

			private void aggregateNormalizedDocument(
					String tweetUserReceived,
					String normalizedText)
			{
				LinkedHashMap<String,Double> userDocument =
						allUserDocuments.get(tweetUserReceived);
				if (userDocument == null)
				{
					userDocument = new LinkedHashMap<String,Double>();
					allUserDocuments.put(tweetUserReceived, userDocument);
				}

				if (algorithmRec == Doc2Vec)
				{
					return;
				}

				int tokenStart = 0;
				while (tokenStart < normalizedText.length())
				{
					int spaceIndex = normalizedText.indexOf(' ', tokenStart);
					String token = spaceIndex < 0
							? normalizedText.substring(tokenStart)
							: normalizedText.substring(tokenStart, spaceIndex);
					allUniqueDocTerms.add(token);
					Double frequency = userDocument.get(token);
					userDocument.put(
							token,
							frequency == null
									? 1.0
									: frequency.doubleValue() + 1.0);
					if (spaceIndex < 0)
					{
						break;
					}
					tokenStart = spaceIndex + 1;
				}
			}

			private void appendExactSvmCorpusRecord(
					String user,
					String normalizedText)
			{
				try
				{
					if (exactSvmCorpusSpool == null)
					{
						exactSvmCorpusSpool = new ExactSvmCorpusSpool();
					}
					exactSvmCorpusSpool.append(user, normalizedText);
				}
				catch (IOException exception)
				{
					throw new IllegalStateException(
							"Could not spool the Exact-SVM corpus for "
							+ getLocalName() + ".", exception);
				}
			}

			private void finishExactSvmCorpusSpoolWriting()
			{
				if (!exactSvmDiskBackedWaves || exactSvmCorpusSpool == null)
				{
					return;
				}
				try
				{
					exactSvmCorpusSpool.finishWriting();
				}
				catch (IOException exception)
				{
					throw new IllegalStateException(
							"Could not finish the Exact-SVM corpus spool for "
							+ getLocalName() + ".", exception);
				}
			}

			private Collection<String> textProcessedUsers()
			{
				return algorithmRec == SVM && exactSvmDiskBackedWaves
						? exactSvmUsableUsers
						: allUserDocuments.keySet();
			}

			private long estimateExactSvmWaveReservationBytes()
			{
				return ExactSvmCorpusAdmission.profile(
						estimatedExactSvmNodeTermEntries,
						Math.max(1, exactSvmUsableUsers.size()),
						1)
						.getEstimatedPeakBytes();
			}

			private boolean hasTextProcessedUser(String user)
			{
				return algorithmRec == SVM && exactSvmDiskBackedWaves
						? exactSvmUsableUsers.contains(user)
						: allUserDocuments.containsKey(user);
			}

			private void materializeExactSvmCorpusSpool()
			{
				if (!exactSvmDiskBackedWaves)
				{
					return;
				}
				allUserDocuments.clear();
				allUserDocumentsTFIDF.clear();
				allTermsDocumentFreq.clear();
				allUniqueDocTerms.clear();
				if (exactSvmCorpusSpool == null)
				{
					return;
				}
				long materializationStart = System.nanoTime();
				try
				{
					exactSvmCorpusSpool.replay(
							new ExactSvmCorpusSpool.RecordConsumer()
							{
								public void accept(
										String user,
										String normalizedText)
								{
									aggregateNormalizedDocument(
											user, normalizedText);
								}
							});
					System.out.println(getLocalName()
							+" materialized its disk-backed Exact-SVM wave: "
							+exactSvmCorpusSpool.size()+" usable rows, estimated "
							+estimatedExactSvmNodeTermEntries
							+" retained term entries, "
							+exactSvmCorpusSpool.normalizedTextBytes()
							+" normalized text bytes.");
					completionTimeTextProcessing +=
							System.nanoTime() - materializationStart;
				}
				catch (IOException exception)
				{
					throw new IllegalStateException(
							"Could not replay the Exact-SVM corpus spool for "
							+ getLocalName() + ".", exception);
				}
				finally
				{
					closeExactSvmCorpusSpool();
				}
			}

		private String[] splitTweetMessage(String tweetReceived)
		{
			int firstTab = tweetReceived.indexOf('\t');
			if (firstTab >= 0)
			{
				int secondTab = tweetReceived.indexOf('\t', firstTab + 1);
				int thirdTab = secondTab >= 0 ? tweetReceived.indexOf('\t', secondTab + 1) : -1;
				int fourthTab = thirdTab >= 0 ? tweetReceived.indexOf('\t', thirdTab + 1) : -1;
				if (secondTab >= 0 && thirdTab >= 0 && fourthTab >= 0)
				{
					return new String[] {
						tweetReceived.substring(0, firstTab),
						tweetReceived.substring(firstTab + 1, secondTab),
						tweetReceived.substring(secondTab + 1, thirdTab),
						tweetReceived.substring(thirdTab + 1, fourthTab),
						tweetReceived.substring(fourthTab + 1)
					};
				}
			}
			return tweetReceived.split(" ", 5);
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

		private void testNeuralNetwork(NeuralNetwork nnet, DataSet testSet) {

			if (nnet == null || testSet == null || testSet.size() <= 0)
			{
				String message = getLocalName()
						+ " Legacy Neuroph MLP test skipped: no test rows.";
				System.out.println(message);
				if (myGui != null)
				{
					myGui.appendResult(message);
				}
				return;
			}

			List<DataSetRow> testSetRows = testSet.getRows();
			// System.out.println(getLocalName()+" followeeNames: "+Arrays.toString(followeeNames));
			// System.out.println(getLocalName()+" testSet Size: "+testSetRows.size());
			
			
			int correctlyClassified = 0;
			for (int i = 0; i < testSetRows.size(); i++)
			{
				double[] inputVector = testSetRows.get(i).getInput();
				nnet.setInput(inputVector);
				nnet.calculate();
				double[] networkOutput = nnet.getOutput();
				// System.out.print("Input: "+ Arrays.toString(inputVector) );
				// System.out.print(getLocalName()+" User: "+testSetUsers.get(i)+" Followee: "+userFollowee.get(testSetUsers.get(i)));
				// System.out.println(" Output: "+ Arrays.toString(networkOutput));
				
				int maxIndex = 0;
				maxIndex = findMaxIndex(networkOutput);
				// System.out.println(getLocalName()+" maxIndex: "+maxIndex);
				
				// System.out.println(getLocalName()+" followeeNameOutput: "+followeeNames[maxIndex]+" followeeNameExpected: "+userFollowee.get(testSetUsers.get(i)));
				String expectedFollowee = testSetUsers != null
						&& i < testSetUsers.size()
						? userFollowee.get(testSetUsers.get(i))
						: followeeNames[findIndexOfMaxValue(
								testSetRows.get(i).getDesiredOutput())];
				if (maxIndex >= 0
						&& maxIndex < followeeNames.length
						&& followeeNames[maxIndex].equals(expectedFollowee))
					correctlyClassified++;
				
			}

			// System.out.println();
			// System.out.println(getLocalName()+" Correctly classified: "+correctlyClassified+ " Total instances: "+testSetUsers.size());
			// System.out.println();
			if (myGui != null)
			{
				myGui.appendResult(getLocalName()+" Correctly classified: "+correctlyClassified+ " Total instances: "+testSetRows.size());
			}
		}
		
		 // Code added by Sepide 
		
		 /* private void recNeuralNetwork2(MultilayerPerceptron nnet, Instances recSet) {

			// Sepide List<DataSetRow> recSetRows = recSet.getRows();
			System.out.println(getLocalName()+" followeeNames: "+Arrays.toString(followeeNames));
			System.out.println(getLocalName()+ " recSet Size: "+recSet.size());
			
			// Added by Sepide
			BufferedReader recommendFile = readDataFile(recommendSetFilePath);
			Instances recommendInstances2 = null;
			
			        try{
						
						recommendInstances2 = new Instances(recommendFile);
					}
					catch (IOException e)
					{
						e.printStackTrace();
					}
					
			recommendInstances2.setClassIndex(recommendInstances2.numAttributes() - 1);
			Instances predictedLabelsRec2 = new Instances(recommendInstances2);
			
			double clsLabel2 = 0.0;
			Map<String,String> userPredictedFollowee2 = new LinkedHashMap<String,String>();
			String predictedClass2;
			MultilayerPerceptron mlp3 = new MultilayerPerceptron();
			mlp3.setLearningRate(LEARNING_RATE_MLP);
			mlp3.setMomentum(0.2);
			mlp3.setHiddenLayers("10");
			
			for (int i = 0; i < recommendInstances2.numInstances(); i++) {
						
						try {
							clsLabel2 = mlp3.classifyInstance(recommendInstances2.instance(i));
						}
						catch (Exception e)
						{
							e.printStackTrace();
						}
						
						     predictedLabelsRec2.instance(i).setClassValue(clsLabel2);
						
						if (i == recommendInstances2.numInstances()-1)
						{
							predictedClass2 = predictedLabelsRec2.instance(i).stringValue(data.numAttributes()-1);
			//				System.out.println(predictedLabels.instance(i).stringValue(data.numAttributes()-2));
							System.out.println(getLocalName()+" Recommended Class: "+predictedLabelsRec2.instance(i).stringValue(data.numAttributes()-1));
							userPredictedFollowee2.put(usersRec.get(i),predictedClass2);
						}
			}
			
			allUserScores = new TreeMap<String,TreeMap<String,Double>>();
			Map<String,Double> userScore1 = new TreeMap<String,Double>();
			double followeeScore = 0.0;
			
			for (String recUser : userPredictedFollowee2.keySet())
					{
						for (String followeeUser: followeeFollowers.keySet())
						{
							if (userPredictedFollowee2.get(recUser).equals(followeeUser))
								followeeScore = 1.0;
							else
								followeeScore = 0.0;
							
							userScore1.put(followeeUser,followeeScore);
						}
						allUserScores.put(recUser,(TreeMap<String,Double>)userScore1);
						userScore1 = new TreeMap<String,Double>();
					}
			
			/*Comment-out by Sepide for (int i = 0; i < recSet.size(); i++)
			{
				double[] inputVector = recSet.get(i).getInput();
				nnet.setInput(inputVector);
				nnet.calculate();
				double[] networkOutput = nnet.getOutput();
				// System.out.print("Input: "+ Arrays.toString(inputVector) );
				System.out.print(getLocalName()+" RecUser: "+usersRec.get(i)+" Followee: "+userFollowee.get(usersRec.get(i)));
				System.out.println(" Output: "+ Arrays.toString(networkOutput));
				
				for (int j = 0; j < networkOutput.length; j++)
				{
					if (networkOutput[j] < 0)
						followeeScore = 0.0;
					else
						followeeScore = networkOutput[j];
					
					userScore1.put(followeeNames[j],followeeScore);
				}
				
				allUserScores.put(usersRec.get(i),(TreeMap<String,Double>)userScore1);
				userScore1 = new TreeMap<String,Double>();
				
			}Comment-out by Sepide */ 

		//} 
		
		// End of code added by Sepide 
		
			private void writeDoc2VecCompatibilityPartFiles(File selectedDatasetFile, String doc2vecDirName, int numFiles, String specificUser) throws IOException
			{
				if (selectedDatasetFile == null)
				{
					throw new IOException("No selected dataset file is available for Doc2Vec splitting.");
				}
				if (numFiles < 1)
				{
					numFiles = 1;
				}

				List<BufferedWriter> writers = new ArrayList<BufferedWriter>();
				long rowsWritten = 0;
				long skippedRows = 0;
				int fileIndex = 0;

				try
				{
					for (int i = 0; i < numFiles; i++)
					{
						writers.add(new BufferedWriter(new FileWriter(doc2vecDirName + "part" + (i + 1) + "_" + ".txt")));
					}

					BufferedReader reader = new BufferedReader(new FileReader(selectedDatasetFile));
					try
					{
						String row;
						while ((row = reader.readLine()) != null)
						{
							String[] columns = row.split("\t", 6);
							if (columns.length < 6)
							{
								skippedRows++;
								continue;
							}

							String newRow = columns[5] + "\t" + columns[4];
							if (columns[4].equals(specificUser))
							{
								for (BufferedWriter writer : writers)
								{
									writer.write(newRow);
									writer.newLine();
									rowsWritten++;
								}
							}
							else
							{
								BufferedWriter writer = writers.get(fileIndex);
								writer.write(newRow);
								writer.newLine();
								rowsWritten++;
								fileIndex = (fileIndex + 1) % numFiles;
							}
						}
					}
					finally
					{
						reader.close();
					}
				}
				finally
				{
					IOException closeException = null;
					for (BufferedWriter writer : writers)
					{
						try
						{
							writer.close();
						}
						catch (IOException e)
						{
							if (closeException == null)
							{
								closeException = e;
							}
						}
					}
					if (closeException != null)
					{
						throw closeException;
					}
				}

				System.out.println(getLocalName()+" Doc2Vec compatibility split complete rowsWritten="+rowsWritten+" skippedRows="+skippedRows+" files="+numFiles);
			}
			
			private void recNeuralNetwork(NeuralNetwork nnet, DataSet recSet) {

			if (nnet == null || recSet == null)
			{
				throw new IllegalStateException(
						"Legacy Neuroph MLP cannot score recommendations because the model or recommendation set is missing.");
			}
			List<DataSetRow> recSetRows = recSet.getRows();
			if (usersRec == null || usersRec.size() < recSetRows.size())
			{
				throw new IllegalStateException(
						"Legacy Neuroph MLP recommendation rows do not match the selected target users.");
			}
			System.out.println(getLocalName()+" followeeNames: "+Arrays.toString(followeeNames));
			System.out.println(getLocalName()+ " recSet Size: "+recSetRows.size());
			
			allUserScores = new TreeMap<String,TreeMap<String,Double>>();
			Map<String,Double> userScore1 = new TreeMap<String,Double>();
			double followeeScore = 0.0;
			
			for (int i = 0; i < recSetRows.size(); i++)
			{
				double[] inputVector = recSetRows.get(i).getInput();
				nnet.setInput(inputVector);
				nnet.calculate();
				double[] networkOutput = nnet.getOutput();
				// System.out.print("Input: "+ Arrays.toString(inputVector) );
				System.out.print(getLocalName()+" RecUser: "+usersRec.get(i)+" Followee: "+userFollowee.get(usersRec.get(i)));
				System.out.println(" Output: "+ Arrays.toString(networkOutput));
				
				for (int j = 0; j < networkOutput.length; j++)
				{
					if (networkOutput[j] < 0)
						followeeScore = 0.0;
					else
						followeeScore = networkOutput[j];
					
					userScore1.put(followeeNames[j],followeeScore);
				}
				
				allUserScores.put(usersRec.get(i),(TreeMap<String,Double>)userScore1);
				userScore1 = new TreeMap<String,Double>();
				
			}

		}

	private double calculateSparseCosineScore(Map<String,Double> doc1, Map<String,Double> doc2)
	{
		if (doc1 == null || doc2 == null || doc1.isEmpty() || doc2.isEmpty())
		{
			return 0.0;
			}

			Map<String,Double> smallerDoc = doc1;
			Map<String,Double> largerDoc = doc2;
			if (doc2.size() < doc1.size())
			{
				smallerDoc = doc2;
				largerDoc = doc1;
			}

			double dotProduct = 0.0;
			for (Map.Entry<String,Double> entry : smallerDoc.entrySet())
			{
				Double largerValue = largerDoc.get(entry.getKey());
				if (largerValue != null)
				{
					dotProduct += entry.getValue() * largerValue;
				}
		}
		return dotProduct;
	}

	static double calculateCommonNeighborScore(
			Map<String,Double> doc1,
			Map<String,Double> doc2)
	{
		return SparseRecommendationMath.commonNeighborScore(doc1, doc2);
	}

	static double calculateSparseEuclideanDistance(
			Map<String,Double> doc1,
			Map<String,Double> doc2)
	{
		return SparseRecommendationMath.sparseEuclideanDistance(doc1, doc2);
	}

	static double calculateEuclideanSimilarityScore(
			Map<String,Double> doc1,
			Map<String,Double> doc2)
	{
		return SparseRecommendationMath.euclideanSimilarityScore(doc1, doc2);
	}

		private TreeMap<String,Double> keepTopCosSimScores(TreeMap<String,Double> scores, int limit)
		{
			if (scores == null || limit <= 0 || scores.size() <= limit)
			{
				return scores;
			}

			ArrayList<Map.Entry<String,Double>> sortedScores = new ArrayList<Map.Entry<String,Double>>(scores.entrySet());
			Collections.sort(sortedScores, new Comparator<Map.Entry<String,Double>>() {
				public int compare(Map.Entry<String,Double> first, Map.Entry<String,Double> second)
				{
					int scoreCompare = Double.compare(second.getValue(), first.getValue());
					if (scoreCompare != 0)
					{
						return scoreCompare;
					}
					return first.getKey().compareTo(second.getKey());
				}
			});

			TreeMap<String,Double> limitedScores = new TreeMap<String,Double>();
			for (int i = 0; i < sortedScores.size() && i < limit; i++)
			{
				Map.Entry<String,Double> entry = sortedScores.get(i);
				limitedScores.put(entry.getKey(), entry.getValue());
			}
				return limitedScores;
			}

			private boolean isExperimentalCandidateSchedulingEnabled(
					int scheduledAlgorithm)
			{
				RecommendationSchedulerPlan plan =
						RecommendationSchedulerRegistry.snapshot();
				return plan != null
						&& plan.allowsExperimentalSideEffects()
						&& numRecAgents > 1
						&& (scheduledAlgorithm == COS_SIM
								|| scheduledAlgorithm == CommonNeighbors);
			}

			private TreeMap<String,TreeMap<String,Double>> runExperimentalCandidateShardScheduling(
					String[] users,
					String[] usersForRec,
					boolean commonNeighbors,
					int localResultLimit)
			{
				TreeMap<String,TreeMap<String,Double>> scheduledScores =
						initializeTargetScoreRows(usersRec, users);
				int currentNode = Math.max(1, Integer.parseInt(nodeNumber));
				int virtualShardCount =
						NodeCapacityScheduler.virtualCandidateShardCount(
								numRecAgents,
								users == null ? 0 : users.length);
				String algorithmKey = commonNeighbors
						? "CommonNeighbors" : "Similarity";
				NodeCapacityProfile profile =
						NodeCapacityRegistry.profileForNode(currentNode);
				String queueDetail =
						NodeCapacityScheduler.virtualCandidateShardDetail(
								currentNode,
								numRecAgents,
								NodeCapacityScheduler.assignedVirtualCandidateShards(
										currentNode,
										numRecAgents,
										virtualShardCount),
								virtualShardCount);
				System.out.println(getLocalName()
						+ " experimental candidate-shard scheduler active: "
						+ queueDetail);
				NodeExecutionMonitor.modelRunning(
						getLocalName(),
						"Experimental " + algorithmKey
								+ " pull queue active: " + queueDetail);

				int claimedCount = 0;
				int scoredCandidates = 0;
				int deferredPolls = 0;
				while (true)
				{
					RecommendationWorkQueueRegistry.ShardClaim claim =
							RecommendationWorkQueueRegistry
									.claimExperimentalCandidateShard(
											runLifecycle.getActiveRunId(),
											algorithmKey,
											currentNode,
											numRecAgents,
											virtualShardCount);
					if (!claim.hasShard())
					{
						if (claim.isDeferred())
						{
							deferredPolls++;
							if (deferredPolls == 1 || deferredPolls % 20 == 0)
							{
								NodeExecutionMonitor.modelRunning(
										getLocalName(),
										"Waiting for experimental scheduler turn: "
												+ claim.getReason());
							}
							try
							{
								Thread.sleep(Math.min(
										500L,
										50L + deferredPolls * 10L));
							}
							catch (InterruptedException interrupted)
							{
								Thread.currentThread().interrupt();
								throw new IllegalStateException(
										"Experimental scheduler was interrupted.",
										interrupted);
							}
							continue;
						}
						break;
					}

					deferredPolls = 0;
					claimedCount++;
					int shardNumber = claim.getShardNumber();
					try
					{
						NodeCapacityScheduler.applyPerJobDelayOnly(
								profile,
								new NodeCapacityScheduler.CancellationCheck()
								{
									public void throwIfCancelled()
											throws InterruptedException
									{
										if (Thread.currentThread().isInterrupted())
										{
											throw new InterruptedException(
													"interrupted");
										}
									}
								});
						LiveWorkloadAdjustmentRegistry.applyClaimPacingDelay(
								runLifecycle.getActiveRunId());
						if (!RecommendationWorkQueueRegistry
								.isActiveExperimentalCandidateShard(
										runLifecycle.getActiveRunId(),
										algorithmKey,
										currentNode,
										numRecAgents,
										virtualShardCount,
										shardNumber))
						{
							NodeExecutionMonitor.modelRunning(
									getLocalName(),
									"Experimental " + algorithmKey
									+ " shard " + shardNumber
									+ " was drained before scoring; "
									+ "skipping stale claim.");
							continue;
						}
						TreeMap<String,TreeMap<String,Double>>
								shardScoreRows = initializeTargetScoreRows(
										usersRec,
										users);
						int shardScores = scoreExperimentalCandidateShard(
								shardScoreRows,
								users,
								usersForRec,
								shardNumber,
								virtualShardCount,
								commonNeighbors);
						if (!RecommendationWorkQueueRegistry
								.isActiveExperimentalCandidateShard(
										runLifecycle.getActiveRunId(),
										algorithmKey,
										currentNode,
										numRecAgents,
										virtualShardCount,
										shardNumber))
						{
							NodeExecutionMonitor.modelRunning(
									getLocalName(),
									"Experimental " + algorithmKey
									+ " shard " + shardNumber
									+ " completed after drain/requeue; "
									+ "discarded stale scores.");
							continue;
						}
						mergeExperimentalShardScores(
								scheduledScores,
								shardScoreRows);
						scoredCandidates += shardScores;
						RecommendationWorkQueueRegistry
								.completeExperimentalCandidateShard(
										runLifecycle.getActiveRunId(),
										algorithmKey,
										currentNode,
										numRecAgents,
										virtualShardCount,
										shardNumber);
						NodeExecutionMonitor.modelRunning(
								getLocalName(),
								"Finished experimental "
										+ algorithmKey
										+ " shard " + shardNumber
										+ "/" + virtualShardCount
										+ "; " + scoredCandidates
										+ " candidate scores so far.");
					}
					catch (InterruptedException interrupted)
					{
						RecommendationWorkQueueRegistry
								.failExperimentalCandidateShard(
										runLifecycle.getActiveRunId(),
										algorithmKey,
										currentNode,
										numRecAgents,
										virtualShardCount,
										shardNumber);
						Thread.currentThread().interrupt();
						throw new IllegalStateException(
								"Experimental scheduler was interrupted.",
								interrupted);
					}
					catch (RuntimeException failure)
					{
						RecommendationWorkQueueRegistry
								.failExperimentalCandidateShard(
										runLifecycle.getActiveRunId(),
										algorithmKey,
										currentNode,
										numRecAgents,
										virtualShardCount,
										shardNumber);
						throw failure;
					}
				}

				for (Map.Entry<String,TreeMap<String,Double>> entry
						: scheduledScores.entrySet())
				{
					if (numRecAgents > 1 && !isUserRankerFolloweeMode())
					{
						entry.setValue(keepTopCosSimScores(
								entry.getValue(),
								localResultLimit));
					}
				}
				NodeExecutionMonitor.modelRunning(
						getLocalName(),
						"Experimental " + algorithmKey
								+ " scheduler finished "
								+ claimedCount + " shard(s), "
								+ scoredCandidates + " candidate scores.");
				return scheduledScores;
			}

			private void mergeExperimentalShardScores(
					Map<String,TreeMap<String,Double>> destination,
					Map<String,TreeMap<String,Double>> shardScores)
			{
				if (destination == null || shardScores == null)
				{
					return;
				}
				for (Map.Entry<String,TreeMap<String,Double>> entry
						: shardScores.entrySet())
				{
					TreeMap<String,Double> sourceScores = entry.getValue();
					if (sourceScores == null || sourceScores.isEmpty())
					{
						continue;
					}
					TreeMap<String,Double> targetScores =
							destination.get(entry.getKey());
					if (targetScores == null)
					{
						targetScores = new TreeMap<String,Double>();
						destination.put(entry.getKey(), targetScores);
					}
					targetScores.putAll(sourceScores);
				}
			}

			private int scoreExperimentalCandidateShard(
					Map<String,TreeMap<String,Double>> scoreRows,
					String[] users,
					String[] usersForRec,
					int shardNumber,
					int virtualShardCount,
					boolean commonNeighbors)
			{
				if (scoreRows == null || users == null || usersForRec == null)
				{
					return 0;
				}
				int safeShardCount = Math.max(1, virtualShardCount);
				int startIndex = Math.max(0, shardNumber - 1);
				int scoresWritten = 0;
				for (int targetIndex = 0; targetIndex < usersForRec.length;
						targetIndex++)
				{
					String recUser = usersForRec[targetIndex];
					LinkedHashMap<String, Double> recDoc =
							allUserDocumentsTFIDF.get(recUser);
					TreeMap<String,Double> userScores = scoreRows.get(recUser);
					if (recDoc == null || userScores == null)
					{
						continue;
					}
					for (int candidateIndex = startIndex;
							candidateIndex < users.length;
							candidateIndex += safeShardCount)
					{
						String candidateUser = users[candidateIndex];
						if (candidateUser == null
								|| recUser.equals(candidateUser))
						{
							continue;
						}
						LinkedHashMap<String, Double> candidateDoc =
								allUserDocumentsTFIDF.get(candidateUser);
						double score = commonNeighbors
								? calculateCommonNeighborScore(recDoc, candidateDoc)
								: calculateSparseCosineScore(recDoc, candidateDoc);
						userScores.put(candidateUser, score);
						scoresWritten++;
					}
				}
				return scoresWritten;
			}

			static TreeMap<String,TreeMap<String,Double>> initializeTargetScoreRows(
					Collection<String> targetUsers, String[] users)
			{
				TreeMap<String,TreeMap<String,Double>> targetScores =
						new TreeMap<String,TreeMap<String,Double>>();
				if (targetUsers == null || users == null)
				{
					return targetScores;
				}
				LinkedHashSet<String> availableUsers =
						new LinkedHashSet<String>(Arrays.asList(users));
				for (String targetUser : targetUsers)
				{
					if (targetUser == null || !availableUsers.contains(targetUser)
							|| targetScores.containsKey(targetUser))
					{
						continue;
					}
					TreeMap<String,Double> userScores =
							new TreeMap<String,Double>();
					for (String candidateUser : users)
					{
						if (candidateUser != null
								&& !targetUser.equals(candidateUser))
						{
							userScores.put(candidateUser, 0.0);
						}
					}
					targetScores.put(targetUser, userScores);
				}
				return targetScores;
			}

			static void putScoreForTrackedTargets(
					Map<String,TreeMap<String,Double>> scoreRows,
					String firstUser,
					String secondUser,
					double score)
			{
				if (scoreRows == null)
				{
					return;
				}
				TreeMap<String,Double> firstRow = scoreRows.get(firstUser);
				if (firstRow != null)
				{
					firstRow.put(secondUser, score);
				}
				TreeMap<String,Double> secondRow = scoreRows.get(secondUser);
				if (secondRow != null)
				{
					secondRow.put(firstUser, score);
				}
			}

			private int getCosSimLocalResultLimit()
		{
			int requestedRecommendations = 0;
			if (myGui != null && myGui.recommendationField != null)
			{
				try
				{
					requestedRecommendations = Integer.parseInt(myGui.recommendationField.getText().trim());
				}
				catch (Exception e)
				{
					requestedRecommendations = 0;
				}
			}

			AlgorithmParameterSettings settings = algorithmSettings();
			int bufferedLimit = requestedRecommendations
					* settings.getSimilarityLocalTopMultiplier();
			if (bufferedLimit < settings.getSimilarityLocalTopMinimum())
			{
				bufferedLimit = settings.getSimilarityLocalTopMinimum();
			}
			return bufferedLimit;
		}
		
		private int findMaxIndex(double[] array)
		{
			int maxIndex = 0;
			double maxValue = array[0];
			for (int i = 0; i < array.length; i++)
			{
				// System.out.println("array["+i+"+]: "+array[i]+" maxValue: "+maxValue);
				if (array[i] > maxValue)
				{
					maxIndex = i;
					maxValue = array[i];
				}
					
			}
			return maxIndex;
		}
	}

	
	
	
	//Source from: http://stackoverflow.com/questions/2808535/round-a-double-to-2-decimal-places
	public static double round(double value, int places) {
		if (places < 0) throw new IllegalArgumentException();

		BigDecimal bd = new BigDecimal(value);
		bd = bd.setScale(places, RoundingMode.HALF_UP);
		return bd.doubleValue();
	}
	
	///Source from: https://stackoverflow.com/questions/11296490/assigning-hashmap-to-hashmap
	// public static <K,J,V> Map<K, Map<J, V>> deepCopyLHM(Map<K, Map<J, V>> original)
	// {
		// Map<K, Map<J, V>> copy;

		// iterate over the map copying values into new map
		// for(Map.Entry<K, Map<J, V>> entry : original.entrySet())
		// {
		   // copy.put(entry.getKey(), new HashMap<J, V>(entry.getValue()));
		// }

		// return copy;
	// }
	
	public static long convertMs(long nanoTimeDiff)
	{
		return nanoTimeDiff/1000000;
	}
	
	public static BufferedReader readDataFile(String filename) {
		BufferedReader inputReader = null;

		try {
			inputReader = new BufferedReader(new FileReader(filename));
		} catch (FileNotFoundException ex) {
			System.err.println("File not found: " + filename);
		}

		return inputReader;
	}

	public static Evaluation classify(Classifier model,
			Instances trainingSet, Instances testingSet) throws Exception {
		Evaluation evaluation = new Evaluation(trainingSet);

		model.buildClassifier(trainingSet);
		evaluation.evaluateModel(model, testingSet);

		return evaluation;
	}

	public static double calculateAccuracy(FastVector predictions) {
		double correct = 0;

		for (int i = 0; i < predictions.size(); i++) {
			NominalPrediction np = (NominalPrediction) predictions.elementAt(i);
			if (np.predicted() == np.actual()) {
				correct++;
			}
		}

		return 100 * correct / predictions.size();
	}

	protected void takeDown()
	{
		releaseActiveMlpLease();
		releaseActiveKMeansLease();
		releaseActiveSvmLease();
		closeExactSvmCorpusSpool();
		NodeExecutionMonitor.stopped(getLocalName());
		try {
			DFService.deregister(this);
			System.out.println(getLocalName()+" DEREGISTERED WITH THE DF");
			//doDelete();
		} catch (FIPAException e) {
			e.printStackTrace();
		}
	}

}
