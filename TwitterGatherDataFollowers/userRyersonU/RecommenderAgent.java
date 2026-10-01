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
import java.util.HashSet;
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
	//Matches whole URLs, including their punctuation, because links are now
	//stripped before punctuation is converted to spaces. The old pattern only
	//worked because punctuation had already been deleted, turning
	//"http://t.co/x" into the single run "httptcox".
	private static final double TEST_SET_PERCENT = 0.30;
	private static final double TRAIN_SET_PERCENT = 0.70;
	public static final int HIDDEN_NEURONS = 10;
	public static final double LEARNING_RATE_MLP = 0.1;
	/** Seed for the MLP row-presentation shuffle; fixed for reproducibility. */
	private static final long MLP_PRESENTATION_SEED = 1L;
	/** Seed of the legacy MLP's initial weights (Neuroph's own generator is unseeded). */
	private static final long MLP_WEIGHT_SEED = 0x4D4C50L;
	/**
	 * Epoch limit of the legacy MLP, so a maximum error the network cannot reach
	 * ends training instead of running forever; reported when it is hit.
	 */
	private static final int MLP_MAX_EPOCHS = Integer.getInteger("dsmp.mlp.maxEpochs", 10000).intValue();
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

	/** Constant-time stop-word lookup built once from {@link #stopWordsArray}. */
	private static final java.util.Set<String> STOP_WORD_SET =
			TweetTextNormalizer.stopWordSet(stopWordsArray);



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
	//Corpus-wide user-document count agreed by every recommender node.
	private int globalTotalDocuments=0;
	//Time spent waiting for other nodes at the corpus-wide TF-IDF barrier.
	private long completionTimeGlobalDfBarrier=0L;
	//Document-frequency counting time, excluding the barrier wait.
	private long completionTimeDocumentFrequency=0L;
	//Top-N captured once when the run starts, so scoring cannot drift if the
	//user edits the field while the run is in flight.
	private int requestedTopRecommendations=0;
	//Parsed node number; MIN_VALUE means not parsed yet.
	private int cachedNodeNumber=Integer.MIN_VALUE;
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
	private Map<String,Integer> followerIndex; //index of array for followers
	private MultiLayerPerceptron multiLayer;  // MLP for Cross Validation Sepide
	private MultilayerPerceptron mlp; //added by Sepide
	private NeuralNetwork averagedNN; //NN with averaged weights
	private RecommendationMemoryCoordinator.Lease activeKMeansLease;
	private RecommendationMemoryCoordinator.Lease activeSvmLease;
	private boolean exactSvmDiskBackedWaves;
	private long estimatedExactSvmNodeTermEntries;
	private ExactSvmCorpusSpool exactSvmCorpusSpool;
	private final LinkedHashSet<String> exactSvmUsableUsers =
			new LinkedHashSet<String>();
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

	/** Reads the requested top-N once, on the thread that starts the run. */
	private int readRequestedTopRecommendations()
	{
		if (myGui == null || myGui.recommendationField == null)
		{
			return 0;
		}
		try
		{
			return Integer.parseInt(myGui.recommendationField.getText().trim());
		}
		catch (Exception ignored)
		{
			return 0;
		}
	}

	private void resetForRecommendationRun(String runId)
	{
		//Drop any half-finished corpus-wide TF-IDF barrier from an aborted run so a
		//stale partial submission cannot block or corrupt the next one.
		GlobalDocumentFrequencyCoordinator.discard(runId);
		requestedTopRecommendations = readRequestedTopRecommendations();
		//Start this run's cluster dumps empty instead of appending to the last run's.
		Cluster.resetClusterOutputFiles(runId);
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
		globalTotalDocuments = 0;
		completionTimeGlobalDfBarrier = 0L;
		completionTimeDocumentFrequency = 0L;
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
		followerIndex = null;
		multiLayer = null;
		mlp = null;
		averagedNN = null;
		releaseActiveKMeansLease();
		releaseActiveSvmLease();
		closeExactSvmCorpusSpool();
		exactSvmUsableUsers.clear();
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

	/**
	 * True when the run's input is streamed with run-time routing: users can be
	 * moved between nodes, so this node's input ends with the delivery-complete
	 * notice rather than at a tweet count known in advance.
	 */
	private boolean runtimeControlledInputQueueActive()
	{
		SchedulingRunContext context = SchedulingRuntime.get(runLifecycle.getActiveRunId());
		return context != null && context.isStreamedInput();
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

	/**
	 * A message crossing this node's emulated link: ready at a wall-clock
	 * nanoTime, ordered by readiness (the up and down directions are separate
	 * links, so an outbound message never holds back an inbound one).
	 */
	private static final class LinkEvent implements Comparable<LinkEvent>
	{
		private final long readyAtNanos;
		private final long sequence;
		private final ACLMessage message;
		private final boolean outbound;

		private LinkEvent(long readyAtNanos, long sequence, ACLMessage message, boolean outbound)
		{
			this.readyAtNanos = readyAtNanos;
			this.sequence = sequence;
			this.message = message;
			this.outbound = outbound;
		}

		public int compareTo(LinkEvent other)
		{
			int byTime = Long.compare(readyAtNanos, other.readyAtNanos);
			return byTime != 0 ? byTime : Long.compare(sequence, other.sequence);
		}
	}

	/** Wire size of a message: envelope plus its content (serialized bytes or UTF-8 text). */
	static long messageBytes(ACLMessage message)
	{
		if (message == null)
		{
			return NetworkModel.MESSAGE_HEADER_BYTES;
		}
		long content = 0L;
		byte[] sequence = message.getByteSequenceContent();
		if (sequence != null)
		{
			content = sequence.length;
		}
		else if (message.getContent() != null)
		{
			content = PayloadSize.string(message.getContent()) - 2L;
		}
		String ontology = message.getOntology();
		return NetworkModel.MESSAGE_HEADER_BYTES + Math.max(0L, content)
				+ (ontology == null ? 0L : ontology.length());
	}

	private class RecommenderServiceBehaviour extends CyclicBehaviour {
		private static final long serialVersionUID = 1L;
		private final java.util.PriorityQueue<LinkEvent> linkQueue = new java.util.PriorityQueue<LinkEvent>();
		private long linkSequence;

		public RecommenderServiceBehaviour(Agent a) {
			super(a);
		}

		/**
		 * Holds this node at a phase boundary while the operator has it paused.
		 * Returns false when the run ended or failed while holding.
		 */
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
			catch (RuntimeException exception)
			{
				if (!RecommendationRunAbortRegistry.isAborted(runLifecycle.getActiveRunId()))
				{
					handleAlgorithmFailure(new IllegalStateException(
							"Stopped while holding at live workload phase boundary "
									+ phaseLabel,
							exception));
				}
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

		/**
		 * Handles one message. Everything this node computes while handling it
		 * is node work: in an emulated run it is slowed to this node's speed
		 * (nested work units and synchronization points settle their own share).
		 */
		public void action()
		{
			NodeEmulator emulator = activeEmulator();
			NodeEmulator.Span span = emulator == null ? null : emulator.begin();
			try
			{
				pumpLink(emulator);
			}
			finally
			{
				if (emulator != null)
				{
					try
					{
						emulator.end(span);
					}
					catch (RuntimeException runEnded)
					{
						//The run ended while this node was being slowed down.
					}
				}
			}
		}

		/** The active run's context when it emulates, else null. */
		private SchedulingRunContext emulatingContext()
		{
			SchedulingRunContext context = SchedulingRuntime.get(runLifecycle.getActiveRunId());
			return context != null && context.isEmulating()
					&& currentNodeIndex() <= context.getNodeCount() ? context : null;
		}

		/**
		 * This node's network link. In an emulated run every message to the
		 * node crosses its down link (latency plus its size over the link's
		 * bandwidth, behind the link's other traffic) and is handled when it
		 * has arrived, in arrival order; messages in flight overlap, as on a
		 * real link, so the node is blocked only by its own work. Messages the
		 * node sends cross its up link the same way (see {@link #sendOverLink}).
		 * Without emulation messages are handled as they arrive.
		 */
		private void pumpLink(NodeEmulator emulator)
		{
			SchedulingRunContext context = emulatingContext();
			ACLMessage arrived;
			while ((arrived = myAgent.receive()) != null)
			{
				long readyAt = System.nanoTime();
				if (context != null)
				{
					double posted = arrived.getPostTimeStamp() > 0L
							? Math.min(context.now(), context.clock().modelMsAtEpochMillis(
									arrived.getPostTimeStamp()))
							: context.now();
					double arrival = context.network().send(NetworkModel.HUB, currentNodeIndex(),
							messageBytes(arrived), posted);
					readyAt = context.clock().wallNanosAt(arrival);
					if (emulator != null)
					{
						emulator.noteLatency(1);
					}
				}
				linkQueue.add(new LinkEvent(readyAt, linkSequence++, arrived, false));
			}
			while (!linkQueue.isEmpty()
					&& linkQueue.peek().readyAtNanos <= System.nanoTime())
			{
				LinkEvent event = linkQueue.poll();
				if (event.outbound)
				{
					send(event.message);
				}
				else
				{
					handleMessage(event.message);
				}
			}
			if (linkQueue.isEmpty())
			{
				block();
			}
			else
			{
				long waitNanos = linkQueue.peek().readyAtNanos - System.nanoTime();
				block(Math.max(1L, (waitNanos + 999999L) / 1000000L));
			}
		}

		/**
		 * Sends a message across this node's up link: in an emulated run it
		 * reaches the coordinator after its link transfer (the node does not
		 * wait; sending overlaps with its work). The node's work so far is
		 * settled first, so a slow node's messages leave late.
		 */
		private void sendOverLink(ACLMessage message)
		{
			SchedulingRunContext context = emulatingContext();
			NodeEmulator emulator = activeEmulator();
			if (context == null || emulator == null)
			{
				send(message);
				return;
			}
			//Callers may reuse the message object; the link carries a copy.
			message = (ACLMessage)message.clone();
			long bytes = messageBytes(message);
			double arrival;
			try
			{
				arrival = emulator.sendToHub(bytes);
			}
			catch (RuntimeException runEnded)
			{
				send(message);
				return;
			}
			linkQueue.add(new LinkEvent(context.clock().wallNanosAt(arrival), linkSequence++, message, true));
		}

		/** Marks this node's entry into a pipeline stage in the run record. */
		private void stageStart(String stage)
		{
			SchedulingRunContext context = SchedulingRuntime.get(runLifecycle.getActiveRunId());
			if (context != null && currentNodeIndex() <= context.getNodeCount())
			{
				context.stageStart(currentNodeIndex(), stage);
			}
		}

		private void stageEnd(String stage)
		{
			SchedulingRunContext context = SchedulingRuntime.get(runLifecycle.getActiveRunId());
			if (context != null && currentNodeIndex() <= context.getNodeCount())
			{
				context.stageEnd(currentNodeIndex(), stage);
			}
		}

		private void handleMessage(ACLMessage msg)
		{
			if (!getUserRecList)
			{
				refreshRecommendationTargets();
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
					stageEnd("input");
					if (!waitForLivePhaseBoundary(
							"INPUT_BATCH -> TEXT_PREPROCESS_USER"))
					{
						return;
					}
					stageStart("text-aggregate");
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
					sendOverLink(msgMessagePassing);
					
					msgMessagePassing.setContent(String.valueOf(convertMs(lastTweetTime-firstTweetTime)));
					msgMessagePassing.setOntology("Message Passing Time");
					sendOverLink(msgMessagePassing);
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
							sendOverLink(stopUserQueryMsg);
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
						sendOverLink(removeUsersMessage);
						
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
					stageEnd("text-aggregate");
					sendOverLink(textProcessMessage);

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
				stageStart("features");
				NodeExecutionMonitor.buildingFeatures(
						getLocalName(),
						algorithmRec == Doc2Vec
								? "Preparing the shared document-vector model."
								: "Building TF-IDF and model-specific inputs.");

				try
				{
				//Corpus-wide TF-IDF is a barrier across every recommender node, so it
				//runs before any memory lease is taken. Waiting at the barrier while
				//holding a lease would let one node block here while another blocks
				//acquiring the capacity only the first can release.
				long documentFrequencyStart = System.nanoTime();

				System.out.println(getLocalName()+" Get Document Freq of Terms");
				int docFreq=0;
				//Users this node is the primary owner of; the unit of the corpus-wide
				//document count, so replicated targets are counted exactly once.
				int ownedDocumentCount=0;
				//Corpus-wide TF-IDF for every model except Doc2Vec (its own model) and
				//the batch Scalable SVM (its shared coordinator weights raw documents).
				//The exact SVM needs it in every mode: its models see the whole run.
				boolean calculateGlobalTfidf = algorithmRec != Doc2Vec
						&& !(algorithmRec == SCALABLE_SVM
								&& isSvmBatchMode()
								&& !isClassifierGuidedUserMode());
				//Get the document frequency of terms
				
				if (calculateGlobalTfidf)
				{
					for (String term : allUniqueDocTerms)
					{
						//Initialize allTermsDocumentFreq to 0
						allTermsDocumentFreq.put(term, 0);
					}

					for (String curName : allUserDocuments.keySet())
					{
						//Recommendation targets are replicated to every node, so only the
						//node that owns a user may contribute it to the corpus-wide
						//counts. Without this, a target would be counted once per node
						//and both the corpus size and its terms' document frequency would
						//grow with the node count, which is exactly the partition
						//dependence the shared barrier exists to remove.
						if (!ownsCandidate(curName))
						{
							continue;
						}
						ownedDocumentCount++;
						LinkedHashMap<String,Double> curDoc = allUserDocuments.get(curName);
						if (VERBOSE_TEXT_PROCESSING_DEBUG)
						{
							System.out.println("============================================");
						}
						//System.out.println(" Sepide testing if the document includes the users with their aggregated tweets" + allUserDocuments.get(curName));  // added by Sepide
						for (String docTerm : curDoc.keySet())
						{
							//The map is pre-seeded from allUniqueDocTerms, which for
							//distributed MLP is the controller's vocabulary. Treating a
							//term the controller did not record as a fresh zero keeps a
							//vocabulary mismatch from becoming a NullPointerException
							//mid-run; the shared normaliser makes that mismatch unlikely,
							//but the count stays correct either way.
							Integer knownDocFreq = allTermsDocumentFreq.get(docTerm);
							docFreq = knownDocFreq == null ? 0 : knownDocFreq.intValue();
							docFreq++;
							allTermsDocumentFreq.put(docTerm,docFreq);
						}
					}
				}
				//Document frequency and corpus size must describe the whole corpus,
				//not this node's slice, or IDF becomes a function of the partition and
				//scores merged from different nodes are not comparable.
				if (calculateGlobalTfidf)
				{
					//Timed separately: waiting for slower nodes is synchronization
					//cost, not TF-IDF work, and folding it into completionTimeTFIDF
					//would distort any speedup comparison across node counts.
					long globalDfStart = System.nanoTime();
					//A collective of the run: each node uploads its non-zero counts,
					//the coordinator sums them, and each node downloads the counts of
					//the terms it holds, all over the emulated links.
					NodeExecutionMonitor.waitingForSharedModel(
							getLocalName(),
							"Exchanging corpus-wide TF-IDF document frequencies with the other nodes.");
					GlobalDocumentFrequencyCoordinator.Totals corpusTotals =
							schedulingContext().collectives().allReduce(
									"tfidf/document-frequency",
									currentNodeIndex(),
									new GlobalDocumentFrequencyCoordinator.Share(
											allTermsDocumentFreq, ownedDocumentCount),
									-1L,
									new RunCollectives.Reducer<GlobalDocumentFrequencyCoordinator.Totals>()
									{
										public GlobalDocumentFrequencyCoordinator.Totals reduce(
												List<Object> shares)
										{
											return GlobalDocumentFrequencyCoordinator.combineShares(shares);
										}
									},
									GlobalDocumentFrequencyCoordinator.NEEDED_TERMS);
					allTermsDocumentFreq = new LinkedHashMap<String,Integer>(
							corpusTotals.getDocumentFrequency());
					globalTotalDocuments = corpusTotals.getTotalDocuments();
					completionTimeGlobalDfBarrier = System.nanoTime() - globalDfStart;
					System.out.println(getLocalName()
							+ " corpus-wide TF-IDF: totalDocuments="
							+ globalTotalDocuments
							+ " distinctTerms=" + allTermsDocumentFreq.size()
							+ " (localDocuments=" + allUserDocuments.size()
							+ ", ownedDocuments=" + ownedDocumentCount
							+ ", nodes=" + numRecAgents
							+ ", barrier wait "
							+ convertMs(completionTimeGlobalDfBarrier) + " ms)");
				}

				//DF counting and the vocabulary sync are TF-IDF work; the barrier wait
				//is not, and neither is the memory-lease wait that follows.
				completionTimeDocumentFrequency = System.nanoTime()
						- documentFrequencyStart - completionTimeGlobalDfBarrier;
				if (completionTimeDocumentFrequency < 0L)
				{
					completionTimeDocumentFrequency = 0L;
				}

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
					//The exact SVM's targets share one corpus-wide feature space.
					boolean targetSpecificPreparation = false;
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

				//Clock restarts here so a blocking memory-lease acquisition above is
				//not counted as TF-IDF compute time.
				startTimeTFIDF = System.nanoTime();

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
				//Local count is kept for telemetry; TF-IDF uses the corpus-wide count.
				totalDocuments = calculateGlobalTfidf
						? globalTotalDocuments : allUserDocuments.size();
				System.out.println("User Documents size (local): "
						+ allUserDocuments.size()
						+ " TF-IDF corpus size: " + totalDocuments);
				System.out.println(getLocalName()+" Calculating TF-IDF");

				double vectorMagnitude=0.0;
				boolean preserveRawDocuments = algorithmRec == SCALABLE_SVM;
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
						//Null-safe for the same reason as the counting pass: a term with
						//no corpus-wide entry contributes nothing rather than throwing.
						Integer corpusDocFreq = allTermsDocumentFreq.get(docTerm);
						df = corpusDocFreq == null ? 0.0 : corpusDocFreq.doubleValue();
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
				//Report TF-IDF compute time only; the barrier wait is reported above.
				completionTimeTFIDF = (endTimeTFIDF - startTimeTFIDF)
						+ completionTimeDocumentFrequency;
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
				if (algorithmRec != SCALABLE_SVM)
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
				stageEnd("features");
				if (!waitForLivePhaseBoundary(
						"FEATURE_BUILD -> MODEL_STAGE"))
				{
					return;
				}
				stageStart("model");
				//Rows are cut at the requested top-N, so every node must use the same value.
				requestedTopRecommendations = schedulingContext().agree(
						"top-recommendations", requestedTopRecommendations);
				NodeExecutionMonitor.modelRunning(
						getLocalName(),
						"Executing "
								+ RecommendationModeSupport.algorithmName(algorithmRec)
								+ " as work units of the run's scheduler.");

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

					System.out.println(getLocalName()+ "CALCULATING COS-SIM SCORES");
					allUserScores = runCandidateScoring(false);

					endTimeAlgorithm = System.nanoTime();
					completionTimeAlgorithm = endTimeAlgorithm - startTimeAlgorithm;

					//Output for cosSIM
					textprocessing_wb_or_tfidf_Data.add("CosSim=TP+TFIDF+CosSim" + "\t" + agentName + "\t" + usableTweetCount + "\t" + tweetCount + "\t" + completionTimeTextProcessing + "\t" + completionTimeTFIDF    + "\t" + completionTimeAlgorithm + "\t" + System.getProperty("line.separator"));
					System.out.println(agentName+"- Tweets Received: " + tweetCount + " Processed: " + usableTweetCount + " TP: " + convertMs(completionTimeTextProcessing) + " TFIDF: " + convertMs(completionTimeTFIDF) + " CosSim: " + convertMs(completionTimeAlgorithm) + " Total: " + convertMs(completionTimeTextProcessing+completionTimeTFIDF+completionTimeAlgorithm));
//					myGui.appendResult(agentName+"\nTweets Received: " + tweetCount + " Processed: " + usableTweetCount + " TP:" + round(completionTimeTextProcessing/1000000.00,2) + "ms TFIDF:" + round(completionTimeTFIDF/1000000.00,2) + "ms CosSim:" + round(completionTimeAlgorithm/1000000.00,2) + "ms Total:" + round((completionTimeTextProcessing+completionTimeTFIDF+completionTimeAlgorithm)/1000000.00,2)+"ms");
					myGui.appendResult(agentName+"\nTweets Received: " + tweetCount + " Processed: " + usableTweetCount + " TP: " + convertMs(completionTimeTextProcessing) + " ms TFIDF: " + convertMs(completionTimeTFIDF) + " ms CosSim: " + convertMs(completionTimeAlgorithm) + " ms Total: " + convertMs(completionTimeTextProcessing+completionTimeTFIDF+completionTimeAlgorithm)+" ms");
				
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

						System.out.println(getLocalName()
								+ " CALCULATING COMMON-NEIGHBOR SCORES");
						allUserScores = runCandidateScoring(true);

						endTimeAlgorithm = System.nanoTime();
						completionTimeAlgorithm = endTimeAlgorithm - startTimeAlgorithm;

						textprocessing_wb_or_tfidf_Data.add(
								"CommonNeighbors=TP+TFIDF+CommonNeighbors"
										+ "\t" + agentName
										+ "\t" + usableTweetCount + "\t" + tweetCount
										+ "\t" + completionTimeTextProcessing
										+ "\t" + completionTimeTFIDF
										+ "\t" + completionTimeAlgorithm
										+ "\t" + System.getProperty("line.separator"));
						System.out.println(agentName
								+"- Tweets Received: " + tweetCount + " Processed: " + usableTweetCount
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
								+"\nTweets Received: " + tweetCount + " Processed: " + usableTweetCount
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
								runTemporalGraphFoundation();

						endTimeAlgorithm = System.nanoTime();
						completionTimeAlgorithm =
								endTimeAlgorithm - startTimeAlgorithm;
						textprocessing_wb_or_tfidf_Data.add(
								"TGF=TP+TFIDF+TemporalGraphFoundation"
										+ "\t" + agentName
										+ "\t" + usableTweetCount + "\t" + tweetCount
										+ "\t" + completionTimeTextProcessing
										+ "\t" + completionTimeTFIDF
										+ "\t" + completionTimeAlgorithm
										+ "\t" + System.getProperty("line.separator"));
						String timing = agentName
								+ "- Tweets Received: " + tweetCount + " Processed: " + usableTweetCount
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
					//k-means on aggregated tweets as document vectors
					AlgorithmParameterSettings algorithmSettings = algorithmSettings();
					startTimeAlgorithm = System.nanoTime();
					System.out.println(getLocalName()
							+ " Calculating exact distributed K-Means: farthest-first seeding, clusterSeed="
							+ algorithmSettings.getKMeansSeed());
					SchedulingRunContext kMeansContext = schedulingContext();
					DistributedKMeans.Result kMeansResult = DistributedKMeans.cosine(
							kMeansContext.collectives(),
							"kmeans",
							currentNodeIndex(),
							ownedDocuments(),
							new ArrayList<String>(allUserDocumentsTFIDF.keySet()),
							distinctTargets(),
							allUserDocumentsTFIDF,
							algorithmSettings.getKMeansClusters(),
							algorithmSettings.getKMeansMaxIterations(),
							algorithmSettings.getKMeansSeed());
					allUserScores = kMeansResult.getScores();
					System.out.println(getLocalName() + " K-Means clusters="
							+ kMeansResult.getClusters()
							+ " iterations=" + kMeansResult.getIterations()
							+ " converged=" + kMeansResult.isConverged()
							+ " sizes=" + java.util.Arrays.toString(kMeansResult.getClusterSizes()));

					
					


					endTimeAlgorithm = System.nanoTime();
					completionTimeAlgorithm = endTimeAlgorithm - startTimeAlgorithm;						

					//Output for K-means	   					
					textprocessing_wb_or_tfidf_Data.add("K-means=TP+TFIDF+K-means" + "\t" + agentName + "\t" + usableTweetCount + "\t" + tweetCount + "\t" + completionTimeTextProcessing + "\t" + completionTimeTFIDF    + "\t" + completionTimeAlgorithm + "\t" + System.getProperty("line.separator"));
//					System.out.println(agentName+"- Tweets Received: " + tweetCount + " Processed: " + usableTweetCount + " TP:" + convertMs(completionTimeTextProcessing) + "ms TFIDF:" + convertMs(completionTimeTFIDF) + "ms K-means:" + convertMs(completionTimeAlgorithm) + "ms Total:" + convertMs(completionTimeTextProcessing+completionTimeTFIDF+completionTimeAlgorithm)+"ms");
					System.out.println("Mapper"+nodeNumber+"- Tweets Received: " + tweetCount + " Processed: " + usableTweetCount + " TP: " + convertMs(completionTimeTextProcessing) + " ms TFIDF: " + convertMs(completionTimeTFIDF) + " ms Reducer"+ nodeNumber+ " K-means: " + convertMs(completionTimeAlgorithm) + " ms Total: " + convertMs(completionTimeTextProcessing+completionTimeTFIDF+completionTimeAlgorithm)+" ms");
//					myGui.appendResult(agentName+"\nTweets Received: " + tweetCount + " Processed: " + usableTweetCount + " TP:" + round(completionTimeTextProcessing/1000000.00,2) + "ms TFIDF:" + round(completionTimeTFIDF/1000000.00,2) + "ms K-means:" + round(completionTimeAlgorithm/1000000.00,2) + "ms Total:" + round((completionTimeTextProcessing+completionTimeTFIDF+completionTimeAlgorithm)/1000000.00,2)+"ms");
					myGui.appendResult("Mapper"+nodeNumber+"- Tweets Received: " + tweetCount + " Processed: " + usableTweetCount + " TP: " + convertMs(completionTimeTextProcessing) + " ms TFIDF: " + convertMs(completionTimeTFIDF) + " ms Reducer"+ nodeNumber+ " K-means: " + convertMs(completionTimeAlgorithm) + " ms Total: " + convertMs(completionTimeTextProcessing+completionTimeTFIDF+completionTimeAlgorithm)+" ms");

				}
				
				// K-means clustering with Euclidean Distance added by Sepide
				
				else if (algorithmRec == K_MEANSEUCLIDEAN)
				{
					AlgorithmParameterSettings algorithmSettings = algorithmSettings();
					startTimeAlgorithm = System.nanoTime();
					System.out.println("Calculating exact distributed Euclidean K-means");
					SchedulingRunContext kMeansContext = schedulingContext();
					DistributedKMeans.Result result = DistributedKMeans.euclidean(
							kMeansContext.collectives(),
							"kmeans-euclidean",
							currentNodeIndex(),
							ownedDocuments(),
							new ArrayList<String>(allUserDocumentsTFIDF.keySet()),
							distinctTargets(),
							algorithmSettings.getKMeansClusters(),
							algorithmSettings.getKMeansMaxIterations());
					allUserScores = result.getScores();
					System.out.println(getLocalName()
							+ " Euclidean K-means requestedClusters="
							+ algorithmSettings.getKMeansClusters()
							+ " effectiveClusters=" + result.getClusters()
							+ " iterations=" + result.getIterations()
							+ " converged=" + result.isConverged()
							+ " sizes=" + java.util.Arrays.toString(result.getClusterSizes()));

					endTimeAlgorithm = System.nanoTime();
					completionTimeAlgorithm = endTimeAlgorithm - startTimeAlgorithm;

					textprocessing_wb_or_tfidf_Data.add(
							"K-meansEuclidean=TP+TFIDF+K-meansEuclidean"
									+ "\t" + agentName
									+ "\t" + usableTweetCount + "\t" + tweetCount
									+ "\t" + completionTimeTextProcessing
									+ "\t" + completionTimeTFIDF
									+ "\t" + completionTimeAlgorithm
									+ "\t" + System.getProperty("line.separator"));
					System.out.println("Mapper" + nodeNumber
							+ "- Tweets Received: " + tweetCount + " Processed: " + usableTweetCount
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
							+ "- Tweets Received: " + tweetCount + " Processed: " + usableTweetCount
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
						else
						{
							startTimeAlgorithm = System.nanoTime();
							allUserScores = runExactSvm();
							endTimeAlgorithm = System.nanoTime();
							completionTimeAlgorithm =
									endTimeAlgorithm - startTimeAlgorithm;

							String svmName = RecommendationModeSupport.algorithmName(
									algorithmRec);
							textprocessing_wb_or_tfidf_Data.add(
									svmName + "=TP+TFIDF+" + svmName + "\t" + agentName
									+ "\t" + usableTweetCount + "\t" + tweetCount + "\t"
									+ completionTimeTextProcessing + "\t"
									+ completionTimeTFIDF + "\t"
									+ completionTimeAlgorithm + "\t"
									+ System.getProperty("line.separator"));
							String timing = "Mapper" + nodeNumber
									+ "- Tweets Received: " + tweetCount + " Processed: " + usableTweetCount
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
					//The per-run value, identical on every node: the exact global
					//top-n depends on all nodes cutting at the same n.
					int topn = Math.max(1, requestedTopRecommendations);
					if (isUserRankerFolloweeMode())
					{
						topn = Math.max(topn, datasetUserFollowee.size());
					}
					allUserScores = runDoc2VecBatchRecommendations(topn);
					endTimeAlgorithm = System.nanoTime();
					completionTimeAlgorithm = endTimeAlgorithm - startTimeAlgorithm;

					System.out.println("Mapper"+nodeNumber+"- Tweets Received: " + tweetCount + " Processed: " + usableTweetCount + " TP: " + convertMs(completionTimeTextProcessing) + " ms Reducer"+ nodeNumber+ " Doc2Vec Batch: " + convertMs(completionTimeAlgorithm) + " ms Total: " + convertMs(completionTimeTextProcessing+completionTimeAlgorithm)+" ms");
					myGui.appendResult("Mapper"+nodeNumber+"- Tweets Received: " + tweetCount + " Processed: " + usableTweetCount + " TP: " + convertMs(completionTimeTextProcessing) + " ms Reducer"+ nodeNumber+ " Doc2Vec Batch: " + convertMs(completionTimeAlgorithm) + " ms Total: " + convertMs(completionTimeTextProcessing+completionTimeAlgorithm)+" ms");
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
					startTimeAlgorithm = System.nanoTime();
					allUserScores = runExactMlp();
					endTimeAlgorithm = System.nanoTime();
					completionTimeAlgorithm = endTimeAlgorithm - startTimeAlgorithm;
					myGui.appendResult("Completion time: " + convertMs(completionTimeAlgorithm));
					textprocessing_wb_or_tfidf_Data.add("MLP=TP+TFIDF+MLP" + "\t" + agentName
							+ "\t" + usableTweetCount + "\t" + tweetCount
							+ "\t" + completionTimeTextProcessing
							+ "\t" + completionTimeTFIDF
							+ "\t" + completionTimeAlgorithm
							+ "\t" + System.getProperty("line.separator"));
					String timing = "Mapper" + nodeNumber
							+ "- Tweets Received: " + tweetCount + " Processed: " + usableTweetCount
							+ " TP: " + convertMs(completionTimeTextProcessing)
							+ " ms TFIDF: " + convertMs(completionTimeTFIDF)
							+ " ms Reducer" + nodeNumber + " MLP: " + convertMs(completionTimeAlgorithm)
							+ " ms MLP Train: " + convertMs(completionTimeTrain)
							+ " ms MLP Test: " + convertMs(completionTimeTest)
							+ " ms Total: " + convertMs(completionTimeTextProcessing
									+ completionTimeTFIDF + completionTimeAlgorithm)
							+ " ms";
					System.out.println(timing);
					myGui.appendResult(timing);
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
					stageEnd("model");
					sendOverLink(msg7);

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
			stageStart("publish");
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
				//Each target is scored on the one node that committed it; the other
				//nodes do not hold it, which is not an error.
				Map<String,String> errors = buildMissingScoreErrorsForHeldTargets(
						allUserScores,
						"Temporal Graph Foundation found no positive text, graph, or temporal candidates.");
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
				//The algorithm's own output (users for the rankers, followee
				//accounts for the classifiers) takes the same structured path as
				//every converted mode: one merge, one ranking rule, and a result
				//every target receives and the run's evaluation scores. Each
				//candidate's score comes from exactly one node, so MAX keeps it
				//unchanged; a node that holds no candidate of a target is not an
				//error, so missing scores are left for the merge to judge.
				RecommendationCandidateType nativeType =
						RecommendationModeSupport.nativeCandidateType(algorithmRec);
				content = RecommendationScorePayload.directWithDetails(
						recommendationConfig.getTargetMode(),
						nativeType == null ? RecommendationCandidateType.USER : nativeType,
						RecommendationMergePolicy.MAX,
						usersRec,
						allUserScores,
						null,
						Collections.<String,String>emptyMap());
				toMergeMsg.setOntology("Merge Recommendation Scores V2");
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
				//The score maps cross the node's up link to the organizer.
				sendOverLink(toMergeMsg);
				stageEnd("publish");
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
						if (!ownsCandidate(candidate))
						{
							continue;
						}
						String followee = getCandidateFollowee(candidate);
						if (followee == null)
						{
							continue;
						}
						double similarity = calculateUnitVectorCosine(
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

		/** Like {@link #buildMissingScoreErrors}, for the targets this node holds scores for. */
		private Map<String,String> buildMissingScoreErrorsForHeldTargets(
				Map<String,? extends Map<?,Double>> scores,
				String message)
		{
			Map<String,String> errors = new TreeMap<String,String>();
			if (scores == null)
			{
				return errors;
			}
			for (Map.Entry<String,? extends Map<?,Double>> row : scores.entrySet())
			{
				boolean hasPositive = false;
				if (row.getValue() != null)
				{
					for (Double score : row.getValue().values())
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
					errors.put(row.getKey(), message);
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
	
	// End of methods added by Sepide 
		
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

		/**
		 * Deterministic Fisher-Yates over a Neuroph {@link DataSet}'s rows.
		 *
		 * DataSet.shuffle() uses an unseeded shuffle, which would make every run
		 * differ; this keeps the presentation random but reproducible.
		 */
		private void shuffleTrainingRows(DataSet dataSet, long seed)
		{
			if (dataSet == null || dataSet.size() < 2)
			{
				return;
			}
			List<DataSetRow> rows = dataSet.getRows();
			java.util.Random random = new java.util.Random(seed);
			for (int i = rows.size() - 1; i > 0; i--)
			{
				int j = random.nextInt(i + 1);
				DataSetRow swap = rows.get(i);
				rows.set(i, rows.get(j));
				rows.set(j, swap);
			}
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

			// ================================================================
			// Scheduled model stages. Every stage below produces, for any node
			// count, placement, capacity profile, live control and failure
			// pattern, the recommendations one node holding the whole corpus
			// would produce: work is cut into units of a phase of the run's
			// scheduling context, every unit is committed exactly once, and
			// what needs the whole corpus is combined through the run's
			// collectives in a fixed order.
			// ================================================================

			/** This run's scheduling context; a run without one gets the original DSMP baseline. */
			private SchedulingRunContext schedulingContext()
			{
				SchedulingRunContext context = SchedulingRuntime.getOrCreateBaseline(
						runLifecycle.getActiveRunId(),
						Math.max(1, numRecAgents),
						algorithmRec);
				if (context.getNodeCount() != Math.max(1, numRecAgents)
						|| currentNodeIndex() > context.getNodeCount())
				{
					throw new IllegalStateException("Run " + context.getRunId() + " was planned for "
							+ context.getNodeCount() + " node(s), but " + getLocalName()
							+ " belongs to a run of " + numRecAgents + ".");
				}
				return context;
			}

			/** This node's emulator in the active run, or null before the run is registered. */
			private NodeEmulator activeEmulator()
			{
				SchedulingRunContext context = SchedulingRuntime.get(runLifecycle.getActiveRunId());
				int node = currentNodeIndex();
				return context == null || node > context.getNodeCount() ? null : context.emulator(node);
			}

			/** Targets in their configured order, each once. */
			private List<String> distinctTargets()
			{
				return new ArrayList<String>(new LinkedHashSet<String>(
						usersRec == null ? Collections.<String>emptyList() : usersRec));
			}

			/** TF-IDF vectors of the users this node owns; every user has exactly one owner. */
			private LinkedHashMap<String,LinkedHashMap<String,Double>> ownedDocuments()
			{
				LinkedHashMap<String,LinkedHashMap<String,Double>> owned =
						new LinkedHashMap<String,LinkedHashMap<String,Double>>();
				for (Map.Entry<String,LinkedHashMap<String,Double>> entry : allUserDocumentsTFIDF.entrySet())
				{
					if (ownsCandidate(entry.getKey()))
					{
						owned.put(entry.getKey(), entry.getValue());
					}
				}
				return owned;
			}

			/** This node's share of the run's corpus: its owned vectors and their followees. */
			private GlobalCorpus.Contribution ownedCorpusContribution()
			{
				LinkedHashMap<String,LinkedHashMap<String,Double>> owned = ownedDocuments();
				LinkedHashMap<String,String> labels = new LinkedHashMap<String,String>();
				for (String user : owned.keySet())
				{
					String followee = userFollowee.get(user);
					if (followee != null && followee.length() > 0)
					{
						labels.put(user, followee);
					}
				}
				return new GlobalCorpus.Contribution(owned, labels);
			}

			private int totalLanes(SchedulingRunContext context)
			{
				int lanes = 0;
				for (int node = 1; node <= context.getNodeCount(); node++)
				{
					lanes += Math.max(1, context.lanesOf(node));
				}
				return lanes;
			}

			// ---------------------------------------------------- Similarity, Common Neighbors

			/**
			 * Similarity or Common-Neighbors scores of every target against every
			 * candidate of the run. The candidates are cut into shards homed on
			 * their owners and scheduled as work units; each unit's scores are
			 * committed once, and the rows each node publishes are its part of the
			 * exact global top L (every score in followee-projection mode). The
			 * merged result is therefore the same for every node count, placement
			 * and schedule.
			 */
			private TreeMap<String,TreeMap<String,Double>> runCandidateScoring(
					final boolean commonNeighbors)
			{
				final SchedulingRunContext context = schedulingContext();
				final int node = currentNodeIndex();
				final String phaseKey = RecommendationWorkQueueRegistry.CANDIDATES;
				final List<String> targets = distinctTargets();
				final int targetCount = Math.max(1, targets.size());
				final int lanes = totalLanes(context);
				final LinkedHashMap<String,LinkedHashMap<String,Double>> owned = ownedDocuments();
				final CandidateShardPlan plan = context.collectives().allReduce(
						phaseKey + "/plan",
						node,
						owned,
						CandidateShardPlan.layoutBytes(owned),
						new RunCollectives.Reducer<CandidateShardPlan>()
						{
							@SuppressWarnings("unchecked")
							public CandidateShardPlan reduce(List<Object> contributionsByNode)
							{
								List<Map<String,? extends Map<String,Double>>> byNode =
										new ArrayList<Map<String,? extends Map<String,Double>>>();
								for (Object contribution : contributionsByNode)
								{
									byNode.add((Map<String,? extends Map<String,Double>>)contribution);
								}
								return new CandidateShardPlan(byNode, targetCount, lanes);
							}
						},
						CandidateShardPlan.LAYOUT);
				WorkPhase phase = context.openPhase(
						phaseKey,
						(commonNeighbors ? "Common-Neighbors" : "Similarity") + " candidate shards",
						new SchedulingRunContext.UnitSupplier()
						{
							public List<WorkUnitSpec> units()
							{
								return plan.units();
							}
						});
				final int limit = isUserRankerFolloweeMode() ? 0 : getCosSimLocalResultLimit();
				//Corpus-wide term degrees (users per term), the same on every node.
				final Map<String,Integer> termDegrees = allTermsDocumentFreq;
				final String linkVariant = algorithmSettings().getCommonNeighborsVariant();
				final LinkedHashMap<String,Map<String,Double>> targetDocuments =
						new LinkedHashMap<String,Map<String,Double>>();
				final TreeMap<String,TreeMap<String,Double>> rows =
						new TreeMap<String,TreeMap<String,Double>>();
				for (String target : targets)
				{
					Map<String,Double> document = allUserDocumentsTFIDF.get(target);
					if (document != null)
					{
						targetDocuments.put(target, document);
						rows.put(target, new TreeMap<String,Double>());
					}
				}
				NodeWorkRunner.Stats stats = NodeWorkRunner.run(
						context,
						phase,
						node,
						new NodeWorkRunner.UnitExecutor()
						{
							public NodeWorkRunner.UnitOutput execute(
									WorkDispatcher.Assignment assignment, int lane)
							{
								TreeMap<String,TreeMap<String,Double>> unitRows =
										new TreeMap<String,TreeMap<String,Double>>();
								List<String> candidates = plan.candidatesOf(assignment.getUnitId());
								for (Map.Entry<String,Map<String,Double>> target : targetDocuments.entrySet())
								{
									//A revoked shard (drain, crash) stops between targets.
									UnitCancellation.check();
									TreeMap<String,Double> row = new TreeMap<String,Double>();
									for (String candidate : candidates)
									{
										if (candidate.equals(target.getKey()))
										{
											continue;
										}
										Map<String,Double> candidateDocument = plan.vectorOf(candidate);
										row.put(candidate, commonNeighbors
												? SparseRecommendationMath.linkPredictionScore(
														target.getValue(), candidateDocument, termDegrees, linkVariant)
												: calculateUnitVectorCosine(target.getValue(), candidateDocument));
									}
									unitRows.put(target.getKey(), GlobalTopRows.localTop(row, limit));
								}
								return NodeWorkRunner.UnitOutput.measured(unitRows);
							}
						},
						new NodeWorkRunner.CommitSink()
						{
							@SuppressWarnings("unchecked")
							public void commit(WorkDispatcher.Assignment assignment, Object value)
							{
								for (Map.Entry<String,TreeMap<String,Double>> unitRow
										: ((TreeMap<String,TreeMap<String,Double>>)value).entrySet())
								{
									TreeMap<String,Double> row = rows.get(unitRow.getKey());
									row.putAll(unitRow.getValue());
									if (limit > 0 && row.size() > limit)
									{
										rows.put(unitRow.getKey(), GlobalTopRows.localTop(row, limit));
									}
								}
							}
						});
				System.out.println(getLocalName() + " candidate shards: " + stats + "; the run has "
						+ plan.candidateCount() + " candidates in " + plan.units().size() + " shard(s).");
				return GlobalTopRows.keep(context.collectives(), phaseKey + "/top", node, rows, limit);
			}

			// ---------------------------------------------------- Doc2Vec

			/** A committed Doc2Vec shard: its per-target top n and per-target errors. */
			private static final class Doc2VecShardOutcome
			{
				private final TreeMap<String,TreeMap<String,Double>> rows;
				private final TreeMap<String,String> errors;

				private Doc2VecShardOutcome(
						TreeMap<String,TreeMap<String,Double>> rows,
						TreeMap<String,String> errors)
				{
					this.rows = rows;
					this.errors = errors;
				}
			}

			/**
			 * Doc2Vec scores of every target against every document of the shared
			 * model. The model's index is cut into contiguous shards scheduled as
			 * work units (one per node, homed, in the original DSMP baseline);
			 * each lane scores its units in its own persistent worker process.
			 * Each shard returns its top n by score and then name, so the union
			 * of the committed shards holds the global top n, which every node
			 * then cuts identically.
			 */
			private TreeMap<String,TreeMap<String,Double>> runDoc2VecBatchRecommendations(
					final int topn) throws Exception
			{
				final List<String> targets = distinctTargets();
				//The run's canonical corpus: the same users, tweets and tokens the
				//other algorithms receive (RunCorpus); the raw dataset otherwise.
				File runCorpus = recommendationConfig == null ? null : recommendationConfig.getRunCorpusFile();
				final File dataset = runCorpus != null && runCorpus.isFile() ? runCorpus : getConfiguredDatasetFile();
				if (dataset == null)
				{
					throw new IllegalStateException(
							"Doc2Vec cannot run because this experiment has no dataset file.");
				}
				String datasetValidation = recommendationConfig == null
						|| recommendationConfig.getDatasetFile() == null
						? null : recommendationConfig.validateDatasetSnapshot();
				if (datasetValidation != null)
				{
					throw new IllegalStateException(datasetValidation);
				}
				final SchedulingRunContext context = schedulingContext();
				final int node = currentNodeIndex();
				final boolean baseline = context.getMode() == SchedulingRunContext.Mode.BASELINE;
				final int shards = baseline
						? context.getNodeCount()
						: Math.max(context.getNodeCount(), 4 * totalLanes(context));
				final double shardCost = Math.max(1, targets.size());
				WorkPhase phase = context.openPhase(
						RecommendationWorkQueueRegistry.DOC2VEC,
						"Doc2Vec candidate shards",
						new SchedulingRunContext.UnitSupplier()
						{
							public List<WorkUnitSpec> units()
							{
								ArrayList<WorkUnitSpec> units = new ArrayList<WorkUnitSpec>();
								for (int shard = 1; shard <= shards; shard++)
								{
									units.add(new WorkUnitSpec(
											shard,
											String.format("shard-%04d-of-%04d", shard, shards),
											shardCost,
											0,
											0,
											Double.POSITIVE_INFINITY,
											baseline ? shard : 0,
											null));
								}
								return units;
							}
						});
				final File script = new File("TwitterGatherDataFollowers/userRyersonU/doc2vec_worker.py");
				final int trainingWorkers = Math.min(
						Math.max(Runtime.getRuntime().availableProcessors() - 1, 1), 12);
				final AlgorithmParameterSettings settings = algorithmSettings();
				final Doc2VecBatchRunner.ProgressListener progress = new Doc2VecBatchRunner.ProgressListener()
				{
					public void onProgress(String event, String detail)
					{
						handleDoc2VecProgress(event, detail);
					}
				};
				final Doc2VecWorker[] workers = new Doc2VecWorker[Math.max(1, context.lanesOf(node))];
				final TreeMap<String,TreeMap<String,Double>> rows =
						new TreeMap<String,TreeMap<String,Double>>();
				final TreeMap<String,String> errors = new TreeMap<String,String>();
				for (String target : targets)
				{
					rows.put(target, new TreeMap<String,Double>());
				}
				try
				{
					//Prepare every lane's worker before claiming work, so the one-time
					//model preparation is not charged to any unit.
					for (int lane = 0; lane < workers.length; lane++)
					{
						workers[lane] = new Doc2VecWorker(
								getPythonCommand(), script, dataset, trainingWorkers, settings, progress);
					}
					NodeWorkRunner.Stats stats = NodeWorkRunner.run(
							context,
							phase,
							node,
							new NodeWorkRunner.UnitExecutor()
							{
								public NodeWorkRunner.UnitOutput execute(
										WorkDispatcher.Assignment assignment, int lane) throws Exception
								{
									if (workers[lane] == null)
									{
										workers[lane] = new Doc2VecWorker(
												getPythonCommand(), script, dataset, trainingWorkers,
												settings, progress);
									}
									UnitCancellation.check();
									Doc2VecWorker.Scored scored;
									try
									{
										scored = workers[lane].score(
												assignment.getUnitId(), shards, topn, targets);
									}
									catch (IOException failure)
									{
										//A dead worker is replaced on the lane's next unit.
										workers[lane].close();
										workers[lane] = null;
										throw failure;
									}
									Doc2VecBatchRunner.Result result = scored.getResult();
									if (result.hasUnsafeNumericalDiagnostic())
									{
										throw new IllegalStateException(
												"Doc2Vec stopped because Gensim reported an unsafe "
												+ "numerical-kernel exception." + result.getDiagnosticSummary());
									}
									TreeMap<String,TreeMap<String,Double>> unitRows =
											new TreeMap<String,TreeMap<String,Double>>();
									TreeMap<String,String> unitErrors = new TreeMap<String,String>();
									for (String target : targets)
									{
										if (!result.isCompleted(target))
										{
											throw new IOException("Doc2Vec shard " + assignment.getUnitId()
													+ " did not complete for " + target + "."
													+ result.getDiagnosticSummary());
										}
										if (result.hasError(target))
										{
											unitErrors.put(target, result.getErrors().get(target));
											continue;
										}
										TreeMap<String,Double> scores = result.getScores(target);
										unitRows.put(target, scores == null
												? new TreeMap<String,Double>() : scores);
									}
									return NodeWorkRunner.UnitOutput.external(
											new Doc2VecShardOutcome(unitRows, unitErrors),
											scored.getCpuNanos());
								}
							},
							new NodeWorkRunner.CommitSink()
							{
								public void commit(WorkDispatcher.Assignment assignment, Object value)
								{
									Doc2VecShardOutcome outcome = (Doc2VecShardOutcome)value;
									for (Map.Entry<String,TreeMap<String,Double>> unitRow : outcome.rows.entrySet())
									{
										rows.get(unitRow.getKey()).putAll(unitRow.getValue());
									}
									errors.putAll(outcome.errors);
								}
							});
					System.out.println(getLocalName() + " Doc2Vec shards (" + shards + "): " + stats);
				}
				finally
				{
					for (int lane = 0; lane < workers.length; lane++)
					{
						if (workers[lane] != null)
						{
							workers[lane].close();
						}
					}
				}
				for (Map.Entry<String,String> error : errors.entrySet())
				{
					System.out.println(getLocalName() + " Doc2Vec warning for "
							+ error.getKey() + ": " + error.getValue());
				}
				return GlobalTopRows.keep(context.collectives(),
						RecommendationWorkQueueRegistry.DOC2VEC + "/top", node, rows, topn);
			}

			// ---------------------------------------------------- Temporal Graph Foundation

			/**
			 * The temporal graph model over the whole run: profiles, the dataset's
			 * temporal scan and the graph context are prepared once from every
			 * node's owned users; each target is then a work unit scored against
			 * every candidate. The node that commits a target publishes its row,
			 * and every node assembles the same full result for reports.
			 */
			private TemporalGraphFoundationModelSupport.Result runTemporalGraphFoundation()
			{
				final SchedulingRunContext context = schedulingContext();
				final int node = currentNodeIndex();
				final String phaseKey = "tgf-targets";
				final List<String> targets = distinctTargets();
				final int limit = getCosSimLocalResultLimit();
				//Activity profiles come from the run's canonical corpus (RunCorpus).
				File runCorpus = recommendationConfig == null ? null : recommendationConfig.getRunCorpusFile();
				final File dataset = runCorpus != null && runCorpus.isFile() ? runCorpus : getConfiguredDatasetFile();
				final LinkedHashMap<String,String> datasetFollowees =
						new LinkedHashMap<String,String>(datasetUserFollowee);
				final String runId = runLifecycle.getActiveRunId();
				final TemporalGraphFoundationModelSupport.Prepared prepared = context.collectives().allReduce(
						phaseKey + "/prepare",
						node,
						ownedCorpusContribution(),
						new RunCollectives.Reducer<TemporalGraphFoundationModelSupport.Prepared>()
						{
							public TemporalGraphFoundationModelSupport.Prepared reduce(
									List<Object> contributionsByNode)
							{
								GlobalCorpus corpus = GlobalCorpus.combine(contributionsByNode);
								return TemporalGraphFoundationModelSupport.prepare(
										new TemporalGraphFoundationModelSupport.Input(
												runId,
												dataset,
												corpus.vectors(),
												corpus.labels(),
												datasetFollowees,
												targets,
												Collections.<String,Integer>emptyMap(),
												1,
												1,
												limit));
							}
						});
				final List<String> scorable = new ArrayList<String>();
				for (String target : targets)
				{
					if (prepared.hasProfile(target))
					{
						scorable.add(target);
					}
				}
				final double unitCost = Math.max(1.0, 0.01 * prepared.profileCount());
				WorkPhase phase = context.openPhase(
						phaseKey,
						"Temporal Graph Foundation targets",
						new SchedulingRunContext.UnitSupplier()
						{
							public List<WorkUnitSpec> units()
							{
								ArrayList<WorkUnitSpec> units = new ArrayList<WorkUnitSpec>();
								for (int index = 0; index < scorable.size(); index++)
								{
									units.add(new WorkUnitSpec(index + 1,
											String.format("t%05d", index + 1), unitCost));
								}
								return units;
							}
						});
				final TreeMap<String,TemporalGraphFoundationModelSupport.TargetOutcome> committed =
						new TreeMap<String,TemporalGraphFoundationModelSupport.TargetOutcome>();
				NodeWorkRunner.run(
						context,
						phase,
						node,
						new NodeWorkRunner.UnitExecutor()
						{
							public NodeWorkRunner.UnitOutput execute(
									WorkDispatcher.Assignment assignment, int lane)
							{
								return NodeWorkRunner.UnitOutput.measured(
										TemporalGraphFoundationModelSupport.scoreTarget(
												prepared, scorable.get(assignment.getUnitId() - 1)));
							}
						},
						new NodeWorkRunner.CommitSink()
						{
							public void commit(WorkDispatcher.Assignment assignment, Object value)
							{
								TemporalGraphFoundationModelSupport.TargetOutcome outcome =
										(TemporalGraphFoundationModelSupport.TargetOutcome)value;
								committed.put(outcome.getTarget(), outcome);
							}
						});
				List<TreeMap<String,TemporalGraphFoundationModelSupport.TargetOutcome>> byNode =
						context.collectives().allGather(phaseKey + "/outcomes", node, committed);
				ArrayList<TemporalGraphFoundationModelSupport.TargetOutcome> ordered =
						new ArrayList<TemporalGraphFoundationModelSupport.TargetOutcome>();
				for (String target : scorable)
				{
					for (TreeMap<String,TemporalGraphFoundationModelSupport.TargetOutcome> outcomes : byNode)
					{
						if (outcomes.containsKey(target))
						{
							ordered.add(outcomes.get(target));
						}
					}
				}
				TemporalGraphFoundationModelSupport.Result result =
						TemporalGraphFoundationModelSupport.assemble(prepared, ordered);
				if (node == 1)
				{
					TemporalGraphFoundationRegistry.record(result);
				}
				TreeMap<String,TreeMap<String,Double>> scores = result.getScores();
				Map<String,TreeMap<String,String>> details = result.getDetails();
				allUserScores = new TreeMap<String,TreeMap<String,Double>>();
				temporalGraphCandidateDetails.clear();
				for (String target : committed.keySet())
				{
					allUserScores.put(target, scores.get(target));
					if (details.get(target) != null)
					{
						temporalGraphCandidateDetails.put(target, details.get(target));
					}
				}
				return result;
			}

			// ---------------------------------------------------- exact SVM

			/** A committed exact-SVM target: its followee scores and test accuracy. */
			private static final class SvmTargetOutcome
			{
				private final String target;
				private final TreeMap<String,Double> scores;
				private final double accuracyPercent;

				private SvmTargetOutcome(String target, TreeMap<String,Double> scores, double accuracyPercent)
				{
					this.target = target;
					this.scores = scores;
					this.accuracyPercent = accuracyPercent;
				}
			}

			/**
			 * Exact SVM in every mode: one Weka SMO model per target, trained on
			 * the stratified split of every other labelled user of the run (the
			 * target is held out of its own model, so its label is never learned
			 * from), with corpus-wide TF-IDF, the corpus-wide vocabulary as
			 * attributes and every followee of the corpus as a class. Each target
			 * is a work unit; the node that commits it publishes its scores.
			 */
			private TreeMap<String,TreeMap<String,Double>> runExactSvm() throws Exception
			{
				final SchedulingRunContext context = schedulingContext();
				final int node = currentNodeIndex();
				final String phaseKey = RecommendationWorkQueueRegistry.SVM_TARGETS;
				final String name = RecommendationModeSupport.algorithmName(algorithmRec);
				final GlobalCorpus corpus = GlobalCorpus.gather(
						context.collectives(), phaseKey + "/corpus", node, ownedCorpusContribution());
				final List<String> targets = distinctTargets();
				for (String target : targets)
				{
					if (!corpus.contains(target))
					{
						throw new IllegalArgumentException(
								name + " target has no prepared document: " + target);
					}
				}
				if (corpus.classes().size() < 2)
				{
					throw new IllegalStateException(name + " needs at least two followee classes; the corpus has "
							+ corpus.classes().size() + ".");
				}
				//Estimated milliseconds: an isolated worker JVM (~400 ms to start and
				//load) plus SMO work growing with the corpus and the class pairs.
				final double unitCost = 400.0 + 1.0e-3 * Math.max(1L, corpus.nonZeroCount())
						* Math.max(1, corpus.classes().size());
				WorkPhase phase = context.openPhase(
						phaseKey,
						name + " target models",
						new SchedulingRunContext.UnitSupplier()
						{
							public List<WorkUnitSpec> units()
							{
								ArrayList<WorkUnitSpec> units = new ArrayList<WorkUnitSpec>();
								for (int index = 0; index < targets.size(); index++)
								{
									units.add(new WorkUnitSpec(index + 1,
											String.format("t%05d", index + 1), unitCost));
								}
								return units;
							}
						});
				final AlgorithmParameterSettings settings = algorithmSettings();
				System.out.println(getLocalName() + " " + name + " corpus: " + corpus.size() + " users, "
						+ corpus.terms().size() + " terms, " + corpus.classes().size()
						+ " classes; split seed " + SvmReproducibility.SPLIT_SEED
						+ ", model seed " + SvmReproducibility.MODEL_SEED + ".");
				final TreeMap<String,TreeMap<String,Double>> committed =
						new TreeMap<String,TreeMap<String,Double>>();
				final ArrayList<Double> accuracies = new ArrayList<Double>();
				NodeWorkRunner.Stats stats = NodeWorkRunner.run(
						context,
						phase,
						node,
						new NodeWorkRunner.UnitExecutor()
						{
							public NodeWorkRunner.UnitOutput execute(
									WorkDispatcher.Assignment assignment, int lane) throws Exception
							{
								String target = targets.get(assignment.getUnitId() - 1);
								UnitCancellation.check();
								ExactSvmWorkerResult result = trainExactSvmTarget(corpus, target, settings);
								List<double[]> distributions = result.getDistributions();
								List<String> predicted = result.getPredictedClasses();
								if (distributions.isEmpty() || predicted.isEmpty())
								{
									throw new IllegalStateException(
											name + " worker returned no prediction for " + target + ".");
								}
								return NodeWorkRunner.UnitOutput.external(
										new SvmTargetOutcome(
												target,
												followeeScores(corpus.classes(), result.getClassNames(),
														distributions.get(0), predicted.get(0)),
												result.getTestAccuracyPercent()),
										result.getWorkerCpuNanos());
							}
						},
						new NodeWorkRunner.CommitSink()
						{
							public void commit(WorkDispatcher.Assignment assignment, Object value)
							{
								SvmTargetOutcome outcome = (SvmTargetOutcome)value;
								committed.put(outcome.target, outcome.scores);
								if (Double.isFinite(outcome.accuracyPercent))
								{
									accuracies.add(Double.valueOf(outcome.accuracyPercent));
								}
							}
						});
				double accuracySum = 0.0;
				for (Double accuracy : accuracies)
				{
					accuracySum += accuracy.doubleValue();
				}
				String summary = getLocalName() + " " + name + " targets: " + stats
						+ (accuracies.isEmpty() ? "" : String.format(
								"; mean held-out test accuracy of its models %.2f%%",
								accuracySum / accuracies.size()));
				System.out.println(summary);
				if (myGui != null)
				{
					myGui.appendResult(summary);
				}
				return committed;
			}

			/** Trains and applies the model of one target in an isolated Weka worker. */
			private ExactSvmWorkerResult trainExactSvmTarget(
					GlobalCorpus corpus,
					String target,
					AlgorithmParameterSettings settings) throws Exception
			{
				SvmReproducibility.Split split = SvmReproducibility.stratifiedSplit(
						corpus.usersByClass(Collections.singleton(target)), TEST_SET_PERCENT);
				File arffDir = new File("Dataset/Arff_files/");
				if (!arffDir.exists())
				{
					arffDir.mkdirs();
				}
				String prefix = "svm-" + sanitizeFileName(target) + "-";
				File train = File.createTempFile(prefix, "-train.arff", arffDir);
				File test = File.createTempFile(prefix, "-test.arff", arffDir);
				File recommend = File.createTempFile(prefix, "-recommend.arff", arffDir);
				try
				{
					writeCorpusArff(train, "trainingSet", split.getTrainUsers(), corpus);
					writeCorpusArff(test, "testSet", split.getTestUsers(), corpus);
					writeCorpusArff(recommend, "recommendSet", Collections.singletonList(target), corpus);
					return ExactSvmWorkerRunner.run(
							getLocalName(),
							"exact SVM target " + target,
							runLifecycle.getActiveRunId(),
							train,
							test,
							recommend,
							corpus.terms().size(),
							split.getTrainUsers().size() + split.getTestUsers().size(),
							corpus.classes().size(),
							settings);
				}
				finally
				{
					deleteTemporaryFile(train.getPath());
					deleteTemporaryFile(test.getPath());
					deleteTemporaryFile(recommend.getPath());
				}
			}

			private void writeCorpusArff(
					File file,
					String relation,
					List<String> users,
					GlobalCorpus corpus) throws IOException
			{
				List<String> terms = corpus.terms();
				Map<String,Integer> termIndex = corpus.termIndex();
				try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, false)))
				{
					SparseArffWriter.writeHeader(writer, relation, terms, corpus.classes());
					for (String user : users)
					{
						SparseArffWriter.writeIndexedRow(writer, corpus.vectorOf(user), termIndex,
								terms.size(), corpus.labelOf(user));
					}
				}
			}

			/**
			 * A classifier's class distribution as followee scores over every
			 * class of the corpus; when nothing is positive the predicted class
			 * scores 1.
			 */
			private TreeMap<String,Double> followeeScores(
					List<String> classes,
					List<String> classNames,
					double[] distribution,
					String predicted)
			{
				TreeMap<String,Double> scores = new TreeMap<String,Double>();
				for (String followee : classes)
				{
					scores.put(followee, 0.0);
				}
				if (distribution != null)
				{
					for (int index = 0; index < distribution.length && index < classNames.size(); index++)
					{
						String className = classNames.get(index);
						if (scores.containsKey(className))
						{
							double score = distribution[index];
							scores.put(className, Double.isFinite(score) ? score : 0.0);
						}
					}
				}
				boolean positive = false;
				for (Double score : scores.values())
				{
					if (score.doubleValue() > 0.0)
					{
						positive = true;
						break;
					}
				}
				if (!positive && predicted != null && scores.containsKey(predicted))
				{
					scores.put(predicted, 1.0);
				}
				return scores;
			}

			// ---------------------------------------------------- exact MLP

			/** The trained MLP's scores for every target and its report. */
			private static final class MlpOutcome
			{
				private final TreeMap<String,TreeMap<String,Double>> scores;
				private final List<String> report;
				private final long trainNanos;
				private final long testNanos;

				private MlpOutcome(
						TreeMap<String,TreeMap<String,Double>> scores,
						List<String> report,
						long trainNanos,
						long testNanos)
				{
					this.scores = scores;
					this.report = report;
					this.trainNanos = trainNanos;
					this.testNanos = testNanos;
				}
			}

			/**
			 * Exact MLP: one model trained on the controller's central split of the
			 * whole run's users, with every recommendation target held out of
			 * training and testing, on the controller's central vocabulary. The
			 * training is one work unit (on node 1 in the original DSMP baseline,
			 * where the policy places it otherwise); the committing node scores
			 * every target and publishes the scores once.
			 */
			private TreeMap<String,TreeMap<String,Double>> runExactMlp() throws Exception
			{
				final SchedulingRunContext context = schedulingContext();
				final int node = currentNodeIndex();
				final String phaseKey = RecommendationWorkQueueRegistry.MLP_TRAINING;
				final GlobalCorpus corpus = GlobalCorpus.gather(
						context.collectives(), phaseKey + "/corpus", node, ownedCorpusContribution());
				final AlgorithmParameterSettings settings = algorithmSettings();
				//Estimated milliseconds of the worst case: every epoch up to the limit
				//updates each weight once per training row (~8e-6 ms per update,
				//measured on the legacy engine).
				int features = centralUniqueDocTerms != null && !centralUniqueDocTerms.isEmpty()
						? centralUniqueDocTerms.size() : corpus.terms().size();
				int classes = Math.max(1, datasetFollowees == null ? 1 : datasetFollowees.size());
				double weights = (double)features * Math.max(1, settings.getMlpHiddenNeurons())
						* Math.max(1, settings.getMlpHiddenLayers())
						+ (double)Math.max(1, settings.getMlpHiddenNeurons()) * classes;
				int epochs = settings.isSparseFederatedMlpEnabled()
						? Math.max(1, settings.getMlpSparseEpochs()) : MLP_MAX_EPOCHS;
				final double unitCost = Math.max(1000.0,
						8.0e-6 * epochs * Math.max(1, corpus.size()) * weights);
				WorkPhase phase = context.openPhase(
						phaseKey,
						"MLP training",
						new SchedulingRunContext.UnitSupplier()
						{
							public List<WorkUnitSpec> units()
							{
								return Collections.singletonList(new WorkUnitSpec(
										1, "model", unitCost, 0, 0, Double.POSITIVE_INFINITY, 1, null));
							}
						});
				final MlpOutcome[] committed = new MlpOutcome[1];
				NodeWorkRunner.run(
						context,
						phase,
						node,
						new NodeWorkRunner.UnitExecutor()
						{
							public NodeWorkRunner.UnitOutput execute(
									WorkDispatcher.Assignment assignment, int lane) throws Exception
							{
								UnitCancellation.check();
								return NodeWorkRunner.UnitOutput.measured(
										trainExactMlp(corpus, settings, context));
							}
						},
						new NodeWorkRunner.CommitSink()
						{
							public void commit(WorkDispatcher.Assignment assignment, Object value)
							{
								committed[0] = (MlpOutcome)value;
							}
						});
				if (committed[0] == null)
				{
					System.out.println(getLocalName() + " MLP was trained on another node; "
							+ "that node publishes the scores.");
					return new TreeMap<String,TreeMap<String,Double>>();
				}
				completionTimeTrain = committed[0].trainNanos;
				completionTimeTest = committed[0].testNanos;
				for (String line : committed[0].report)
				{
					String message = getLocalName() + " " + line;
					System.out.println(message);
					if (myGui != null)
					{
						myGui.appendResult(message);
					}
				}
				return committed[0].scores;
			}

			private MlpOutcome trainExactMlp(
					GlobalCorpus corpus,
					AlgorithmParameterSettings settings,
					final SchedulingRunContext context) throws Exception
			{
				List<String> targets = distinctTargets();
				HashSet<String> centralTrain = new HashSet<String>(
						centralTrainSetUsers == null ? Collections.<String>emptyList() : centralTrainSetUsers);
				HashSet<String> centralTest = new HashSet<String>(
						centralTestSetUsers == null ? Collections.<String>emptyList() : centralTestSetUsers);
				String[] classNames = (datasetFollowees == null
						? Collections.<String>emptyList() : datasetFollowees).toArray(new String[0]);
				LinkedHashMap<String,Integer> classIndex = new LinkedHashMap<String,Integer>();
				for (int index = 0; index < classNames.length; index++)
				{
					classIndex.put(classNames[index], Integer.valueOf(index));
				}
				TreeSet<String> vocabulary = centralUniqueDocTerms != null && !centralUniqueDocTerms.isEmpty()
						? centralUniqueDocTerms : new TreeSet<String>(corpus.terms());
				LinkedHashMap<String,String> labels = new LinkedHashMap<String,String>();
				for (String user : corpus.users())
				{
					String label = corpus.labelOf(user);
					if (label != null && classIndex.containsKey(label))
					{
						labels.put(user, label);
					}
				}
				//A target is never in the training data of the model that predicts
				//it: all targets are held out of one model, or fold by fold when
				//they are many (TargetFolds).
				List<List<String>> folds = TargetFolds.of(targets, labels, labels.size());
				ArrayList<String> report = new ArrayList<String>();
				report.add("MLP (" + settings.getMlpEngineLabel() + ") on the whole run: "
						+ TargetFolds.describe(folds, labels.size()) + "; " + vocabulary.size()
						+ " features, " + classNames.length + " classes.");
				TreeMap<String,TreeMap<String,Double>> scores = new TreeMap<String,TreeMap<String,Double>>();
				long trainNanos = 0L;
				long testNanos = 0L;
				int correct = 0;
				int tested = 0;
				for (int foldIndex = 0; foldIndex < folds.size(); foldIndex++)
				{
					HashSet<String> heldOut = new HashSet<String>(folds.get(foldIndex));
					ArrayList<String> train = new ArrayList<String>();
					ArrayList<String> test = new ArrayList<String>();
					for (String user : corpus.users())
					{
						if (heldOut.contains(user) || !labels.containsKey(user))
						{
							continue;
						}
						if (centralTrain.contains(user))
						{
							train.add(user);
						}
						else if (centralTest.contains(user))
						{
							test.add(user);
						}
					}
					HashSet<String> trainClasses = new HashSet<String>();
					for (String user : train)
					{
						trainClasses.add(labels.get(user));
					}
					if (trainClasses.size() < 2)
					{
						throw new IllegalStateException("MLP needs at least two followee classes in the training "
								+ "set after the recommendation targets are held out; it has "
								+ trainClasses.size() + ".");
					}
					String foldLabel = folds.size() == 1 ? "" : " (fold " + (foldIndex + 1) + " of " + folds.size() + ")";
					MlpFold fold = settings.isSparseFederatedMlpEnabled()
							? trainSoftmaxMlpFold(corpus, settings, vocabulary, classIndex, classNames.length,
									labels, train, test, folds.get(foldIndex), foldIndex, foldLabel)
							: trainNeurophMlpFold(corpus, settings, context, vocabulary, classIndex,
									classNames, train, test, folds.get(foldIndex), foldLabel);
					scores.putAll(fold.scores);
					trainNanos += fold.trainNanos;
					testNanos += fold.testNanos;
					correct += fold.correct;
					tested += fold.tested;
					report.add(fold.description);
				}
				report.add(tested == 0
						? "MLP held-out accuracy skipped: no test users."
						: String.format(java.util.Locale.US,
								"MLP held-out accuracy (test split, targets excluded): %.2f%% (%d/%d)",
								100.0 * correct / tested, correct, tested));
				return new MlpOutcome(scores, report, trainNanos, testNanos);
			}

			/** What training and testing one fold's model produced. */
			private static final class MlpFold
			{
				private final TreeMap<String,TreeMap<String,Double>> scores =
						new TreeMap<String,TreeMap<String,Double>>();
				private long trainNanos;
				private long testNanos;
				private int correct;
				private int tested;
				private String description = "";
			}

			/**
			 * The softmax MLP on one fold: the training users are split once more
			 * (stratified, 15%) into a fitting and a validation set for early
			 * stopping; the test users only measure accuracy; the fold's targets
			 * get the model's class probabilities.
			 */
			private MlpFold trainSoftmaxMlpFold(
					GlobalCorpus corpus,
					AlgorithmParameterSettings settings,
					TreeSet<String> vocabulary,
					Map<String,Integer> classIndex,
					int classCount,
					Map<String,String> labels,
					List<String> train,
					List<String> test,
					List<String> foldTargets,
					int foldIndex,
					String foldLabel) throws Exception
			{
				MlpFold fold = new MlpFold();
				Map<String,Integer> termIndex = buildSparseMlpTermIndex(vocabulary);
				TreeMap<String,List<String>> trainByClass = new TreeMap<String,List<String>>();
				for (String user : train)
				{
					String label = labels.get(user);
					List<String> members = trainByClass.get(label);
					if (members == null)
					{
						members = new ArrayList<String>();
						trainByClass.put(label, members);
					}
					members.add(user);
				}
				SvmReproducibility.Split inner = SvmReproducibility.stratifiedSplit(trainByClass, 0.15);
				List<SparseFederatedMlpModelSupport.TrainingExample> fitExamples =
						sparseExamples(inner.getTrainUsers(), corpus, termIndex, classIndex, true);
				List<SparseFederatedMlpModelSupport.TrainingExample> validationExamples =
						sparseExamples(inner.getTestUsers(), corpus, termIndex, classIndex, true);
				List<SparseFederatedMlpModelSupport.TrainingExample> testExamples =
						sparseExamples(test, corpus, termIndex, classIndex, true);
				SparseFederatedMlpModelSupport.SparseMlpModel model =
						SparseFederatedMlpModelSupport.SparseMlpModel.create(
								vocabulary.size(),
								settings.getMlpHiddenLayers(),
								settings.getMlpHiddenNeurons(),
								classCount,
								0x5A17EEDL + foldIndex);
				try (RecommendationMemoryCoordinator.Lease ignored =
						RecommendationMemoryCoordinator.acquire(
								getLocalName(),
								"Softmax MLP training",
								RecommendationMemoryCoordinator.Workload.MLP_TRAIN,
								RecommendationMemoryCoordinator.estimateSparseMlpTrainingBytes(
										vocabulary.size(),
										settings.getMlpHiddenNeurons(),
										settings.getMlpHiddenLayers(),
										classCount,
										corpus.nonZeroCount(),
										train.size() + test.size() + foldTargets.size())))
				{
					long trainStart = System.nanoTime();
					SparseFederatedMlpModelSupport.SparseMlpModel.TrainingReport training =
							model.trainWithEarlyStopping(
									fitExamples,
									validationExamples,
									settings.getMlpSparseEpochs(),
									AlgorithmParameterSettings.MLP_EARLY_STOPPING_PATIENCE,
									settings.getMlpLearningRate(),
									settings.getMlpSparseL2());
					fold.trainNanos = System.nanoTime() - trainStart;
					long testStart = System.nanoTime();
					for (SparseFederatedMlpModelSupport.TrainingExample example : testExamples)
					{
						if (findIndexOfMaxValue(model.predict(example.getFeatures()))
								== example.getLabelIndex())
						{
							fold.correct++;
						}
						fold.tested++;
					}
					fold.testNanos = System.nanoTime() - testStart;
					fold.description = "Softmax MLP" + foldLabel + ": " + fitExamples.size()
							+ " fitting, " + validationExamples.size() + " validation and "
							+ testExamples.size() + " test users; " + training.describe() + ".";
					String[] classNames = classIndex.keySet().toArray(new String[0]);
					for (SparseFederatedMlpModelSupport.TrainingExample example
							: sparseExamples(foldTargets, corpus, termIndex, classIndex, false))
					{
						double[] output = model.predict(example.getFeatures());
						TreeMap<String,Double> row = new TreeMap<String,Double>();
						for (int index = 0; index < output.length && index < classNames.length; index++)
						{
							row.put(classNames[index], Math.max(0.0, output[index]));
						}
						fold.scores.put(example.getUser(), row);
					}
				}
				return fold;
			}

			/**
			 * The Neuroph MLP (V3's engine) on one fold: tanh hidden layers and
			 * sigmoid outputs trained by back-propagation on the squared error,
			 * one transfer-function instance per output neuron (a shared softmax
			 * instance mixed neurons' outputs and gradients). The fold's targets
			 * get the outputs normalised to sum to one.
			 */
			private MlpFold trainNeurophMlpFold(
					GlobalCorpus corpus,
					AlgorithmParameterSettings settings,
					final SchedulingRunContext context,
					TreeSet<String> vocabulary,
					Map<String,Integer> classIndex,
					String[] classNames,
					List<String> train,
					List<String> test,
					List<String> foldTargets,
					String foldLabel) throws Exception
			{
				MlpFold fold = new MlpFold();
				validateLegacyNeurophMlpConfiguration(
						vocabulary.size(),
						settings.getMlpHiddenNeurons(),
						settings.getMlpHiddenLayers(),
						classNames.length,
						train.size(),
						test.size(),
						foldTargets.size());
				long reservation = RecommendationMemoryCoordinator.estimateMlpTrainingBytes(
						vocabulary.size(),
						settings.getMlpHiddenNeurons(),
						settings.getMlpHiddenLayers(),
						classNames.length,
						train.size() + test.size() + Math.max(1, foldTargets.size()));
				try (RecommendationMemoryCoordinator.Lease ignored =
						RecommendationMemoryCoordinator.acquire(
								getLocalName(),
								"Neuroph MLP dataset construction and training",
								RecommendationMemoryCoordinator.Workload.MLP_TRAIN,
								reservation))
				{
					reportLegacyNeurophMlpConfiguration(
							vocabulary.size(),
							settings.getMlpHiddenNeurons(),
							settings.getMlpHiddenLayers(),
							classNames.length,
							train.size(),
							test.size(),
							foldTargets.size(),
							reservation);
					DataSet trainSet = denseDataSet(train, corpus, vocabulary, classIndex, classNames.length);
					DataSet testSet = denseDataSet(test, corpus, vocabulary, classIndex, classNames.length);
					MultiLayerPerceptron network = createLegacyNeurophMlp(
							vocabulary.size(),
							settings.getMlpHiddenNeurons(),
							settings.getMlpHiddenLayers(),
							classNames.length);
					//Neuroph draws initial weights from an unseeded generator; the
					//same range from a fixed seed makes training reproducible.
					network.randomizeWeights(new org.neuroph.util.random.RangeRandomizer(
							-0.7, 0.7, new java.util.Random(MLP_WEIGHT_SEED)));
					List<Layer> layers = network.getLayers();
					for (Neuron outputNeuron : layers.get(layers.size() - 1).getNeurons())
					{
						//One instance per neuron: a transfer function keeps the
						//neuron's last output for its derivative.
						outputNeuron.setTransferFunction(new org.neuroph.core.transfer.Sigmoid());
					}
					final BackPropagation rule = (BackPropagation)network.getLearningRule();
					rule.setLearningRate(settings.getMlpLearningRate());
					rule.setMaxError(settings.getMlpMaxError());
					rule.setMaxIterations(MLP_MAX_EPOCHS);
					rule.addListener(new org.neuroph.core.events.LearningEventListener()
					{
						public void handleLearningEvent(org.neuroph.core.events.LearningEvent event)
						{
							//A revoked training unit (drain, crash) stops at the next epoch.
							if (context.isAborted() || UnitCancellation.isRevoked())
							{
								rule.stopLearning();
							}
						}
					});
					shuffleTrainingRows(trainSet, MLP_PRESENTATION_SEED);
					long trainStart = System.nanoTime();
					network.learn(trainSet);
					fold.trainNanos = System.nanoTime() - trainStart;
					UnitCancellation.check();
					context.requireActive();
					long testStart = System.nanoTime();
					for (DataSetRow row : testSet.getRows())
					{
						network.setInput(row.getInput());
						network.calculate();
						if (findIndexOfMaxValue(network.getOutput())
								== findIndexOfMaxValue(row.getDesiredOutput()))
						{
							fold.correct++;
						}
						fold.tested++;
					}
					fold.testNanos = System.nanoTime() - testStart;
					fold.description = "Neuroph MLP" + foldLabel + ": " + trainSet.size() + " training and "
							+ testSet.size() + " test users; stopped after " + rule.getCurrentIteration()
							+ " epoch(s) with total error " + rule.getTotalNetworkError()
							+ (rule.getCurrentIteration() >= MLP_MAX_EPOCHS
									? " (epoch limit " + MLP_MAX_EPOCHS + " reached before max error "
											+ settings.getMlpMaxError() + ")"
									: "") + ".";
					for (String target : foldTargets)
					{
						Map<String,Double> document = corpus.vectorOf(target);
						if (document == null)
						{
							continue;
						}
						network.setInput(denseVector(document, vocabulary));
						network.calculate();
						double[] output = network.getOutput();
						double total = 0.0;
						for (int index = 0; index < output.length && index < classNames.length; index++)
						{
							total += Math.max(0.0, output[index]);
						}
						TreeMap<String,Double> row = new TreeMap<String,Double>();
						for (int index = 0; index < output.length && index < classNames.length; index++)
						{
							double value = Math.max(0.0, output[index]);
							row.put(classNames[index], total > 0.0 ? value / total : 0.0);
						}
						fold.scores.put(target, row);
					}
				}
				return fold;
			}

			private List<SparseFederatedMlpModelSupport.TrainingExample> sparseExamples(
					List<String> users,
					GlobalCorpus corpus,
					Map<String,Integer> termIndex,
					Map<String,Integer> classIndex,
					boolean requireLabel)
			{
				ArrayList<SparseFederatedMlpModelSupport.TrainingExample> examples =
						new ArrayList<SparseFederatedMlpModelSupport.TrainingExample>();
				for (String user : users)
				{
					Map<String,Double> document = corpus.vectorOf(user);
					if (document == null)
					{
						continue;
					}
					Integer label = classIndex.get(corpus.labelOf(user));
					if (requireLabel && label == null)
					{
						continue;
					}
					LinkedHashMap<Integer,Double> features = new LinkedHashMap<Integer,Double>();
					for (Map.Entry<String,Double> entry : document.entrySet())
					{
						Integer feature = termIndex.get(entry.getKey());
						Double value = entry.getValue();
						if (feature != null && value != null && Double.isFinite(value.doubleValue())
								&& value.doubleValue() != 0.0)
						{
							features.put(feature, value);
						}
					}
					examples.add(new SparseFederatedMlpModelSupport.TrainingExample(
							user, features, requireLabel ? label.intValue() : -1));
				}
				return examples;
			}

			private DataSet denseDataSet(
					List<String> users,
					GlobalCorpus corpus,
					TreeSet<String> vocabulary,
					Map<String,Integer> classIndex,
					int classCount)
			{
				DataSet dataSet = new DataSet(vocabulary.size(), classCount);
				for (String user : users)
				{
					Integer label = classIndex.get(corpus.labelOf(user));
					Map<String,Double> document = corpus.vectorOf(user);
					if (label == null || document == null)
					{
						continue;
					}
					double[] desired = new double[classCount];
					desired[label.intValue()] = 1.0;
					dataSet.addRow(new DataSetRow(denseVector(document, vocabulary), desired));
				}
				return dataSet;
			}

			private double[] denseVector(Map<String,Double> document, TreeSet<String> vocabulary)
			{
				double[] vector = new double[vocabulary.size()];
				int index = 0;
				for (String term : vocabulary)
				{
					Double value = document.get(term);
					vector[index++] = value == null ? 0.0 : value.doubleValue();
				}
				return vector;
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

			private boolean isSvmBatchMode()
			{
				return RecommendationModeSupport.isSvmFamilyAlgorithm(algorithmRec)
						&& usersRec != null && usersRec.size() > 1;
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

			private void handleAlgorithmFailure(Throwable failure)
			{
				NodeExecutionMonitor.failed(getLocalName(), failure);
				releaseActiveKMeansLease();
				releaseExactSvmPreparationState();
				releaseActiveSvmLease();
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
	
			/**
			 * Delegates to {@link TweetTextNormalizer} so the controller and every
			 * recommender node tokenise identically. Divergent copies of this
			 * pipeline desynchronise the vocabularies used for distributed models.
			 */
			private String normalizeProcessedTweet(String tweetText)
			{
				return TweetTextNormalizer.normalize(
						tweetText,
						retweetedby_temp == RE_TWEETS,
						hashtags_temp == HASH_TAGS,
						stopWordFlag_temp == STOP_WORDS,
						STOP_WORD_SET);
			}

			private int countNormalizedTokens(String normalizedText)
			{
				return TweetTextNormalizer.countTokens(normalizedText);
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
			sendOverLink(acknowledgement);
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
				{
					firstTweetTime = System.nanoTime();
					stageStart("input");
				}
				
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
						sendOverLink(msgLastTweetFromRecUser);
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
						if (countNormalizedTokens(normalizedText)
								< TweetTextNormalizer.MINIMUM_USABLE_TOKENS)
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
			
	/**
	 * Cosine similarity of two L2-normalised sparse vectors.
	 *
	 * Computes the dot product only. That equals cosine because every caller
	 * passes TF-IDF vectors that were normalised to unit length when they were
	 * built, and skipping the norms keeps the inner loop cheap over millions of
	 * pairs. Passing unnormalised vectors silently returns something that is not
	 * a cosine, which is why the method is no longer named for the general case.
	 */
	private double calculateUnitVectorCosine(Map<String,Double> doc1, Map<String,Double> doc2)
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

			/**
			 * Snapshot of user -> cluster id across all clusters.
			 *
			 * Returned by value: the caller may hold it across iterations that clear and
			 * refill the underlying {@link Cluster} point lists.
			 */
			/**
			 * True when this node is the one that should score {@code candidate}.
			 *
			 * Recommendation targets are replicated to every recommender node so each
			 * node can build the target's document. Without this check every node also
			 * scores those replicated users as candidates, and the merge (which sums
			 * duplicates) inflates them by roughly the node count. Candidates with no
			 * recorded owner are scored locally, which is the single-node case.
			 */
			private boolean ownsCandidate(String candidate)
			{
				Integer primaryServer = userPrimaryRecServer.get(candidate);
				if (primaryServer == null)
				{
					//Input that carried no owner (per-tweet delivery): the
					//controller's placement names it, identically on every node.
					SchedulingRunContext context =
							SchedulingRuntime.get(runLifecycle.getActiveRunId());
					int planned = context == null ? 0 : context.plannedOwnerOf(candidate);
					if (planned > 0)
					{
						primaryServer = Integer.valueOf(planned);
					}
				}
				return primaryServer == null
						|| primaryServer.intValue() == thisNodeNumber();
			}

			/**
			 * This node's number, parsed once.
			 *
			 * Ownership is checked inside the scoring loops, once per candidate per
			 * target, so re-parsing the string there was needless work on every
			 * comparison. If the value is somehow unparseable the node claims every
			 * candidate, which is the single-node behaviour: over-counting is
			 * recoverable, silently returning no recommendations is not.
			 */
			private int thisNodeNumber()
			{
				if (cachedNodeNumber == Integer.MIN_VALUE)
				{
					try
					{
						cachedNodeNumber = Integer.parseInt(nodeNumber.trim());
					}
					catch (RuntimeException unparseable)
					{
						System.err.println(getLocalName()
								+ " could not parse its node number '" + nodeNumber
								+ "'; scoring every local candidate.");
						cachedNodeNumber = -1;
					}
				}
				return cachedNodeNumber;
			}


			/**
			 * Size of each node's local candidate shortlist.
			 *
			 * The requested top-N is captured once per run rather than read live from
			 * the Swing text field: reading it here happened on a JADE agent thread
			 * (off the event dispatch thread) and picked up whatever the user had
			 * typed at that instant, so editing the field mid-run silently changed
			 * pruning, and different nodes could read different values.
			 */
			private int getCosSimLocalResultLimit()
		{
			int requestedRecommendations = Math.max(0, requestedTopRecommendations);

			AlgorithmParameterSettings settings = algorithmSettings();
			int bufferedLimit = requestedRecommendations
					* settings.getSimilarityLocalTopMultiplier();
			if (bufferedLimit < settings.getSimilarityLocalTopMinimum())
			{
				bufferedLimit = settings.getSimilarityLocalTopMinimum();
			}
			return bufferedLimit;
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
