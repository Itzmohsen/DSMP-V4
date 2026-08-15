package TwitterGatherDataFollowers.userRyersonU;


import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.text.DateFormat;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.awt.image.BufferedImage;
import java.awt.event.*;    // added by Sepide

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.border.*;
import javax.swing.UIManager.*;
import java.util.Collections;
import java.awt.event.*;

import jade.gui.*;


public class ControllerAgentGui extends JFrame implements ActionListener {

	private ControllerAgent myAgent;
	private StarterAgent myStarter;   // added by Sepide 

	private static final int FRAME_WIDTH = 1575;
	private static final int FRAME_HEIGHT = 670;
	private static final int AREA_ROWS = 15;
	private static final int AREA_COLUMNS = 40;
	private static final int MAX_USER_GENERATION_LOG_CHARACTERS = 300000;
	private static final int SURFACE_RADIUS = 8;
	private static final Color APP_BACKGROUND_TOP = StudioNightTheme.adaptiveColor(
			new Color(246, 248, 251), StudioNightTheme.NIGHT_BACKGROUND);
	private static final Color APP_BACKGROUND_BOTTOM = StudioNightTheme.adaptiveColor(
			new Color(229, 234, 242), StudioNightTheme.NIGHT_BACKGROUND_BOTTOM);
	private static final Color APP_SURFACE = StudioNightTheme.adaptiveColor(
			new Color(255, 255, 255, 238), StudioNightTheme.NIGHT_SURFACE);
	private static final Color APP_SURFACE_ALT = StudioNightTheme.adaptiveColor(
			new Color(248, 250, 252, 240), StudioNightTheme.NIGHT_SURFACE_ALT);
	private static final Color APP_BORDER = StudioNightTheme.adaptiveColor(
			new Color(207, 216, 227), StudioNightTheme.NIGHT_BORDER);
	private static final Color APP_TEXT = StudioNightTheme.adaptiveColor(
			new Color(24, 29, 36), StudioNightTheme.NIGHT_TEXT);
	private static final Color APP_MUTED_TEXT = StudioNightTheme.adaptiveColor(
			new Color(91, 101, 116), StudioNightTheme.NIGHT_MUTED);
	private static final Color APP_BLUE = StudioNightTheme.adaptiveColor(
			new Color(0, 113, 227), StudioNightTheme.NIGHT_BLUE);
	private static final Color APP_BLUE_HOVER = StudioNightTheme.adaptiveColor(
			new Color(38, 139, 236), new Color(120, 184, 255));
	private static final Color APP_GREEN = StudioNightTheme.adaptiveColor(
			new Color(20, 150, 105), StudioNightTheme.NIGHT_GREEN);
	private static final Color APP_GREEN_HOVER = StudioNightTheme.adaptiveColor(
			new Color(31, 170, 123), new Color(92, 230, 169));
	private static final Color APP_RED = StudioNightTheme.adaptiveColor(
			new Color(211, 47, 47), StudioNightTheme.NIGHT_RED);
	private static final Color APP_RED_HOVER = StudioNightTheme.adaptiveColor(
			new Color(226, 75, 75), new Color(255, 130, 124));
	private static final Color APP_NEUTRAL = StudioNightTheme.adaptiveColor(
			new Color(241, 244, 248), StudioNightTheme.NIGHT_PANEL_INK);
	private static final Color APP_NEUTRAL_HOVER = StudioNightTheme.adaptiveColor(
			new Color(229, 235, 243), StudioNightTheme.NIGHT_SURFACE_ALT);
	private static final Color APP_TEXT_AREA = StudioNightTheme.adaptiveColor(
			new Color(15, 20, 26), new Color(8, 12, 18));
	private static final Color APP_TEXT_AREA_TEXT = StudioNightTheme.adaptiveColor(
			new Color(234, 241, 248), StudioNightTheme.NIGHT_TEXT);
	private static final Color APP_SELECTION = StudioNightTheme.adaptiveColor(
			new Color(217, 236, 255), new Color(43, 89, 134));
	private static final int BUTTON_PRIMARY = 0;
	private static final int BUTTON_SECONDARY = 1;
	private static final int BUTTON_DANGER = 2;
	private static final int BUTTON_NEUTRAL = 3;
	private static final int DEFAULT_K_REC = 3;
	private static final int HASH_TAGS = 1;
	private static final int RE_TWEETS = 1;
	private static final int STOP_WORDS = 1;
	private static final String BEGIN_DATE = "2007-01-01";
	// private static final String END_DATE = "2017-01-01";
	private static String END_DATE;
	private static final String DEFAULT_DATASET = "RyersonU";
	public static final int COS_SIM = 0;
	public static final int K_MEANS = 1;
	public static final int SVM = 2;
	public static final int MLP = 3;
	public static final int Doc2Vec = 4;
	public static final int CommonNeighbors = 5;        // added by Sepide
	public static final int K_MEANSEUCLIDEAN = 6;   // added by Sepide 
	public static final int SCALABLE_SVM = 7;
	public static final int TEMPORAL_GRAPH_FOUNDATION = 8;
	public static final int zero = 0;               // added by Sepide
	public static final int one = 1;               // added by Sepide
	public static final int two = 2;               // added by Sepide
	public static final int three = 3;               // added by Sepide
	public static final int four = 4;                // added by Sepide
	public static final int FROM_DB = 1;
	public static final int FROM_TEXT = 0;
	public static final int FROM_GENERATION = 2;
	public static final int FROM_ARTIFICIAL = 3;
	public static final int FOLLOWEES = 1;
	public static final int FOLLOWERS = 2;
	public static final int DEFAULT_TWEETS_GENERATED = 3;
	public static final int DEFAULT_MAPPERS = 1;
	public static final int DEFAULT_REDUCERS = 1;
	public static final int DEFAULT_NUM_ARTIFICIAL_TWEETS = 0;
	public static final int FEATURE_EXTRACTION = 0;
	public static final int ALGORITHM_CLUSTERING = 1;
	public static final int CHANGE_TO_PERFORMANCE = 0;
	public static final int CHANGE_TO_USERSIM = 1;
	public static final int COLLECT_DATA = 4;
	public static final int START_USER_GEN_SIM =  5;
	public static final int RESET_EXPERIMENT = 6;
	private static final int APPLICATION_TAB_SIMULATOR = 0;
	private static final int APPLICATION_TAB_GRAPH_INSIGHTS = 1;
	private static final int APPLICATION_TAB_TGF_STUDIO = 2;
	private static final int APPLICATION_TAB_EVIDENCE_STUDIO = 3;
	private static final int APPLICATION_TAB_EXPERIMENT_OBSERVATORY = 4;
	private static final int APPLICATION_TAB_USER_STUDIO = 5;
	public static final String USERSIM_PANEL_ID = "Change to User Sim";
	public static final String PERFORMANCE_PANEL_ID = "Performance Measurement";
	private int simulationNo;    // added by Sepide 
	private int sepNum = 0;   // added by Sepide  71 was the index for journal-authors
	public int listSize;

	public JComboBox<String> algorithmSelectionBox;
	private JComboBox<RecommendationTargetMode> recommendationTargetModeBox;
	private JComboBox<String> mapperSelectionBox;
	private JComboBox<String> reducerSelectionBox;
	public JComboBox<String> simulationSelectionBox;             // added by Sepide
	private JLabel enterDatasetLabel;
	private JLabel numNodesLabel;
	private JLabel tweetLimitLabel;
	private JLabel beginDateLabel;
	private JLabel endDateLabel;
	private JLabel recommendationLabel;
	private JLabel svmBatchUsersLabel;
	private JLabel algorithmLabel;
	private JLabel recommendationTargetModeLabel;
	private JLabel setupModelDescriptionLabel;
	private BadgeLabel setupOutputBadge;
	private BadgeLabel setupBatchBadge;
	private BadgeLabel setupAvailabilityBadge;
	private JPanel simulatorDoc2VecCachePanel;
	private JPanel simulatorDoc2VecCacheSection;
	private BadgeLabel simulatorDoc2VecCacheBadge;
	private JLabel simulatorDoc2VecCacheDetailLabel;
	private JLabel simulatorDoc2VecRowsLabel;
	private JLabel simulatorDoc2VecUsersLabel;
	private JLabel simulatorDoc2VecVectorsLabel;
	private JLabel simulatorDoc2VecSizeLabel;
	private JLabel simulatorDoc2VecKeyLabel;
	private JButton simulatorDoc2VecRefreshButton;
	private JButton simulatorDoc2VecRemoveButton;
	private JLabel numReducersLabel;
	private JLabel numMappersLabel;
	private JLabel reducerChoiceLabel;
	private JLabel mapperChoiceLabel;
	private JLabel labNameLabel;
	private JLabel userGenTitleLabel;
	private JLabel userGenProgressLabel;
	private JLabel performanceProgressLabel;
	private JLabel performanceStatusContextLabel;
	private JLabel userGenStatusContextLabel;
	private StatusPill performanceStatusPill;
	private StatusPill userGenStatusPill;
	private SourceChoiceButton textSourceButton;
	private SourceChoiceButton databaseSourceButton;
	private SourceChoiceButton generatedSourceButton;
	private SourceChoiceButton artificialSourceButton;
	private JLabel sourceSelectionIconLabel;
	private JLabel sourceSelectionTitleLabel;
	private JLabel sourceSelectionDetailLabel;
	private int selectedSourceMode = FROM_TEXT;
	private JLabel simulationNumber;      // added by Sepide
	private JTextField enterDatasetField;
	public JTextField numNodesField;
	private JTextField tweetLimitField;
	private JTextField beginDateField;
	private JTextField endDateField;
	public JTextField recommendationField;
	public JTextField svmBatchUsersField;
	private JTextField maxHeapField;
	private NumericStepper numNodesStepper;
	private NumericStepper tweetLimitStepper;
	private NumericStepper recommendationStepper;
	private NumericStepper batchUsersStepper;
	private NumericStepper maxHeapStepper;
	private JTextField numReducersField;
	private JTextField numMappersField;
	public JButton initializeButton;
	private JButton quitButton;
	private JButton resetExperimentButton;
	private JButton applyHeapButton;
	private JButton algorithmSettingsButton;
	private JButton nodeCapacityButton;
	public JButton simulationButton;  // added by Sepide
	public JButton startButton;
	private JButton getUsersButton;
	private JButton changePerformanceButton;
	private JButton changeUserSimButton;
	private JButton helpUserSimButton;
	private JButton exitButton;
	private JButton dataCollectionButton;
	private JButton loadCorpusButton;
	private JButton setSimulatedTweetsButton;
	private JButton startUserSimButton;
	private JTextArea recommendationArea;
	private JTextArea resultArea;
	private JTextArea runAccuracyArea;
	private JTextArea previousResultArea;
	private JTextArea clusterResultArea;
	private JTextArea userGenTweetsResultArea;
	private JTextArea grabTwitterResultArea;
	private JPanel recommendationCardsPanel;
	private JLabel recommendationOverviewLabel;
	private JLabel recommendationContextLabel;
	private JComponent runAccuracyPanel;
	private JLabel runAccuracySummaryLabel;
	private JLabel runAccuracyMeasurementLabel;
	private JPanel recommendationEmptyState;
	private JTabbedPane outputTabs;
	private int recommendationUserCount;
	private int recommendationItemCount;
	private int recommendationIssueCount;
	private int currentRunAccuracyLineCount;
	private int currentRunAccuracyAlgorithm = K_MEANS;
	private boolean simulatorInitialized;
	private boolean simulatorInitializationInProgress;
	private TitledBorder agentsListTitle;
	private TitledBorder resultsTitle;
	private TitledBorder previousResultsTitle;
	private TitledBorder initializationTitle;
	private TitledBorder labNameTitle;
	private TitledBorder commandsTitle;
	private TitledBorder chooseDatasetTitle;
	private TitledBorder textProcessingTitle;
	private TitledBorder recommendationTitle;
	private TitledBorder clusterTitle;
	private TitledBorder userGenTweetsResultTitle;
	private TitledBorder grabTwitterResultTitle;
	private JCheckBox removeHashTags;
	private JCheckBox removeRetweets;
	private JCheckBox removeStopWords;
	private JCheckBox simulateTweetDelay;
	private JCheckBox generateGraphOutput;
	private JLabel algorithmSettingsProfileLabel;
	private JLabel algorithmSettingsSummaryLabel;
	private AlgorithmParameterSettings algorithmParameterSettings =
			AlgorithmParameterSettings.defaults();
	private JLabel runtimeMemoryLabel;
	private JLabel runtimeSchedulerLabel;
	private JLabel nodeCapacitySummaryLabel;
	private StatusPill runtimeStatusPill;
	private DefaultListModel<String> agentsList;
	public JList showAgentsList;
	private Border blackBorder;
	private JMenuItem fromTextMenu;
	private JMenuItem fromDbMenu;
	private JMenuItem fromGenerationMenu;
	private JMenuItem fromArtificialMenu;
	private JMenuItem helpPerformanceMenu;
	private JMenuItem helpUserGenSimMenu;
	private JPanel cards; //panel to hold the cards in card layout
	private JTabbedPane applicationTabs;
	private JPanel applicationShell;
	private ThemeChromePanel themeChromePanel;
	private JLabel themeModeLabel;
	private ThemeToggleButton themeToggleButton;
	private PopulationSweepPanel populationSweepPanel;
	private UciRetailImportPanel uciRetailImportPanel;
	private SimulatorHelpPanel simulatorHelpPanel;
	private GraphInsightsPanel graphInsightsPanel;
	private TemporalGraphFoundationPanel temporalGraphFoundationPanel;
	private RecommendationEvidenceStudioPanel recommendationEvidenceStudioPanel;
	private ExperimentObservatoryPanel experimentObservatoryPanel;
	private volatile boolean observatoryAutomationMode;
	private volatile String observatoryAutomationRunId = "";
	private volatile ObservatoryAutomationListener observatoryAutomationListener;
	private ActualSchedulerPanel actualSchedulerPanel;
	private LiveWorkloadAdjustmentPanel liveWorkloadAdjustmentPanel;
	private SchedulerLabPanel schedulerLabPanel;
	private DynamicQueueLabPanel dynamicQueueLabPanel;
	public JFileChooser fileChooser;  // private access changed to public by Sepide
	public ArrayList<String> usersRec = new ArrayList<String>(); //Users looking for recommendation  added by Sepide

	private ArrayList<Timings> timings;
	public Timings currentTiming;

	private String referenceUser;
	private String beginDate;
	private String endDate;
	private String recommendeeName; //Selected from list
	private String recommendeeName2; // added by Sepide
	private String nameFolloweesToGrab; //Followee name(s) separated by comma to get from Twitter API
	private int numNodes;
	private int kRecommend;
	private int hashTags;
	private int retweets;
	private int stopWords;
	private int simulationIteration;
	public int indexToRecommend;
	private int[] indexArray;  // added by Sepide
	//S private int[] indexToRecommend;   //added by Sepide
	private int indexToRecommend2;  // added by Sepide 
	private int tweetLimit;
	private int algorithmRec;
	private int numFollowers;
	private int numFollowees;
	private int numTweetsGenerated;
	private int numReducers;
	private int numMappers;
	private int reducerChoice;
	private int mapperChoice;
	private int numFollowersToGrab; //Number of followers to get from Twitter API
	private int numArtificialTweets; //Number of artificial tweets to generate
	private Long userGenerationSeed;
	private int[] a;   // added by Sepide
	private int[] b;   // added by Sepide
	private int[] c;   // added by Sepide
	private Object[] names;   // added by Sepide
	private volatile int simulatorDoc2VecCacheRequest;
	private volatile Doc2VecCacheAdmin.CacheInfo simulatorDoc2VecCacheInfo;
	

	private int countRecServersTP;
	private int countRecServersTfidf;
	private int countRecServersAlgorithm;
	private double currentMaxTPTime;
	private double currentMaxTfidfTime;
	private double currentMaxAlgorithmTime;
	private long currentMessagePassingTime;
	private long currentMessagePassingCost;

	interface ObservatoryAutomationListener
	{
		void initializationFinished(String runId);

		void simulationFinished(String runId, String message);

		void automationFailed(String runId, String phase, String message);
	}

	public ControllerAgentGui(ControllerAgent controller) {
		super("Multi-Agent System Simulator for Distributed Recommender System");

		installModernLookAndFeel();

		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Date date = new Date();
		END_DATE = dateFormat.format(date);
		
		myAgent = controller;
		simulationIteration = 0;
		agentsList = new DefaultListModel();
		blackBorder = BorderFactory.createLineBorder(Color.BLACK, 1);
		numNodes = 1;
		tweetLimit = 0;
		numReducers = DEFAULT_REDUCERS;
		numMappers = DEFAULT_MAPPERS;
		beginDate = BEGIN_DATE;
		endDate = END_DATE;
		kRecommend = DEFAULT_K_REC;
		referenceUser = DEFAULT_DATASET;
		hashTags = HASH_TAGS;
		retweets = RE_TWEETS;
		stopWords = STOP_WORDS;
		algorithmRec = K_MEANS;
		numFollowees = FOLLOWEES;
		numFollowers = FOLLOWERS;
		numTweetsGenerated = DEFAULT_TWEETS_GENERATED;
		mapperChoice = FEATURE_EXTRACTION;
		reducerChoice = ALGORITHM_CLUSTERING;
		numArtificialTweets = DEFAULT_NUM_ARTIFICIAL_TWEETS;
		userGenerationSeed = null;

		fileChooser = new JFileChooser();
		fileChooser.setCurrentDirectory(new File(System.getProperty("user.dir")));
		// Action details = fileChooser.getActionMap().get("viewTypeDetails");
		// details.actionPerformed(null);
		
		
		indexToRecommend = 0;
		indexToRecommend2 = 0;    // added by Sepide 
		timings = new ArrayList<Timings>();
		countRecServersTP = 0;
		countRecServersTfidf = 0;
		countRecServersAlgorithm = 0;
		currentMaxTPTime = 0;
		currentMaxTfidfTime = 0;
		currentMaxAlgorithmTime = 0;
		currentMessagePassingTime = 0;
		
		createTextAreas();
			createTextFields();
			createInitializationBoxes();
			createButtons();
			createList();
			createModernPanels();
			RecommendationMemoryCoordinator.configureRequestedParallelism(numNodes);
			NodeCapacityRegistry.configureForNodeCount(
					numNodes,
					currentHeapSetting());
			RecommendationMemoryCoordinator.setStatusListener(
					new RecommendationMemoryCoordinator.StatusListener() {
						public void onStatus(
								RecommendationMemoryCoordinator.Snapshot snapshot)
						{
							updateRuntimeResourceStatus(snapshot);
						}
					});

		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				shutDown();
			}
		});


		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		double widthScreen = screenSize.getWidth();
		double heightScreen = screenSize.getHeight();

		if (widthScreen >= FRAME_WIDTH && heightScreen >= FRAME_HEIGHT)
		{
			setSize(FRAME_WIDTH, FRAME_HEIGHT);
		}
		else
		{
			double widthRatio = 0.0;
			double heightRatio = 0.0;

			if (widthScreen < FRAME_WIDTH && heightScreen < FRAME_HEIGHT)
			{
				widthRatio = widthScreen/FRAME_WIDTH;
				heightRatio = heightScreen/FRAME_HEIGHT;
			}
			else if (widthScreen < FRAME_WIDTH && heightScreen >= FRAME_HEIGHT)
			{
				widthRatio = widthScreen/FRAME_WIDTH;
				heightRatio = widthRatio;
			}
			else if (widthScreen >= FRAME_WIDTH && heightScreen < FRAME_HEIGHT)
			{
				heightRatio = heightScreen/FRAME_HEIGHT;
				widthRatio = heightRatio;
			}

			setSize((int) (FRAME_WIDTH * widthRatio), (int)(FRAME_HEIGHT * heightRatio + 75));
		}
			this.setResizable(true);
			setLocationByPlatform(false);
			setLocationRelativeTo(null);
			applyStartupMaximizedWindow();
			}

			void revealAfterLaunch()
			{
				Runnable reveal = new Runnable() {
					public void run() {
						setVisible(true);
						applyStartupMaximizedWindow();
						toFront();
						requestFocus();
						requestFocusInWindow();
					boolean previousAlwaysOnTop = isAlwaysOnTop();
					try
					{
						setAlwaysOnTop(true);
						setAlwaysOnTop(previousAlwaysOnTop);
					}
					catch (SecurityException ignored)
					{
						// Some platforms do not allow changing the foreground window.
					}
					System.out.println("ControllerAgentGui visible at "
							+ getLocation().x + "," + getLocation().y
							+ " size " + getWidth() + "x" + getHeight());
				}
			};
			if (SwingUtilities.isEventDispatchThread())
			{
				reveal.run();
			}
			else
			{
				SwingUtilities.invokeLater(reveal);
			}
			}

			private void applyStartupMaximizedWindow()
			{
				setState(Frame.NORMAL);
				setExtendedState((getExtendedState() & ~Frame.ICONIFIED) | Frame.MAXIMIZED_BOTH);
			}

			private void installModernLookAndFeel()
			{
			try
			{
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			}
			catch (Exception e)
			{
				try
				{
					for (LookAndFeelInfo info : UIManager.getInstalledLookAndFeels())
					{
						if ("Nimbus".equals(info.getName()))
						{
							UIManager.setLookAndFeel(info.getClassName());
							break;
						}
					}
				}
				catch (Exception ignored)
				{
					// Keep Swing's default look and feel if the platform theme is unavailable.
				}
			}
				Color dialogBackground = new Color(
						APP_SURFACE.getRed(),
						APP_SURFACE.getGreen(),
						APP_SURFACE.getBlue());
			UIManager.put("control", dialogBackground);
			UIManager.put("info", dialogBackground);
			UIManager.put("nimbusBase", APP_BLUE);
			UIManager.put("nimbusFocus", new Color(80, 150, 255));
			UIManager.put("OptionPane.background", dialogBackground);
			UIManager.put("Panel.background", dialogBackground);
			UIManager.put("TextField.selectionBackground", APP_SELECTION);
			UIManager.put("TextArea.selectionBackground", new Color(55, 116, 184));
		}

		private Font appFont(int style, int size)
		{
			return new Font("SansSerif", style, size);
		}

		private void styleLabel(JLabel label)
		{
			label.setForeground(APP_MUTED_TEXT);
			label.setFont(appFont(Font.BOLD, 12));
			label.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 4));
		}

		private void styleTitleLabel(JLabel label, int size)
		{
			label.setForeground(APP_TEXT);
			label.setFont(appFont(Font.BOLD, size));
			label.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
		}

		private void styleTextArea(JTextArea textArea, boolean highlight)
		{
			textArea.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
			textArea.setEditable(false);
			textArea.setLineWrap(true);
			textArea.setWrapStyleWord(true);
			textArea.setFont(appFont(highlight ? Font.BOLD : Font.PLAIN, 12));
			textArea.setBackground(highlight ? APP_SURFACE : APP_TEXT_AREA);
			textArea.setForeground(highlight ? APP_TEXT : APP_TEXT_AREA_TEXT);
			textArea.setCaretColor(highlight ? APP_TEXT : APP_TEXT_AREA_TEXT);
			textArea.setSelectionColor(highlight ? APP_SELECTION : new Color(55, 116, 184));
			textArea.setSelectedTextColor(highlight ? APP_TEXT : Color.WHITE);
		}

		private void styleTextField(JTextField textField)
		{
			textField.setFont(appFont(Font.PLAIN, 13));
			textField.setForeground(APP_TEXT);
			textField.setBackground(APP_SURFACE);
			textField.setCaretColor(APP_BLUE);
			textField.setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createLineBorder(APP_BORDER, 1),
					BorderFactory.createEmptyBorder(7, 9, 7, 9)));
		}

		private void styleComboBox(JComboBox<?> comboBox)
		{
			comboBox.setFont(appFont(Font.BOLD, 13));
			comboBox.setForeground(APP_TEXT);
			comboBox.setBackground(APP_SURFACE);
			comboBox.setBorder(BorderFactory.createLineBorder(APP_BORDER, 1));
			comboBox.setPreferredSize(new Dimension(180, 40));
		}

		private void styleCheckBox(JCheckBox checkBox)
		{
			checkBox.setOpaque(false);
			checkBox.setForeground(APP_TEXT);
			checkBox.setFont(appFont(Font.BOLD, 12));
			checkBox.setFocusPainted(false);
			checkBox.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
		}

		private JButton createModernButton(String text, int kind)
		{
			return createModernButton(text, kind, null);
		}

		private JButton createModernButton(String text, int kind, LineIconType iconType)
		{
			Color base = APP_NEUTRAL;
			Color hover = APP_NEUTRAL_HOVER;
			Color pressed = new Color(216, 224, 235);
			Color foreground = APP_TEXT;
			if (kind == BUTTON_PRIMARY)
			{
				base = APP_BLUE;
				hover = APP_BLUE_HOVER;
				pressed = new Color(0, 88, 184);
				foreground = Color.WHITE;
			}
			else if (kind == BUTTON_SECONDARY)
			{
				base = APP_GREEN;
				hover = APP_GREEN_HOVER;
				pressed = new Color(17, 126, 88);
				foreground = Color.WHITE;
			}
			else if (kind == BUTTON_DANGER)
			{
				base = APP_RED;
				hover = APP_RED_HOVER;
				pressed = new Color(178, 39, 39);
				foreground = Color.WHITE;
			}
			ModernButton button = new ModernButton(text, base, hover, pressed, foreground);
			if (iconType != null)
			{
				button.setIcon(new LineIcon(iconType, 18));
			}
			return button;
		}

		private void styleList()
		{
			showAgentsList.setFont(appFont(Font.PLAIN, 13));
			showAgentsList.setBackground(APP_SURFACE);
			showAgentsList.setForeground(APP_TEXT);
				showAgentsList.setSelectionBackground(APP_SELECTION);
				showAgentsList.setSelectionForeground(StudioNightTheme.adaptiveColor(
						new Color(0, 68, 136), StudioNightTheme.NIGHT_TEXT));
				showAgentsList.setFixedCellHeight(46);
				showAgentsList.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
				showAgentsList.setCellRenderer(new ModernUserCellRenderer());
			}

		private void styleScrollPane(JScrollPane scrollPane)
		{
			scrollPane.setOpaque(false);
			scrollPane.getViewport().setOpaque(false);
			scrollPane.setBorder(BorderFactory.createEmptyBorder());
			scrollPane.getVerticalScrollBar().setUnitIncrement(14);
		}

		private TitledBorder createModernTitle(String title)
		{
			TitledBorder titledBorder = BorderFactory.createTitledBorder(
					BorderFactory.createEmptyBorder(), title);
			titledBorder.setTitleJustification(TitledBorder.LEFT);
			titledBorder.setTitleFont(appFont(Font.BOLD, 13));
			titledBorder.setTitleColor(APP_MUTED_TEXT);
			return titledBorder;
		}

		private SurfacePanel createSurfacePanel(LayoutManager layout)
		{
			SurfacePanel panel = new SurfacePanel(layout, APP_SURFACE);
			panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
			return panel;
		}

		private SurfacePanel createSurfacePanel(LayoutManager layout, Color fillColor)
		{
			SurfacePanel panel = new SurfacePanel(layout, fillColor);
			panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
			return panel;
		}

		private JLabel createPanelTitle(String text)
		{
			JLabel label = new JLabel(text);
			label.setFont(appFont(Font.BOLD, 15));
			label.setForeground(APP_TEXT);
			label.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
			return label;
		}

		private JLabel createHeroTitle(String text)
		{
			JLabel label = new JLabel(text);
			label.setFont(appFont(Font.BOLD, 28));
			label.setForeground(APP_TEXT);
			label.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));
			return label;
		}

		private JPanel createAttributionLine(String role, String name)
		{
			JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
			row.setOpaque(false);
			row.setAlignmentX(Component.LEFT_ALIGNMENT);

			JLabel roleLabel = new JLabel(role.toUpperCase(Locale.US));
			roleLabel.setFont(appFont(Font.BOLD, 9));
			roleLabel.setForeground(APP_MUTED_TEXT);
			roleLabel.setBorder(BorderFactory.createEmptyBorder(1, 0, 0, 0));

			JLabel nameLabel = new JLabel(name);
			nameLabel.setFont(appFont(Font.BOLD, 12));
			nameLabel.setForeground(APP_TEXT);

			row.add(roleLabel);
			row.add(nameLabel);
			return row;
		}

		private JPanel createAttributionBlock()
		{
			JPanel block = new JPanel();
			block.setOpaque(false);
			block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
			block.setAlignmentX(Component.LEFT_ALIGNMENT);
			block.setBorder(BorderFactory.createEmptyBorder(5, 1, 0, 0));
			block.add(createAttributionLine(
					"Faculty Advisor",
					"Dr. Abdolreza Abhari"));
			block.add(Box.createRigidArea(new Dimension(0, 3)));
			block.add(createAttributionLine(
					"Developer",
					"Mohsen Alavi"));
			return block;
		}

		private JLabel createHeaderSubtitle(String text)
		{
			JLabel label = new JLabel(text);
			label.setFont(appFont(Font.PLAIN, 12));
			label.setForeground(APP_MUTED_TEXT);
			label.setAlignmentX(Component.LEFT_ALIGNMENT);
			return label;
		}

		private JLabel createSubtleLabel(String text)
		{
			JLabel label = new JLabel(text);
			label.setFont(appFont(Font.BOLD, 12));
			label.setForeground(APP_MUTED_TEXT);
			return label;
		}

		private BadgeLabel createBadge(String text, Color accentColor)
		{
			return new BadgeLabel(text, accentColor);
		}

		private void addFieldPair(JPanel panel, JLabel label, JComponent component)
		{
			panel.add(label);
			component.setPreferredSize(new Dimension(150, 34));
			panel.add(component);
		}

		private JLabel createSectionEyebrow(String text)
		{
			JLabel label = new JLabel(text.toUpperCase(Locale.US));
			label.setFont(appFont(Font.BOLD, 11));
			label.setForeground(APP_BLUE);
			label.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
			return label;
		}

		private JPanel createFieldBlock(String labelText, String tooltip, JComponent component)
		{
			JPanel block = new JPanel(new BorderLayout(0, 7));
			block.setOpaque(false);
			JLabel label = new JLabel(labelText);
			label.setFont(appFont(Font.BOLD, 12));
			label.setForeground(APP_MUTED_TEXT);
			label.setLabelFor(component);
			if (tooltip != null)
			{
				label.setToolTipText(tooltip);
				component.setToolTipText(tooltip);
			}
			block.add(label, BorderLayout.NORTH);
			block.add(component, BorderLayout.CENTER);
			return block;
		}

		private JPanel createSetupSection(String title, JComponent content)
		{
			JPanel section = new JPanel(new BorderLayout(0, 4));
			section.setOpaque(false);
			section.add(createSectionEyebrow(title), BorderLayout.NORTH);
			section.add(content, BorderLayout.CENTER);
			section.setBorder(BorderFactory.createMatteBorder(
					0, 0, 1, 0, new Color(222, 228, 236)));
			return section;
		}

		private void addGridComponent(
				JPanel panel,
				Component component,
				int gridX,
				double weightX,
				Insets insets)
		{
			GridBagConstraints constraints = new GridBagConstraints();
			constraints.gridx = gridX;
			constraints.gridy = 0;
			constraints.weightx = weightX;
			constraints.fill = GridBagConstraints.HORIZONTAL;
			constraints.anchor = GridBagConstraints.NORTHWEST;
			constraints.insets = insets;
			panel.add(component, constraints);
		}

		private JTabbedPane createOutputTabs()
		{
			JTabbedPane tabs = new JTabbedPane();
			outputTabs = tabs;
			tabs.setFont(appFont(Font.BOLD, 12));
			tabs.setForeground(APP_TEXT);
			tabs.setBackground(APP_SURFACE);
			tabs.setBorder(BorderFactory.createEmptyBorder());

				JScrollPane resultScrollPane = new JScrollPane(resultArea);
				styleScrollPane(resultScrollPane);
				JComponent liveTimingView = createLiveTimingView(resultScrollPane);
				JScrollPane previousResultScrollPane = new JScrollPane(previousResultArea);
				styleScrollPane(previousResultScrollPane);
				JComponent recommendationResultsView = createRecommendationResultsView();
				JComponent nodeExecutionView = new NodeExecutionDashboard();

				tabs.addTab("Live Timing", new LineIcon(LineIconType.ITERATIONS, 16), liveTimingView);
				tabs.addTab("Previous", new LineIcon(LineIconType.RESET, 16), previousResultScrollPane);
				tabs.addTab("Recommendations", new LineIcon(LineIconType.RECOMMENDER, 16), recommendationResultsView);
				tabs.addTab("Nodes", new LineIcon(LineIconType.NODES, 16), nodeExecutionView);
				return tabs;
			}

		private JComponent createLiveTimingView(JScrollPane resultScrollPane)
		{
			JPanel view = new JPanel(new BorderLayout(0, 10));
			view.setBackground(APP_TEXT_AREA);
			view.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
			view.add(createRunAccuracyPanel(), BorderLayout.NORTH);
			view.add(resultScrollPane, BorderLayout.CENTER);
			return view;
		}

		private JComponent createRunAccuracyPanel()
		{
			SurfacePanel panel = createSurfacePanel(new BorderLayout(10, 6), APP_SURFACE_ALT);
			runAccuracyPanel = panel;
			panel.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

			JPanel header = new JPanel(new BorderLayout(10, 2));
			header.setOpaque(false);
			JLabel title = new JLabel("Run Accuracy");
			title.setForeground(APP_TEXT);
			title.setFont(appFont(Font.BOLD, 13));
			runAccuracySummaryLabel = new JLabel("Waiting for run accuracy");
			runAccuracySummaryLabel.setForeground(APP_MUTED_TEXT);
			runAccuracySummaryLabel.setFont(appFont(Font.BOLD, 11));
			header.add(title, BorderLayout.WEST);
			header.add(runAccuracySummaryLabel, BorderLayout.CENTER);

			runAccuracyMeasurementLabel = new JLabel(
					runAccuracyMeasurementText());
			runAccuracyMeasurementLabel.setForeground(APP_MUTED_TEXT);
			runAccuracyMeasurementLabel.setFont(appFont(Font.PLAIN, 10));

			JScrollPane accuracyScroll = new JScrollPane(runAccuracyArea);
			styleScrollPane(accuracyScroll);
			accuracyScroll.setPreferredSize(new Dimension(100, 58));

			panel.add(header, BorderLayout.NORTH);
			panel.add(accuracyScroll, BorderLayout.CENTER);
			panel.add(runAccuracyMeasurementLabel, BorderLayout.SOUTH);
			updateRunAccuracyPanelVisibility();
			return panel;
		}

		private JComponent createRecommendationResultsView()
		{
			JPanel dashboard = new JPanel(new BorderLayout(0, 12));
			dashboard.setBackground(APP_SURFACE_ALT);
			dashboard.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

			JPanel summary = new JPanel(new BorderLayout(12, 0));
			summary.setOpaque(false);
			summary.setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createMatteBorder(0, 0, 1, 0, APP_BORDER),
					BorderFactory.createEmptyBorder(2, 2, 12, 2)));

			JPanel summaryText = new JPanel();
			summaryText.setOpaque(false);
			summaryText.setLayout(new BoxLayout(summaryText, BoxLayout.Y_AXIS));
			JLabel title = new JLabel("Recommendation Results");
			title.setForeground(APP_TEXT);
			title.setFont(appFont(Font.BOLD, 17));
			recommendationContextLabel = new JLabel("Waiting for simulation results");
			recommendationContextLabel.setForeground(APP_MUTED_TEXT);
			recommendationContextLabel.setFont(appFont(Font.PLAIN, 12));
			summaryText.add(title);
			summaryText.add(Box.createRigidArea(new Dimension(0, 3)));
			summaryText.add(recommendationContextLabel);

			recommendationOverviewLabel = new JLabel("0 TARGETS  |  0 MATCHES");
			recommendationOverviewLabel.setForeground(APP_BLUE);
			recommendationOverviewLabel.setFont(appFont(Font.BOLD, 11));
			recommendationOverviewLabel.setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createLineBorder(new Color(169, 207, 245), 1),
					BorderFactory.createEmptyBorder(7, 10, 7, 10)));
			recommendationOverviewLabel.setOpaque(true);
			recommendationOverviewLabel.setBackground(new Color(236, 246, 255));

			summary.add(summaryText, BorderLayout.CENTER);
			summary.add(recommendationOverviewLabel, BorderLayout.EAST);

			recommendationCardsPanel = new JPanel();
			recommendationCardsPanel.setOpaque(false);
			recommendationCardsPanel.setLayout(new BoxLayout(recommendationCardsPanel, BoxLayout.Y_AXIS));
			recommendationEmptyState = createRecommendationEmptyState();
			recommendationCardsPanel.add(recommendationEmptyState);

			JPanel cardsHolder = new JPanel(new BorderLayout());
			cardsHolder.setBackground(APP_SURFACE_ALT);
			cardsHolder.add(recommendationCardsPanel, BorderLayout.NORTH);

			JScrollPane scrollPane = new JScrollPane(cardsHolder);
			scrollPane.setBorder(BorderFactory.createEmptyBorder());
			scrollPane.getViewport().setBackground(APP_SURFACE_ALT);
			scrollPane.getVerticalScrollBar().setUnitIncrement(18);
			scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

			dashboard.add(summary, BorderLayout.NORTH);
			dashboard.add(scrollPane, BorderLayout.CENTER);
			return dashboard;
		}

		private JPanel createRecommendationEmptyState()
		{
			JPanel emptyState = new JPanel();
			emptyState.setOpaque(false);
			emptyState.setLayout(new BoxLayout(emptyState, BoxLayout.Y_AXIS));
			emptyState.setBorder(BorderFactory.createEmptyBorder(72, 16, 24, 16));

			JLabel icon = new JLabel(new LineIcon(LineIconType.RECOMMENDER, 34));
			icon.setForeground(new Color(145, 162, 181));
			icon.setAlignmentX(Component.CENTER_ALIGNMENT);
			JLabel title = new JLabel("No recommendations yet");
			title.setForeground(APP_TEXT);
			title.setFont(appFont(Font.BOLD, 15));
			title.setAlignmentX(Component.CENTER_ALIGNMENT);
			JLabel detail = new JLabel("Completed results will appear here by target user.");
			detail.setForeground(APP_MUTED_TEXT);
			detail.setFont(appFont(Font.PLAIN, 12));
			detail.setAlignmentX(Component.CENTER_ALIGNMENT);

			emptyState.add(icon);
			emptyState.add(Box.createRigidArea(new Dimension(0, 12)));
			emptyState.add(title);
			emptyState.add(Box.createRigidArea(new Dimension(0, 5)));
			emptyState.add(detail);
			return emptyState;
		}

		private static ArrayList<RecommendationResult> parseRecommendationResults(String recommendationText)
		{
			ArrayList<RecommendationResult> parsedResults = new ArrayList<RecommendationResult>();
			if (recommendationText == null || recommendationText.trim().length() == 0)
			{
				return parsedResults;
			}

			RecommendationResult currentResult = null;
			String[] lines = recommendationText.split("\\r?\\n");
			String headerMarker = "Recommendations for ";
			for (String rawLine : lines)
			{
				String line = rawLine.trim();
				int headerIndex = line.indexOf(headerMarker);
				if (headerIndex >= 0)
				{
					String targetUser = line.substring(headerIndex + headerMarker.length()).replace("=", "").trim();
					if (targetUser.length() > 0)
					{
						currentResult = new RecommendationResult(targetUser);
						parsedResults.add(currentResult);
					}
					continue;
				}

				if (currentResult != null && line.startsWith("Recommendation "))
				{
					int separatorIndex = line.indexOf(':');
					if (separatorIndex >= 0 && separatorIndex + 1 < line.length())
					{
						String recommendationPayload = line.substring(separatorIndex + 1).trim();
						String score = null;
						int scoreIndex = recommendationPayload.lastIndexOf("\tScore: ");
						if (scoreIndex >= 0)
						{
							score = recommendationPayload.substring(scoreIndex + "\tScore: ".length()).trim();
							recommendationPayload = recommendationPayload.substring(0, scoreIndex).trim();
						}
						String recommendationName = recommendationPayload;
						if (recommendationName.length() > 0)
						{
							currentResult.recommendations.add(new RecommendationMatch(recommendationName, score));
						}
					}
				}
			}
			return parsedResults;
		}

		private void addRecommendationResult(RecommendationResult result)
		{
			if (recommendationCardsPanel == null)
			{
				return;
			}
			recordRecommendationEvidence(result);
			if (temporalGraphFoundationPanel != null)
			{
				temporalGraphFoundationPanel.refreshFromRegistry();
			}
			if (recommendationUserCount == 0)
			{
				recommendationCardsPanel.removeAll();
			}
			else
			{
				recommendationCardsPanel.add(Box.createRigidArea(new Dimension(0, 10)));
			}

			recommendationUserCount++;
			recommendationItemCount += result.recommendations.size();
			if (result.status != RecommendationResultStatus.OK)
			{
				recommendationIssueCount++;
			}
			recommendationCardsPanel.add(new RecommendationCard(result));
			updateRecommendationOverview();
			setPerformanceStatus(
					"RECOMMENDING",
					"Received recommendations for " + recommendationUserCount
							+ " target" + (recommendationUserCount == 1 ? "" : "s")
							+ "; continuing the current run.",
					StatusTone.RUNNING);
			recommendationCardsPanel.revalidate();
			recommendationCardsPanel.repaint();
		}

		private void updateRecommendationOverview()
		{
			if (recommendationOverviewLabel == null || recommendationContextLabel == null)
			{
				return;
			}
			String targetWord = recommendationUserCount == 1 ? "TARGET" : "TARGETS";
			String matchWord = recommendationItemCount == 1 ? "MATCH" : "MATCHES";
			recommendationOverviewLabel.setText(
					recommendationUserCount + " " + targetWord + "  |  "
							+ recommendationItemCount + " " + matchWord);
			if (outputTabs != null && outputTabs.getTabCount() > 2)
			{
				outputTabs.setTitleAt(2, "Recommendations (" + recommendationUserCount + ")");
			}

			Object selectedAlgorithm = algorithmSelectionBox == null ? null : algorithmSelectionBox.getSelectedItem();
			String algorithmName = selectedAlgorithm == null ? "Recommendation algorithm" : selectedAlgorithm.toString();
			String runContext = simulationIteration > 0
					? algorithmName + "  |  Simulation iteration " + simulationIteration
					: algorithmName + " ranked output";
			recommendationContextLabel.setText(runContext);
		}

		private void clearRecommendationResults()
		{
			recommendationArea.setText("");
			recommendationUserCount = 0;
			recommendationItemCount = 0;
			recommendationIssueCount = 0;
			if (recommendationCardsPanel != null)
			{
				recommendationCardsPanel.removeAll();
				recommendationCardsPanel.add(recommendationEmptyState);
				recommendationCardsPanel.revalidate();
				recommendationCardsPanel.repaint();
			}
			if (recommendationOverviewLabel != null)
			{
				recommendationOverviewLabel.setText("0 TARGETS  |  0 MATCHES");
			}
			if (recommendationContextLabel != null)
			{
				recommendationContextLabel.setText("Waiting for simulation results");
			}
			if (outputTabs != null && outputTabs.getTabCount() > 2)
			{
				outputTabs.setTitleAt(2, "Recommendations");
			}
			if (recommendationEvidenceStudioPanel != null)
			{
				recommendationEvidenceStudioPanel.clearRecommendations();
			}
			TemporalGraphFoundationRegistry.clear();
			if (temporalGraphFoundationPanel != null)
			{
				temporalGraphFoundationPanel.refreshFromRegistry();
			}
		}

		private void recordRecommendationEvidence(RecommendationResult result)
		{
			if (recommendationEvidenceStudioPanel == null
					|| result == null
					|| result.recommendations.isEmpty())
			{
				return;
			}
			SimulatorHelpPanel.Snapshot snapshot = buildSimulatorHelpSnapshot();
			RecommendationCandidateType candidateType = result.candidateType;
			if (candidateType == null)
			{
				candidateType = snapshot.outputType == null
						? RecommendationCandidateType.USER : snapshot.outputType;
			}
			ArrayList<RecommendationEvidenceEngine.MatchEvidence> matches =
					new ArrayList<RecommendationEvidenceEngine.MatchEvidence>();
			int rank = 1;
			for (RecommendationMatch match : result.recommendations)
			{
				matches.add(new RecommendationEvidenceEngine.MatchEvidence(
						rank++,
						match.name,
						match.score,
						match.detail));
			}
			RecommendationEvidenceEngine.RecommendationEvent event =
					new RecommendationEvidenceEngine.RecommendationEvent(
							result.targetUser,
							candidateType,
							result.status,
							result.resultLabel,
							result.message,
							matches);
			recommendationEvidenceStudioPanel.recordRecommendation(snapshot, event);
		}

		private static String getInitials(String value)
		{
			if (value == null || value.trim().length() == 0)
			{
				return "?";
			}
			String trimmed = value.trim();
			StringBuilder initials = new StringBuilder();
			initials.append(Character.toUpperCase(trimmed.charAt(0)));
			for (int i = 1; i < trimmed.length() && initials.length() < 2; i++)
			{
				char current = trimmed.charAt(i);
				char previous = trimmed.charAt(i - 1);
				if (Character.isUpperCase(current) || Character.isWhitespace(previous)
						|| previous == '_' || previous == '-')
				{
					initials.append(Character.toUpperCase(current));
				}
			}
			if (initials.length() == 1 && trimmed.length() > 1)
			{
				initials.append(Character.toUpperCase(trimmed.charAt(1)));
			}
			return initials.toString();
		}

		private JTabbedPane createUserGenTabs()
		{
			JTabbedPane tabs = new JTabbedPane();
			tabs.setFont(appFont(Font.BOLD, 12));
			tabs.setForeground(APP_TEXT);
			tabs.setBackground(APP_SURFACE);
			tabs.setBorder(BorderFactory.createEmptyBorder());

			JScrollPane userGenTweetsScrollPane = new JScrollPane(userGenTweetsResultArea);
			styleScrollPane(userGenTweetsScrollPane);
			JScrollPane clusterResultScrollPane = new JScrollPane(clusterResultArea);
			styleScrollPane(clusterResultScrollPane);
			JScrollPane grabTwitterResultScrollPane = new JScrollPane(grabTwitterResultArea);
			styleScrollPane(grabTwitterResultScrollPane);

				tabs.addTab("Artificial Tweets", new LineIcon(LineIconType.ARTIFICIAL, 16), userGenTweetsScrollPane);
				tabs.addTab("Homophily", new LineIcon(LineIconType.GENERATED, 16), clusterResultScrollPane);
				tabs.addTab("Twitter Gathering", new LineIcon(LineIconType.COLLECT, 16), grabTwitterResultScrollPane);
				return tabs;
			}

		private JPanel createStatusPanel(JLabel progressLabel, boolean performancePanel)
		{
			SurfacePanel panel = createSurfacePanel(new BorderLayout(18, 0), APP_SURFACE);
			panel.setBorder(BorderFactory.createEmptyBorder(9, 14, 9, 14));

			JPanel progressGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
			progressGroup.setOpaque(false);
			JLabel progressTitle = new JLabel("Current progress");
			progressTitle.setForeground(APP_MUTED_TEXT);
			progressTitle.setFont(appFont(Font.BOLD, 11));

			StatusPill statusPill = new StatusPill("READY", StatusTone.READY);
			progressLabel.setText(performancePanel
					? "Choose a data source to begin a new experiment."
					: "User Generation Studio is ready.");
			progressLabel.setFont(appFont(Font.BOLD, 12));
			progressLabel.setForeground(APP_TEXT);
			progressGroup.add(progressTitle);
			progressGroup.add(statusPill);
			progressGroup.add(progressLabel);

			JLabel contextLabel = new JLabel(performancePanel
					? "No dataset loaded"
					: "No collection or generation task running");
			contextLabel.setFont(appFont(Font.PLAIN, 11));
			contextLabel.setForeground(APP_MUTED_TEXT);
			contextLabel.setHorizontalAlignment(SwingConstants.RIGHT);
			contextLabel.setIcon(new LineIcon(
					performancePanel ? LineIconType.DATABASE : LineIconType.STUDIO, 15));
			contextLabel.setIconTextGap(7);

			if (performancePanel)
			{
				performanceStatusPill = statusPill;
				performanceStatusContextLabel = contextLabel;
			}
			else
			{
				userGenStatusPill = statusPill;
				userGenStatusContextLabel = contextLabel;
			}

			panel.add(progressGroup, BorderLayout.CENTER);
			panel.add(contextLabel, BorderLayout.EAST);
			return panel;
		}

		private static Color blend(Color from, Color to, float amount)
		{
			float clamped = Math.max(0.0f, Math.min(1.0f, amount));
			int red = Math.round(from.getRed() + (to.getRed() - from.getRed()) * clamped);
			int green = Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * clamped);
			int blue = Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * clamped);
			int alpha = Math.round(from.getAlpha() + (to.getAlpha() - from.getAlpha()) * clamped);
			return new Color(red, green, blue, alpha);
		}

		private enum LineIconType
		{
			TEXT_FILE,
			DATABASE,
			GENERATED,
			ARTIFICIAL,
			USERS,
			INITIALIZE,
			PLAY,
			RESET,
			HELP,
			CLOSE,
			SWITCH,
			COLLECT,
			CORPUS,
			SETTINGS,
			STUDIO,
			RECOMMENDER,
			ITERATIONS,
			NODES
		}

		private static final class LineIcon implements Icon
		{
			private final LineIconType type;
			private final int size;

			LineIcon(LineIconType type, int size)
			{
				this.type = type;
				this.size = size;
			}

			public int getIconWidth()
			{
				return size;
			}

			public int getIconHeight()
			{
				return size;
			}

			public void paintIcon(Component component, Graphics graphics, int x, int y)
			{
				Graphics2D g2 = (Graphics2D) graphics.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g2.setColor(component.getForeground());
				int w = size;
				int h = size;
				int left = x + 2;
				int top = y + 2;
				int right = x + w - 3;
				int bottom = y + h - 3;

				switch (type)
				{
					case TEXT_FILE:
						g2.drawRoundRect(left + 1, top, w - 7, h - 4, 3, 3);
						g2.drawLine(right - 4, top, right, top + 4);
						g2.drawLine(right, top + 4, right, bottom);
						g2.drawLine(left + 5, top + 7, right - 3, top + 7);
						g2.drawLine(left + 5, top + 11, right - 5, top + 11);
						break;
					case DATABASE:
						g2.drawOval(left + 1, top, w - 6, 5);
						g2.drawLine(left + 1, top + 2, left + 1, bottom - 4);
						g2.drawLine(right, top + 2, right, bottom - 4);
						g2.drawOval(left + 1, bottom - 7, w - 6, 5);
						g2.drawArc(left + 1, top + 6, w - 6, 5, 0, -180);
						break;
					case GENERATED:
						g2.drawLine(x + w / 2, top, x + w / 2, bottom);
						g2.drawLine(left, y + h / 2, right, y + h / 2);
						g2.drawLine(left + 2, top + 2, right - 2, bottom - 2);
						g2.drawLine(right - 2, top + 2, left + 2, bottom - 2);
						break;
					case ARTIFICIAL:
						g2.drawRoundRect(left + 1, top + 2, w - 6, h - 7, 4, 4);
						g2.drawLine(left + 5, top, right - 4, top);
						g2.drawLine(left + 5, top, left + 5, top + 4);
						g2.drawLine(right - 4, top, right - 4, top + 4);
						g2.drawLine(left + 4, bottom - 6, right - 3, bottom - 6);
						g2.fillOval(left + 5, bottom - 10, 3, 3);
						g2.fillOval(right - 7, bottom - 11, 3, 3);
						break;
					case USERS:
						g2.drawOval(left + 2, top + 1, 5, 5);
						g2.drawOval(right - 6, top + 3, 5, 5);
						g2.drawArc(left, top + 8, 10, 8, 15, 150);
						g2.drawArc(right - 8, top + 10, 10, 7, 15, 150);
						break;
					case INITIALIZE:
						g2.drawOval(left + 4, top + 4, w - 12, h - 12);
						g2.drawLine(x + w / 2, top, x + w / 2, top + 4);
						g2.drawLine(x + w / 2, bottom - 4, x + w / 2, bottom);
						g2.drawLine(left, y + h / 2, left + 4, y + h / 2);
						g2.drawLine(right - 4, y + h / 2, right, y + h / 2);
						g2.drawOval(x + w / 2 - 2, y + h / 2 - 2, 4, 4);
						break;
					case PLAY:
						Polygon play = new Polygon();
						play.addPoint(left + 3, top + 1);
						play.addPoint(right - 1, y + h / 2);
						play.addPoint(left + 3, bottom - 1);
						g2.fillPolygon(play);
						break;
					case RESET:
						g2.drawArc(left + 1, top + 2, w - 6, h - 7, 40, 285);
						g2.drawLine(left + 3, top + 4, left + 3, top + 10);
						g2.drawLine(left + 3, top + 4, left + 9, top + 5);
						break;
					case HELP:
						g2.setFont(new Font("SansSerif", Font.BOLD, size - 4));
						FontMetrics metrics = g2.getFontMetrics();
						String question = "?";
						g2.drawString(question, x + (w - metrics.stringWidth(question)) / 2, y + h - 4);
						break;
					case CLOSE:
						g2.drawLine(left + 2, top + 2, right - 2, bottom - 2);
						g2.drawLine(right - 2, top + 2, left + 2, bottom - 2);
						break;
					case SWITCH:
						g2.drawLine(left, top + 5, right - 3, top + 5);
						g2.drawLine(right - 6, top + 2, right - 3, top + 5);
						g2.drawLine(right - 6, top + 8, right - 3, top + 5);
						g2.drawLine(right, bottom - 5, left + 3, bottom - 5);
						g2.drawLine(left + 6, bottom - 8, left + 3, bottom - 5);
						g2.drawLine(left + 6, bottom - 2, left + 3, bottom - 5);
						break;
					case COLLECT:
						g2.drawLine(x + w / 2, top, x + w / 2, bottom - 5);
						g2.drawLine(x + w / 2, bottom - 5, left + 4, bottom - 9);
						g2.drawLine(x + w / 2, bottom - 5, right - 4, bottom - 9);
						g2.drawLine(left + 2, bottom, right - 2, bottom);
						break;
					case CORPUS:
						g2.drawRoundRect(left, top + 2, w - 5, h - 5, 3, 3);
						g2.drawLine(left + 4, top + 2, left + 4, bottom);
						g2.drawLine(left + 7, top + 7, right - 3, top + 7);
						g2.drawLine(left + 7, top + 11, right - 5, top + 11);
						break;
					case SETTINGS:
						g2.drawLine(left, top + 4, right, top + 4);
						g2.drawLine(left, y + h / 2, right, y + h / 2);
						g2.drawLine(left, bottom - 3, right, bottom - 3);
						g2.fillOval(left + 4, top + 2, 4, 4);
						g2.fillOval(right - 8, y + h / 2 - 2, 4, 4);
						g2.fillOval(left + 8, bottom - 5, 4, 4);
						break;
					case STUDIO:
						g2.drawLine(left + 1, bottom - 1, right - 2, top + 2);
						g2.fillOval(right - 4, top, 4, 4);
						g2.drawLine(left + 1, top + 4, left + 1, top + 9);
						g2.drawLine(left - 1, top + 6, left + 4, top + 6);
						break;
					case RECOMMENDER:
						g2.fillOval(left + 1, top + 5, 5, 5);
						g2.fillOval(right - 6, top + 1, 5, 5);
						g2.fillOval(right - 5, bottom - 6, 5, 5);
						g2.drawLine(left + 6, top + 7, right - 6, top + 4);
						g2.drawLine(left + 6, top + 9, right - 5, bottom - 4);
						break;
					case ITERATIONS:
						g2.drawArc(left, top + 2, w - 5, h - 7, 35, 260);
						g2.drawLine(right - 4, top + 4, right, top + 5);
						g2.drawLine(right - 4, top + 4, right - 3, top + 9);
						g2.drawOval(x + w / 2 - 2, y + h / 2 - 2, 4, 4);
						break;
					case NODES:
						g2.drawLine(left + 4, top + 4, right - 4, top + 4);
						g2.drawLine(left + 4, top + 4, left + 4, bottom - 4);
						g2.drawLine(right - 4, top + 4, right - 4, bottom - 4);
						g2.drawLine(left + 4, bottom - 4, right - 4, bottom - 4);
						g2.fillOval(left + 1, top + 1, 6, 6);
						g2.fillOval(right - 7, top + 1, 6, 6);
						g2.fillOval(left + 1, bottom - 7, 6, 6);
						g2.fillOval(right - 7, bottom - 7, 6, 6);
						break;
					default:
						g2.drawRect(left, top, w - 5, h - 5);
						break;
				}
				g2.dispose();
			}
		}

		private static final class ThemeChromePanel extends JPanel
		{
			ThemeChromePanel(LayoutManager layout)
			{
				super(layout);
				setOpaque(false);
			}

			protected void paintComponent(Graphics graphics)
			{
				Graphics2D g2 = (Graphics2D) graphics.create();
				g2.setRenderingHint(
						RenderingHints.KEY_ANTIALIASING,
						RenderingHints.VALUE_ANTIALIAS_ON);
				int width = getWidth();
				int height = getHeight();
				if (StudioNightTheme.isNightMode())
				{
					g2.setPaint(new GradientPaint(
							0,
							0,
							StudioNightTheme.NIGHT_BACKGROUND,
							width,
							0,
							StudioNightTheme.NIGHT_SURFACE));
					g2.fillRect(0, 0, width, height);
					g2.setColor(StudioNightTheme.withAlpha(StudioNightTheme.NIGHT_BLUE, 44));
					g2.drawLine(0, height - 1, width, height - 1);
					g2.setColor(StudioNightTheme.withAlpha(StudioNightTheme.NIGHT_GREEN, 20));
					for (int x = 0; x < width; x += 42)
					{
						g2.drawLine(x, 0, x + 18, height);
					}
				}
				else
				{
					g2.setColor(new Color(255, 255, 255, 236));
					g2.fillRect(0, 0, width, height);
					g2.setColor(new Color(205, 216, 227));
					g2.drawLine(0, height - 1, width, height - 1);
				}
				g2.dispose();
				super.paintComponent(graphics);
			}
		}

		private final class ThemeToggleButton extends JToggleButton
		{
			ThemeToggleButton()
			{
				setPreferredSize(new Dimension(122, 34));
				setMinimumSize(new Dimension(122, 34));
				setFocusPainted(false);
				setContentAreaFilled(false);
				setBorderPainted(false);
				setOpaque(false);
				setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				setToolTipText("Day / Night");
				getAccessibleContext().setAccessibleName("Day Night theme toggle");
			}

			protected void paintComponent(Graphics graphics)
			{
				Graphics2D g2 = (Graphics2D) graphics.create();
				g2.setRenderingHint(
						RenderingHints.KEY_ANTIALIASING,
						RenderingHints.VALUE_ANTIALIAS_ON);
				boolean night = isSelected();
				int width = getWidth();
				int height = getHeight();
				Color trackStart = night
						? StudioNightTheme.NIGHT_PANEL_INK
						: new Color(244, 248, 253);
				Color trackEnd = night
						? StudioNightTheme.blend(
								StudioNightTheme.NIGHT_PANEL_INK,
								StudioNightTheme.NIGHT_BLUE,
								0.24f)
						: Color.WHITE;
				g2.setPaint(new GradientPaint(0, 0, trackStart, width, height, trackEnd));
				g2.fillRoundRect(0, 0, width - 1, height - 1, 17, 17);
				g2.setColor(night
						? StudioNightTheme.withAlpha(StudioNightTheme.NIGHT_BLUE, 150)
						: new Color(190, 205, 222));
				g2.drawRoundRect(0, 0, width - 1, height - 1, 17, 17);

				int knob = 24;
				int knobX = night ? width - knob - 5 : 5;
				int knobY = (height - knob) / 2;
				g2.setColor(night
						? StudioNightTheme.withAlpha(StudioNightTheme.NIGHT_BLUE, 42)
						: new Color(210, 225, 242));
				g2.fillOval(knobX - 2, knobY - 2, knob + 4, knob + 4);
				g2.setColor(night ? StudioNightTheme.NIGHT_TEXT : Color.WHITE);
				g2.fillOval(knobX, knobY, knob, knob);
				g2.setColor(night ? StudioNightTheme.NIGHT_TEAL : APP_BLUE);
				if (night)
				{
					g2.fillOval(knobX + 7, knobY + 6, 11, 11);
					g2.setColor(night ? trackEnd : Color.WHITE);
					g2.fillOval(knobX + 11, knobY + 4, 10, 10);
				}
				else
				{
					g2.fillOval(knobX + 8, knobY + 8, 8, 8);
					g2.setStroke(new BasicStroke(1.3f));
					for (int i = 0; i < 8; i++)
					{
						double angle = i * Math.PI / 4.0;
						int cx = knobX + knob / 2;
						int cy = knobY + knob / 2;
						int x1 = cx + (int) Math.round(Math.cos(angle) * 7.0);
						int y1 = cy + (int) Math.round(Math.sin(angle) * 7.0);
						int x2 = cx + (int) Math.round(Math.cos(angle) * 10.0);
						int y2 = cy + (int) Math.round(Math.sin(angle) * 10.0);
						g2.drawLine(x1, y1, x2, y2);
					}
				}

				g2.setFont(appFont(Font.BOLD, 11));
				String text = night ? "NIGHT" : "DAY";
				FontMetrics metrics = g2.getFontMetrics();
				int textX = night ? 14 : 48;
				int textY = (height - metrics.getHeight()) / 2 + metrics.getAscent();
				g2.setColor(night ? StudioNightTheme.NIGHT_TEXT : APP_TEXT);
				g2.drawString(text, textX, textY);
				g2.dispose();
			}
		}

		private static final class AppBackgroundPanel extends JPanel
		{
			private BufferedImage backgroundCache;
			private boolean cachedNightMode;

			AppBackgroundPanel(LayoutManager layout)
			{
				super(layout);
				setOpaque(true);
				setBackground(APP_BACKGROUND_BOTTOM);
			}

			protected void paintComponent(Graphics graphics)
			{
				super.paintComponent(graphics);
				int width = getWidth();
				int height = getHeight();
				if (width <= 0 || height <= 0)
				{
					return;
				}

				if (backgroundCache == null
						|| backgroundCache.getWidth() != width
						|| backgroundCache.getHeight() != height
						|| cachedNightMode != StudioNightTheme.isNightMode())
				{
					if (backgroundCache != null)
					{
						backgroundCache.flush();
					}
					cachedNightMode = StudioNightTheme.isNightMode();
					backgroundCache = renderBackground(width, height);
				}

				graphics.drawImage(backgroundCache, 0, 0, null);
			}

			private BufferedImage renderBackground(int width, int height)
			{
				BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
				Graphics2D g2 = image.createGraphics();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				GradientPaint gradient = new GradientPaint(0, 0, APP_BACKGROUND_TOP, 0, height, APP_BACKGROUND_BOTTOM);
				g2.setPaint(gradient);
				g2.fillRect(0, 0, width, height);

				g2.setComposite(AlphaComposite.SrcOver.derive(
						StudioNightTheme.isNightMode() ? 0.26f : 0.45f));
				g2.setColor(StudioNightTheme.isNightMode()
						? StudioNightTheme.withAlpha(StudioNightTheme.NIGHT_BORDER, 82)
						: new Color(255, 255, 255, 86));
				for (int x = 0; x < width; x += 32)
				{
					g2.drawLine(x, 0, x, height);
				}
				for (int y = 0; y < height; y += 32)
				{
					g2.drawLine(0, y, width, y);
				}

				paintFlowRibbon(g2, width, height, 0.18f, 0.35f,
						StudioNightTheme.isNightMode()
								? StudioNightTheme.withAlpha(StudioNightTheme.NIGHT_BLUE, 34)
								: new Color(0, 113, 227, 44));
				paintFlowRibbon(g2, width, height, 0.48f, -0.35f,
						StudioNightTheme.isNightMode()
								? StudioNightTheme.withAlpha(StudioNightTheme.NIGHT_GREEN, 28)
								: new Color(20, 150, 105, 36));
				paintFlowRibbon(g2, width, height, 0.78f, 0.19f,
						StudioNightTheme.isNightMode()
								? StudioNightTheme.withAlpha(StudioNightTheme.NIGHT_PURPLE, 24)
								: new Color(128, 88, 216, 28));

				g2.setComposite(AlphaComposite.SrcOver);
				g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g2.setColor(StudioNightTheme.isNightMode()
						? StudioNightTheme.withAlpha(StudioNightTheme.NIGHT_TEXT, 34)
						: new Color(255, 255, 255, 125));
				for (int x = -width; x < width * 2; x += 84)
				{
					g2.drawLine(x + 15, 0, x + 15 + height / 2, height);
				}

				g2.setColor(StudioNightTheme.isNightMode()
						? StudioNightTheme.withAlpha(StudioNightTheme.NIGHT_BLUE, 44)
						: new Color(255, 255, 255, 115));
				g2.fillRect(0, 0, width, 1);
				g2.dispose();
				return image;
			}

			private void paintFlowRibbon(Graphics2D g2, int width, int height, float anchor, float motion, Color color)
			{
				java.awt.geom.Path2D.Float path = new java.awt.geom.Path2D.Float();
				float baseY = height * anchor + motion * 18.0f;
				path.moveTo(-40, baseY);
				path.curveTo(width * 0.22f, baseY - 52, width * 0.42f, baseY + 54, width * 0.64f, baseY);
				path.curveTo(width * 0.82f, baseY - 34, width * 0.94f, baseY + 30, width + 40, baseY - 16);
				g2.setColor(color);
				g2.setStroke(new BasicStroke(22.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g2.draw(path);
				g2.setColor(StudioNightTheme.isNightMode()
						? StudioNightTheme.withAlpha(StudioNightTheme.NIGHT_TEXT, 26)
						: new Color(255, 255, 255, 60));
				g2.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g2.draw(path);
			}
		}

		private static class SurfacePanel extends JPanel
		{
			private final Color fillColor;

			SurfacePanel(LayoutManager layout, Color fillColor)
			{
				super(layout);
				this.fillColor = fillColor;
				setOpaque(false);
			}

			protected void paintComponent(Graphics graphics)
			{
				Graphics2D g2 = (Graphics2D) graphics.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(StudioNightTheme.isNightMode()
						? new Color(0, 0, 0, 78)
						: new Color(34, 44, 58, 24));
				g2.fillRoundRect(1, 3, getWidth() - 3, getHeight() - 4, SURFACE_RADIUS, SURFACE_RADIUS);
				g2.setColor(StudioNightTheme.isNightMode()
						? StudioNightTheme.NIGHT_SURFACE
						: fillColor);
				g2.fillRoundRect(0, 0, getWidth() - 2, getHeight() - 4, SURFACE_RADIUS, SURFACE_RADIUS);
				g2.setColor(StudioNightTheme.isNightMode()
						? StudioNightTheme.NIGHT_BORDER
						: APP_BORDER);
				g2.drawRoundRect(0, 0, getWidth() - 2, getHeight() - 4, SURFACE_RADIUS, SURFACE_RADIUS);
				g2.dispose();
				super.paintComponent(graphics);
			}
		}

		private static final class SignalGraphPanel extends JPanel
		{
			private final Color primary;
			private final Color secondary;
			private float phase;
			private final javax.swing.Timer timer;

			SignalGraphPanel(Color primary, Color secondary)
			{
				this.primary = primary;
				this.secondary = secondary;
				setOpaque(false);
				setPreferredSize(new Dimension(280, 74));
				timer = new javax.swing.Timer(16, new ActionListener() {
					public void actionPerformed(ActionEvent event)
					{
						phase += 0.018f;
						if (phase > 1.0f)
						{
							phase -= 1.0f;
						}
						repaint();
					}
				});
				timer.setCoalesce(true);
				addHierarchyListener(new HierarchyListener() {
					public void hierarchyChanged(HierarchyEvent event)
					{
						if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0)
						{
							updateAnimationState();
						}
					}
				});
			}

			public void addNotify()
			{
				super.addNotify();
				updateAnimationState();
			}

			public void removeNotify()
			{
				timer.stop();
				super.removeNotify();
			}

			private void updateAnimationState()
			{
				if (isShowing())
				{
					if (!timer.isRunning())
					{
						timer.start();
					}
				}
				else
				{
					timer.stop();
				}
			}

			protected void paintComponent(Graphics graphics)
			{
				Graphics2D g2 = (Graphics2D) graphics.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				int width = getWidth();
				int height = getHeight();
				if (StudioNightTheme.isNightMode())
				{
					g2.setColor(StudioNightTheme.NIGHT_PANEL_INK);
					g2.fillRoundRect(0, 0, width - 1, height - 1, SURFACE_RADIUS, SURFACE_RADIUS);
				}
				int pad = 14;
				int[] xs = {
						pad,
						width / 5,
						width / 2 - 12,
						width / 2 + 34,
						width - width / 5,
						width - pad
				};
				int[] ys = new int[xs.length];
				for (int i = 0; i < xs.length; i++)
				{
					ys[i] = Math.round(height / 2.0f
							+ (float) Math.sin((phase + i * 0.17f) * Math.PI * 2.0f) * 14.0f);
				}

				g2.setComposite(AlphaComposite.SrcOver.derive(0.55f));
				g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g2.setColor(new Color(primary.getRed(), primary.getGreen(), primary.getBlue(), 86));
				for (int i = 0; i < xs.length - 1; i++)
				{
					g2.drawLine(xs[i], ys[i], xs[i + 1], ys[i + 1]);
				}
				g2.setColor(new Color(secondary.getRed(), secondary.getGreen(), secondary.getBlue(), 70));
				g2.drawLine(xs[1], ys[1], xs[3], ys[3]);
				g2.drawLine(xs[2], ys[2], xs[4], ys[4]);

				g2.setComposite(AlphaComposite.SrcOver);
				for (int i = 0; i < xs.length; i++)
				{
					Color fill = i % 2 == 0 ? primary : secondary;
					g2.setColor(new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), 42));
					g2.fillOval(xs[i] - 7, ys[i] - 7, 14, 14);
					g2.setColor(new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), 160));
					g2.drawOval(xs[i] - 7, ys[i] - 7, 14, 14);
					g2.setColor(StudioNightTheme.isNightMode()
							? StudioNightTheme.NIGHT_TEXT
							: new Color(255, 255, 255, 225));
					g2.fillOval(xs[i] - 3, ys[i] - 3, 6, 6);
				}

				g2.setColor(StudioNightTheme.isNightMode()
						? StudioNightTheme.withAlpha(StudioNightTheme.NIGHT_BORDER, 140)
						: new Color(255, 255, 255, 115));
				g2.drawRoundRect(0, 0, width - 1, height - 1, SURFACE_RADIUS, SURFACE_RADIUS);
				g2.dispose();
				super.paintComponent(graphics);
			}
		}

		private static final class SetupScrollPanel extends JPanel implements Scrollable
		{
			SetupScrollPanel(LayoutManager layout)
			{
				super(layout);
				setOpaque(false);
			}

			public Dimension getPreferredScrollableViewportSize()
			{
				return getPreferredSize();
			}

			public int getScrollableUnitIncrement(
					Rectangle visibleRect,
					int orientation,
					int direction)
			{
				return 16;
			}

			public int getScrollableBlockIncrement(
					Rectangle visibleRect,
					int orientation,
					int direction)
			{
				return Math.max(48, visibleRect.height - 48);
			}

			public boolean getScrollableTracksViewportWidth()
			{
				return true;
			}

			public boolean getScrollableTracksViewportHeight()
			{
				return false;
			}
		}

		private enum StatusTone
		{
			READY,
			RUNNING,
			SUCCESS,
			WARNING,
			ERROR
		}

		private static final class StatusPill extends JLabel
		{
			private StatusTone tone;

			StatusPill(String text, StatusTone tone)
			{
				super(text);
				this.tone = tone;
				setFont(new Font("SansSerif", Font.BOLD, 10));
				setBorder(BorderFactory.createEmptyBorder(5, 9, 5, 9));
				setOpaque(false);
				updateForeground();
			}

			void setStatus(String text, StatusTone tone)
			{
				setText(text);
				this.tone = tone;
				updateForeground();
				repaint();
			}

			private void updateForeground()
			{
				setForeground(statusColor(tone).darker());
			}

			protected void paintComponent(Graphics graphics)
			{
				Color color = statusColor(tone);
				Graphics2D g2 = (Graphics2D) graphics.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 32));
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), SURFACE_RADIUS, SURFACE_RADIUS);
				g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 105));
				g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, SURFACE_RADIUS, SURFACE_RADIUS);
				g2.dispose();
				super.paintComponent(graphics);
			}

			private static Color statusColor(StatusTone tone)
			{
				switch (tone)
				{
					case RUNNING:
						return APP_BLUE;
					case SUCCESS:
						return APP_GREEN;
					case WARNING:
						return new Color(207, 125, 31);
					case ERROR:
						return APP_RED;
					case READY:
					default:
						return new Color(82, 105, 130);
				}
			}
		}

		private final class SourceChoiceButton extends JButton
		{
			private final String title;
			private final String description;
			private final LineIcon icon;

			SourceChoiceButton(String title, String description, LineIconType iconType)
			{
				this.title = title;
				this.description = description;
				this.icon = new LineIcon(iconType, 20);
				setPreferredSize(new Dimension(138, 78));
				setContentAreaFilled(false);
				setBorderPainted(false);
				setFocusPainted(false);
				setOpaque(false);
				setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				setToolTipText(description);
				getAccessibleContext().setAccessibleName(title);
				setRolloverEnabled(true);
			}

			protected void paintComponent(Graphics graphics)
			{
				Graphics2D g2 = (Graphics2D) graphics.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				boolean selected = isSelected();
				boolean hovered = getModel().isRollover();
				Color background = selected
						? APP_SELECTION
						: hovered ? APP_SURFACE_ALT : APP_SURFACE;
				Color border = selected ? APP_BLUE : hovered
						? new Color(176, 194, 215) : APP_BORDER;
				g2.setColor(background);
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), SURFACE_RADIUS, SURFACE_RADIUS);
				g2.setColor(border);
				g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, SURFACE_RADIUS, SURFACE_RADIUS);

				g2.setColor(selected ? APP_BLUE : APP_MUTED_TEXT);
				icon.paintIcon(this, g2, 11, 11);
				g2.setFont(appFont(Font.BOLD, 12));
				g2.setColor(APP_TEXT);
				g2.drawString(title, 39, 25);
				g2.setFont(appFont(Font.PLAIN, 9));
				g2.setColor(APP_MUTED_TEXT);
				drawWrappedText(g2, description, 11, 47, getWidth() - 22, 11, 2);

				if (selected)
				{
					g2.setColor(APP_BLUE);
					g2.fillOval(getWidth() - 17, 10, 7, 7);
				}
				g2.dispose();
			}

			private void drawWrappedText(
					Graphics2D graphics,
					String text,
					int x,
					int y,
					int maxWidth,
					int lineHeight,
					int maxLines)
			{
				FontMetrics metrics = graphics.getFontMetrics();
				StringBuilder line = new StringBuilder();
				int lineCount = 0;
				for (String word : text.split(" "))
				{
					String candidate = line.length() == 0 ? word : line + " " + word;
					if (metrics.stringWidth(candidate) > maxWidth && line.length() > 0)
					{
						graphics.drawString(line.toString(), x, y + lineCount * lineHeight);
						lineCount++;
						line.setLength(0);
						line.append(word);
						if (lineCount >= maxLines)
						{
							return;
						}
					}
					else
					{
						if (line.length() > 0)
						{
							line.append(' ');
						}
						line.append(word);
					}
				}
				if (line.length() > 0 && lineCount < maxLines)
				{
					graphics.drawString(line.toString(), x, y + lineCount * lineHeight);
				}
			}
		}

		private static final class BadgeLabel extends JLabel
		{
			private Color accentColor;

			BadgeLabel(String text, Color accentColor)
			{
				super(text);
				this.accentColor = accentColor;
				setFont(new Font("SansSerif", Font.BOLD, 11));
				setForeground(accentColor.darker());
				setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
				setOpaque(false);
			}

			protected void paintComponent(Graphics graphics)
			{
				Graphics2D g2 = (Graphics2D) graphics.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 34));
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), SURFACE_RADIUS, SURFACE_RADIUS);
				g2.setColor(new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 90));
				g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, SURFACE_RADIUS, SURFACE_RADIUS);
				g2.dispose();
				super.paintComponent(graphics);
			}
		}

		private final class AlgorithmChoiceRenderer extends DefaultListCellRenderer
		{
			public Component getListCellRendererComponent(
					JList<?> list,
					Object value,
					int index,
					boolean isSelected,
					boolean cellHasFocus)
			{
				JLabel label = (JLabel) super.getListCellRendererComponent(
						list, value, index, isSelected, cellHasFocus);
				int algorithm = algorithmIndexForName(value == null ? "" : value.toString());
				label.setIcon(new LineIcon(iconForAlgorithm(algorithm), 17));
				label.setIconTextGap(9);
				label.setFont(appFont(index < 0 ? Font.BOLD : Font.PLAIN, 13));
				label.setBorder(BorderFactory.createEmptyBorder(7, 9, 7, 9));
				return label;
			}
		}

		private final class RecommendationModeRenderer extends DefaultListCellRenderer
		{
			public Component getListCellRendererComponent(
					JList<?> list,
					Object value,
					int index,
					boolean isSelected,
					boolean cellHasFocus)
			{
				JLabel label = (JLabel) super.getListCellRendererComponent(
						list, value, index, isSelected, cellHasFocus);
				RecommendationTargetMode mode = value instanceof RecommendationTargetMode
						? (RecommendationTargetMode) value
						: RecommendationTargetMode.ALGORITHM_DEFAULT;
				LineIconType icon = mode == RecommendationTargetMode.SIMILAR_USERS
						? LineIconType.USERS
						: mode == RecommendationTargetMode.FOLLOWEE_ACCOUNTS
						? LineIconType.RECOMMENDER
						: LineIconType.SETTINGS;
				label.setIcon(new LineIcon(icon, 17));
				label.setIconTextGap(9);
				label.setFont(appFont(index < 0 ? Font.BOLD : Font.PLAIN, 13));
				label.setBorder(BorderFactory.createEmptyBorder(7, 9, 7, 9));
				return label;
			}
		}

		private int algorithmIndexForName(String name)
		{
			if ("Similarity".equals(name))
			{
				return COS_SIM;
			}
			if ("K-Means".equals(name))
			{
				return K_MEANS;
			}
			if ("SVM".equals(name))
			{
				return SVM;
			}
			if ("MLP".equals(name))
			{
				return MLP;
			}
			if ("Doc2Vec".equals(name))
			{
				return Doc2Vec;
			}
			if ("Common-Neighbors".equals(name))
			{
				return CommonNeighbors;
			}
			if ("K-meansEuclidean".equals(name))
			{
				return K_MEANSEUCLIDEAN;
			}
			if ("Scalable Linear SVM".equals(name))
			{
				return SCALABLE_SVM;
			}
			if ("Temporal Graph Foundation".equals(name))
			{
				return TEMPORAL_GRAPH_FOUNDATION;
			}
			return -1;
		}

		private LineIconType iconForAlgorithm(int algorithm)
		{
			switch (algorithm)
			{
				case COS_SIM:
					return LineIconType.RECOMMENDER;
				case K_MEANS:
				case K_MEANSEUCLIDEAN:
					return LineIconType.GENERATED;
				case SVM:
				case SCALABLE_SVM:
				case MLP:
					return LineIconType.SETTINGS;
				case Doc2Vec:
					return LineIconType.TEXT_FILE;
				case CommonNeighbors:
				case TEMPORAL_GRAPH_FOUNDATION:
					return LineIconType.USERS;
				default:
					return LineIconType.RECOMMENDER;
			}
		}

		private final class NumericStepper extends JPanel
		{
			private final JTextField field;
			private final int minimum;
			private final int step;
			private final StepperButton decrementButton;
			private final StepperButton incrementButton;

			NumericStepper(JTextField field, int minimum, int step)
			{
				super(new BorderLayout());
				this.field = field;
				this.minimum = minimum;
				this.step = Math.max(1, step);
				setOpaque(true);
				setBackground(APP_SURFACE);
				setBorder(BorderFactory.createLineBorder(APP_BORDER, 1));

				field.setBorder(BorderFactory.createEmptyBorder(7, 9, 7, 4));
				field.setOpaque(false);
				add(field, BorderLayout.CENTER);

				JPanel controls = new JPanel(new GridLayout(1, 2));
				controls.setOpaque(false);
				decrementButton = new StepperButton("\u2212");
				incrementButton = new StepperButton("+");
				decrementButton.setToolTipText("Decrease by " + this.step);
				incrementButton.setToolTipText("Increase by " + this.step);
				decrementButton.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent event)
					{
						adjustValue(-NumericStepper.this.step);
					}
				});
				incrementButton.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent event)
					{
						adjustValue(NumericStepper.this.step);
					}
				});
				controls.add(decrementButton);
				controls.add(incrementButton);
				add(controls, BorderLayout.EAST);
				setPreferredSize(new Dimension(150, 40));
			}

			private void adjustValue(int delta)
			{
				int currentValue;
				try
				{
					currentValue = Integer.parseInt(field.getText().trim());
				}
				catch (NumberFormatException exception)
				{
					currentValue = minimum;
				}
				long adjusted = Math.max(minimum, (long) currentValue + delta);
				adjusted = Math.min(Integer.MAX_VALUE, adjusted);
				field.setText(Long.toString(adjusted));
				field.postActionEvent();
			}

			public void setEnabled(boolean enabled)
			{
				super.setEnabled(enabled);
				field.setEnabled(enabled);
				decrementButton.setEnabled(enabled);
				incrementButton.setEnabled(enabled);
				setBackground(enabled ? APP_SURFACE : APP_SURFACE_ALT);
				repaint();
			}
		}

		private static final class StepperButton extends JButton
		{
			StepperButton(String text)
			{
				super(text);
				setFont(new Font("SansSerif", Font.BOLD, 15));
				setForeground(APP_MUTED_TEXT);
				setPreferredSize(new Dimension(32, 38));
				setFocusPainted(false);
				setContentAreaFilled(false);
				setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, APP_BORDER));
				setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			}

			protected void paintComponent(Graphics graphics)
			{
				Graphics2D g2 = (Graphics2D) graphics.create();
				if (getModel().isPressed())
				{
					g2.setColor(StudioNightTheme.isNightMode()
							? StudioNightTheme.NIGHT_BORDER : new Color(220, 231, 244));
				}
				else if (getModel().isRollover())
				{
					g2.setColor(StudioNightTheme.isNightMode()
							? StudioNightTheme.NIGHT_SURFACE_ALT : new Color(235, 243, 252));
				}
				else
				{
					g2.setColor(StudioNightTheme.isNightMode()
							? StudioNightTheme.NIGHT_PANEL_INK : new Color(248, 250, 252));
				}
				g2.fillRect(0, 0, getWidth(), getHeight());
				g2.dispose();
				super.paintComponent(graphics);
			}
		}

		private final class ModernUserCellRenderer extends JPanel implements ListCellRenderer<String>
		{
			private final JLabel indexBadge;
			private final JLabel userName;
			private final JLabel detail;

			ModernUserCellRenderer()
			{
				super(new BorderLayout(10, 0));
				setOpaque(true);
				setBorder(BorderFactory.createEmptyBorder(5, 7, 5, 7));
				indexBadge = new JLabel();
				indexBadge.setHorizontalAlignment(SwingConstants.CENTER);
				indexBadge.setPreferredSize(new Dimension(38, 26));
				indexBadge.setFont(appFont(Font.BOLD, 11));

				userName = new JLabel();
				userName.setFont(appFont(Font.BOLD, 13));
				detail = new JLabel();
				detail.setFont(appFont(Font.PLAIN, 11));

				JPanel textStack = new JPanel();
				textStack.setOpaque(false);
				textStack.setLayout(new BoxLayout(textStack, BoxLayout.Y_AXIS));
				textStack.add(userName);
				textStack.add(detail);
				add(indexBadge, BorderLayout.WEST);
				add(textStack, BorderLayout.CENTER);
			}

			public Component getListCellRendererComponent(
					JList<? extends String> list,
					String value,
					int index,
					boolean isSelected,
					boolean cellHasFocus)
			{
				String displayValue = value == null ? "" : value;
				String[] parts = displayValue.split("-", 2);
				indexBadge.setText(Integer.toString(index + 1));
				userName.setText(parts[0]);
				detail.setText(parts.length > 1 ? parts[1] : "Recommendation target");

				boolean night = StudioNightTheme.isNightMode();
				Color background = isSelected ? APP_SELECTION : APP_SURFACE;
				Color foreground = isSelected
						? night ? StudioNightTheme.NIGHT_TEXT : new Color(0, 68, 136)
						: APP_TEXT;
				Color muted = isSelected
						? night ? StudioNightTheme.NIGHT_MUTED : new Color(38, 105, 170)
						: APP_MUTED_TEXT;
				setBackground(background);
				userName.setForeground(foreground);
				detail.setForeground(muted);
				indexBadge.setForeground(isSelected ? Color.WHITE : APP_BLUE);
				indexBadge.setBorder(BorderFactory.createCompoundBorder(
						BorderFactory.createLineBorder(isSelected ? APP_BLUE
								: night ? StudioNightTheme.NIGHT_BORDER : new Color(181, 212, 246), 1),
						BorderFactory.createEmptyBorder(4, 6, 4, 6)));
				indexBadge.setOpaque(true);
				indexBadge.setBackground(isSelected ? APP_BLUE
						: night ? StudioNightTheme.NIGHT_PANEL_INK : new Color(238, 247, 255));
				return this;
			}
		}

		private static final class RecommendationResult
		{
			private final String targetUser;
			private final ArrayList<RecommendationMatch> recommendations = new ArrayList<RecommendationMatch>();
			private RecommendationCandidateType candidateType;
			private RecommendationResultStatus status = RecommendationResultStatus.OK;
			private String resultLabel;
			private String message;

			RecommendationResult(String targetUser)
			{
				this.targetUser = targetUser;
			}

			RecommendationResult(RecommendationResultPayload payload, int limit)
			{
				this.targetUser = payload.getTargetUser();
				this.candidateType = payload.getCandidateType();
				this.status = payload.getStatus();
				this.resultLabel = payload.getResultLabel();
				this.message = payload.getMessage();
				int count = 0;
				for (Map.Entry<String,Double> entry : payload.getScores().entrySet())
				{
					if (count >= limit)
					{
						break;
					}
					recommendations.add(new RecommendationMatch(
							entry.getKey(),
							String.format(Locale.US, "%.6g", entry.getValue()),
							payload.getCandidateDetails().get(entry.getKey())));
					count++;
				}
			}
		}

		private static final class RecommendationMatch
		{
			private final String name;
			private final String score;
			private final String detail;

			RecommendationMatch(String name, String score)
			{
				this(name, score, null);
			}

			RecommendationMatch(String name, String score, String detail)
			{
				this.name = name;
				this.score = score;
				this.detail = detail;
			}
		}

		private final class RecommendationCard extends SurfacePanel
		{
			RecommendationCard(RecommendationResult result)
			{
				super(new BorderLayout(0, 12), APP_SURFACE);
				setBorder(BorderFactory.createEmptyBorder(14, 14, 16, 14));
				setAlignmentX(Component.LEFT_ALIGNMENT);
				int cardHeight = 82 + Math.max(1, result.recommendations.size()) * 47;
				setPreferredSize(new Dimension(390, cardHeight));
				setMaximumSize(new Dimension(Integer.MAX_VALUE, cardHeight));

				JPanel header = new JPanel(new BorderLayout(10, 0));
				header.setOpaque(false);
				header.add(new RecommendationAvatar(result.targetUser), BorderLayout.WEST);

				JPanel identity = new JPanel();
				identity.setOpaque(false);
				identity.setLayout(new BoxLayout(identity, BoxLayout.Y_AXIS));
				JLabel targetLabel = new JLabel("TARGET USER");
				targetLabel.setForeground(APP_MUTED_TEXT);
				targetLabel.setFont(appFont(Font.BOLD, 10));
				JLabel userLabel = new JLabel(result.targetUser);
				userLabel.setForeground(APP_TEXT);
				userLabel.setFont(appFont(Font.BOLD, 15));
				identity.add(targetLabel);
				identity.add(Box.createRigidArea(new Dimension(0, 2)));
				identity.add(userLabel);

				String resultType = result.resultLabel != null
						? result.resultLabel.toUpperCase(Locale.US)
						: result.candidateType == RecommendationCandidateType.FOLLOWEE
						? "FOLLOWEE ACCOUNTS"
						: result.candidateType == RecommendationCandidateType.USER
						? "SIMILAR USERS" : "RANKED MATCHES";
				JLabel matchCount = new JLabel(result.status == RecommendationResultStatus.OK
						? result.recommendations.size() + " " + resultType
						: result.status.toString().replace('_', ' '));
				matchCount.setForeground(result.status == RecommendationResultStatus.OK
						? APP_GREEN : new Color(177, 65, 57));
				matchCount.setFont(appFont(Font.BOLD, 10));
				matchCount.setBorder(BorderFactory.createCompoundBorder(
						BorderFactory.createLineBorder(new Color(168, 220, 198), 1),
						BorderFactory.createEmptyBorder(6, 8, 6, 8)));
				matchCount.setOpaque(true);
				matchCount.setBackground(result.status == RecommendationResultStatus.OK
						? new Color(237, 249, 243) : new Color(254, 241, 240));

				header.add(identity, BorderLayout.CENTER);
				header.add(matchCount, BorderLayout.EAST);

				JPanel rankedResults = new JPanel(new GridLayout(0, 1, 0, 7));
				rankedResults.setOpaque(false);
				for (int i = 0; i < result.recommendations.size(); i++)
				{
					rankedResults.add(createRecommendationRankRow(i + 1, result.recommendations.get(i)));
				}
				if (result.recommendations.isEmpty())
				{
					JLabel emptyMessage = new JLabel(
							result.message == null ? "No recommendation candidates were available."
									: result.message);
					emptyMessage.setForeground(APP_MUTED_TEXT);
					emptyMessage.setFont(appFont(Font.PLAIN, 12));
					rankedResults.add(emptyMessage);
				}

				add(header, BorderLayout.NORTH);
				add(rankedResults, BorderLayout.CENTER);
			}
		}

		private JPanel createRecommendationRankRow(int rank, RecommendationMatch match)
		{
			boolean topResult = rank == 1;
			Color accent = topResult ? APP_BLUE
					: rank == 2 ? new Color(128, 88, 216)
					: rank == 3 ? APP_GREEN
					: new Color(207, 125, 31);
			Color rowBackground = StudioNightTheme.isNightMode()
					? (topResult ? StudioNightTheme.NIGHT_SURFACE_ALT
							: StudioNightTheme.NIGHT_PANEL_INK)
					: (topResult ? new Color(238, 247, 255) : new Color(248, 250, 252));

			JPanel row = new JPanel(new BorderLayout(10, 0));
			row.setBackground(rowBackground);
			row.setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createLineBorder(
							new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 90), 1),
					BorderFactory.createEmptyBorder(8, 9, 8, 10)));

			JLabel rankBadge = new JLabel(Integer.toString(rank));
			rankBadge.setHorizontalAlignment(SwingConstants.CENTER);
			rankBadge.setPreferredSize(new Dimension(28, 28));
			rankBadge.setForeground(Color.WHITE);
			rankBadge.setBackground(accent);
			rankBadge.setOpaque(true);
			rankBadge.setFont(appFont(Font.BOLD, 12));

			JPanel namePanel = new JPanel();
			namePanel.setOpaque(false);
			namePanel.setLayout(new BoxLayout(namePanel, BoxLayout.Y_AXIS));
			JLabel name = new JLabel(match.name);
			name.setForeground(APP_TEXT);
			name.setFont(appFont(Font.BOLD, 13));
			JLabel description = new JLabel(match.detail != null
					? match.detail
					: topResult ? "Highest-ranked recommendation" : "Ranked recommendation");
			description.setForeground(APP_MUTED_TEXT);
			description.setFont(appFont(Font.PLAIN, 10));
			namePanel.add(name);
			namePanel.add(description);

			row.add(rankBadge, BorderLayout.WEST);
			row.add(namePanel, BorderLayout.CENTER);
			JPanel resultMeta = new JPanel();
			resultMeta.setOpaque(false);
			resultMeta.setLayout(new BoxLayout(resultMeta, BoxLayout.Y_AXIS));
			if (match.score != null)
			{
				JLabel scoreLabel = new JLabel("MODEL SCORE");
				scoreLabel.setForeground(APP_MUTED_TEXT);
				scoreLabel.setFont(appFont(Font.BOLD, 8));
				scoreLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
				JLabel scoreValue = new JLabel(match.score);
				scoreValue.setForeground(accent);
				scoreValue.setFont(appFont(Font.BOLD, 11));
				scoreValue.setAlignmentX(Component.RIGHT_ALIGNMENT);
				resultMeta.add(scoreLabel);
				resultMeta.add(scoreValue);
			}
			else if (topResult)
			{
				JLabel topBadge = new JLabel("TOP RESULT");
				topBadge.setForeground(APP_BLUE);
				topBadge.setFont(appFont(Font.BOLD, 9));
				topBadge.setAlignmentX(Component.RIGHT_ALIGNMENT);
				resultMeta.add(topBadge);
			}
			if (resultMeta.getComponentCount() > 0)
			{
				row.add(resultMeta, BorderLayout.EAST);
			}
			return row;
		}

		private final class RecommendationAvatar extends JLabel
		{
			RecommendationAvatar(String userName)
			{
				super(getInitials(userName));
				setHorizontalAlignment(SwingConstants.CENTER);
				setPreferredSize(new Dimension(42, 42));
				setForeground(Color.WHITE);
				setFont(appFont(Font.BOLD, 13));
				setOpaque(false);
			}

			protected void paintComponent(Graphics graphics)
			{
				Graphics2D g2 = (Graphics2D) graphics.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(new Color(224, 239, 255));
				g2.fillOval(0, 0, getWidth(), getHeight());
				g2.setColor(APP_BLUE);
				g2.fillOval(3, 3, getWidth() - 6, getHeight() - 6);
				g2.dispose();
				super.paintComponent(graphics);
			}
		}

		private static final class ModernButton extends JButton
		{
			private final Color baseColor;
			private final Color hoverColor;
			private final Color pressedColor;
			private final Color enabledForeground;
			private float hoverProgress;
			private float targetProgress;
			private final javax.swing.Timer timer;

			ModernButton(String text, Color baseColor, Color hoverColor, Color pressedColor, Color foreground)
			{
				super(text);
				this.baseColor = baseColor;
				this.hoverColor = hoverColor;
				this.pressedColor = pressedColor;
				this.enabledForeground = foreground;
				setForeground(foreground);
					setFont(new Font("SansSerif", Font.BOLD, 13));
					setFocusPainted(false);
					setContentAreaFilled(false);
					setBorderPainted(false);
					setOpaque(false);
					setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
					setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
					setIconTextGap(8);
					setHorizontalAlignment(SwingConstants.CENTER);
					setVerticalAlignment(SwingConstants.CENTER);
					setHorizontalTextPosition(SwingConstants.RIGHT);
					timer = new javax.swing.Timer(16, new ActionListener() {
					public void actionPerformed(ActionEvent event)
					{
						hoverProgress += (targetProgress - hoverProgress) * 0.28f;
						if (Math.abs(targetProgress - hoverProgress) < 0.01f)
						{
							hoverProgress = targetProgress;
							timer.stop();
						}
						repaint();
					}
				});
				addMouseListener(new MouseAdapter() {
					public void mouseEntered(MouseEvent event)
					{
						animateTo(1.0f);
					}

					public void mouseExited(MouseEvent event)
					{
						animateTo(0.0f);
					}
				});
			}

			private void animateTo(float target)
			{
				targetProgress = target;
				if (!timer.isRunning())
				{
					timer.start();
				}
			}

			protected void paintComponent(Graphics graphics)
			{
				Graphics2D g2 = (Graphics2D) graphics.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				Color fill = getModel().isPressed() ? pressedColor : blend(baseColor, hoverColor, hoverProgress);
				if (!isEnabled())
				{
					fill = new Color(218, 224, 232);
					setForeground(new Color(126, 136, 150));
				}
				else
				{
					setForeground(enabledForeground);
				}
				g2.setColor(fill);
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), SURFACE_RADIUS, SURFACE_RADIUS);
				g2.dispose();
				super.paintComponent(graphics);
			}
		}

		private void createTextAreas()
		{
			resultArea = new JTextArea(AREA_ROWS, AREA_COLUMNS);
			resultArea.setText("");
			styleTextArea(resultArea, false);

			runAccuracyArea = new JTextArea(2, AREA_COLUMNS);
			styleTextArea(runAccuracyArea, true);
			runAccuracyArea.setText("No run accuracy line has been logged yet.");

			previousResultArea = new JTextArea(AREA_ROWS,AREA_COLUMNS);
			previousResultArea.setText("");
			styleTextArea(previousResultArea, false);

			recommendationArea = new JTextArea(10,AREA_COLUMNS);
			recommendationArea.setText("");
			styleTextArea(recommendationArea, true);

			clusterResultArea = new JTextArea(AREA_ROWS,AREA_COLUMNS);
			clusterResultArea.setText("");
			styleTextArea(clusterResultArea, false);

			userGenTweetsResultArea = new JTextArea(AREA_ROWS,AREA_COLUMNS);
			userGenTweetsResultArea.setText("");
			styleTextArea(userGenTweetsResultArea, false);

			grabTwitterResultArea = new JTextArea(AREA_ROWS,AREA_COLUMNS);
			grabTwitterResultArea.setText("");
			styleTextArea(grabTwitterResultArea, false);
		}

	private void createTextFields()
	{
		final int FIELD_WIDTH = 10;

			enterDatasetLabel = new JLabel("Enter dataset: ");
			styleLabel(enterDatasetLabel);
			enterDatasetField = new JTextField(FIELD_WIDTH);
			enterDatasetField.setText(DEFAULT_DATASET);
			enterDatasetField.setHorizontalAlignment(JTextField.CENTER);
			enterDatasetField.addActionListener(new GetReferenceListener());
			styleTextField(enterDatasetField);

			numNodesLabel = new JLabel("Number of Nodes: ");
			numNodesLabel.setHorizontalAlignment(JLabel.RIGHT);
			styleLabel(numNodesLabel);
			numNodesField = new JTextField(FIELD_WIDTH);
			numNodesField.setText("1");
			numNodesField.setHorizontalAlignment(JTextField.CENTER);
			numNodesField.addActionListener(new NumNodesListener());
			styleTextField(numNodesField);

			maxHeapField = new JTextField(FIELD_WIDTH);
			maxHeapField.setText(Integer.toString(
					RuntimeResourceSettings.currentMaxHeapMb()));
			maxHeapField.setHorizontalAlignment(JTextField.CENTER);
			styleTextField(maxHeapField);

			tweetLimitLabel = new JLabel("Number of Latest Tweets: ");
			tweetLimitLabel.setHorizontalAlignment(JLabel.RIGHT);
			styleLabel(tweetLimitLabel);
			tweetLimitField = new JTextField(FIELD_WIDTH);
			tweetLimitField.setText("0");
			tweetLimitField.setHorizontalAlignment(JTextField.CENTER);
			tweetLimitField.addActionListener(new TweetLimitListener());
			styleTextField(tweetLimitField);

			numMappersLabel = new JLabel("Number of Mappers: ");
			numMappersLabel.setHorizontalAlignment(JLabel.RIGHT);
			styleLabel(numMappersLabel);
			numMappersField = new JTextField(FIELD_WIDTH);
			numMappersField.setText("1");
			numMappersField.setHorizontalAlignment(JTextField.CENTER);
			numMappersField.addActionListener(new NumMappersListener());
			styleTextField(numMappersField);

			numReducersLabel = new JLabel("Number of Reducers: ");
			numReducersLabel.setHorizontalAlignment(JLabel.RIGHT);
			styleLabel(numReducersLabel);
			numReducersField = new JTextField(FIELD_WIDTH);
			numReducersField.setText("1");
			numReducersField.setHorizontalAlignment(JTextField.CENTER);
			numReducersField.addActionListener(new NumReducersListener());
			styleTextField(numReducersField);

			beginDateLabel = new JLabel("Begin date: ");
			beginDateLabel.setHorizontalAlignment(JLabel.RIGHT);
			styleLabel(beginDateLabel);
			beginDateField = new JTextField(FIELD_WIDTH);
			beginDateField.setText(BEGIN_DATE);
			beginDateField.setHorizontalAlignment(JTextField.CENTER);
			beginDateField.addActionListener(new BeginDateListener());
			styleTextField(beginDateField);

			endDateLabel = new JLabel("End date: ");
			endDateLabel.setHorizontalAlignment(JLabel.RIGHT);
			styleLabel(endDateLabel);
			endDateField = new JTextField(FIELD_WIDTH);
			endDateField.setText(END_DATE);
			endDateField.setHorizontalAlignment(JTextField.CENTER);
			endDateField.addActionListener(new EndDateListener());
			styleTextField(endDateField);

			recommendationLabel = new JLabel("Top Recommendations: ");
			recommendationLabel.setHorizontalAlignment(JLabel.RIGHT);
			styleLabel(recommendationLabel);
				recommendationField = new JTextField(FIELD_WIDTH);
				recommendationField.setText("3");
				recommendationField.setHorizontalAlignment(JTextField.CENTER);
				recommendationField.addActionListener(new RecommendationListener());
				styleTextField(recommendationField);

				svmBatchUsersLabel = new JLabel("Batch Users: ");
				svmBatchUsersLabel.setHorizontalAlignment(JLabel.RIGHT);
				styleLabel(svmBatchUsersLabel);
				svmBatchUsersField = new JTextField(FIELD_WIDTH);
				svmBatchUsersField.setText("1");
				svmBatchUsersField.setHorizontalAlignment(JTextField.CENTER);
				styleTextField(svmBatchUsersField);

				algorithmLabel = new JLabel("Algorithm: ");
			algorithmLabel.setHorizontalAlignment(JLabel.RIGHT);
			styleLabel(algorithmLabel);

			recommendationTargetModeLabel = new JLabel("Recommend: ");
			recommendationTargetModeLabel.setHorizontalAlignment(JLabel.RIGHT);
			styleLabel(recommendationTargetModeLabel);

			simulationNumber = new JLabel("Iteration Number: ");       // added by Sepide
			simulationNumber.setHorizontalAlignment(JLabel.RIGHT);      // added by Sepide
			styleLabel(simulationNumber);

			mapperChoiceLabel = new JLabel("Mapper Choice: ");
			mapperChoiceLabel.setHorizontalAlignment(JLabel.RIGHT);
			styleLabel(mapperChoiceLabel);

				reducerChoiceLabel = new JLabel("Reducer Choice: ");
				reducerChoiceLabel.setHorizontalAlignment(JLabel.RIGHT);
				styleLabel(reducerChoiceLabel);

			String[] algorithmsSelection = {"Similarity", "K-Means", "SVM", "MLP", "Doc2Vec", "Common-Neighbors", "K-meansEuclidean", "Scalable Linear SVM", "Temporal Graph Foundation"};
			algorithmSelectionBox = new JComboBox<String>(algorithmsSelection);
			algorithmSelectionBox.setSelectedIndex(K_MEANS);
			algorithmSelectionBox.setRenderer(new AlgorithmChoiceRenderer());
			algorithmSelectionBox.addActionListener(new AlgorithmSelectionListener());
			algorithmSelectionBox.setToolTipText(
					"SVM preserves the original exact calibrated SMO model. "
					+ "Scalable Linear SVM and Temporal Graph Foundation are separate experimental models.");
			styleComboBox(algorithmSelectionBox);

			recommendationTargetModeBox =
					new JComboBox<RecommendationTargetMode>(RecommendationTargetMode.values());
			recommendationTargetModeBox.setSelectedItem(RecommendationTargetMode.ALGORITHM_DEFAULT);
			recommendationTargetModeBox.setRenderer(new RecommendationModeRenderer());
			recommendationTargetModeBox.setToolTipText(
					"Algorithm Default preserves the original recommender output. "
					+ "Explicit modes choose similar users or followee accounts.");
			recommendationTargetModeBox.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent event)
				{
					invalidateSimulatorInitializationState();
					updateExperimentSetupContext();
					refreshSimulatorHelpPanel();
				}
			});
			styleComboBox(recommendationTargetModeBox);

			String[] simulationSelection = {"0", "1", "2", "3", "4", "5"};       // added by Sepide
			simulationSelectionBox = new JComboBox<String>(simulationSelection);       // added by Sepide
			simulationSelectionBox.setSelectedIndex(zero);          // added by Sepide
			((JLabel)simulationSelectionBox.getRenderer()).setHorizontalAlignment(SwingConstants.CENTER);     // added by Sepide
			styleComboBox(simulationSelectionBox);
			simulationSelectionBox.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent event)
				{
					invalidateSimulatorInitializationState();
					refreshSimulatorHelpPanel();
				}
			});
			//simulationSelectionBox.addActionListener(new simulationSelectionListener());       // added by Sepide

			String[] mappersSelection = {"Feature Extraction", "Clustering"};
			mapperSelectionBox = new JComboBox<String>(mappersSelection);
			mapperSelectionBox.setSelectedIndex(FEATURE_EXTRACTION);
			((JLabel)mapperSelectionBox.getRenderer()).setHorizontalAlignment(SwingConstants.CENTER);
			mapperSelectionBox.addActionListener(new MapperSelectionListener());
			styleComboBox(mapperSelectionBox);

			String[] reducersSelection = {"Feature Extraction", "Clustering"};
			reducerSelectionBox = new JComboBox<String>(reducersSelection);
				reducerSelectionBox.setSelectedIndex(ALGORITHM_CLUSTERING);
				((JLabel)reducerSelectionBox.getRenderer()).setHorizontalAlignment(SwingConstants.CENTER);
				reducerSelectionBox.addActionListener(new ReducerSelectionListener());
				styleComboBox(reducerSelectionBox);
				installSimulatorHelpRefreshListeners();
				updateBatchUsersFieldState();
			}

	private void installSimulatorHelpRefreshListeners()
	{
		DocumentListener experimentListener = new DocumentListener()
		{
			public void insertUpdate(DocumentEvent event)
			{
				invalidateSimulatorInitializationState();
				scheduleSimulatorHelpRefresh();
			}

			public void removeUpdate(DocumentEvent event)
			{
				invalidateSimulatorInitializationState();
				scheduleSimulatorHelpRefresh();
			}

			public void changedUpdate(DocumentEvent event)
			{
				invalidateSimulatorInitializationState();
				scheduleSimulatorHelpRefresh();
			}
		};
		DocumentListener displayOnlyListener = new DocumentListener()
		{
			public void insertUpdate(DocumentEvent event)
			{
				scheduleSimulatorHelpRefresh();
			}

			public void removeUpdate(DocumentEvent event)
			{
				scheduleSimulatorHelpRefresh();
			}

			public void changedUpdate(DocumentEvent event)
			{
				scheduleSimulatorHelpRefresh();
			}
		};
		attachSimulatorHelpRefresh(enterDatasetField, experimentListener);
		attachSimulatorHelpRefresh(numNodesField, experimentListener);
		attachSimulatorHelpRefresh(tweetLimitField, experimentListener);
		attachSimulatorHelpRefresh(beginDateField, experimentListener);
		attachSimulatorHelpRefresh(endDateField, experimentListener);
		attachSimulatorHelpRefresh(recommendationField, experimentListener);
		attachSimulatorHelpRefresh(svmBatchUsersField, experimentListener);
		attachSimulatorHelpRefresh(maxHeapField, displayOnlyListener);
	}

	private void attachSimulatorHelpRefresh(
			JTextField field,
			DocumentListener listener)
	{
		if (field == null || listener == null)
		{
			return;
		}
		field.getDocument().addDocumentListener(listener);
	}

	private void scheduleSimulatorHelpRefresh()
	{
		SwingUtilities.invokeLater(new Runnable()
		{
			public void run()
			{
				refreshSimulatorHelpPanel();
			}
		});
	}

	class AlgorithmSelectionListener implements ActionListener
	{
		public void actionPerformed(ActionEvent event)
		{
				System.out.println("algorithmSelectionBox.getSelectedIndex(): "+ algorithmSelectionBox.getSelectedIndex());
				invalidateSimulatorInitializationState();
				algorithmRec = algorithmSelectionBox.getSelectedIndex();
				updateBatchUsersFieldState();
				updateExperimentSetupContext();
				updateAlgorithmSettingsSummary();
				resetRunAccuracyPanel();
				updateRunAccuracyPanelVisibility();
				refreshSimulatorDoc2VecCacheStatus();
				refreshSimulatorHelpPanel();
				if (actualSchedulerPanel != null)
				{
					actualSchedulerPanel.refreshFromSimulator();
				}
			}
		}

		private void updateBatchUsersFieldState()
		{
			if (algorithmSelectionBox == null || svmBatchUsersField == null || svmBatchUsersLabel == null)
			{
				return;
			}
			int selectedAlgorithm = algorithmSelectionBox.getSelectedIndex();
			boolean batchEnabled = RecommendationModeSupport.isSvmFamilyAlgorithm(
					selectedAlgorithm) || selectedAlgorithm == Doc2Vec;
			setBatchUsersControlEnabled(batchEnabled);
			svmBatchUsersLabel.setEnabled(batchEnabled);
			svmBatchUsersField.setBackground(batchEnabled ? APP_SURFACE : APP_SURFACE_ALT);
			svmBatchUsersField.setToolTipText(batchEnabled
					? "Batch users applies to SVM models and Doc2Vec."
					: "Batch users is available for SVM models and Doc2Vec only.");
			if (setupBatchBadge != null)
			{
				setupBatchBadge.setText(batchEnabled ? "Batch ready" : "Single target");
			}
		}

		private void setBatchUsersControlEnabled(boolean enabled)
		{
			svmBatchUsersField.setEnabled(enabled);
			if (batchUsersStepper != null)
			{
				batchUsersStepper.setEnabled(enabled);
			}
		}

		private void updateExperimentSetupContext()
		{
			if (algorithmSelectionBox == null || setupModelDescriptionLabel == null)
			{
				return;
			}

			int algorithm = algorithmSelectionBox.getSelectedIndex();
			String description;
			boolean available = true;
			boolean experimental = false;
			boolean scalable = false;
			switch (algorithm)
			{
				case COS_SIM:
					description = "TF-IDF cosine similarity";
					break;
				case K_MEANS:
					description = "Cosine-clustered user similarity";
					break;
				case SVM:
					description = "Exact calibrated SMO followee classification";
					break;
				case SCALABLE_SVM:
					description = "Sparse high-cardinality multiclass max-margin SVM (separate model)";
					scalable = true;
					break;
				case MLP:
					description = "Neural followee classification";
					break;
				case Doc2Vec:
					description = "Learned document-vector similarity";
					break;
				case CommonNeighbors:
					description = "Shared-term common-neighbor user similarity";
					break;
				case K_MEANSEUCLIDEAN:
					description = "Nearest-centroid Euclidean cluster analysis";
					break;
				case TEMPORAL_GRAPH_FOUNDATION:
					description = "Temporal graph foundation profile: text + graph + time + bridge evidence";
					experimental = true;
					scalable = true;
					break;
				default:
					description = "Recommendation model";
					break;
			}
			setupModelDescriptionLabel.setText(description);
			boolean doc2VecSelected = algorithm == Doc2Vec;
			if (simulatorDoc2VecCacheSection != null)
			{
				simulatorDoc2VecCacheSection.setVisible(doc2VecSelected);
				simulatorDoc2VecCacheSection.revalidate();
			}
			if (!doc2VecSelected)
			{
				simulatorDoc2VecCacheInfo = null;
			}

			RecommendationCandidateType candidateType =
					RecommendationModeSupport.resolveCandidateType(
							getRecommendationTargetMode(), algorithm);
			setupOutputBadge.setText(
					candidateType == RecommendationCandidateType.FOLLOWEE
					? "Outputs accounts" : "Outputs users");

			if (!available)
			{
				setupAvailabilityBadge.setText("Unavailable");
				setupAvailabilityBadge.setVisible(true);
			}
			else if (experimental)
			{
				setupAvailabilityBadge.setText("Experimental");
				setupAvailabilityBadge.setVisible(true);
			}
			else if (scalable)
			{
				setupAvailabilityBadge.setText("High scale");
				setupAvailabilityBadge.setVisible(true);
			}
			else
			{
				setupAvailabilityBadge.setVisible(false);
			}
		}

		private File selectedSimulatorDoc2VecDatasetFile()
		{
			if (fileChooser == null)
			{
				return null;
			}
			File selected = fileChooser.getSelectedFile();
			return selected != null && selected.isFile()
					? selected.getAbsoluteFile() : null;
		}

		private void refreshSimulatorDoc2VecCacheStatus()
		{
			if (algorithmSelectionBox == null
					|| algorithmSelectionBox.getSelectedIndex() != Doc2Vec)
			{
				return;
			}
			final File selectedDataset = selectedSimulatorDoc2VecDatasetFile();
			if (selectedDataset == null)
			{
				simulatorDoc2VecCacheInfo = null;
				clearSimulatorDoc2VecCacheStats();
				applySimulatorDoc2VecCacheMessage(
						"CACHE UNKNOWN",
						"Choose a text dataset to inspect whether Doc2Vec will train or reuse cached vectors.",
						APP_MUTED_TEXT);
				if (simulatorDoc2VecRemoveButton != null)
				{
					simulatorDoc2VecRemoveButton.setEnabled(false);
				}
				return;
			}

			final int request = ++simulatorDoc2VecCacheRequest;
			applySimulatorDoc2VecCacheMessage(
					"CHECKING",
					"Reading Doc2Vec metadata and dataset shape in the background.",
					APP_BLUE);
			setSimulatorDoc2VecCacheButtons(false, false);
			Thread thread = new Thread(new Runnable() {
				public void run()
				{
					try
					{
						final Doc2VecCacheAdmin.CacheInfo info =
								Doc2VecCacheAdmin.inspect(
										System.getProperty(
													"recommender.python.command", "python3"),
											selectedDataset,
											Doc2VecCacheAdmin.trainingWorkers(),
											getAlgorithmParameterSettings());
						SwingUtilities.invokeLater(new Runnable() {
							public void run()
							{
								if (request == simulatorDoc2VecCacheRequest
										&& selectedDataset.equals(
												selectedSimulatorDoc2VecDatasetFile())
										&& algorithmSelectionBox.getSelectedIndex()
												== Doc2Vec)
								{
									applySimulatorDoc2VecCacheInfo(info);
								}
							}
						});
					}
					catch (final Throwable failure)
					{
						SwingUtilities.invokeLater(new Runnable() {
							public void run()
							{
								if (request == simulatorDoc2VecCacheRequest
										&& selectedDataset.equals(
												selectedSimulatorDoc2VecDatasetFile())
										&& algorithmSelectionBox.getSelectedIndex()
												== Doc2Vec)
								{
									simulatorDoc2VecCacheInfo = null;
									applySimulatorDoc2VecCacheMessage(
											"CACHE CHECK FAILED",
											failure.getMessage() == null
													? failure.getClass().getSimpleName()
													: failure.getMessage(),
											APP_RED);
									setSimulatorDoc2VecCacheButtons(true, false);
								}
							}
						});
					}
				}
			}, "Simulator-Doc2Vec-Cache-Inspector");
			thread.setDaemon(true);
			thread.start();
		}

		private void removeSimulatorDoc2VecCache()
		{
			if (algorithmSelectionBox == null
					|| algorithmSelectionBox.getSelectedIndex() != Doc2Vec)
			{
				return;
			}
			if (simulatorDoc2VecCacheDeletionLocked())
			{
				JOptionPane.showMessageDialog(
						this,
						"Stop the active simulator run before removing Doc2Vec cache files.",
						"Simulator Is Busy",
						JOptionPane.INFORMATION_MESSAGE);
				return;
			}
			final File selectedDataset = selectedSimulatorDoc2VecDatasetFile();
				if (selectedDataset == null
						|| simulatorDoc2VecCacheInfo == null
						|| !simulatorDoc2VecCacheInfo.hasExactCache())
				{
					return;
				}
			int answer = JOptionPane.showConfirmDialog(
					this,
					"<html><b>Remove Doc2Vec cache for this selected dataset?</b><br>"
							+ "The dataset file is not touched. The next Doc2Vec simulator run "
							+ "will retrain the shared model before scoring.</html>",
					"Remove Doc2Vec Cache",
					JOptionPane.OK_CANCEL_OPTION,
					JOptionPane.WARNING_MESSAGE);
			if (answer != JOptionPane.OK_OPTION)
			{
				return;
			}

			final int request = ++simulatorDoc2VecCacheRequest;
			applySimulatorDoc2VecCacheMessage(
					"CLEARING CACHE",
					"Removing only Doc2Vec cache files tied to the selected simulator dataset.",
					new Color(207, 125, 31));
			setSimulatorDoc2VecCacheButtons(false, false);
			Thread thread = new Thread(new Runnable() {
				public void run()
				{
					try
					{
						final Doc2VecCacheAdmin.DeleteResult result =
								Doc2VecCacheAdmin.delete(
										System.getProperty(
													"recommender.python.command", "python3"),
											selectedDataset,
											Doc2VecCacheAdmin.trainingWorkers(),
											getAlgorithmParameterSettings());
						SwingUtilities.invokeLater(new Runnable() {
							public void run()
							{
								if (request == simulatorDoc2VecCacheRequest
										&& selectedDataset.equals(
												selectedSimulatorDoc2VecDatasetFile())
										&& algorithmSelectionBox.getSelectedIndex()
												== Doc2Vec)
								{
									appendResult(
											"DOC2VEC CACHE: removed "
													+ result.getDeletedFiles()
													+ " file(s), "
													+ formatBytes(
															result.getDeletedBytes())
													+ ".");
									applySimulatorDoc2VecCacheInfo(
											result.getCacheInfoAfterDelete());
									setPerformanceStatus(
											"CACHE CLEARED",
											"Doc2Vec will retrain this dataset on the next run.",
											StatusTone.WARNING);
								}
							}
						});
					}
					catch (final Throwable failure)
					{
						SwingUtilities.invokeLater(new Runnable() {
							public void run()
							{
								if (request == simulatorDoc2VecCacheRequest
										&& selectedDataset.equals(
												selectedSimulatorDoc2VecDatasetFile())
										&& algorithmSelectionBox.getSelectedIndex()
												== Doc2Vec)
								{
									applySimulatorDoc2VecCacheMessage(
											"CACHE CLEAR FAILED",
											failure.getMessage() == null
													? failure.getClass().getSimpleName()
													: failure.getMessage(),
											APP_RED);
									setSimulatorDoc2VecCacheButtons(
												true,
												simulatorDoc2VecCacheInfo != null
														&& simulatorDoc2VecCacheInfo
																.hasExactCache());
								}
							}
						});
					}
				}
			}, "Simulator-Doc2Vec-Cache-Delete");
			thread.setDaemon(true);
			thread.start();
		}

		private boolean simulatorDoc2VecCacheDeletionLocked()
		{
			NodeExecutionMonitor.Snapshot snapshot =
					NodeExecutionMonitor.snapshot();
			if (snapshot.activeCount() > 0)
			{
				return true;
			}
			if (snapshot.getRunStartedAt() <= 0L
					|| snapshot.getNodes().isEmpty())
			{
				return false;
			}
			int terminal = snapshot.count(NodeExecutionMonitor.State.COMPLETE)
					+ snapshot.count(NodeExecutionMonitor.State.FAILED)
					+ snapshot.count(NodeExecutionMonitor.State.STOPPED);
			return terminal < snapshot.getNodes().size();
		}

		private void applySimulatorDoc2VecCacheInfo(
				Doc2VecCacheAdmin.CacheInfo info)
		{
			simulatorDoc2VecCacheInfo = info;
			simulatorDoc2VecRowsLabel.setText(formatCount(info.getDatasetRows()));
			simulatorDoc2VecUsersLabel.setText(formatCount(info.getDatasetUsers()));
			long vectors = info.getVectorRows() > 0L
					? info.getVectorRows() : info.getTagCount();
			simulatorDoc2VecVectorsLabel.setText(
					vectors > 0L
							? formatCount(vectors) + " x "
									+ Math.max(0L, info.getVectorDims())
							: "--");
			simulatorDoc2VecSizeLabel.setText(
					formatBytes(info.getDatasetCacheBytes()));
			String cacheKey = info.getCacheKey();
			simulatorDoc2VecKeyLabel.setText(
					cacheKey == null || cacheKey.length() == 0
							? "--"
							: cacheKey.length() > 10
									? cacheKey.substring(0, 10) : cacheKey);
			simulatorDoc2VecKeyLabel.setToolTipText(cacheKey);

			if (info.isWarm())
			{
				applySimulatorDoc2VecCacheMessage(
						"WARM CACHE",
						"Training will be skipped. The simulator will load "
								+ formatCount(vectors)
								+ " cached user vectors for "
								+ getAlgorithmParameterSettings().summaryForAlgorithm(Doc2Vec)
								+ ".",
						APP_GREEN);
			}
			else if (info.hasAnyDatasetCache())
			{
				applySimulatorDoc2VecCacheMessage(
						"STALE CACHE",
						"Cache files exist for this dataset path, but not for the exact current file and Doc2Vec parameter signature. The next run will retrain.",
						new Color(207, 125, 31));
			}
			else
			{
				applySimulatorDoc2VecCacheMessage(
						"COLD CACHE",
						"No Doc2Vec cache exists for this selected dataset. The next run will train first, then score.",
						APP_BLUE);
			}
				setSimulatorDoc2VecCacheButtons(
						true,
						info.hasExactCache()
								&& !simulatorDoc2VecCacheDeletionLocked());
		}

		private void clearSimulatorDoc2VecCacheStats()
		{
			if (simulatorDoc2VecRowsLabel != null)
			{
				simulatorDoc2VecRowsLabel.setText("--");
				simulatorDoc2VecUsersLabel.setText("--");
				simulatorDoc2VecVectorsLabel.setText("--");
				simulatorDoc2VecSizeLabel.setText("--");
				simulatorDoc2VecKeyLabel.setText("--");
				simulatorDoc2VecKeyLabel.setToolTipText(null);
			}
		}

		private void applySimulatorDoc2VecCacheMessage(
				String state,
				String detail,
				Color color)
		{
			if (simulatorDoc2VecCacheBadge != null)
			{
				simulatorDoc2VecCacheBadge.setText(state);
				simulatorDoc2VecCacheBadge.accentColor = color;
				simulatorDoc2VecCacheBadge.setForeground(color.darker());
				simulatorDoc2VecCacheBadge.repaint();
			}
			if (simulatorDoc2VecCacheDetailLabel != null)
			{
				simulatorDoc2VecCacheDetailLabel.setText(detail);
				simulatorDoc2VecCacheDetailLabel.setToolTipText(detail);
			}
		}

		private void setSimulatorDoc2VecCacheButtons(
				boolean refreshEnabled,
				boolean removeEnabled)
		{
			if (simulatorDoc2VecRefreshButton != null)
			{
				simulatorDoc2VecRefreshButton.setEnabled(refreshEnabled);
			}
			if (simulatorDoc2VecRemoveButton != null)
			{
				simulatorDoc2VecRemoveButton.setEnabled(removeEnabled);
			}
		}

	class MapperSelectionListener implements ActionListener
	{
		public void actionPerformed(ActionEvent event)
		{
			System.out.println("mapperSelectionBox.getSelectedIndex(): "+ mapperSelectionBox.getSelectedIndex());
			mapperChoice = mapperSelectionBox.getSelectedIndex();
			//			System.out.println("mapperChoice: "+mapperChoice+" reducerChoice: "+reducerChoice);
			if (reducerChoice == mapperChoice)
			{
				//				System.out.println("Entered mapper selection equals");
				if (reducerChoice == FEATURE_EXTRACTION)
				{
					mapperChoice = ALGORITHM_CLUSTERING;

				}
				else if (reducerChoice == ALGORITHM_CLUSTERING)
				{
					mapperChoice = FEATURE_EXTRACTION;
				}
				mapperSelectionBox.setSelectedIndex(mapperChoice);
			}
		}
	}
	
	// Added by Sepide 
	 /* class simulationSelectionListener implements ActionListener
		{
			
			public void actionPerformed(ActionEvent event)
				{
					System.out.println("simulationSelectionBox.getSelectedIndex(): "+ simulationSelectionBox.getSelectedIndex());
					simulationNo = simulationSelectionBox.getSelectedIndex();
					listSize = showAgentsList.getModel().getSize();
					
					while (sepNum < listSize) {
					indexToRecommend = sepNum;
					showAgentsList.setSelectedIndex(indexToRecommend);
					initializeAgents();
					sepNum++;
					
					
					break;
							
					}
						//for (int i=0; i<simulationNo; i++){
					startSimulation();
					return;
							
						//}
					
				}
		} */ 
      // End of code added by Sepide 
	  
	class ReducerSelectionListener implements ActionListener
	{
		public void actionPerformed(ActionEvent event)
		{
			System.out.println("reducerSelectionBox.getSelectedIndex(): "+ reducerSelectionBox.getSelectedIndex());
			reducerChoice = reducerSelectionBox.getSelectedIndex();
			//			System.out.println("mapperChoice: "+mapperChoice+" reducerChoice: "+reducerChoice);
			if (mapperChoice == reducerChoice)
			{
				//				System.out.println("Entered reducer selection equals");
				if (mapperChoice == FEATURE_EXTRACTION)
				{
					reducerChoice = ALGORITHM_CLUSTERING;

				}
				else if (mapperChoice == ALGORITHM_CLUSTERING)
				{
					reducerChoice = FEATURE_EXTRACTION;
				}
				reducerSelectionBox.setSelectedIndex(reducerChoice);
			}

		}
	}

	class GetReferenceListener implements ActionListener
	{
		public void actionPerformed(ActionEvent event)
		{
			String enteredData = enterDatasetField.getText();
			if (!enteredData.equals(""))
			{
				referenceUser = enteredData;
				System.out.println("referenceUser: "+referenceUser);
			}
		}
	}

	class NumNodesListener implements ActionListener
	{
		public void actionPerformed(ActionEvent event)
		{
			try
			{
				numNodes = Math.max(
						1,
						Integer.parseInt(numNodesField.getText().trim()));
			}
			catch (NumberFormatException exception)
			{
				numNodes = 1;
			}
			numNodesField.setText(String.valueOf(numNodes));

			RecommendationMemoryCoordinator.configureRequestedParallelism(
					numNodes);
			NodeCapacityRegistry.configureForNodeCount(
					numNodes,
					currentHeapSetting());
			updateNodeCapacitySummary();
			NodeExecutionMonitor.refreshCapacityProfiles();
			refreshSimulatorHelpPanel();
			if (actualSchedulerPanel != null)
			{
				actualSchedulerPanel.refreshFromSimulator();
			}
			if (schedulerLabPanel != null)
			{
				schedulerLabPanel.refreshCapacityFromSimulator();
			}
			System.out.println("numNodes: "+numNodes);

		}
	}

	class NumMappersListener implements ActionListener
	{
		public void actionPerformed(ActionEvent event)
		{
			if (numMappersField.getText().equals("") || Integer.parseInt(numMappersField.getText()) < 1)
				numMappers = 1;
			else
				numMappers = Integer.parseInt(numMappersField.getText());

			System.out.println("numMappers: "+numMappers);

		}
	}

	class NumReducersListener implements ActionListener
	{
		public void actionPerformed(ActionEvent event)
		{
			if (numReducersField.getText().equals("") || Integer.parseInt(numReducersField.getText()) < 1)
				numReducers = 1;
			else
				numReducers = Integer.parseInt(numReducersField.getText());

			System.out.println("numReducers: "+numReducers);

		}
	}

	//If blank, assume 1000 tweets limit
	class TweetLimitListener implements ActionListener
	{
		public void actionPerformed(ActionEvent event)
		{
			if (tweetLimitField.getText().equals(""))
				tweetLimit = 1000;
			else
				tweetLimit = Integer.parseInt(tweetLimitField.getText());

			System.out.println("tweetLimit: "+tweetLimit);

		}
	}

	class BeginDateListener implements ActionListener
	{
		public void actionPerformed(ActionEvent event)
		{
			if (beginDateField.getText().equals(""))
				beginDate = BEGIN_DATE;
			else
				beginDate = beginDateField.getText();

			System.out.println("beginDate: "+beginDate);

		}
	}

	class EndDateListener implements ActionListener
	{
		public void actionPerformed(ActionEvent event)
		{
			if (endDateField.getText().equals(""))
				endDate = END_DATE;
			else
				endDate = endDateField.getText();

			System.out.println("endDate: "+endDate);

		}
	}

	class RecommendationListener implements ActionListener
	{
		public void actionPerformed(ActionEvent event)
		{
			if (recommendationField.getText().equals("") || Integer.parseInt(recommendationField.getText()) < DEFAULT_K_REC)
				kRecommend = DEFAULT_K_REC;
			else
				kRecommend = Integer.parseInt(recommendationField.getText());

			System.out.println("kRecommend: "+kRecommend);

		}
	}

		private void createInitializationBoxes()
		{
			removeHashTags = new JCheckBox("Remove #'s");
			styleCheckBox(removeHashTags);
			removeHashTags.setSelected(true);
			removeHashTags.addItemListener(new RemoveBoxListener());

			removeRetweets = new JCheckBox("Remove RT's");
			styleCheckBox(removeRetweets);
			removeRetweets.setSelected(true);
			removeRetweets.addItemListener(new RemoveBoxListener());

			removeStopWords = new JCheckBox("Remove Stop Words");
			styleCheckBox(removeStopWords);
			removeStopWords.setSelected(true);
			removeStopWords.addItemListener(new RemoveBoxListener());

			simulateTweetDelay = new JCheckBox("Simulate Tweet Delay");
			styleCheckBox(simulateTweetDelay);
			simulateTweetDelay.setSelected(true);
			simulateTweetDelay.setToolTipText("Selected: original DSMP timing simulation. Unselected: skip the artificial MobileAgent tweet-delay wait after all tweet data is delivered.");
			simulateTweetDelay.addItemListener(new ItemListener()
			{
				public void itemStateChanged(ItemEvent event)
				{
					invalidateSimulatorInitializationState();
					refreshSimulatorHelpPanel();
				}
			});

			generateGraphOutput = new JCheckBox("Generate Final Graph Output");
			styleCheckBox(generateGraphOutput);
			generateGraphOutput.setSelected(true);
			generateGraphOutput.setToolTipText("Node and edge data is always saved. Selected: also build, export, and open the final graph after recommendations.");
			generateGraphOutput.addItemListener(new ItemListener()
			{
				public void itemStateChanged(ItemEvent event)
				{
					refreshSimulatorHelpPanel();
				}
			});
		}

	class RemoveBoxListener implements ItemListener
	{
		public void itemStateChanged(ItemEvent event)
		{
			int index = 0;
			Object source = event.getItemSelectable();
			if (source == removeHashTags)
			{
				index = 0;
				hashTags = HASH_TAGS;
			}
			else if (source == removeRetweets)
			{
				index = 1;
				retweets = RE_TWEETS;
			}
			else if (source == removeStopWords)
			{
				index = 2;
				stopWords = STOP_WORDS;
			}

			if (event.getStateChange() == ItemEvent.DESELECTED){
				if (index == 0)
					hashTags = 0;
				else if (index == 1)
					retweets = 0;
				else if (index == 2)
					stopWords = 0;

			}

				System.out.println("hashTags: "+hashTags+" retweets: "+retweets+ "stopWords: "+stopWords);
				invalidateSimulatorInitializationState();
				refreshSimulatorHelpPanel();
			}
		}


		private void createButtons()
		{
				initializeButton = createModernButton("Initialize", BUTTON_PRIMARY, LineIconType.INITIALIZE);
				initializeButton.addActionListener(this);

				startButton = createModernButton("Run Simulation", BUTTON_SECONDARY, LineIconType.PLAY);
				disableStartButton();
				//		startButton.setVisible(false);
				startButton.addActionListener(this);

				quitButton = createModernButton("Quit", BUTTON_DANGER, LineIconType.CLOSE);
				quitButton.addActionListener(this);

				resetExperimentButton = createModernButton("Reset Experiment", BUTTON_NEUTRAL, LineIconType.RESET);
				resetExperimentButton.addActionListener(this);

				applyHeapButton = createModernButton(
						"Apply & Restart",
						BUTTON_NEUTRAL,
						LineIconType.RESET);
				applyHeapButton.addActionListener(this);

				algorithmSettingsButton = createModernButton(
						"Algorithm Settings",
						BUTTON_NEUTRAL,
						LineIconType.SETTINGS);
				algorithmSettingsButton.setToolTipText(
						"Open validated advanced parameters for the selected algorithm.");
				algorithmSettingsButton.addActionListener(this);

				nodeCapacityButton = createModernButton(
						"Node Capacity",
						BUTTON_NEUTRAL,
						LineIconType.NODES);
				nodeCapacityButton.setToolTipText(
						"Configure capacity-aware node assignment for DSMP v4 scheduling and what-if analysis.");
				nodeCapacityButton.addActionListener(this);

				// added by Sepide
				simulationButton = createModernButton("Simulations", BUTTON_NEUTRAL, LineIconType.ITERATIONS);
				//simulationButton.addActionListener(new simulationSelectionListener());
				// added by Sepide

				getUsersButton = createModernButton("Get Users", BUTTON_PRIMARY, LineIconType.USERS);
				getUsersButton.addActionListener(this);

				changePerformanceButton = createModernButton("Recommender", BUTTON_PRIMARY, LineIconType.RECOMMENDER);
				changePerformanceButton.addActionListener(this);

				changeUserSimButton = createModernButton("User Studio", BUTTON_NEUTRAL, LineIconType.STUDIO);
				changeUserSimButton.addActionListener(this);

				helpUserSimButton = createModernButton("Help", BUTTON_NEUTRAL, LineIconType.HELP);
				helpUserSimButton.addActionListener(this);

				dataCollectionButton = createModernButton("Collect Data", BUTTON_PRIMARY, LineIconType.COLLECT);
				dataCollectionButton.addActionListener(this);

				loadCorpusButton = createModernButton("Load Corpus", BUTTON_PRIMARY, LineIconType.CORPUS);
				loadCorpusButton.addActionListener(this);

				setSimulatedTweetsButton = createModernButton("Tweet Count", BUTTON_PRIMARY, LineIconType.SETTINGS);
				setSimulatedTweetsButton.addActionListener(this);

				startUserSimButton = createModernButton("Start User Sim", BUTTON_SECONDARY, LineIconType.PLAY);
				startUserSimButton.addActionListener(this);

				exitButton = createModernButton("Exit", BUTTON_DANGER, LineIconType.CLOSE);
				exitButton.addActionListener(this);
			}

	private void createList()
	{
		showAgentsList = new JList(agentsList);
		/* S showAgentsList.setSelectionMode(
                     ListSelectionModel.MULTIPLE_INTERVAL_SELECTION); */  // added by Sepide to allow multiple users to be selected for Recommendation
		showAgentsList.setVisibleRowCount(28);
		showAgentsList.setPrototypeCellValue(String.format("%80s", ""));
		showAgentsList.setLayoutOrientation(JList.VERTICAL);
			//Only allow one user to be selected for recommendation
			showAgentsList.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
			showAgentsList.addListSelectionListener(new AddListListener());
			styleList();

		}

	class AddListListener implements ListSelectionListener
	{
		public void valueChanged(ListSelectionEvent event)
		{
			if (event.getValueIsAdjusting() == false) 
			{
				if (showAgentsList.getSelectedIndex() == -1) 
				{
					//No selection, disable start button.
					//					startButton.setEnabled(false);
					disableStartButton();
				}
				
				// Code added by Sepide 
				else if (simulationNo == one || simulationNo == two || simulationNo == three) {
					
					//algorithmRec = COS_SIM;
					enableStartButton();
					indexToRecommend = showAgentsList.getSelectedIndex();
					//recommendeeName = showAgentsList.getSelectedValue().toString();
					System.out.println("Please press the Initialize Button");
					System.out.println("Then Please press the Start Simulation Button");
					recommendeeName = showAgentsList.getSelectedValue().toString();
					// if (event.getSource() == simulationSelectionBox){
						// startSimulation(); 
					// }
					
					// simulationSelectionBox.addItemListener(new ItemListener(){
						// public void itemStateChanged(ItemEvent e){
							// System.out.println(e.getItem() + " " + e.getStateChange() );
							// startSimulation();
						// }
					// });
					
					//int selectedIndex = simulationSelectionBox.getSelectedIndex();
					
					 /* if (selectedIndex == 1) {
						  startSimulation();
					  }  */

					//for (int i=0; i< 10; i++)  {
						
						//usersRec = showAgentsList.selectValueAt(i);
						//usersRec.add(agentsList.selectValueAt(i));
						//indexToRecommend = 0;
						//showAgentsList.setSelectedIndex(indexToRecommend);
						
						//getUsersRec();
						
						//initializeAgents();
						//myStarter.setup();						
						
					//} 
					
				}
				// End of code added by Sepide 
				else 
				{
					//Selection, enable the start button.
					//					startButton.setEnabled(true);
					enableStartButton();
					//S indexArray = showAgentsList.getSelectedIndices();  // added by Sepide to get an array of selected indices for selected users 
					indexToRecommend = showAgentsList.getSelectedIndex();
					     //S indexToRecommend = indexArray[0];   // added by Sepideh
						 //S indexToRecommend2 = indexArray[1];   // added by Sepideh
					//S indexToRecommend2 = showAgentsList.getSelectedIndex();  // added by Sepide
					/*S names = showAgentsList.getSelectedValues();
					recommendeeName = names[0].toString();
					recommendeeName2 = names[1].toString();  */
					recommendeeName = showAgentsList.getSelectedValue().toString();
					//S recommendeeName2 = showAgentsList.getSelectedValue().toString();   // added by Sepide
				}
					if (showAgentsList.getSelectedIndex() >= 0)
					{
						int targetCount = showAgentsList.getSelectedIndices().length;
						boolean readyToRun = simulatorInitialized
								&& !simulatorInitializationInProgress;
							setPerformanceStatus(
									readyToRun ? "READY TO RUN" : "TARGETS SELECTED",
									targetCount + " recommendation target"
											+ (targetCount == 1 ? " is" : "s are")
											+ (readyToRun
											? " selected. Press Run Simulation."
											: " selected. Initialize when the settings are ready."),
									readyToRun ? StatusTone.SUCCESS : StatusTone.READY);
						}
					refreshSimulatorHelpPanel();
				}
			}
		}

	private void createModernPanels()
	{
		JMenuBar menuBar = new JMenuBar();
		menuBar.setOpaque(false);
		menuBar.setBorder(BorderFactory.createEmptyBorder());
		JMenu fileMenu = new JMenu("File");
		fileMenu.setFont(appFont(Font.BOLD, 13));
		fileMenu.setForeground(APP_TEXT);
		menuBar.add(fileMenu);
		fromTextMenu = new JMenuItem("Dataset From Text",KeyEvent.VK_T);
		fromTextMenu.addActionListener(this);
		fromDbMenu = new JMenuItem("Dataset From Database",KeyEvent.VK_D);
		fromDbMenu.addActionListener(this);
		fromGenerationMenu = new JMenuItem("Generated Dataset",KeyEvent.VK_G);
		fromGenerationMenu.addActionListener(this);
		fromArtificialMenu = new JMenuItem("Artificial Dataset",KeyEvent.VK_A);
		fromArtificialMenu.addActionListener(this);
		helpPerformanceMenu = new JMenuItem("Help",KeyEvent.VK_H);
		helpPerformanceMenu.addActionListener(this);
		fileMenu.add(fromTextMenu);
		fileMenu.add(fromDbMenu);
		fileMenu.add(fromGenerationMenu);
		fileMenu.add(fromArtificialMenu);
		fileMenu.addSeparator();
		fileMenu.add(helpPerformanceMenu);

		agentsListTitle = createModernTitle("List of Users");
		chooseDatasetTitle = createModernTitle("Data Source");
		initializationTitle = createModernTitle("Experiment Setup");
		labNameTitle = createModernTitle("Simulator");
		commandsTitle = createModernTitle("Run Pipeline");
		resultsTitle = createModernTitle("Live Output");
		previousResultsTitle = createModernTitle("Previous Timing Results");
		textProcessingTitle = createModernTitle("Text Processing");
		recommendationTitle = createModernTitle("Recommendations for User");
		userGenTweetsResultTitle = createModernTitle("Artificial Tweets");
		clusterTitle = createModernTitle("Homophily Validation");
		grabTwitterResultTitle = createModernTitle("Twitter Gathering Results");

		labNameLabel = new JLabel("DSMP Lab: Dr. Abdolreza Abhari");
		styleTitleLabel(labNameLabel, 18);
		userGenTitleLabel = new JLabel("User Generation Studio");
		styleTitleLabel(userGenTitleLabel, 18);
		userGenProgressLabel = createSubtleLabel("Current Progress: ");
		performanceProgressLabel = createSubtleLabel("Current Progress: ");

		JPanel mainPanel = new AppBackgroundPanel(new BorderLayout(16, 16));
		mainPanel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
		mainPanel.add(createRecommendationHeader(menuBar), BorderLayout.NORTH);
		mainPanel.add(createRecommendationWorkspace(), BorderLayout.CENTER);
		mainPanel.add(createStatusPanel(performanceProgressLabel, true), BorderLayout.SOUTH);

		JPanel userGenMainPanel = new AppBackgroundPanel(new BorderLayout(16, 16));
		userGenMainPanel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
		userGenMainPanel.add(createUserGenHeader(), BorderLayout.NORTH);
		userGenMainPanel.add(createUserGenWorkspace(), BorderLayout.CENTER);
		userGenMainPanel.add(createStatusPanel(userGenProgressLabel, false), BorderLayout.SOUTH);

		populationSweepPanel = new PopulationSweepPanel(this);
		uciRetailImportPanel = new UciRetailImportPanel(this);
		simulatorHelpPanel = new SimulatorHelpPanel(this);
		graphInsightsPanel = new GraphInsightsPanel(this);
		temporalGraphFoundationPanel = new TemporalGraphFoundationPanel(this);
		recommendationEvidenceStudioPanel =
				new RecommendationEvidenceStudioPanel(this);
		experimentObservatoryPanel = new ExperimentObservatoryPanel(this);
		actualSchedulerPanel = new ActualSchedulerPanel(this);
		liveWorkloadAdjustmentPanel = new LiveWorkloadAdjustmentPanel(this);
		schedulerLabPanel = new SchedulerLabPanel(this);
		dynamicQueueLabPanel = new DynamicQueueLabPanel(this);
		JComponent simulatorTab =
				StudioNightTheme.wrapTab(mainPanel, true);
		JComponent graphInsightsTab =
				StudioNightTheme.wrapTab(graphInsightsPanel, true);
		JComponent tgfStudioTab =
				StudioNightTheme.wrapTab(temporalGraphFoundationPanel, false);
		JComponent evidenceStudioTab =
				StudioNightTheme.wrapTab(recommendationEvidenceStudioPanel, true);
		JComponent observatoryTab =
				StudioNightTheme.wrapTab(experimentObservatoryPanel, true);
		JComponent userStudioTab =
				StudioNightTheme.wrapTab(userGenMainPanel, true);
		JComponent populationTab =
				StudioNightTheme.wrapTab(populationSweepPanel, true);
		JComponent schedulerTab =
				StudioNightTheme.wrapTab(actualSchedulerPanel, true);
		JComponent liveWorkloadTab =
				StudioNightTheme.wrapTab(liveWorkloadAdjustmentPanel, true);
		JComponent dynamicQueueTab =
				StudioNightTheme.wrapTab(dynamicQueueLabPanel, true);
		JComponent schedulerDemoTab =
				StudioNightTheme.wrapTab(schedulerLabPanel, true);
		JComponent importTab =
				StudioNightTheme.wrapTab(uciRetailImportPanel, true);
		JComponent helpTab =
				StudioNightTheme.wrapTab(simulatorHelpPanel, true);
		applicationTabs = new JTabbedPane();
		applicationTabs.setFont(appFont(Font.BOLD, 12));
		applicationTabs.setForeground(APP_TEXT);
		applicationTabs.setBackground(APP_BACKGROUND_TOP);
		applicationTabs.setBorder(BorderFactory.createEmptyBorder());
		applicationTabs.addTab(
				"Simulator",
				new LineIcon(LineIconType.RECOMMENDER, 16),
				simulatorTab,
				"Run the JADE recommendation simulator");
		applicationTabs.addTab(
				"Graph Insights",
				new LineIcon(LineIconType.NODES, 16),
				graphInsightsTab,
				"Analyze accumulated graph nodes, edges, hubs, and communities");
		applicationTabs.addTab(
				"TGF Studio",
				new LineIcon(LineIconType.NODES, 16),
				tgfStudioTab,
				"Inspect the latest Temporal Graph Foundation recommendation run");
		applicationTabs.addTab(
				"Evidence Studio",
				new LineIcon(LineIconType.RECOMMENDER, 16),
				evidenceStudioTab,
				"Explain completed recommendations with dataset, term, and graph evidence");
		applicationTabs.addTab(
				"Experiment Observatory",
				new LineIcon(LineIconType.NODES, 16),
				observatoryTab,
				"Design controlled benchmark suites and export reproducibility capsules");
		applicationTabs.addTab(
				"User Studio",
				new LineIcon(LineIconType.STUDIO, 16),
				userStudioTab,
				"Collect users, load corpora, and generate artificial tweet datasets");
		applicationTabs.addTab(
				"Exact Multi-Node Lab",
				new LineIcon(LineIconType.NODES, 16),
				populationTab,
				"Verified population-wide parallel recommendation benchmark");
		applicationTabs.addTab(
				"Scheduler Lab",
				new LineIcon(LineIconType.NODES, 16),
				schedulerTab,
				"Apply a real scheduling plan to the simulator's safe assignment boundary");
		applicationTabs.addTab(
				"Live Workload",
				new LineIcon(LineIconType.NODES, 16),
				liveWorkloadTab,
				"Adjust live safe-boundary queue work while the simulator is running");
		applicationTabs.addTab(
				"Dynamic Queue Lab (Demo)",
				new LineIcon(LineIconType.NODES, 16),
				dynamicQueueTab,
				"Run isolated pull-queue scheduling demonstrations without producing simulator recommendations");
		applicationTabs.addTab(
				"Scheduler Lab (Demo)",
				new LineIcon(LineIconType.NODES, 16),
				schedulerDemoTab,
				"Run isolated what-if scheduling demonstrations without changing simulator scoring");
		applicationTabs.addTab(
				"Data Preprocessing Lab",
				new LineIcon(LineIconType.TEXT_FILE, 16),
				importTab,
				"Convert, augment, balance, and audit DSMP-ready text datasets");
		applicationTabs.addTab(
				"Help",
				new LineIcon(LineIconType.HELP, 16),
				helpTab,
				"Understand the current simulator settings, data columns, nodes, and merge behavior");
		applicationTabs.addChangeListener(new ChangeListener()
		{
			public void stateChanged(ChangeEvent event)
			{
				if (isSelectedApplicationTab(graphInsightsPanel))
				{
					refreshGraphInsightsPanel();
				}
				else if (isSelectedApplicationTab(simulatorHelpPanel))
				{
					refreshSimulatorHelpPanel();
				}
				else if (isSelectedApplicationTab(temporalGraphFoundationPanel))
				{
					temporalGraphFoundationPanel.refreshFromRegistry();
				}
				else if (isSelectedApplicationTab(recommendationEvidenceStudioPanel))
				{
					recommendationEvidenceStudioPanel.refreshFromSimulator();
				}
				else if (isSelectedApplicationTab(experimentObservatoryPanel))
				{
					experimentObservatoryPanel.refreshFromSimulator();
				}
				else if (isSelectedApplicationTab(schedulerLabPanel))
				{
					schedulerLabPanel.refreshCapacityFromSimulator();
				}
				else if (isSelectedApplicationTab(actualSchedulerPanel))
				{
					actualSchedulerPanel.refreshFromSimulator();
				}
				else if (isSelectedApplicationTab(liveWorkloadAdjustmentPanel))
				{
					liveWorkloadAdjustmentPanel.refreshFromSimulator();
				}
			}
		});
		applicationShell = new JPanel(new BorderLayout());
		applicationShell.setOpaque(true);
		applicationShell.setBackground(APP_BACKGROUND_TOP);
		themeChromePanel = createThemeChrome();
		applicationShell.add(themeChromePanel, BorderLayout.NORTH);
		applicationShell.add(applicationTabs, BorderLayout.CENTER);
		add(applicationShell);
		applyNightMode(false);
		refreshSimulatorHelpPanel();
	}

	private boolean isSelectedApplicationTab(Component component)
	{
		if (applicationTabs == null)
		{
			return false;
		}
		return StudioNightTheme.unwrap(applicationTabs.getSelectedComponent())
				== component;
	}

	private ThemeChromePanel createThemeChrome()
	{
		ThemeChromePanel chrome = new ThemeChromePanel(new BorderLayout(14, 0));
		chrome.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));

		JPanel titleStack = new JPanel();
		titleStack.setOpaque(false);
		titleStack.setLayout(new BoxLayout(titleStack, BoxLayout.Y_AXIS));
		JLabel product = new JLabel("DSMP Studio");
		product.setFont(appFont(Font.BOLD, 13));
		product.setForeground(APP_TEXT);
		themeModeLabel = new JLabel("Day mode");
		themeModeLabel.setFont(appFont(Font.BOLD, 10));
		themeModeLabel.setForeground(APP_MUTED_TEXT);
		titleStack.add(product);
		titleStack.add(themeModeLabel);

		themeToggleButton = new ThemeToggleButton();
		themeToggleButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				applyNightMode(themeToggleButton.isSelected());
			}
		});

		chrome.add(titleStack, BorderLayout.WEST);
		chrome.add(themeToggleButton, BorderLayout.EAST);
		return chrome;
	}

	private void applyNightMode(boolean enabled)
	{
		StudioNightTheme.setNightMode(enabled);
		if (themeToggleButton != null && themeToggleButton.isSelected() != enabled)
		{
			themeToggleButton.setSelected(enabled);
		}
		StudioNightTheme.applyTabbedPaneTheme(this);
		StudioNightTheme.applyThemeTree(this);
		updateThemeChrome();
		if (applicationShell != null)
		{
			applicationShell.setBackground(enabled
					? StudioNightTheme.NIGHT_BACKGROUND
					: APP_BACKGROUND_TOP);
		}
		if (applicationTabs != null)
		{
			applicationTabs.setForeground(enabled
					? StudioNightTheme.NIGHT_TEXT
					: APP_TEXT);
			applicationTabs.setBackground(enabled
					? StudioNightTheme.NIGHT_BACKGROUND
					: APP_BACKGROUND_TOP);
			applicationTabs.revalidate();
		}
		revalidate();
		repaint();
	}

	private void updateThemeChrome()
	{
		boolean night = StudioNightTheme.isNightMode();
		if (themeModeLabel != null)
		{
			themeModeLabel.setText(night ? "Night mode" : "Day mode");
			themeModeLabel.setForeground(night
					? StudioNightTheme.NIGHT_MUTED
					: APP_MUTED_TEXT);
		}
		if (themeChromePanel != null)
		{
			themeChromePanel.repaint();
		}
		if (themeToggleButton != null)
		{
			themeToggleButton.repaint();
		}
	}

	private JPanel createRecommendationHeader(JMenuBar menuBar)
	{
		SurfacePanel headerPanel = createSurfacePanel(new BorderLayout(16, 0), APP_SURFACE);
		headerPanel.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));

		JPanel identityPanel = new JPanel(new BorderLayout(18, 0));
		identityPanel.setOpaque(false);
		JPanel titleStack = new JPanel();
		titleStack.setOpaque(false);
		titleStack.setLayout(new BoxLayout(titleStack, BoxLayout.Y_AXIS));
		titleStack.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel productTitle = createHeroTitle("Distributed Recommender Simulator");
		productTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
		productTitle.setHorizontalAlignment(SwingConstants.LEFT);
		titleStack.add(productTitle);
		titleStack.add(Box.createRigidArea(new Dimension(0, 4)));
		titleStack.add(createHeaderSubtitle("Run the JADE recommendation simulator."));
		titleStack.add(createAttributionBlock());
		identityPanel.add(titleStack, BorderLayout.WEST);
		identityPanel.add(new SignalGraphPanel(APP_BLUE, APP_GREEN), BorderLayout.CENTER);

		JPanel headerActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
		headerActions.setOpaque(false);
		headerActions.add(menuBar);
		headerActions.add(resetExperimentButton);
		headerActions.add(quitButton);

		headerPanel.add(identityPanel, BorderLayout.CENTER);
		headerPanel.add(headerActions, BorderLayout.EAST);
		return headerPanel;
	}

	private JPanel createRecommendationWorkspace()
	{
		JPanel workspace = new JPanel(new BorderLayout(16, 0));
		workspace.setOpaque(false);
		workspace.add(createRecommendationLeftColumn(), BorderLayout.WEST);
		workspace.add(createExperimentSetupPanel(), BorderLayout.CENTER);
		workspace.add(createOutputPanel(), BorderLayout.EAST);
		return workspace;
	}

	private JPanel createRecommendationLeftColumn()
	{
		JPanel leftColumn = new JPanel(new BorderLayout(0, 16));
		leftColumn.setOpaque(false);
		leftColumn.setPreferredSize(new Dimension(330, 0));
		leftColumn.add(createSourcePanel(), BorderLayout.NORTH);
		leftColumn.add(createUsersPanel(), BorderLayout.CENTER);
		return leftColumn;
	}

	private JPanel createSourcePanel()
	{
		SurfacePanel sourcePanel = createSurfacePanel(new BorderLayout(0, 12));
		sourcePanel.setBorder(BorderFactory.createCompoundBorder(chooseDatasetTitle, BorderFactory.createEmptyBorder(16, 16, 16, 16)));

		JPanel sourceChoices = new JPanel(new GridLayout(2, 2, 9, 9));
		sourceChoices.setOpaque(false);
		textSourceButton = new SourceChoiceButton(
				"Text file",
				"Load a Twitter or retail dataset",
				LineIconType.TEXT_FILE);
		textSourceButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent event) {
				fromTextMenu.doClick();
			}
		});
		databaseSourceButton = new SourceChoiceButton(
				"Database",
				"Use the configured MySQL source",
				LineIconType.DATABASE);
		databaseSourceButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent event) {
				fromDbMenu.doClick();
			}
		});
		generatedSourceButton = new SourceChoiceButton(
				"Generated",
				"Create a synthetic user network",
				LineIconType.GENERATED);
		generatedSourceButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent event) {
				fromGenerationMenu.doClick();
			}
		});
		artificialSourceButton = new SourceChoiceButton(
				"Artificial",
				"Use the bundled artificial corpus",
				LineIconType.ARTIFICIAL);
		artificialSourceButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent event) {
				fromArtificialMenu.doClick();
			}
		});
		sourceChoices.add(textSourceButton);
		sourceChoices.add(databaseSourceButton);
		sourceChoices.add(generatedSourceButton);
		sourceChoices.add(artificialSourceButton);

		JPanel selectedSourcePanel = new JPanel(new BorderLayout(10, 0));
		selectedSourcePanel.setOpaque(true);
		selectedSourcePanel.setBackground(APP_SURFACE_ALT);
		selectedSourcePanel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(new Color(205, 220, 238), 1),
				BorderFactory.createEmptyBorder(10, 11, 10, 11)));
		sourceSelectionIconLabel = new JLabel(new LineIcon(LineIconType.TEXT_FILE, 19));
		sourceSelectionIconLabel.setForeground(APP_BLUE);
		selectedSourcePanel.add(sourceSelectionIconLabel, BorderLayout.WEST);

		JPanel selectedSourceText = new JPanel();
		selectedSourceText.setOpaque(false);
		selectedSourceText.setLayout(new BoxLayout(selectedSourceText, BoxLayout.Y_AXIS));
		sourceSelectionTitleLabel = new JLabel("Text file selected");
		sourceSelectionTitleLabel.setFont(appFont(Font.BOLD, 12));
		sourceSelectionTitleLabel.setForeground(APP_TEXT);
		sourceSelectionDetailLabel = new JLabel("Choose a dataset file");
		sourceSelectionDetailLabel.setFont(appFont(Font.PLAIN, 10));
		sourceSelectionDetailLabel.setForeground(APP_MUTED_TEXT);
		selectedSourceText.add(sourceSelectionTitleLabel);
		selectedSourceText.add(Box.createRigidArea(new Dimension(0, 2)));
		selectedSourceText.add(sourceSelectionDetailLabel);
		selectedSourcePanel.add(selectedSourceText, BorderLayout.CENTER);

		JPanel datasetLabelPanel = new JPanel(new BorderLayout(0, 6));
		datasetLabelPanel.setOpaque(false);
		JLabel datasetLabelTitle = new JLabel("Experiment label");
		datasetLabelTitle.setFont(appFont(Font.BOLD, 11));
		datasetLabelTitle.setForeground(APP_MUTED_TEXT);
		datasetLabelTitle.setLabelFor(enterDatasetField);
		enterDatasetField.setToolTipText(
				"Names this dataset in result folders and database queries.");
		datasetLabelPanel.add(datasetLabelTitle, BorderLayout.NORTH);
		datasetLabelPanel.add(enterDatasetField, BorderLayout.CENTER);

		JPanel sourceDetails = new JPanel();
		sourceDetails.setOpaque(false);
		sourceDetails.setLayout(new BoxLayout(sourceDetails, BoxLayout.Y_AXIS));
		selectedSourcePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		datasetLabelPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		selectedSourcePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));
		datasetLabelPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
		sourceDetails.add(selectedSourcePanel);
		sourceDetails.add(Box.createRigidArea(new Dimension(0, 10)));
		sourceDetails.add(datasetLabelPanel);

		sourcePanel.add(sourceChoices, BorderLayout.NORTH);
		sourcePanel.add(sourceDetails, BorderLayout.CENTER);
		updateSourceSelection(
				FROM_TEXT,
				"Text file selected",
				"Choose a dataset file");
		return sourcePanel;
	}

	private void updateSourceSelection(int sourceMode, String title, String detail)
	{
		selectedSourceMode = sourceMode;
		if (textSourceButton != null)
		{
			textSourceButton.setSelected(sourceMode == FROM_TEXT);
			databaseSourceButton.setSelected(sourceMode == FROM_DB);
			generatedSourceButton.setSelected(sourceMode == FROM_GENERATION);
			artificialSourceButton.setSelected(sourceMode == FROM_ARTIFICIAL);
		}
		if (sourceSelectionTitleLabel != null)
		{
			sourceSelectionIconLabel.setIcon(new LineIcon(
					sourceMode == FROM_DB ? LineIconType.DATABASE
					: sourceMode == FROM_GENERATION ? LineIconType.GENERATED
					: sourceMode == FROM_ARTIFICIAL ? LineIconType.ARTIFICIAL
					: LineIconType.TEXT_FILE,
					19));
			sourceSelectionTitleLabel.setText(title);
			sourceSelectionDetailLabel.setText(detail);
			}
			updatePerformanceStatusContext();
			refreshSimulatorHelpPanel();
		}

	private String sourceModeName()
	{
		switch (selectedSourceMode)
		{
			case FROM_DB:
				return "Database";
			case FROM_GENERATION:
				return "Generated dataset";
			case FROM_ARTIFICIAL:
				return "Artificial dataset";
			case FROM_TEXT:
			default:
				return "Text dataset";
		}
	}

	private void updatePerformanceStatusContext()
	{
		if (performanceStatusContextLabel == null)
		{
			return;
		}
		StringBuilder context = new StringBuilder(sourceModeName());
		if (selectedSourceMode == FROM_TEXT
				&& fileChooser != null
				&& fileChooser.getSelectedFile() != null)
		{
			context.append("  |  ").append(fileChooser.getSelectedFile().getName());
		}
		if (agentsList != null && !agentsList.isEmpty())
		{
			context.append("  |  ").append(agentsList.size()).append(" users");
		}
		performanceStatusContextLabel.setText(context.toString());
	}

	private void setPerformanceStatus(
			final String status,
			final String detail,
			final StatusTone tone)
	{
		Runnable update = new Runnable() {
			public void run()
			{
				if (performanceStatusPill != null)
				{
					performanceStatusPill.setStatus(status, tone);
				}
				if (performanceProgressLabel != null)
				{
					performanceProgressLabel.setText(detail);
				}
				updatePerformanceStatusContext();
			}
		};
		if (SwingUtilities.isEventDispatchThread())
		{
			update.run();
		}
		else
		{
			SwingUtilities.invokeLater(update);
		}
	}

	private void updateRuntimeResourceStatus(
			final RecommendationMemoryCoordinator.Snapshot snapshot)
	{
		if (snapshot == null)
		{
			return;
		}
		Runnable update = new Runnable() {
			public void run()
			{
				if (runtimeMemoryLabel == null
						|| runtimeSchedulerLabel == null
						|| runtimeStatusPill == null)
				{
					return;
				}
				long physicalMemoryMb =
						RuntimeResourceSettings.totalPhysicalMemoryMb();
				String memoryText = "Heap "
						+ RuntimeResourceSettings.formatMegabytes(
								snapshot.getMaxHeapMb())
						+ "  |  Model budget "
						+ RuntimeResourceSettings.formatMegabytes(
								snapshot.getModelBudgetMb());
				if (physicalMemoryMb > 0L)
				{
					memoryText += "  |  System "
							+ RuntimeResourceSettings.formatMegabytes(
									physicalMemoryMb);
				}
				runtimeMemoryLabel.setText(memoryText);

				int requested = snapshot.getRequestedParallelism();
				int effectiveLimit = snapshot.getEffectiveLimit();
				int active = snapshot.getActiveLeases();
				int waiting = snapshot.getWaitingLeases();
				if (waiting > 0)
				{
					runtimeStatusPill.setStatus(
							"SAFE WAVES",
							StatusTone.WARNING);
					runtimeSchedulerLabel.setText(
							active + " model stage"
									+ (active == 1 ? "" : "s")
									+ " running, " + waiting
									+ " waiting safely; "
									+ snapshot.getReservedMb()
									+ " MB reserved.");
				}
				else if (active > 0)
				{
					runtimeStatusPill.setStatus(
							"PARALLEL " + active,
							StatusTone.RUNNING);
					runtimeSchedulerLabel.setText(
							active + " of " + requested
									+ " requested model stages are running; "
									+ snapshot.getReservedMb()
									+ " MB reserved.");
				}
				else if (requested > effectiveLimit)
				{
					runtimeStatusPill.setStatus(
							"CPU LIMITED",
							StatusTone.WARNING);
					runtimeSchedulerLabel.setText(
							requested + " nodes requested; up to "
									+ effectiveLimit
									+ " can run simultaneously before memory checks.");
				}
				else
				{
					runtimeStatusPill.setStatus(
							"ADAPTIVE",
							StatusTone.READY);
					runtimeSchedulerLabel.setText(
							requested + " node"
									+ (requested == 1 ? "" : "s")
									+ " requested; all run together when their models fit.");
				}
			}
		};
		if (SwingUtilities.isEventDispatchThread())
		{
			update.run();
		}
		else
		{
			SwingUtilities.invokeLater(update);
		}
	}

	private void applyHeapSetting()
	{
		final int requestedHeapMb;
		try
		{
			requestedHeapMb =
					RuntimeResourceSettings.parseHeapMb(maxHeapField.getText());
		}
		catch (IllegalArgumentException exception)
		{
			JOptionPane.showMessageDialog(
					this,
					exception.getMessage(),
					"Invalid Java Heap",
					JOptionPane.ERROR_MESSAGE);
			maxHeapField.requestFocusInWindow();
			return;
		}

		if (RuntimeResourceSettings.isCurrentHeap(requestedHeapMb))
		{
			JOptionPane.showMessageDialog(
					this,
					"The simulator is already running with a "
							+ RuntimeResourceSettings.formatMegabytes(
									requestedHeapMb)
							+ " maximum Java heap.",
					"Java Heap Already Active",
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		long physicalMemoryMb =
				RuntimeResourceSettings.totalPhysicalMemoryMb();
		int recommendedMaximum =
				RuntimeResourceSettings.maximumRecommendedHeapMb();
		StringBuilder message = new StringBuilder();
		message.append("Restart the simulator with a ")
				.append(RuntimeResourceSettings.formatMegabytes(requestedHeapMb))
				.append(" maximum Java heap?\n\n")
				.append("The current experiment will stop. After restart, the scheduler ")
				.append("will run every requested node simultaneously when the models fit, ")
				.append("or automatically use safe waves when they do not.");
		if (physicalMemoryMb > 0L)
		{
			message.append("\n\nSystem memory: ")
					.append(RuntimeResourceSettings.formatMegabytes(
							physicalMemoryMb));
		}
		if (requestedHeapMb > recommendedMaximum)
		{
			message.append("\n\nWarning: this exceeds the recommended 75% ")
					.append("of system memory and may cause the operating system to swap.");
		}

		int choice = JOptionPane.showConfirmDialog(
				this,
				message.toString(),
				"Apply Java Heap",
				JOptionPane.YES_NO_OPTION,
				requestedHeapMb > recommendedMaximum
						? JOptionPane.WARNING_MESSAGE
						: JOptionPane.QUESTION_MESSAGE);
		if (choice != JOptionPane.YES_OPTION)
		{
			return;
		}

		restartWithHeap(
				requestedHeapMb,
				"Applying the new Java heap and restarting the simulator.");
	}

	private void restartWithHeap(int requestedHeapMb, String statusMessage)
	{
		applyHeapButton.setEnabled(false);
		setPerformanceStatus("RESTARTING", statusMessage, StatusTone.RUNNING);
		runtimeStatusPill.setStatus("RESTARTING", StatusTone.RUNNING);
		runtimeSchedulerLabel.setText(
				"Waiting for the current JADE process to exit before relaunch.");
		try
		{
			RuntimeResourceSettings.saveHeapMb(requestedHeapMb);
			if (RuntimeRelauncher.isManagedByLauncher())
			{
				System.exit(RuntimeRelauncher.restartExitCode());
			}
			RuntimeRelauncher.relaunchWithHeap(requestedHeapMb);
			System.exit(0);
		}
		catch (Exception exception)
		{
			applyHeapButton.setEnabled(true);
			getUsersButton.setEnabled(true);
			setPerformanceStatus(
					"READY",
					"The heap restart could not be started.",
					StatusTone.ERROR);
			JOptionPane.showMessageDialog(
					this,
					"Could not restart the simulator:\n"
							+ exception.getMessage(),
					"Heap Restart Failed",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	int currentNodeCountSetting()
	{
		try
		{
			if (numNodesField != null
					&& numNodesField.getText() != null
					&& numNodesField.getText().trim().length() > 0)
			{
				return Math.max(1,
						Integer.parseInt(numNodesField.getText().trim()));
			}
		}
		catch (NumberFormatException ignored)
		{
		}
		return Math.max(1, numNodes);
	}

	int currentHeapSetting()
	{
		try
		{
			if (maxHeapField != null
					&& maxHeapField.getText() != null
					&& maxHeapField.getText().trim().length() > 0)
			{
				return RuntimeResourceSettings.parseHeapMb(
						maxHeapField.getText());
			}
		}
		catch (IllegalArgumentException ignored)
		{
		}
		return RuntimeResourceSettings.currentMaxHeapMb();
	}

	void applyObservatoryRunSpec(ExperimentObservatoryEngine.RunSpec run)
	{
		if (run == null)
		{
			return;
		}
		if (!SwingUtilities.isEventDispatchThread())
		{
			throw new IllegalStateException(
					"Observatory run specs must be applied on the Swing event thread.");
		}
		invalidateSimulatorInitializationState();
		int algorithm = Math.max(0, run.algorithm);
		if (algorithmSelectionBox != null
				&& algorithm < algorithmSelectionBox.getItemCount())
		{
			algorithmSelectionBox.setSelectedIndex(algorithm);
		}
		algorithmRec = algorithm;
		numNodes = Math.max(1, run.nodes);
		kRecommend = Math.max(DEFAULT_K_REC, run.topResults);
		if (numNodesField != null)
		{
			numNodesField.setText(Integer.toString(numNodes));
		}
		if (recommendationField != null)
		{
			recommendationField.setText(Integer.toString(kRecommend));
		}
		if (svmBatchUsersField != null)
		{
			svmBatchUsersField.setText(Integer.toString(Math.max(1, run.batchUsers)));
		}
		if (generateGraphOutput != null)
		{
			generateGraphOutput.setSelected(run.graphEnabled);
		}
		if (simulateTweetDelay != null)
		{
			simulateTweetDelay.setSelected(run.simulateTweetDelay);
		}
		NodeCapacityRegistry.configureForNodeCount(numNodes, currentHeapSetting());
		applyObservatorySchedulerPolicy(run);
		updateNodeCapacitySummary();
		NodeExecutionMonitor.refreshCapacityProfiles();
		updateBatchUsersFieldState();
		updateAlgorithmSettingsSummary();
		refreshSimulatorHelpPanel();
		setPerformanceStatus(
				"MATRIX RUN",
				"Configured " + run.id() + " for automated initialization.",
				StatusTone.RUNNING);
	}

	private void applyObservatorySchedulerPolicy(
			ExperimentObservatoryEngine.RunSpec run)
	{
		int heap = currentHeapSetting();
		String policyLabel = run.policy == null ? "" : run.policy.trim();
		if (policyLabel.length() == 0
				|| "DSMP default".equalsIgnoreCase(policyLabel))
		{
			RecommendationSchedulerRegistry.restoreDefault(numNodes, heap);
		}
		else
		{
			RecommendationSchedulerRegistry.apply(
					RecommendationSchedulerPlan.Mode.SCORING_SAFE,
					observatoryPolicyFor(policyLabel),
					numNodes,
					heap,
					"Experiment Observatory matrix row " + run.id());
		}
		if (actualSchedulerPanel != null)
		{
			actualSchedulerPanel.refreshFromSimulator();
		}
	}

	private SchedulerLabEngine.SchedulingPolicy observatoryPolicyFor(String label)
	{
		if (label != null)
		{
			for (SchedulerLabEngine.SchedulingPolicy policy
					: SchedulerLabEngine.SchedulingPolicy.values())
			{
				if (policy.getLabel().equalsIgnoreCase(label.trim()))
				{
					return policy;
				}
			}
			String normalized = label.toLowerCase(Locale.US);
			if (normalized.indexOf("weighted round") >= 0)
			{
				return SchedulerLabEngine.SchedulingPolicy.WEIGHTED_ROUND_ROBIN;
			}
			if (normalized.indexOf("memory") >= 0)
			{
				return SchedulerLabEngine.SchedulingPolicy.MEMORY_AWARE_SAFE_WAVE;
			}
			if (normalized.indexOf("capacity") >= 0)
			{
				return SchedulerLabEngine.SchedulingPolicy.CAPACITY_WEIGHTED;
			}
		}
		return SchedulerLabEngine.SchedulingPolicy.CAPACITY_WEIGHTED;
	}

	void beginObservatoryAutomation(
			String runId,
			ObservatoryAutomationListener listener)
	{
		synchronized (this)
		{
			observatoryAutomationMode = true;
			observatoryAutomationRunId = runId == null ? "" : runId;
			observatoryAutomationListener = listener;
		}
	}

	void endObservatoryAutomation(
			String runId,
			ObservatoryAutomationListener listener)
	{
		synchronized (this)
		{
			boolean sameRun = observatoryAutomationRunId.equals(
					runId == null ? "" : runId);
			boolean sameListener = observatoryAutomationListener == listener;
			if (sameRun && sameListener)
			{
				observatoryAutomationMode = false;
				observatoryAutomationRunId = "";
				observatoryAutomationListener = null;
			}
		}
	}

	private boolean isObservatoryAutomationActive()
	{
		return observatoryAutomationMode;
	}

	private boolean notifyObservatoryAutomationInitialized()
	{
		ObservatoryAutomationListener listener;
		String runId;
		synchronized (this)
		{
			if (!observatoryAutomationMode)
			{
				return false;
			}
			listener = observatoryAutomationListener;
			runId = observatoryAutomationRunId;
		}
		if (listener != null)
		{
			listener.initializationFinished(runId);
		}
		return true;
	}

	private boolean notifyObservatoryAutomationFinished(String message)
	{
		ObservatoryAutomationListener listener;
		String runId;
		synchronized (this)
		{
			if (!observatoryAutomationMode)
			{
				return false;
			}
			listener = observatoryAutomationListener;
			runId = observatoryAutomationRunId;
		}
		if (listener != null)
		{
			listener.simulationFinished(runId, message == null ? "" : message);
		}
		return true;
	}

	private boolean notifyObservatoryAutomationFailure(
			String phase,
			String message)
	{
		ObservatoryAutomationListener listener;
		String runId;
		synchronized (this)
		{
			if (!observatoryAutomationMode)
			{
				return false;
			}
			listener = observatoryAutomationListener;
			runId = observatoryAutomationRunId;
		}
		if (listener != null)
		{
			listener.automationFailed(
					runId,
					phase == null ? "automation" : phase,
					message == null ? "No detail was reported." : message);
		}
		return true;
	}

	private void openNodeCapacityDialog()
	{
		int nodeCount = currentNodeCountSetting();
		int heapMb = currentHeapSetting();
		NodeCapacityRegistry.configureForNodeCount(nodeCount, heapMb);
		boolean applied =
				NodeCapacitySettingsDialog.showDialog(this, nodeCount, heapMb);
		if (!applied)
		{
			return;
		}
		updateNodeCapacitySummary();
		NodeExecutionMonitor.refreshCapacityProfiles();
		refreshSimulatorHelpPanel();
		if (actualSchedulerPanel != null)
		{
			actualSchedulerPanel.refreshFromSimulator();
		}
		if (schedulerLabPanel != null)
		{
			schedulerLabPanel.refreshCapacityFromSimulator();
		}
		setPerformanceStatus(
				"CAPACITY",
				"Updated capacity-aware node assignment profile for scheduling and what-if analysis.",
				StatusTone.READY);
	}

	private void updateNodeCapacitySummary()
	{
		if (nodeCapacitySummaryLabel != null)
		{
			nodeCapacitySummaryLabel.setText(NodeCapacityRegistry.summary());
		}
	}

	private void setUserGenStatus(
			final String status,
			final String detail,
			final StatusTone tone,
			final String context)
	{
		Runnable update = new Runnable() {
			public void run()
			{
				if (userGenStatusPill != null)
				{
					userGenStatusPill.setStatus(status, tone);
				}
				if (userGenProgressLabel != null)
				{
					userGenProgressLabel.setText(detail);
				}
				if (userGenStatusContextLabel != null && context != null)
				{
					userGenStatusContextLabel.setText(context);
				}
			}
		};
		if (SwingUtilities.isEventDispatchThread())
		{
			update.run();
		}
		else
		{
			SwingUtilities.invokeLater(update);
		}
	}

	private void updateStatusFromLog(String resultText)
	{
		if (resultText == null)
		{
			return;
		}
		String normalized = resultText.toLowerCase(Locale.US);
		if (normalized.contains("text processing") || normalized.contains("after processing"))
		{
			setPerformanceStatus(
					"PROCESSING",
					"Cleaning and preparing tweet text across recommender nodes.",
					StatusTone.RUNNING);
		}
		else if (normalized.contains("tfidf"))
		{
			setPerformanceStatus(
					"FEATURES",
					"Building the feature representation used by the selected model.",
					StatusTone.RUNNING);
		}
		else if (normalized.contains("algorithm start")
				|| normalized.contains("starting algorithm"))
		{
			setPerformanceStatus(
					"SCORING",
					"Running the recommendation model for the selected targets.",
					StatusTone.RUNNING);
		}
		else if (normalized.contains("wait merge") || normalized.contains("merge completed"))
		{
			setPerformanceStatus(
					"MERGING",
					"Combining recommendation scores from all recommender nodes.",
					StatusTone.RUNNING);
		}
		else if (normalized.contains("wait querying") || normalized.contains("querying completed"))
		{
			setPerformanceStatus(
					"DELIVERING",
					"Delivering ranked recommendations to each target user.",
					StatusTone.RUNNING);
		}
		else if (normalized.contains("graph visualization")
				|| normalized.contains("graph export")
				|| normalized.contains("graph build"))
		{
			setPerformanceStatus(
					"GRAPH",
					"Building and exporting the final accumulated network graph.",
					StatusTone.RUNNING);
		}
	}

	private JPanel createUsersPanel()
	{
		JScrollPane dataPane = new JScrollPane(showAgentsList);
		styleScrollPane(dataPane);
		SurfacePanel usersPanel = createSurfacePanel(new BorderLayout(0, 10));
		usersPanel.setBorder(BorderFactory.createCompoundBorder(agentsListTitle, BorderFactory.createEmptyBorder(16, 16, 16, 16)));
		usersPanel.add(dataPane, BorderLayout.CENTER);
		usersPanel.add(getUsersButton, BorderLayout.SOUTH);
		return usersPanel;
	}

	private JPanel createExperimentSetupPanel()
	{
		SurfacePanel setupPanel = createSurfacePanel(new BorderLayout(0, 14));
		setupPanel.setBorder(BorderFactory.createCompoundBorder(initializationTitle, BorderFactory.createEmptyBorder(18, 18, 18, 18)));

		JPanel modelGrid = new JPanel(new GridBagLayout());
		modelGrid.setOpaque(false);
		addGridComponent(
				modelGrid,
				createFieldBlock(
						"Algorithm",
						"Choose the recommendation model.",
						algorithmSelectionBox),
				0, 1.0, new Insets(0, 0, 0, 7));
		addGridComponent(
				modelGrid,
				createFieldBlock(
						"Recommendation target",
						"Choose whether results represent users or field-0 accounts.",
						recommendationTargetModeBox),
				1, 1.0, new Insets(0, 7, 0, 0));

		JPanel modelContext = new JPanel();
		modelContext.setLayout(new BoxLayout(modelContext, BoxLayout.Y_AXIS));
		modelContext.setOpaque(true);
		modelContext.setBackground(APP_SURFACE_ALT);
		modelContext.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(new Color(205, 220, 238), 1),
				BorderFactory.createEmptyBorder(8, 11, 8, 11)));
		setupModelDescriptionLabel = new JLabel();
		setupModelDescriptionLabel.setFont(appFont(Font.BOLD, 12));
		setupModelDescriptionLabel.setForeground(APP_TEXT);
		setupModelDescriptionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		modelContext.add(setupModelDescriptionLabel);
		modelContext.add(Box.createRigidArea(new Dimension(0, 7)));

		JPanel modelBadges = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
		modelBadges.setOpaque(false);
		modelBadges.setAlignmentX(Component.LEFT_ALIGNMENT);
		setupOutputBadge = createBadge("Outputs users", APP_BLUE);
		setupBatchBadge = createBadge("Single target", APP_GREEN);
		setupAvailabilityBadge = createBadge("Unavailable", new Color(196, 92, 34));
		modelBadges.add(setupOutputBadge);
		modelBadges.add(setupBatchBadge);
		modelBadges.add(setupAvailabilityBadge);
		modelContext.add(modelBadges);

		JPanel modelContent = new JPanel();
		modelContent.setOpaque(false);
		modelContent.setLayout(new BoxLayout(modelContent, BoxLayout.Y_AXIS));
		modelGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
		modelContext.setAlignmentX(Component.LEFT_ALIGNMENT);
			modelGrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));
			modelContext.setMaximumSize(new Dimension(Integer.MAX_VALUE, 68));
			modelContent.add(modelGrid);
			modelContent.add(Box.createRigidArea(new Dimension(0, 9)));
			modelContent.add(modelContext);
			modelContent.add(Box.createRigidArea(new Dimension(0, 9)));
			modelContent.add(createAlgorithmSettingsPanel());
			JComponent doc2VecCacheCard = createSimulatorDoc2VecCachePanel();

		numNodesStepper = new NumericStepper(numNodesField, 1, 1);
		tweetLimitStepper = new NumericStepper(tweetLimitField, 0, 100);
		recommendationStepper = new NumericStepper(recommendationField, DEFAULT_K_REC, 1);
		batchUsersStepper = new NumericStepper(svmBatchUsersField, 1, 1);
		maxHeapStepper = new NumericStepper(
				maxHeapField,
				RuntimeResourceSettings.MIN_HEAP_MB,
				RuntimeResourceSettings.HEAP_STEP_MB);

		JPanel workloadGrid = new JPanel(new GridLayout(1, 4, 12, 0));
		workloadGrid.setOpaque(false);
		workloadGrid.add(createFieldBlock(
				"Recommender nodes",
				"Number of distributed recommender agents.",
				numNodesStepper));
		workloadGrid.add(createFieldBlock(
				"Latest tweets",
				"0 uses every available tweet; otherwise use the newest count.",
				tweetLimitStepper));
		workloadGrid.add(createFieldBlock(
				"Top results",
				"Number of ranked recommendations shown per target.",
				recommendationStepper));
		workloadGrid.add(createFieldBlock(
				"Batch users",
				"Available for exact SVM, Scalable Linear SVM, and Doc2Vec.",
				batchUsersStepper));

		JPanel dateRange = new JPanel(new GridBagLayout());
		dateRange.setOpaque(false);
		JPanel beginBlock = createFieldBlock(
				"From",
				"Experiment start date in YYYY-MM-DD format.",
				beginDateField);
		JPanel endBlock = createFieldBlock(
				"To",
				"Experiment end date in YYYY-MM-DD format.",
				endDateField);
		JLabel dateArrow = new JLabel("\u2192");
		dateArrow.setHorizontalAlignment(SwingConstants.CENTER);
		dateArrow.setForeground(APP_MUTED_TEXT);
		dateArrow.setFont(appFont(Font.BOLD, 18));
		addGridComponent(dateRange, beginBlock, 0, 1.0, new Insets(0, 0, 0, 8));
		addGridComponent(dateRange, dateArrow, 1, 0.0, new Insets(23, 0, 0, 0));
		addGridComponent(dateRange, endBlock, 2, 1.0, new Insets(0, 8, 0, 0));

		JPanel windowGrid = new JPanel(new GridBagLayout());
		windowGrid.setOpaque(false);
		addGridComponent(
				windowGrid,
				createFieldBlock(
						"Date window",
						"Only records inside this date range are included.",
						dateRange),
				0, 2.4, new Insets(0, 0, 0, 14));
		addGridComponent(
				windowGrid,
				createFieldBlock(
						"Iteration",
						"Simulation iteration index.",
						simulationSelectionBox),
				1, 0.7, new Insets(0, 0, 0, 0));

		JPanel textProcessingPanel = createSurfacePanel(new FlowLayout(FlowLayout.LEFT, 10, 0), APP_SURFACE_ALT);
		textProcessingPanel.setBorder(BorderFactory.createCompoundBorder(textProcessingTitle, BorderFactory.createEmptyBorder(12, 12, 12, 12)));
		textProcessingPanel.add(removeHashTags);
		textProcessingPanel.add(removeRetweets);
			textProcessingPanel.add(removeStopWords);
			textProcessingPanel.add(simulateTweetDelay);

			JPanel outputOptionsPanel = createSurfacePanel(new FlowLayout(FlowLayout.LEFT, 10, 0), APP_SURFACE_ALT);
			outputOptionsPanel.setBorder(BorderFactory.createCompoundBorder(
					createModernTitle("Post-processing"),
					BorderFactory.createEmptyBorder(12, 12, 12, 12)));
			outputOptionsPanel.add(generateGraphOutput);

			JPanel commandsPanel = createSurfacePanel(new GridLayout(0, 2, 10, 10), APP_SURFACE_ALT);
		commandsPanel.setBorder(BorderFactory.createCompoundBorder(commandsTitle, BorderFactory.createEmptyBorder(14, 14, 14, 14)));
		commandsPanel.add(initializeButton);
		commandsPanel.add(startButton);
		commandsPanel.add(simulationButton);
		JButton helpButton = createModernButton("Help", BUTTON_NEUTRAL, LineIconType.HELP);
		helpButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent event) {
				helpPerformanceMenu.doClick();
			}
		});
		commandsPanel.add(helpButton);

		SetupScrollPanel setupStack = new SetupScrollPanel(new GridBagLayout());
		JPanel modelSection = createSetupSection("Model & objective", modelContent);
		simulatorDoc2VecCacheSection =
				createSetupSection("Doc2Vec cache", doc2VecCacheCard);
		JPanel workloadSection = createSetupSection("Workload", workloadGrid);
		JPanel runtimeSection = createRuntimeResourcesPanel();
		JPanel windowSection = createSetupSection("Data window", windowGrid);
		addSetupStackRow(setupStack, modelSection, 0, 14);
		addSetupStackRow(setupStack, simulatorDoc2VecCacheSection, 1, 14);
		addSetupStackRow(setupStack, workloadSection, 2, 14);
		addSetupStackRow(setupStack, runtimeSection, 3, 14);
		addSetupStackRow(setupStack, windowSection, 4, 14);
		addSetupStackRow(setupStack, textProcessingPanel, 5, 14);
		addSetupStackRow(setupStack, outputOptionsPanel, 6, 4);

		GridBagConstraints fillerConstraints = new GridBagConstraints();
		fillerConstraints.gridx = 0;
		fillerConstraints.gridy = 7;
		fillerConstraints.weightx = 1.0;
		fillerConstraints.weighty = 1.0;
		fillerConstraints.fill = GridBagConstraints.BOTH;
		setupStack.add(Box.createGlue(), fillerConstraints);

		JScrollPane setupScrollPane = new JScrollPane(
				setupStack,
				ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
				ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		setupScrollPane.setBorder(BorderFactory.createEmptyBorder());
		setupScrollPane.setOpaque(false);
		setupScrollPane.getViewport().setOpaque(false);
		setupScrollPane.getVerticalScrollBar().setUnitIncrement(16);
		setupPanel.add(setupScrollPane, BorderLayout.CENTER);
		setupPanel.add(commandsPanel, BorderLayout.SOUTH);
		updateBatchUsersFieldState();
		updateExperimentSetupContext();
		return setupPanel;
	}

	private JPanel createAlgorithmSettingsPanel()
	{
		JPanel panel = new JPanel(new BorderLayout(12, 0));
		panel.setOpaque(true);
		panel.setBackground(new Color(250, 252, 255));
		panel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(new Color(207, 218, 234), 1),
				BorderFactory.createEmptyBorder(10, 12, 10, 12)));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));

		JPanel copy = new JPanel();
		copy.setOpaque(false);
		copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
		JLabel title = new JLabel("Advanced parameters");
		title.setFont(appFont(Font.BOLD, 12));
		title.setForeground(APP_TEXT);
		algorithmSettingsSummaryLabel = new JLabel();
		algorithmSettingsSummaryLabel.setFont(appFont(Font.PLAIN, 10));
		algorithmSettingsSummaryLabel.setForeground(APP_MUTED_TEXT);
		copy.add(title);
		copy.add(Box.createRigidArea(new Dimension(0, 3)));
		copy.add(algorithmSettingsSummaryLabel);
		panel.add(copy, BorderLayout.CENTER);

		JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		controls.setOpaque(false);
		algorithmSettingsProfileLabel = createBadge("Default", APP_GREEN);
		algorithmSettingsButton.setPreferredSize(new Dimension(190, 34));
		controls.add(algorithmSettingsProfileLabel);
		controls.add(algorithmSettingsButton);
		panel.add(controls, BorderLayout.EAST);
		updateAlgorithmSettingsSummary();
		return panel;
	}

	private JComponent createSimulatorDoc2VecCachePanel()
	{
		simulatorDoc2VecCachePanel =
				createSurfacePanel(new BorderLayout(14, 0), APP_SURFACE_ALT);
		simulatorDoc2VecCachePanel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(new Color(190, 218, 204), 1),
				BorderFactory.createEmptyBorder(10, 12, 10, 12)));

		JPanel textStack = new JPanel();
		textStack.setOpaque(false);
		textStack.setLayout(new BoxLayout(textStack, BoxLayout.Y_AXIS));
		JPanel heading = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		heading.setOpaque(false);
		JLabel title = new JLabel("Doc2Vec cache transparency");
		title.setFont(appFont(Font.BOLD, 12));
		title.setForeground(APP_TEXT);
		simulatorDoc2VecCacheBadge = createBadge("CACHE UNKNOWN", APP_MUTED_TEXT);
		heading.add(title);
		heading.add(simulatorDoc2VecCacheBadge);

		simulatorDoc2VecCacheDetailLabel = new JLabel(
				"Choose a text dataset to inspect whether Doc2Vec will train or reuse cached vectors.");
		simulatorDoc2VecCacheDetailLabel.setFont(appFont(Font.PLAIN, 10));
		simulatorDoc2VecCacheDetailLabel.setForeground(APP_MUTED_TEXT);

		JPanel stats = new JPanel(new GridLayout(1, 5, 8, 0));
		stats.setOpaque(false);
		simulatorDoc2VecRowsLabel = cacheStatValue("--");
		simulatorDoc2VecUsersLabel = cacheStatValue("--");
		simulatorDoc2VecVectorsLabel = cacheStatValue("--");
		simulatorDoc2VecSizeLabel = cacheStatValue("--");
		simulatorDoc2VecKeyLabel = cacheStatValue("--");
		stats.add(createCacheStatBlock("Dataset rows", simulatorDoc2VecRowsLabel));
		stats.add(createCacheStatBlock("Target users", simulatorDoc2VecUsersLabel));
		stats.add(createCacheStatBlock("User vectors", simulatorDoc2VecVectorsLabel));
		stats.add(createCacheStatBlock("Cache size", simulatorDoc2VecSizeLabel));
		stats.add(createCacheStatBlock("Cache key", simulatorDoc2VecKeyLabel));

		textStack.add(heading);
		textStack.add(Box.createRigidArea(new Dimension(0, 4)));
		textStack.add(simulatorDoc2VecCacheDetailLabel);
		textStack.add(Box.createRigidArea(new Dimension(0, 8)));
		textStack.add(stats);

		JPanel actions = new JPanel();
		actions.setOpaque(false);
		actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
		simulatorDoc2VecRefreshButton =
				createModernButton("Refresh Cache", BUTTON_NEUTRAL, LineIconType.RESET);
		simulatorDoc2VecRemoveButton =
				createModernButton("Remove This Cache", BUTTON_DANGER, LineIconType.CLOSE);
		simulatorDoc2VecRefreshButton.setMaximumSize(new Dimension(176, 34));
		simulatorDoc2VecRemoveButton.setMaximumSize(new Dimension(176, 34));
		simulatorDoc2VecRefreshButton.setToolTipText(
				"Re-check whether this selected dataset has a valid Doc2Vec cache.");
		simulatorDoc2VecRemoveButton.setToolTipText(
				"Delete only Doc2Vec cache files for the selected simulator dataset.");
		simulatorDoc2VecRefreshButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent event)
			{
				refreshSimulatorDoc2VecCacheStatus();
			}
		});
		simulatorDoc2VecRemoveButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent event)
			{
				removeSimulatorDoc2VecCache();
			}
		});
		actions.add(simulatorDoc2VecRefreshButton);
		actions.add(Box.createRigidArea(new Dimension(0, 7)));
		actions.add(simulatorDoc2VecRemoveButton);

		simulatorDoc2VecCachePanel.add(textStack, BorderLayout.CENTER);
		simulatorDoc2VecCachePanel.add(actions, BorderLayout.EAST);
		applySimulatorDoc2VecCacheMessage(
				"CACHE UNKNOWN",
				"Choose a text dataset to inspect whether Doc2Vec will train or reuse cached vectors.",
				APP_MUTED_TEXT);
		simulatorDoc2VecRemoveButton.setEnabled(false);
		return simulatorDoc2VecCachePanel;
	}

	private JPanel createCacheStatBlock(String labelText, JLabel valueLabel)
	{
		JPanel block = new JPanel(new BorderLayout(0, 2));
		block.setOpaque(false);
		JLabel title = new JLabel(labelText);
		title.setFont(appFont(Font.BOLD, 9));
		title.setForeground(APP_MUTED_TEXT);
		block.add(title, BorderLayout.NORTH);
		block.add(valueLabel, BorderLayout.CENTER);
		return block;
	}

	private JLabel cacheStatValue(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(appFont(Font.BOLD, 12));
		label.setForeground(APP_TEXT);
		return label;
	}

	private JPanel createRuntimeResourcesPanel()
	{
		JPanel heapControl = new JPanel(new BorderLayout(8, 0));
		heapControl.setOpaque(false);
		heapControl.add(maxHeapStepper, BorderLayout.CENTER);
		heapControl.add(applyHeapButton, BorderLayout.EAST);

		JPanel statusPanel = new JPanel(new BorderLayout(10, 0));
		statusPanel.setOpaque(true);
		statusPanel.setBackground(APP_SURFACE_ALT);
		statusPanel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(new Color(205, 220, 238), 1),
				BorderFactory.createEmptyBorder(9, 11, 9, 11)));
		runtimeStatusPill = new StatusPill("ADAPTIVE", StatusTone.READY);
		statusPanel.add(runtimeStatusPill, BorderLayout.WEST);

		JPanel statusText = new JPanel();
		statusText.setOpaque(false);
		statusText.setLayout(new BoxLayout(statusText, BoxLayout.Y_AXIS));
		runtimeMemoryLabel = new JLabel("Reading JVM memory limits...");
		runtimeMemoryLabel.setFont(appFont(Font.BOLD, 11));
		runtimeMemoryLabel.setForeground(APP_TEXT);
		runtimeSchedulerLabel = new JLabel(
				"Requested nodes run together when memory permits.");
		runtimeSchedulerLabel.setFont(appFont(Font.PLAIN, 10));
		runtimeSchedulerLabel.setForeground(APP_MUTED_TEXT);
		statusText.add(runtimeMemoryLabel);
		statusText.add(Box.createRigidArea(new Dimension(0, 3)));
		statusText.add(runtimeSchedulerLabel);
		statusPanel.add(statusText, BorderLayout.CENTER);

		JPanel capacityPanel = new JPanel(new BorderLayout(10, 0));
		capacityPanel.setOpaque(true);
		capacityPanel.setBackground(APP_SURFACE_ALT);
		capacityPanel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(new Color(205, 220, 238), 1),
				BorderFactory.createEmptyBorder(9, 11, 9, 11)));
		JPanel capacityText = new JPanel();
		capacityText.setOpaque(false);
		capacityText.setLayout(new BoxLayout(capacityText, BoxLayout.Y_AXIS));
		JLabel capacityTitle = new JLabel("Node capacity contract");
		capacityTitle.setFont(appFont(Font.BOLD, 11));
		capacityTitle.setForeground(APP_TEXT);
		nodeCapacitySummaryLabel =
				new JLabel(NodeCapacityRegistry.summary());
		nodeCapacitySummaryLabel.setFont(appFont(Font.PLAIN, 10));
		nodeCapacitySummaryLabel.setForeground(APP_MUTED_TEXT);
		capacityText.add(capacityTitle);
		capacityText.add(Box.createRigidArea(new Dimension(0, 3)));
		capacityText.add(nodeCapacitySummaryLabel);
		nodeCapacityButton.setPreferredSize(new Dimension(152, 34));
		capacityPanel.add(capacityText, BorderLayout.CENTER);
		capacityPanel.add(nodeCapacityButton, BorderLayout.EAST);

		JPanel resourcesGrid = new JPanel(new GridBagLayout());
		resourcesGrid.setOpaque(false);
		addGridComponent(
				resourcesGrid,
				createFieldBlock(
						"Maximum Java heap (MB)",
						"Applying a new limit safely restarts the simulator.",
						heapControl),
				0, 1.0, new Insets(0, 0, 0, 12));
		addGridComponent(
				resourcesGrid,
				createFieldBlock(
						"Parallel execution",
						"Runs all requested nodes when they fit; otherwise uses safe waves.",
						statusPanel),
				1, 1.25, new Insets(0, 0, 0, 12));
		addGridComponent(
				resourcesGrid,
				createFieldBlock(
						"Node capacity",
						"Configure CPU, memory, queue, delay, and reliability for each node.",
						capacityPanel),
				2, 1.35, new Insets(0, 0, 0, 0));
		return createSetupSection("Runtime resources", resourcesGrid);
	}

	private void addSetupStackRow(
			JPanel stack,
			Component component,
			int row,
			int bottomInset)
	{
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = 0;
		constraints.gridy = row;
		constraints.weightx = 1.0;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.anchor = GridBagConstraints.NORTHWEST;
		constraints.insets = new Insets(0, 0, bottomInset, 6);
		stack.add(component, constraints);
	}

	private JPanel createOutputPanel()
	{
		SurfacePanel outputPanel = createSurfacePanel(new BorderLayout(0, 10));
		outputPanel.setPreferredSize(new Dimension(470, 0));
		outputPanel.setBorder(BorderFactory.createCompoundBorder(resultsTitle, BorderFactory.createEmptyBorder(16, 16, 16, 16)));
		outputPanel.add(createOutputTabs(), BorderLayout.CENTER);
		return outputPanel;
	}

		private JPanel createUserGenHeader()
		{
			SurfacePanel userGenHeader = createSurfacePanel(new BorderLayout(16, 0), APP_SURFACE);
			userGenHeader.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
			JPanel userGenHeaderText = new JPanel();
			userGenHeaderText.setOpaque(false);
			userGenHeaderText.setLayout(new BoxLayout(userGenHeaderText, BoxLayout.Y_AXIS));
			userGenHeaderText.setAlignmentX(Component.LEFT_ALIGNMENT);
			JLabel studioTitle = createHeroTitle("User Generation Studio");
			studioTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
			studioTitle.setHorizontalAlignment(SwingConstants.LEFT);
			userGenHeaderText.add(studioTitle);
			userGenHeaderText.add(Box.createRigidArea(new Dimension(0, 4)));
			userGenHeaderText.add(createHeaderSubtitle("Collect users, load corpora, and generate artificial tweet datasets."));
			userGenHeaderText.add(createAttributionBlock());

			JPanel userGenIdentityPanel = new JPanel(new BorderLayout(18, 0));
			userGenIdentityPanel.setOpaque(false);
			userGenIdentityPanel.add(userGenHeaderText, BorderLayout.WEST);
		userGenIdentityPanel.add(new SignalGraphPanel(APP_GREEN, new Color(128, 88, 216)), BorderLayout.CENTER);

		JPanel userGenHeaderActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
		userGenHeaderActions.setOpaque(false);
		userGenHeaderActions.add(exitButton);

		userGenHeader.add(userGenIdentityPanel, BorderLayout.CENTER);
		userGenHeader.add(userGenHeaderActions, BorderLayout.EAST);
		return userGenHeader;
	}

	private JPanel createUserGenWorkspace()
	{
		JPanel workspace = new JPanel(new BorderLayout(16, 0));
		workspace.setOpaque(false);

		SurfacePanel userGenOptionsPanel = createSurfacePanel(new GridLayout(0, 1, 0, 10));
		userGenOptionsPanel.setPreferredSize(new Dimension(310, 0));
		userGenOptionsPanel.setBorder(BorderFactory.createCompoundBorder(createModernTitle("Actions"), BorderFactory.createEmptyBorder(16, 16, 16, 16)));
		userGenOptionsPanel.add(helpUserSimButton);
		userGenOptionsPanel.add(dataCollectionButton);
		userGenOptionsPanel.add(loadCorpusButton);
		userGenOptionsPanel.add(setSimulatedTweetsButton);
		userGenOptionsPanel.add(startUserSimButton);

		SurfacePanel userGenOutputPanel = createSurfacePanel(new BorderLayout(0, 10));
		userGenOutputPanel.setBorder(BorderFactory.createCompoundBorder(createModernTitle("Studio Output"), BorderFactory.createEmptyBorder(16, 16, 16, 16)));
		userGenOutputPanel.add(createUserGenTabs(), BorderLayout.CENTER);

		workspace.add(userGenOptionsPanel, BorderLayout.WEST);
		workspace.add(userGenOutputPanel, BorderLayout.CENTER);
		return workspace;
	}

		//Creates all the panels in the GUI and add to the frame
		private void createPanels()
		{
			JMenuBar menuBar = new JMenuBar();
			menuBar.setOpaque(false);
			menuBar.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
			JMenu fileMenu = new JMenu("File");
			fileMenu.setFont(appFont(Font.BOLD, 13));
			fileMenu.setForeground(APP_TEXT);
			menuBar.add(fileMenu);
			fromTextMenu = new JMenuItem("Dataset From Text",KeyEvent.VK_T);
			fromTextMenu.addActionListener(this);
			fromDbMenu = new JMenuItem("Dataset From Database",KeyEvent.VK_D);
		fromDbMenu.addActionListener(this);
		fromGenerationMenu = new JMenuItem("Generated Dataset",KeyEvent.VK_G);
		fromGenerationMenu.addActionListener(this);
		fromArtificialMenu = new JMenuItem("Artificial Dataset",KeyEvent.VK_A);
		fromArtificialMenu.addActionListener(this);
		helpPerformanceMenu = new JMenuItem("Help",KeyEvent.VK_H);
		helpPerformanceMenu.addActionListener(this);
		fileMenu.add(fromTextMenu);
		fileMenu.add(fromDbMenu);
		fileMenu.add(fromGenerationMenu);
			fileMenu.add(fromArtificialMenu);
			fileMenu.add(helpPerformanceMenu);

			JPanel mainPanel = new AppBackgroundPanel(new BorderLayout(14, 14));
			mainPanel.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
			mainPanel.add(menuBar,BorderLayout.NORTH);

			JPanel userGenMainPanel = new AppBackgroundPanel(new BorderLayout(14, 14));
			userGenMainPanel.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

			JPanel centerPanel = new JPanel();
			centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
			centerPanel.setOpaque(false);
			centerPanel.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));

			JPanel resultsPanel = new JPanel();
			resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.Y_AXIS));
			resultsPanel.setOpaque(false);
			resultsPanel.setPreferredSize(new Dimension(440, 0));

			JPanel inputDbPanel = createSurfacePanel(new FlowLayout(FlowLayout.CENTER, 10, 8));
			JPanel commandsPanel = createSurfacePanel(new GridLayout(0, 2, 10, 10));
			JPanel labNamePanel = createSurfacePanel(new BorderLayout());
			JPanel labNamePanel2 = createSurfacePanel(new BorderLayout());
			JPanel userGenHeaderTextPanel = new JPanel();
			userGenHeaderTextPanel.setOpaque(false);
			labNamePanel2.setLayout(new BoxLayout(labNamePanel2, BoxLayout.Y_AXIS));
			userGenHeaderTextPanel.setLayout(new BoxLayout(userGenHeaderTextPanel, BoxLayout.Y_AXIS));

			JPanel initializationsPanel = createSurfacePanel(new GridLayout(0, 2, 10, 8));
			JPanel textProcessingPanel = createSurfacePanel(new FlowLayout(FlowLayout.CENTER, 12, 4));
			JPanel userGenOptionsPanel = createSurfacePanel(new GridLayout(0, 1, 0, 10));
			userGenOptionsPanel.setPreferredSize(new Dimension(280, 0));
			JPanel userGenResultsPanel = new JPanel();
			userGenResultsPanel.setLayout(new BoxLayout(userGenResultsPanel, BoxLayout.Y_AXIS));
			userGenResultsPanel.setOpaque(false);
			JPanel userGenPanel = new JPanel();
			userGenPanel.setLayout(new BorderLayout(14, 0));
			userGenPanel.setOpaque(false);
			JPanel userGenProgressPanel = createSurfacePanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
			JPanel progressPanel = createSurfacePanel(new FlowLayout(FlowLayout.LEFT, 8, 0));

			JScrollPane resultScrollPane = new JScrollPane(resultArea);
			styleScrollPane(resultScrollPane);
			JScrollPane previousResultScrollPane = new JScrollPane(previousResultArea);
			styleScrollPane(previousResultScrollPane);
			JScrollPane dataPane = new JScrollPane(showAgentsList);
			dataPane.setPreferredSize(new Dimension(290, 0));
			styleScrollPane(dataPane);
			JScrollPane recommendationPane = new JScrollPane(recommendationArea);
			recommendationPane.setPreferredSize(new Dimension(300,145));
			recommendationPane.setMaximumSize(new Dimension(900,165));
			styleScrollPane(recommendationPane);
			JScrollPane userGenTweetsScrollPane = new JScrollPane(userGenTweetsResultArea);
			userGenTweetsScrollPane.setMaximumSize(new Dimension(600,750));
			styleScrollPane(userGenTweetsScrollPane);
			JScrollPane clusterResultScrollPane = new JScrollPane(clusterResultArea);
			clusterResultScrollPane.setMaximumSize(new Dimension(600,750));
			styleScrollPane(clusterResultScrollPane);
			JScrollPane grabTwitterResultScrollPane = new JScrollPane(grabTwitterResultArea);
			grabTwitterResultScrollPane.setMaximumSize(new Dimension(750,750));
			styleScrollPane(grabTwitterResultScrollPane);

			agentsListTitle = createModernTitle("List of Users");
			chooseDatasetTitle = createModernTitle("Dataset Chooser");
			initializationTitle = createModernTitle("Performance Measurement");
			labNameTitle = createModernTitle("DSMP Lab");
			commandsTitle = createModernTitle("Commands");
			resultsTitle = createModernTitle("Timing Results");
			previousResultsTitle = createModernTitle("Previous Timing Results");
			textProcessingTitle = createModernTitle("Text Processing Parameters");
			recommendationTitle = createModernTitle("Recommendations for User");
			userGenTweetsResultTitle = createModernTitle("Artificial Tweets");
			clusterTitle = createModernTitle("Homophily Validation");
			grabTwitterResultTitle = createModernTitle("Twitter Gathering Results");

			dataPane.setBorder(BorderFactory.createCompoundBorder(agentsListTitle, BorderFactory.createEmptyBorder(8, 8, 8, 8)));
			resultScrollPane.setBorder(BorderFactory.createCompoundBorder(resultsTitle, BorderFactory.createEmptyBorder(8, 8, 8, 8)));
			previousResultScrollPane.setBorder(BorderFactory.createCompoundBorder(previousResultsTitle, BorderFactory.createEmptyBorder(8, 8, 8, 8)));
			textProcessingPanel.setBorder(BorderFactory.createCompoundBorder(textProcessingTitle, BorderFactory.createEmptyBorder(16, 16, 16, 16)));
			recommendationPane.setBorder(BorderFactory.createCompoundBorder(recommendationTitle, BorderFactory.createEmptyBorder(8, 8, 8, 8)));
			userGenTweetsScrollPane.setBorder(BorderFactory.createCompoundBorder(userGenTweetsResultTitle, BorderFactory.createEmptyBorder(8, 8, 8, 8)));
			clusterResultScrollPane.setBorder(BorderFactory.createCompoundBorder(clusterTitle, BorderFactory.createEmptyBorder(8, 8, 8, 8)));
			grabTwitterResultScrollPane.setBorder(BorderFactory.createCompoundBorder(grabTwitterResultTitle, BorderFactory.createEmptyBorder(8, 8, 8, 8)));
			initializationsPanel.setBorder(BorderFactory.createCompoundBorder(initializationTitle, BorderFactory.createEmptyBorder(18, 18, 18, 18)));
			commandsPanel.setBorder(BorderFactory.createCompoundBorder(commandsTitle, BorderFactory.createEmptyBorder(18, 18, 18, 18)));
			labNamePanel.setBorder(BorderFactory.createCompoundBorder(labNameTitle, BorderFactory.createEmptyBorder(12, 16, 12, 16)));

			inputDbPanel.add(enterDatasetLabel);
			inputDbPanel.add(enterDatasetField);
			inputDbPanel.setBorder(chooseDatasetTitle);

	//		labNameLabel = new JLabel("DSMP Lab: Dr. Abdolreza Abhari, Sepideh Banihashemi");
			labNameLabel = new JLabel("DSMP Lab: Dr. Abdolreza Abhari");
			labNameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
			styleTitleLabel(labNameLabel, 20);

			userGenTitleLabel = new JLabel("TWITTER USER GENERATION SIMULATION");
			userGenTitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
			styleTitleLabel(userGenTitleLabel, 18);

			userGenProgressLabel = new JLabel("Current Progress: ");
			userGenProgressLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
			userGenProgressLabel.setForeground(APP_MUTED_TEXT);
			userGenProgressLabel.setFont(appFont(Font.BOLD, 13));

			performanceProgressLabel = new JLabel("Current Progress: ");
			performanceProgressLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
			performanceProgressLabel.setForeground(APP_MUTED_TEXT);
			performanceProgressLabel.setFont(appFont(Font.BOLD, 13));

			labNamePanel.add(labNameLabel, BorderLayout.CENTER);
			userGenHeaderTextPanel.add(labNameLabel = new JLabel("DSMP Lab: Dr. Abdolreza Abhari"));
			styleTitleLabel(labNameLabel, 20);
			labNameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
			userGenHeaderTextPanel.add(userGenTitleLabel);
			labNamePanel2.add(userGenHeaderTextPanel, BorderLayout.CENTER);

			initializationsPanel.add(numNodesLabel);
			initializationsPanel.add(numNodesField);
		initializationsPanel.add(tweetLimitLabel);
		initializationsPanel.add(tweetLimitField);
		// initializationsPanel.add(numMappersLabel);
		// initializationsPanel.add(numMappersField);
		// initializationsPanel.add(numReducersLabel);
		// initializationsPanel.add(numReducersField);	
		// initializationsPanel.add(mapperChoiceLabel);
		// initializationsPanel.add(mapperSelectionBox);
		// initializationsPanel.add(reducerChoiceLabel);
		// initializationsPanel.add(reducerSelectionBox);
		initializationsPanel.add(beginDateLabel);
		initializationsPanel.add(beginDateField);
		initializationsPanel.add(endDateLabel);
		initializationsPanel.add(endDateField);
			initializationsPanel.add(recommendationLabel);
			initializationsPanel.add(recommendationField);
			initializationsPanel.add(svmBatchUsersLabel);
			initializationsPanel.add(svmBatchUsersField);
			initializationsPanel.add(algorithmLabel);
			initializationsPanel.add(algorithmSelectionBox);
			initializationsPanel.add(recommendationTargetModeLabel);
			initializationsPanel.add(recommendationTargetModeBox);
		initializationsPanel.add(simulationNumber);      // added by Sepide
			initializationsPanel.add(simulationSelectionBox);        // added by Sepide

			textProcessingPanel.add(removeHashTags);
			textProcessingPanel.add(removeRetweets);
			textProcessingPanel.add(removeStopWords);
			textProcessingPanel.add(simulateTweetDelay);
			textProcessingPanel.add(generateGraphOutput);
		
		commandsPanel.add(getUsersButton);
		commandsPanel.add(initializeButton);
		commandsPanel.add(startButton);
			commandsPanel.add(resetExperimentButton);
			commandsPanel.add(quitButton);
			commandsPanel.add(simulationButton);   // added by Sepide
			commandsPanel.add(changeUserSimButton);

			userGenOptionsPanel.add(helpUserSimButton);
			userGenOptionsPanel.add(dataCollectionButton);
		userGenOptionsPanel.add(loadCorpusButton);
		userGenOptionsPanel.add(setSimulatedTweetsButton);
		userGenOptionsPanel.add(startUserSimButton);
		userGenOptionsPanel.add(changePerformanceButton);
		userGenOptionsPanel.add(exitButton);

			userGenResultsPanel.add(userGenTweetsScrollPane);
			userGenResultsPanel.add(Box.createRigidArea(new Dimension(0, 12)));
			userGenResultsPanel.add(clusterResultScrollPane);

			userGenProgressPanel.add(userGenProgressLabel);

			progressPanel.add(performanceProgressLabel);

			Dimension maxSize = new Dimension(Short.MAX_VALUE, 100);

			centerPanel.add(labNamePanel);
			centerPanel.add(Box.createRigidArea(new Dimension(0,10)));
			centerPanel.add(initializationsPanel);
			centerPanel.add(Box.createRigidArea(new Dimension(0,10)));
			centerPanel.add(textProcessingPanel);
			centerPanel.add(Box.createRigidArea(new Dimension(0,12)));
			centerPanel.add(recommendationPane);
			centerPanel.add(Box.createRigidArea(new Dimension(0,12)));
			centerPanel.add(commandsPanel);

			resultsPanel.add(resultScrollPane);
			resultsPanel.add(Box.createRigidArea(new Dimension(0, 12)));
			resultsPanel.add(previousResultScrollPane);

			mainPanel.add(dataPane,BorderLayout.WEST);
		mainPanel.add(centerPanel,BorderLayout.CENTER);
		mainPanel.add(resultsPanel,BorderLayout.EAST);
		mainPanel.add(progressPanel,BorderLayout.SOUTH);
		
			userGenPanel.add(userGenOptionsPanel, BorderLayout.WEST);
			userGenPanel.add(grabTwitterResultScrollPane, BorderLayout.CENTER);

			userGenMainPanel.add(labNamePanel2,BorderLayout.NORTH);
			userGenMainPanel.add(userGenResultsPanel,BorderLayout.CENTER);
		userGenMainPanel.add(userGenPanel,BorderLayout.WEST);
		userGenMainPanel.add(userGenProgressPanel,BorderLayout.SOUTH);

			cards = new JPanel(new CardLayout());
			cards.setOpaque(false);
			cards.add(userGenMainPanel,USERSIM_PANEL_ID);
			cards.add(mainPanel,PERFORMANCE_PANEL_ID);
		
		add(cards);
		// add(mainPanel);

	}

	public void actionPerformed(ActionEvent event) {

		if (event.getSource() == quitButton || event.getSource() == exitButton)
		{
			shutDown();
		}	
		else if (event.getSource() == getUsersButton)
		{
			getUsers();
		}
		else if (event.getSource() == resetExperimentButton)
		{
			requestExperimentReset();
		}
		else if (event.getSource() == applyHeapButton)
		{
			applyHeapSetting();
		}
		else if (event.getSource() == algorithmSettingsButton)
		{
			openAlgorithmSettingsDialog();
		}
		else if (event.getSource() == nodeCapacityButton)
		{
			openNodeCapacityDialog();
		}
		else if (event.getSource() == initializeButton)
		{
			initializeAgents();
		}
		else if (event.getSource() == startButton)
		{
			startSimulation();
		}
		else if (event.getSource() == dataCollectionButton)
		{
			System.out.println("DATA COLLECTION");
			setUserGenStatus(
					"CONFIGURING",
					"Enter the accounts and follower count to collect.",
					StatusTone.RUNNING,
					"Twitter collection setup");
			selectTwitterOptions();
		}
		else if (event.getSource() == loadCorpusButton)
		{
			System.out.println("LOAD A CORPUS");
			setUserGenStatus(
					"SELECTING",
					"Choose the source corpus for artificial tweet generation.",
					StatusTone.RUNNING,
					"Corpus selection");
			selectCorpusFile();
		}
		else if (event.getSource() == setSimulatedTweetsButton)
		{
			System.out.println("SETTING SIZE OF SIMULATED TWEETS");
			selectNumArtificialTweets();
		}		
		else if (event.getSource() == startUserSimButton)
		{
			System.out.println("STARTING USER SIM");
			setUserGenStatus(
					"RUNNING",
					"Generating artificial tweets and validating homophily.",
					StatusTone.RUNNING,
					"User generation simulation");
			startUserGenSimulation();
		}
		else if (event.getSource() == changePerformanceButton)
		{
			changeCardPanel(CHANGE_TO_PERFORMANCE);
		}
		else if (event.getSource() == changeUserSimButton)
		{
			changeCardPanel(CHANGE_TO_USERSIM);
		}
		else if (event.getSource() == helpUserSimButton)
		{
			helpUserSimText();
		}
		else if (event.getSource() == fromTextMenu)
		{
			selectFile(FROM_TEXT);
			System.out.println("fromTextMenu");
		}
		else if (event.getSource() == fromDbMenu)
		{
			invalidateSimulatorInitializationState();
			myAgent.setReadFrom(FROM_DB);
			updateSourceSelection(
					FROM_DB,
					"Database selected",
					"Configured MySQL source");
			setPerformanceStatus(
					"READY",
					"Database source selected. Press Get Users to query available users.",
					StatusTone.READY);
			System.out.println("fromDbMenu");
		}
		else if (event.getSource() == fromGenerationMenu)
		{
			invalidateSimulatorInitializationState();
			selectNumFolloweesFollowers();
			System.out.println("fromGenerationMenu");
		}
		else if (event.getSource() == fromArtificialMenu)
		{
			invalidateSimulatorInitializationState();
			myAgent.setReadFrom(FROM_ARTIFICIAL);
			updateSourceSelection(
					FROM_ARTIFICIAL,
					"Artificial dataset selected",
					"Bundled artificial corpus");
			setPerformanceStatus(
					"READY",
					"Artificial dataset selected. Press Get Users to load its users.",
					StatusTone.READY);
			System.out.println("fromArtificialMenu");
		}
		else if (event.getSource() == helpPerformanceMenu)
		{
			helpPerformanceText();
			System.out.println("helpPerformanceMenu");
		}
	}

	public void noFileSelected()
	{
		setPerformanceStatus(
				"WAITING",
				"Select a dataset file before loading users.",
				StatusTone.WARNING);
		JOptionPane.showMessageDialog(this,"No file was selected. Please select a file before proceeding in simulation!","No File Selected", JOptionPane.INFORMATION_MESSAGE);
	}
	
	//performance measurement file chooser
	public void selectFile(int fileOption)
	{
		File selectedFile = ModernFilePicker.chooseDataset(
				this,
				fileChooser.getCurrentDirectory(),
				fileChooser.getSelectedFile());
			if (selectedFile != null)
			{
				invalidateSimulatorInitializationState();
				fileChooser.setSelectedFile(selectedFile);
				if (selectedFile.getParentFile() != null)
			{
				fileChooser.setCurrentDirectory(selectedFile.getParentFile());
			}
			myAgent.setFile(selectedFile);
			updateSourceSelection(
					FROM_TEXT,
					"Text dataset ready",
					selectedFile.getName());
			setPerformanceStatus(
					"READY",
					"Dataset selected. Press Get Users to inspect available recommendation targets.",
					StatusTone.READY);
			refreshSimulatorDoc2VecCacheStatus();
			System.out.println("Selected file: " + selectedFile.getAbsolutePath());
			JOptionPane.showMessageDialog(
					this,
					"Loaded File: " + selectedFile.getName(),
					"File Selected",
					JOptionPane.INFORMATION_MESSAGE);
		}
		else
		{
			setPerformanceStatus(
					"WAITING",
					"No file was selected. Choose a text dataset when ready.",
					StatusTone.WARNING);
		}

		if (fileOption == FROM_TEXT)
			myAgent.setReadFrom(FROM_TEXT);
	}

	void loadImportedDataset(File selectedFile, String experimentLabel)
	{
		if (selectedFile == null || !selectedFile.isFile())
		{
			setPerformanceStatus(
					"WAITING",
					"The imported dataset file could not be found.",
					StatusTone.WARNING);
			return;
			}
			fileChooser.setSelectedFile(selectedFile);
			invalidateSimulatorInitializationState();
			if (selectedFile.getParentFile() != null)
			{
				fileChooser.setCurrentDirectory(selectedFile.getParentFile());
		}
		myAgent.setFile(selectedFile);
		myAgent.setReadFrom(FROM_TEXT);
		if (experimentLabel != null && experimentLabel.trim().length() > 0)
		{
			enterDatasetField.setText(experimentLabel.trim());
		}
		updateSourceSelection(
				FROM_TEXT,
				"Imported dataset ready",
				selectedFile.getName());
		setPerformanceStatus(
				"READY",
				"Imported DSMP dataset selected. Press Get Users to inspect available recommendation targets.",
				StatusTone.READY);
		refreshSimulatorDoc2VecCacheStatus();
			selectApplicationTab(APPLICATION_TAB_SIMULATOR);
	}
	
	//user gen sim corpus chooser
	public void selectCorpusFile()
	{
		File selectedFile = ModernFilePicker.chooseTextFile(
				this,
				fileChooser.getCurrentDirectory(),
				fileChooser.getSelectedFile());
		if (selectedFile != null)
		{
			fileChooser.setSelectedFile(selectedFile);
			if (selectedFile.getParentFile() != null)
			{
				fileChooser.setCurrentDirectory(selectedFile.getParentFile());
			}
			myAgent.setCorpusGenFile(selectedFile);
			setUserGenStatus(
					"READY",
					"Corpus loaded. Set the tweet count or start the user simulation.",
					StatusTone.SUCCESS,
					selectedFile.getName());
			System.out.println("Selected corpus file: " + selectedFile.getAbsolutePath());
			JOptionPane.showMessageDialog(
					this,
					"Loaded File: " + selectedFile.getName(),
					"File Selected",
					JOptionPane.INFORMATION_MESSAGE);
		}
		else
		{
			setUserGenStatus(
					"WAITING",
					"No corpus selected.",
					StatusTone.WARNING,
					"Corpus selection cancelled");
		}
	}
	
	public void selectNumFolloweesFollowers()
	{
		JTextField followeesField = new JTextField(5);
		JTextField followersField = new JTextField(5);
		JTextField tweetsGeneratedField = new JTextField(9);

		JPanel myPanel = new JPanel();
		myPanel.add(new JLabel("Number of Followees: "));
		myPanel.add(followeesField);
		myPanel.add(Box.createHorizontalStrut(15)); // a spacer
		myPanel.add(new JLabel("Number of Followers: "));
		myPanel.add(followersField);
		myPanel.add(Box.createHorizontalStrut(15)); // a spacer
		myPanel.add(new JLabel("Number of Tweets: "));
		myPanel.add(tweetsGeneratedField);

		int result = JOptionPane.showConfirmDialog(null, myPanel, 
				"Please Enter Followee, Follower, Total Tweets Values", JOptionPane.OK_CANCEL_OPTION);
		if (result == JOptionPane.OK_OPTION) {
			System.out.println("Number of Followees: " + followeesField.getText());
			System.out.println("Number of Followers: " + followersField.getText());
			System.out.println("Number of Tweets: " + tweetsGeneratedField.getText());
			numFollowees = Integer.parseInt(followeesField.getText());
			numFollowers = Integer.parseInt(followersField.getText());
			numTweetsGenerated = Integer.parseInt(tweetsGeneratedField.getText());
			updateSourceSelection(
					FROM_GENERATION,
					"Generated dataset configured",
					numFollowees + " followees, " + numFollowers
							+ " followers, " + numTweetsGenerated + " tweets");
			setPerformanceStatus(
					"READY",
					"Generated dataset configured. Press Get Users to create the synthetic population.",
					StatusTone.READY);
		}
		else
		{
			updateSourceSelection(
					FROM_GENERATION,
					"Generated dataset selected",
					"Using the current generation parameters");
			setPerformanceStatus(
					"READY",
					"Generated source selected with the current parameters.",
					StatusTone.READY);
		}
		myAgent.setReadFrom(FROM_GENERATION);
	}
	
	public void selectTwitterOptions()
	{
		JTextField numFollowersToGrabField = new JTextField(5);
		JTextField nameFolloweesToGrabField = new JTextField(30);

		JPanel myPanel = new JPanel();
		myPanel.add(new JLabel("Name of Followee(s): "));
		myPanel.add(nameFolloweesToGrabField);
		myPanel.add(Box.createHorizontalStrut(15)); // a spacer
		myPanel.add(new JLabel("Number of Followers: "));
		myPanel.add(numFollowersToGrabField);
	

		int result = JOptionPane.showConfirmDialog(null, myPanel, 
				"Please Enter Followee Name(s) separated by comma if more than 1, Number of Followers", JOptionPane.OK_CANCEL_OPTION);
		if (result == JOptionPane.OK_OPTION) {
			System.out.println("Name of Followee(s): " + nameFolloweesToGrabField.getText());
			System.out.println("Number of Followers: " + numFollowersToGrabField.getText());
			nameFolloweesToGrab = nameFolloweesToGrabField.getText();
			numFollowersToGrab = Integer.parseInt(numFollowersToGrabField.getText());
			
			GuiEvent ge = new GuiEvent(this,myAgent.COLLECT_DATA);
			ge.addParameter(nameFolloweesToGrab);
			ge.addParameter(numFollowersToGrab);
			setUserGenStatus(
					"COLLECTING",
					"Collecting follower data for the requested accounts.",
					StatusTone.RUNNING,
					nameFolloweesToGrab + "  |  " + numFollowersToGrab + " followers each");

			myAgent.postGuiEvent(ge);
		}
		else
		{
			setUserGenStatus(
					"READY",
					"Collection setup was cancelled.",
					StatusTone.READY,
					"No collection task running");
		}
	}
	
	public void selectNumArtificialTweets()
	{
		JTextField numArtificialTweetsField = new JTextField(10);
		numArtificialTweetsField.setText(Integer.toString(numArtificialTweets));
		JTextField randomSeedField = new JTextField(16);
		randomSeedField.setText(
				userGenerationSeed == null
						? ""
						: Long.toString(userGenerationSeed.longValue()));

		JPanel myPanel = new JPanel(new GridLayout(2, 2, 10, 8));
		myPanel.add(new JLabel("Artificial tweet rows:"));
		myPanel.add(numArtificialTweetsField);
		myPanel.add(new JLabel("Random seed (optional):"));
		myPanel.add(randomSeedField);

		int result = JOptionPane.showConfirmDialog(
				this,
				myPanel,
				"Configure Artificial Dataset",
				JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.PLAIN_MESSAGE);
		if (result == JOptionPane.OK_OPTION) {
			int requestedTweetCount;
			Long requestedSeed;
			try
			{
				String enteredValue = numArtificialTweetsField.getText().trim();
				requestedTweetCount = Integer.parseInt(enteredValue);
				if (requestedTweetCount < 0)
				{
					throw new NumberFormatException("Negative count");
				}
				String enteredSeed = randomSeedField.getText().trim();
				requestedSeed = enteredSeed.isEmpty()
						? null
						: Long.valueOf(Long.parseLong(enteredSeed));
			}
			catch (NumberFormatException e)
			{
				JOptionPane.showMessageDialog(
						this,
						"Enter a whole number from 0 to "
						+ String.format(Locale.US, "%,d", Integer.MAX_VALUE)
						+ " for the row count.\n\n"
						+ "The optional random seed must be a whole number from "
						+ Long.MIN_VALUE + " to " + Long.MAX_VALUE + ".\n\n"
						+ "0 rows means the same number of usable rows as the source corpus.",
						"Invalid Generation Settings",
						JOptionPane.ERROR_MESSAGE);
				setUserGenStatus(
						"WAITING",
						"The artificial dataset settings were not changed.",
						StatusTone.WARNING,
						"Check the row count and optional seed");
				return;
			}

			numArtificialTweets = requestedTweetCount;
			userGenerationSeed = requestedSeed;
			JOptionPane.showMessageDialog(
					this,
					(numArtificialTweets == 0
							? "The generated dataset will match the usable source row count."
							: String.format(
									Locale.US,
									"The generated dataset will contain exactly %,d rows.",
									numArtificialTweets))
							+ "\n"
							+ (userGenerationSeed == null
									? "A fresh random seed will be used."
									: "Reproducible random seed: "
									+ userGenerationSeed.longValue()),
					"Artificial Dataset Settings",
					JOptionPane.INFORMATION_MESSAGE);

			myAgent.setNumArtificialTweets(numArtificialTweets);
			myAgent.setUserGenerationSeed(userGenerationSeed);
			setUserGenStatus(
					"READY",
					"Generation settings configured. Start the user simulation when ready.",
					StatusTone.SUCCESS,
					(numArtificialTweets == 0
							? "Match the source corpus size"
							: String.format(
									Locale.US,
									"Exactly %,d artificial tweets",
									numArtificialTweets))
							+ (userGenerationSeed == null
									? "  |  Random seed"
									: "  |  Seed " + userGenerationSeed.longValue()));
		}
	}

	public void changeCardPanel(int panelNum)
	{
		if (panelNum == CHANGE_TO_PERFORMANCE)
		{
			selectApplicationTab(APPLICATION_TAB_SIMULATOR);
		}
		else if (panelNum == CHANGE_TO_USERSIM)
		{
			selectApplicationTab(APPLICATION_TAB_USER_STUDIO);
		}
		else if (cards != null)
		{
			CardLayout cl = (CardLayout)(cards.getLayout());
			cl.show(cards, PERFORMANCE_PANEL_ID);
		}
	}

	private void selectApplicationTab(int tabIndex)
	{
		if (applicationTabs != null
				&& tabIndex >= 0
				&& tabIndex < applicationTabs.getTabCount())
		{
			applicationTabs.setSelectedIndex(tabIndex);
		}
	}
	
	public void getUsers()
	{
		invalidateSimulatorInitializationState();
		if (!ensureHeapForUserDiscovery())
		{
			return;
		}
		//		String enteredData = enterDatasetField.getText();
		//		if (!enteredData.equals(""))
		//		{
		//			referenceUser = enteredData;
		//			System.out.println("referenceUser: "+referenceUser);
		//		}
		//		else
		//		{
		//			referenceUser = DEFAULT_DATASET;
		//		}
		
		
		initializeButton.setEnabled(false);
		startButton.setEnabled(false);
		getUsersButton.setEnabled(false);
		showAgentsList.clearSelection();
		setPerformanceStatus(
				"LOADING",
				"Reading " + sourceModeName().toLowerCase(Locale.US)
						+ " and discovering available users.",
				StatusTone.RUNNING);
		
			clearRecommendationResults();

		if (tweetLimitField.getText().equals(""))
			tweetLimit = 1000;
		else
			tweetLimit = Integer.parseInt(tweetLimitField.getText());

		if (beginDateField.getText().equals(""))
			beginDate = BEGIN_DATE;
		else
			beginDate = beginDateField.getText();

		if (endDateField.getText().equals(""))
			endDate = END_DATE;
		else
			endDate = endDateField.getText();

		if (numFollowees < 1)
			numFollowees = FOLLOWEES;

		if (numFollowers < 2)
			numFollowers = FOLLOWERS;

		if (numTweetsGenerated < 3)
			numTweetsGenerated = DEFAULT_TWEETS_GENERATED;

		GuiEvent ge = new GuiEvent(this,myAgent.GET_USERS);
		ge.addParameter(referenceUser);
		ge.addParameter(tweetLimit);
		ge.addParameter(beginDate);
		ge.addParameter(endDate);
		ge.addParameter(numFollowees);
		ge.addParameter(numFollowers);
		ge.addParameter(numTweetsGenerated);
		myAgent.postGuiEvent(ge);

	}

	private boolean ensureHeapForUserDiscovery()
	{
		if (selectedSourceMode != FROM_TEXT
				|| fileChooser == null
				|| fileChooser.getSelectedFile() == null
				|| !fileChooser.getSelectedFile().isFile())
		{
			return true;
		}

		File selectedDataset = fileChooser.getSelectedFile();
		int requiredHeapMb =
				RuntimeResourceSettings.recommendedHeapForTextDatasetMb(
						selectedDataset);
		int currentHeapMb = RuntimeResourceSettings.currentMaxHeapMb();
		if (currentHeapMb >= requiredHeapMb)
		{
			return true;
		}

		String message = "This dataset is too large to load safely with the current Java heap.\n\n"
				+ "Dataset: " + selectedDataset.getName() + "\n"
				+ "Current heap: "
				+ RuntimeResourceSettings.formatMegabytes(currentHeapMb)
				+ "\n"
				+ "Recommended minimum: "
				+ RuntimeResourceSettings.formatMegabytes(requiredHeapMb)
				+ "\n\nApply the recommended heap and restart now?";
		int choice = JOptionPane.showConfirmDialog(
				this,
				message,
				"Increase Java Heap Before Loading Users",
				JOptionPane.YES_NO_OPTION,
				JOptionPane.WARNING_MESSAGE);
		if (choice == JOptionPane.YES_OPTION)
		{
			maxHeapField.setText(Integer.toString(requiredHeapMb));
			restartWithHeap(
					requiredHeapMb,
					"Applying the recommended heap before loading users.");
		}
		else
		{
			setPerformanceStatus(
					"WAITING",
					"Increase Java heap before loading this dataset.",
					StatusTone.WARNING);
			getUsersButton.setEnabled(true);
		}
		return false;
	}

	private boolean ensureHeapForInitialization()
	{
		if (selectedSourceMode != FROM_TEXT
				|| fileChooser == null
				|| fileChooser.getSelectedFile() == null
				|| !fileChooser.getSelectedFile().isFile())
		{
			return true;
		}

		File selectedDataset = fileChooser.getSelectedFile();
		Object selectedAlgorithm = algorithmSelectionBox == null
				? null : algorithmSelectionBox.getSelectedItem();
		String algorithmName = selectedAlgorithm == null
				? "selected algorithm" : selectedAlgorithm.toString();
		int requiredHeapMb =
				RuntimeResourceSettings.recommendedHeapForTextDatasetAndAlgorithmMb(
						selectedDataset,
						algorithmName);
		int currentHeapMb = RuntimeResourceSettings.currentMaxHeapMb();
		if (currentHeapMb >= requiredHeapMb)
		{
			return true;
		}

		String message = "This run is too large to initialize safely with the current Java heap.\n\n"
				+ "Dataset: " + selectedDataset.getName() + "\n"
				+ "Algorithm: " + algorithmName + "\n"
				+ "Current heap: "
				+ RuntimeResourceSettings.formatMegabytes(currentHeapMb)
				+ "\n"
				+ "Recommended minimum: "
				+ RuntimeResourceSettings.formatMegabytes(requiredHeapMb)
				+ "\n\nThe run was stopped before creating recommender agents.\n"
				+ "Apply the recommended heap and restart now?";
		if (isObservatoryAutomationActive())
		{
			setPerformanceStatus(
					"WAITING",
					"Increase Java heap before initializing this matrix run.",
					StatusTone.WARNING);
			notifyObservatoryAutomationFailure(
					"initialize",
					"This run needs "
							+ RuntimeResourceSettings.formatMegabytes(requiredHeapMb)
							+ " but the current heap is "
							+ RuntimeResourceSettings.formatMegabytes(currentHeapMb)
							+ ".");
			initializeButton.setEnabled(true);
			return false;
		}
		int choice = JOptionPane.showConfirmDialog(
				this,
				message,
				"Increase Java Heap Before Initializing",
				JOptionPane.YES_NO_OPTION,
				JOptionPane.WARNING_MESSAGE);
		if (choice == JOptionPane.YES_OPTION)
		{
			maxHeapField.setText(Integer.toString(requiredHeapMb));
			restartWithHeap(
					requiredHeapMb,
					"Applying the recommended heap before initializing.");
		}
		else
		{
			setPerformanceStatus(
					"WAITING",
					"Increase Java heap before initializing this run.",
					StatusTone.WARNING);
			getUsersButton.setEnabled(true);
			initializeButton.setEnabled(true);
		}
		return false;
	}

	private void requestExperimentReset()
	{
		int choice = JOptionPane.showConfirmDialog(
				this,
				"Reset the current experiment?\n\n"
						+ "Running experiment agents will stop and current on-screen results will be cleared.\n"
						+ "Datasets, model caches, and archived results will be preserved.",
				"Reset Experiment",
				JOptionPane.YES_NO_OPTION,
				JOptionPane.WARNING_MESSAGE);
		if (choice != JOptionPane.YES_OPTION)
		{
			return;
		}

		resetExperimentButton.setEnabled(false);
		getUsersButton.setEnabled(false);
		initializeButton.setEnabled(false);
		startButton.setEnabled(false);
		showAgentsList.setEnabled(false);
		setPerformanceStatus(
				"RESETTING",
				"Stopping experiment agents and clearing current experiment outputs.",
				StatusTone.RUNNING);

		GuiEvent ge = new GuiEvent(this, myAgent.RESET_EXPERIMENT);
		myAgent.postGuiEvent(ge);
	}

	public void resetForNewExperiment()
	{
		simulatorInitialized = false;
		simulatorInitializationInProgress = false;
		simulationNo = 0;
		sepNum = 0;
		listSize = 0;
		simulationIteration = 0;
		indexToRecommend = 0;
		indexToRecommend2 = 0;
		tweetLimit = 0;
		numNodes = 1;
		RecommendationMemoryCoordinator.configureRequestedParallelism(1);
		NodeCapacityRegistry.resetToDefaults(1, currentHeapSetting());
		updateNodeCapacitySummary();
		NodeExecutionMonitor.reset();
		kRecommend = DEFAULT_K_REC;
		hashTags = HASH_TAGS;
		retweets = RE_TWEETS;
		stopWords = STOP_WORDS;
		algorithmRec = K_MEANS;
		numFollowees = FOLLOWEES;
		numFollowers = FOLLOWERS;
		numTweetsGenerated = DEFAULT_TWEETS_GENERATED;
		numReducers = DEFAULT_REDUCERS;
		numMappers = DEFAULT_MAPPERS;
		reducerChoice = ALGORITHM_CLUSTERING;
		mapperChoice = FEATURE_EXTRACTION;
			numArtificialTweets = DEFAULT_NUM_ARTIFICIAL_TWEETS;
			userGenerationSeed = null;
			algorithmParameterSettings = AlgorithmParameterSettings.defaults();
			numFollowersToGrab = 0;
		referenceUser = DEFAULT_DATASET;
		beginDate = BEGIN_DATE;
		endDate = END_DATE;
		recommendeeName = null;
		recommendeeName2 = null;
		nameFolloweesToGrab = null;
		indexArray = null;
		a = null;
		b = null;
		c = null;
		names = null;

		countRecServersTP = 0;
		countRecServersTfidf = 0;
		countRecServersAlgorithm = 0;
		currentMaxTPTime = 0;
		currentMaxTfidfTime = 0;
		currentMaxAlgorithmTime = 0;
		currentMessagePassingTime = 0;
		currentMessagePassingCost = 0;
		currentTiming = null;
		timings.clear();
		usersRec.clear();

		enterDatasetField.setText(DEFAULT_DATASET);
		numNodesField.setText("1");
		tweetLimitField.setText("0");
		beginDateField.setText(BEGIN_DATE);
		endDateField.setText(END_DATE);
		recommendationField.setText(Integer.toString(DEFAULT_K_REC));
		svmBatchUsersField.setText("1");
		numReducersField.setText(Integer.toString(DEFAULT_REDUCERS));
		numMappersField.setText(Integer.toString(DEFAULT_MAPPERS));
				algorithmSelectionBox.setSelectedIndex(K_MEANS);
				algorithmSelectionBox.setEnabled(true);
				algorithmSettingsButton.setEnabled(true);
				recommendationTargetModeBox.setSelectedItem(RecommendationTargetMode.ALGORITHM_DEFAULT);
			recommendationTargetModeBox.setEnabled(true);
			simulationSelectionBox.setSelectedIndex(zero);
			mapperSelectionBox.setSelectedIndex(FEATURE_EXTRACTION);
			reducerSelectionBox.setSelectedIndex(ALGORITHM_CLUSTERING);
				updateBatchUsersFieldState();
				updateAlgorithmSettingsSummary();
				removeHashTags.setSelected(true);
		removeRetweets.setSelected(true);
			removeStopWords.setSelected(true);
			simulateTweetDelay.setSelected(true);
			generateGraphOutput.setSelected(true);
			generateGraphOutput.setEnabled(true);
			fileChooser.setSelectedFile(null);
			simulatorDoc2VecCacheInfo = null;
			clearSimulatorDoc2VecCacheStats();
			applySimulatorDoc2VecCacheMessage(
					"CACHE UNKNOWN",
					"Choose a text dataset to inspect whether Doc2Vec will train or reuse cached vectors.",
					APP_MUTED_TEXT);
			setSimulatorDoc2VecCacheButtons(true, false);

		resultArea.setText("");
		resetRunAccuracyPanel();
		previousResultArea.setText("");
			clearRecommendationResults();
		clusterResultArea.setText("");
		userGenTweetsResultArea.setText("");
		grabTwitterResultArea.setText("");
		agentsList.clear();
		showAgentsList.clearSelection();
		showAgentsList.setEnabled(true);
		agentsListTitle.setTitle("List of Users");
		recommendationTitle.setTitle("Recommendations for User");
		updateSourceSelection(
				FROM_TEXT,
				"Text file selected",
				"Choose a dataset file");
		setPerformanceStatus(
				"READY",
				"New experiment ready. Choose a data source to begin.",
				StatusTone.READY);
		setUserGenStatus(
				"READY",
				"User Generation Studio is ready.",
				StatusTone.READY,
				"No collection or generation task running");

		getUsersButton.setEnabled(true);
		initializeButton.setEnabled(false);
		startButton.setEnabled(false);
		resetExperimentButton.setEnabled(true);
		quitButton.setEnabled(true);
		enableUserGenSimButtons();
		changeCardPanel(CHANGE_TO_PERFORMANCE);
		repaint();

		JOptionPane.showMessageDialog(
				this,
				"Experiment reset. Select a dataset and press Get Users to begin.",
				"New Experiment Ready",
				JOptionPane.INFORMATION_MESSAGE);
	}

	public void initializeAgents()
	{
		if (!validateAlgorithmSettingsForInitialize())
		{
			return;
		}
		if (!ensureHeapForInitialization())
		{
			return;
		}
		getUsersButton.setEnabled(false);
		startButton.setEnabled(false);
		initializeButton.setEnabled(false);
		simulatorInitialized = false;
		simulatorInitializationInProgress = true;
		setPerformanceStatus(
				"INITIALIZING",
				"Creating agents and preparing the selected recommendation model.",
				StatusTone.RUNNING);
		setSimulatorDoc2VecCacheButtons(false, false);
		
			clearRecommendationResults();
		if (numNodesField.getText().equals("") || Integer.parseInt(numNodesField.getText()) < 1)
			numNodes = 1;
		else
			numNodes = Integer.parseInt(numNodesField.getText());
		RecommendationMemoryCoordinator.configureRequestedParallelism(numNodes);
		NodeCapacityRegistry.configureForNodeCount(
				numNodes,
				currentHeapSetting());
		updateNodeCapacitySummary();
		Object selectedAlgorithm = algorithmSelectionBox.getSelectedItem();
		NodeExecutionMonitor.beginExperiment(
				numNodes,
				selectedAlgorithm == null
						? "Recommendation model"
						: selectedAlgorithm.toString());

		if (tweetLimitField.getText().equals(""))
			tweetLimit = 1000;
		else
			tweetLimit = Integer.parseInt(tweetLimitField.getText());

		if (beginDateField.getText().equals(""))
			beginDate = BEGIN_DATE;
		else
			beginDate = beginDateField.getText();

		if (endDateField.getText().equals(""))
			endDate = END_DATE;
		else
			endDate = endDateField.getText();

		if (recommendationField.getText().equals("") || Integer.parseInt(recommendationField.getText()) < DEFAULT_K_REC)
			kRecommend = DEFAULT_K_REC;
		else
			kRecommend = Integer.parseInt(recommendationField.getText());

		String enteredData = enterDatasetField.getText();
		if (!enteredData.equals(""))
		{
			referenceUser = enteredData;
			System.out.println("referenceUser: "+referenceUser);
		}

		if (numFollowees < 1)
			numFollowees = FOLLOWEES;

		if (numFollowers < 2)
			numFollowers = FOLLOWERS;

		if (numTweetsGenerated < 3)
			numTweetsGenerated = DEFAULT_TWEETS_GENERATED;

		GuiEvent ge = new GuiEvent(this,myAgent.INITIALIZE);
		ge.addParameter(numNodes);
		ge.addParameter(tweetLimit);
		ge.addParameter(beginDate);
		ge.addParameter(endDate);
		ge.addParameter(kRecommend);
		ge.addParameter(referenceUser);
		ge.addParameter(hashTags);
		ge.addParameter(retweets);
		ge.addParameter(stopWords);
		ge.addParameter(algorithmRec);
		ge.addParameter(numFollowees);
		ge.addParameter(numFollowers);
			ge.addParameter(numTweetsGenerated);
			ge.addParameter(getRecommendationTargetMode());
			ge.addParameter(isGraphGenerationEnabled());
				algorithmSelectionBox.setEnabled(false);
				algorithmSettingsButton.setEnabled(false);
				setBatchUsersControlEnabled(false);
			recommendationTargetModeBox.setEnabled(false);
			generateGraphOutput.setEnabled(false);
			myAgent.postGuiEvent(ge);

	}

	public void startSimulation()
	{
		if (!simulatorInitialized || simulatorInitializationInProgress)
		{
			startButton.setEnabled(false);
			setPerformanceStatus(
					"INITIALIZE FIRST",
					"Wait until initialization is complete before running the simulation.",
					StatusTone.WARNING);
			notifyObservatoryAutomationFailure(
					"start",
					"Simulation start was requested before initialization finished.");
			return;
		}
		startButton.setEnabled(false);
		initializeButton.setEnabled(false);
		getUsersButton.setEnabled(false);
		setPerformanceStatus(
				"RUNNING",
				"Processing tweets and generating ranked recommendations.",
				StatusTone.RUNNING);
		setSimulatorDoc2VecCacheButtons(false, false);

		simulationIteration++;
		if (simulationIteration > 1)
		{
			previousResultArea.append(resultArea.getText());
			resultArea.setText("");
		}

		resultArea.append("====================== Simulation Iteration "+ simulationIteration+" =======================\n");
		resetRunAccuracyPanel();
		resetCurrentTiming();

		GuiEvent ge = new GuiEvent(this,myAgent.START_SIM);
		ge.addParameter(numNodes);
		ge.addParameter(tweetLimit);
		ge.addParameter(beginDate);
		ge.addParameter(endDate);
		ge.addParameter(kRecommend);
		ge.addParameter(referenceUser);
		ge.addParameter(hashTags);
		ge.addParameter(retweets);
			ge.addParameter(stopWords);
				ge.addParameter(algorithmRec);
				ge.addParameter(isGraphGenerationEnabled());
				generateGraphOutput.setEnabled(false);
				myAgent.postGuiEvent(ge);
			
			
		//simulationSelectionListener sSep = new simulationSelectionListener();
		//ActionEvent event = new ActionEvent(this, );
		//sSep.actionPerformed(event);
		//simulationSelectionBox.setSelectedIndex(1);
		//simulationButton.doClick();
		
	}
	
	public void startUserGenSimulation()
	{
		GuiEvent ge = new GuiEvent(this,myAgent.START_USER_GEN_SIM);
		myAgent.postGuiEvent(ge);
	}

	public ArrayList<String> getUsersRec()
	{
		String userToRecommend;
		//S String userToRecommend2;  // add the second user by Sepide
		// S ArrayList<String> usersToRec = new ArrayList<String>();
		System.out.println("indexToRecommend: "+indexToRecommend);
		//S System.out.println("indexToRecommend: "+indexToRecommend2);  // added by Sepide
		// code added by Sepide to allow selecting two users from the GUI
		/*S agentsList.add(indexToRecommend,usersToRec);
		agentsList.add(indexToRecommend2,usersToRec); */  // added by Sepide 
		ArrayList<String> usersRec = new ArrayList<String>();
		int batchCount = getRecommendationBatchUserCount();
		int startIndex = indexToRecommend;
		if (startIndex < 0)
		{
			startIndex = 0;
		}
		for (int i = startIndex; i < agentsList.size() && usersRec.size() < batchCount; i++)
		{
			userToRecommend = agentsList.getElementAt(i);
			//S userToRecommend2 = userToRecommend2.split("-",2)[0]; // added by Sepide
			userToRecommend = userToRecommend.split("-",2)[0];
			usersRec.add(userToRecommend);
		}
		//S usersRec.add(userToRecommend2);  // added by Sepide
		System.out.println("controllerGUI usersRec: "+usersRec);
		return usersRec;

	}

	public int getSvmBatchUserCount()
	{
		if (algorithmSelectionBox == null
				|| !RecommendationModeSupport.isSvmFamilyAlgorithm(
						algorithmSelectionBox.getSelectedIndex()))
		{
			return 1;
		}
		return parseBatchUserCount();
	}

	public int getRecommendationBatchUserCount()
	{
		if (algorithmSelectionBox == null)
		{
			return 1;
		}
		int selectedAlgorithm = algorithmSelectionBox.getSelectedIndex();
		if (!RecommendationModeSupport.isSvmFamilyAlgorithm(selectedAlgorithm)
				&& selectedAlgorithm != Doc2Vec)
		{
			return 1;
		}
		return parseBatchUserCount();
	}

	public int getCandidateUserCountEstimate()
	{
		int batchCount = getRecommendationBatchUserCount();
		if (agentsList == null)
		{
			return Math.max(1, batchCount);
		}
		return Math.max(1, Math.max(batchCount, agentsList.size()));
	}

	public RecommendationTargetMode getRecommendationTargetMode()
	{
		Object selection = recommendationTargetModeBox == null
				? null : recommendationTargetModeBox.getSelectedItem();
		return selection instanceof RecommendationTargetMode
				? (RecommendationTargetMode) selection
				: RecommendationTargetMode.ALGORITHM_DEFAULT;
	}

	public AlgorithmParameterSettings getAlgorithmParameterSettings()
	{
		return algorithmParameterSettings == null
				? AlgorithmParameterSettings.defaults()
				: algorithmParameterSettings.copy();
	}

	public void setAlgorithmParameterSettings(
			AlgorithmParameterSettings settings)
	{
		invalidateSimulatorInitializationState();
		algorithmParameterSettings = settings == null
				? AlgorithmParameterSettings.defaults()
				: settings.copy();
		updateAlgorithmSettingsSummary();
		refreshSimulatorDoc2VecCacheStatus();
		if (populationSweepPanel != null)
		{
			populationSweepPanel.refreshAlgorithmSettingsSummary();
		}
		refreshSimulatorHelpPanel();
	}

	SimulatorHelpPanel.Snapshot buildSimulatorHelpSnapshot()
	{
		int selectedAlgorithm = algorithmSelectionBox == null
				? algorithmRec : algorithmSelectionBox.getSelectedIndex();
		RecommendationTargetMode targetMode = getRecommendationTargetMode();
		RecommendationCandidateType nativeType =
				RecommendationModeSupport.nativeCandidateType(selectedAlgorithm);
		RecommendationCandidateType requestedType =
				RecommendationModeSupport.resolveCandidateType(
						targetMode,
						selectedAlgorithm);
		boolean convertedPipeline =
				RecommendationModeSupport.requiresConvertedPipeline(
						targetMode,
						selectedAlgorithm);
		boolean batchEnabled =
				RecommendationModeSupport.isSvmFamilyAlgorithm(selectedAlgorithm)
				|| selectedAlgorithm == Doc2Vec;
		AlgorithmParameterSettings settings = getAlgorithmParameterSettings();
		return new SimulatorHelpPanel.Snapshot(
				selectedAlgorithm,
				helpParseInt(numNodesField, numNodes > 0 ? numNodes : 1),
				helpParseInt(recommendationField,
						kRecommend > 0 ? kRecommend : DEFAULT_K_REC),
				getRecommendationBatchUserCount(),
				helpParseInt(tweetLimitField, tweetLimit),
				helpParseInt(maxHeapField,
						RuntimeResourceSettings.currentMaxHeapMb()),
				getSelectedSimulationNumber(),
				getSelectedDatasetSourceLabel(),
				getSelectedDatasetName(),
				getSelectedDatasetPath(),
				RecommendationModeSupport.algorithmName(selectedAlgorithm),
				targetMode,
				targetMode.toString(),
				requestedType,
				nativeType,
				convertedPipeline,
				RecommendationModeSupport.isWorkingAlgorithm(selectedAlgorithm),
				batchEnabled,
				removeHashTags != null && removeHashTags.isSelected(),
				removeRetweets != null && removeRetweets.isSelected(),
				removeStopWords != null && removeStopWords.isSelected(),
				simulateTweetDelay != null && simulateTweetDelay.isSelected(),
				generateGraphOutput == null || generateGraphOutput.isSelected(),
				settings.profileLabel(),
				settings.summaryForAlgorithm(selectedAlgorithm),
				NodeCapacityRegistry.summary(),
				getSelectedTargetsSummary());
	}

	private void refreshSimulatorHelpPanel()
	{
		if (simulatorHelpPanel != null)
		{
			simulatorHelpPanel.refreshFromSimulator();
		}
		if (experimentObservatoryPanel != null)
		{
			experimentObservatoryPanel.refreshFromSimulator();
		}
		refreshGraphInsightsPanel();
	}

	private void refreshGraphInsightsPanel()
	{
		if (graphInsightsPanel != null)
		{
			graphInsightsPanel.refreshFromSimulator();
		}
	}

	private int helpParseInt(JTextField field, int fallback)
	{
		if (field == null)
		{
			return fallback;
		}
		try
		{
			String value = field.getText();
			return value == null || value.trim().length() == 0
					? fallback : Integer.parseInt(value.trim());
		}
		catch (NumberFormatException exception)
		{
			return fallback;
		}
	}

	private int getSelectedSimulationNumber()
	{
		if (simulationSelectionBox == null
				|| simulationSelectionBox.getSelectedItem() == null)
		{
			return simulationNo;
		}
		try
		{
			return Integer.parseInt(
					simulationSelectionBox.getSelectedItem().toString().trim());
		}
		catch (NumberFormatException exception)
		{
			return simulationNo;
		}
	}

	private String getSelectedDatasetSourceLabel()
	{
		switch (selectedSourceMode)
		{
			case FROM_DB:
				return "Database";
			case FROM_GENERATION:
				return "Generated dataset";
			case FROM_ARTIFICIAL:
				return "Artificial dataset";
			case FROM_TEXT:
			default:
				return "Text dataset";
		}
	}

	private String getSelectedDatasetName()
	{
		File selectedFile = fileChooser == null ? null : fileChooser.getSelectedFile();
		if (selectedFile != null)
		{
			return selectedFile.getName();
		}
		String dataset = enterDatasetField == null ? "" : enterDatasetField.getText();
		if (dataset == null || dataset.trim().length() == 0)
		{
			return "No dataset selected";
		}
		return dataset.trim();
	}

	private String getSelectedDatasetPath()
	{
		File selectedFile = fileChooser == null ? null : fileChooser.getSelectedFile();
		return selectedFile == null ? "" : selectedFile.getAbsolutePath();
	}

	private String getSelectedTargetsSummary()
	{
		if (showAgentsList == null || showAgentsList.getSelectedIndex() < 0)
		{
			return "NO TARGET SELECTED";
		}
		int selectedCount = showAgentsList.getSelectedIndices().length;
		String firstTarget = String.valueOf(showAgentsList.getSelectedValue());
		if (firstTarget.indexOf("-") >= 0)
		{
			firstTarget = firstTarget.split("-", 2)[0];
		}
		if (selectedCount <= 1)
		{
			return "TARGET: " + firstTarget;
		}
		return selectedCount + " TARGETS FROM " + firstTarget;
	}

	private void openAlgorithmSettingsDialog()
	{
		int selectedAlgorithm = algorithmSelectionBox == null
				? K_MEANS : algorithmSelectionBox.getSelectedIndex();
		AlgorithmParameterSettings updated = AlgorithmSettingsDialog.showDialog(
				this,
				selectedAlgorithm,
				getAlgorithmParameterSettings());
		if (updated == null)
		{
			return;
		}
		setAlgorithmParameterSettings(updated);
		setPerformanceStatus(
				algorithmParameterSettings.isCustomEnabled()
						? "CUSTOM MODEL" : "DEFAULT MODEL",
				algorithmParameterSettings.summaryForAlgorithm(selectedAlgorithm),
				algorithmParameterSettings.isCustomEnabled()
						? StatusTone.WARNING : StatusTone.READY);
	}

	private void updateAlgorithmSettingsSummary()
	{
		if (algorithmSettingsProfileLabel == null
				|| algorithmSettingsSummaryLabel == null)
		{
			return;
		}
		AlgorithmParameterSettings settings = getAlgorithmParameterSettings();
		int selectedAlgorithm = algorithmSelectionBox == null
				? K_MEANS : algorithmSelectionBox.getSelectedIndex();
		algorithmSettingsProfileLabel.setText(settings.profileLabel());
		algorithmSettingsProfileLabel.setForeground(settings.isCustomEnabled()
				? new Color(110, 71, 0) : new Color(0, 93, 57));
		algorithmSettingsProfileLabel.setBackground(settings.isCustomEnabled()
				? StudioNightTheme.adaptiveColor(
						new Color(255, 241, 210), StudioNightTheme.NIGHT_PANEL_INK)
				: StudioNightTheme.adaptiveColor(
						new Color(224, 246, 235), StudioNightTheme.NIGHT_PANEL_INK));
		algorithmSettingsSummaryLabel.setText(
				settings.summaryForAlgorithm(selectedAlgorithm));
	}

	private boolean validateAlgorithmSettingsForInitialize()
	{
		AlgorithmParameterSettings settings = getAlgorithmParameterSettings();
		int selectedAlgorithm = algorithmSelectionBox == null
				? K_MEANS : algorithmSelectionBox.getSelectedIndex();
		java.util.List<String> errors =
				settings.validateForAlgorithm(selectedAlgorithm);
		if (errors.isEmpty())
		{
			return true;
		}
		StringBuilder plain = new StringBuilder(
				"Invalid algorithm parameters before initialization: ");
		for (int i = 0; i < errors.size(); i++)
		{
			if (i > 0)
			{
				plain.append("; ");
			}
			plain.append(errors.get(i));
		}
		if (isObservatoryAutomationActive())
		{
			notifyObservatoryAutomationFailure("initialize", plain.toString());
			return false;
		}
		StringBuilder builder = new StringBuilder(
				"<html><b>Fix these algorithm parameters before initializing:</b><br>");
		for (String error : errors)
		{
			builder.append("&bull; ").append(error).append("<br>");
		}
		builder.append("</html>");
		JOptionPane.showMessageDialog(
				this,
				builder.toString(),
				"Invalid Algorithm Parameters",
				JOptionPane.WARNING_MESSAGE);
		return false;
	}

	private int parseBatchUserCount()
	{
		if (svmBatchUsersField == null)
		{
			return 1;
		}
		try
		{
			int batchCount = Integer.parseInt(svmBatchUsersField.getText().trim());
			if (batchCount < 1)
			{
				return 1;
			}
			return batchCount;
		}
		catch (NumberFormatException e)
		{
			return 1;
		}
	}

	public boolean isSimulateTweetDelayEnabled()
	{
		return simulateTweetDelay == null || simulateTweetDelay.isSelected();
	}

	public boolean isGraphGenerationEnabled()
	{
		return generateGraphOutput == null || generateGraphOutput.isSelected();
	}

	public void shutDown()
	{
		if (populationSweepPanel != null)
		{
			populationSweepPanel.cancelCurrentRun();
		}
		System.exit(0);
		GuiEvent ge = new GuiEvent(this, myAgent.QUIT);
		myAgent.postGuiEvent(ge);
	}

	public void appendResult(String resultText)
	{
		resultArea.append(resultText+"\n");
		captureRunAccuracyLine(resultText);
		updateStatusFromLog(resultText);
		if (isGraphRelevantLog(resultText))
		{
			refreshGraphInsightsPanel();
		}
	}

	private void resetRunAccuracyPanel()
	{
		currentRunAccuracyAlgorithm = selectedRunAccuracyAlgorithm();
		currentRunAccuracyLineCount = 0;
		String algorithmName =
				RecommendationModeSupport.algorithmName(
						currentRunAccuracyAlgorithm);
		if (runAccuracyArea != null)
		{
			runAccuracyArea.setText(runAccuracyInitialText(
					currentRunAccuracyAlgorithm, algorithmName));
		}
		if (runAccuracySummaryLabel != null)
		{
			runAccuracySummaryLabel.setText(runAccuracyInitialSummary(
					currentRunAccuracyAlgorithm, algorithmName));
		}
		if (runAccuracyMeasurementLabel != null)
		{
			runAccuracyMeasurementLabel.setText(
					runAccuracyMeasurementText());
		}
		updateRunAccuracyPanelVisibility();
	}

	private void updateRunAccuracyPanelVisibility()
	{
		if (runAccuracyPanel == null)
		{
			return;
		}
		runAccuracyPanel.setVisible(true);
		Container parent = runAccuracyPanel.getParent();
		if (parent != null)
		{
			parent.revalidate();
			parent.repaint();
		}
	}

	private int selectedRunAccuracyAlgorithm()
	{
		int selectedAlgorithm = algorithmRec;
		if (algorithmSelectionBox == null)
		{
			return selectedAlgorithm;
		}
		int comboSelection = algorithmSelectionBox.getSelectedIndex();
		if (comboSelection >= 0)
		{
			selectedAlgorithm = comboSelection;
		}
		return selectedAlgorithm;
	}

	private boolean algorithmReportsRunAccuracy(int algorithm)
	{
		return RecommendationModeSupport.isSvmFamilyAlgorithm(algorithm)
				|| algorithm == MLP;
	}

	private String runAccuracyInitialText(int algorithm, String algorithmName)
	{
		if (algorithmReportsRunAccuracy(algorithm))
		{
			return "No " + algorithmName
					+ " accuracy line has been logged for this run yet.";
		}
		return algorithmName
				+ " does not compute a classifier test-accuracy line in this run.";
	}

	private String runAccuracyInitialSummary(int algorithm, String algorithmName)
	{
		if (algorithmReportsRunAccuracy(algorithm))
		{
			return "Waiting for " + algorithmName
					+ " accuracy in this run";
		}
		return "No test accuracy metric for " + algorithmName;
	}

	private String runAccuracyMeasurementText()
	{
		String algorithmName =
				RecommendationModeSupport.algorithmName(
						currentRunAccuracyAlgorithm);
		if (RecommendationModeSupport.isSvmFamilyAlgorithm(
				currentRunAccuracyAlgorithm))
		{
			return "<html>Mirrors the exact " + algorithmName
					+ " accuracy log line. Exact SVM uses Weka Evaluation.pctCorrect(), "
					+ "or correct/test*100 for more than 200 classes. Scalable Linear SVM uses correct/test*100 on the test ARFF.</html>";
		}
		if (currentRunAccuracyAlgorithm == MLP)
		{
			return "<html>Mirrors the exact MLP accuracy log line. Legacy Neuroph MLP and Sparse Federated MLP both measure argmax(predicted output) against the test label, then report correct/test*100 for this run.</html>";
		}
		return "<html>" + algorithmName
				+ " currently has no classifier test-accuracy calculation in the code path. The panel is cleared at each run start, so old accuracy lines are not reused.</html>";
	}

	private void captureRunAccuracyLine(String resultText)
	{
		if (!algorithmReportsRunAccuracy(currentRunAccuracyAlgorithm))
		{
			return;
		}
		if (!isRunAccuracyLineForAlgorithm(
				resultText, currentRunAccuracyAlgorithm))
		{
			return;
		}
		currentRunAccuracyLineCount++;
		if (runAccuracyArea != null)
		{
			if (currentRunAccuracyLineCount == 1)
			{
				runAccuracyArea.setText("");
			}
			runAccuracyArea.append(resultText.trim()+"\n");
			runAccuracyArea.setCaretPosition(
					runAccuracyArea.getDocument().getLength());
		}
		if (runAccuracySummaryLabel != null)
		{
			runAccuracySummaryLabel.setText(
					currentRunAccuracyLineCount == 1
							? "1 exact accuracy line captured from the run log"
							: currentRunAccuracyLineCount
									+ " exact accuracy lines captured from the run log");
		}
	}

	private boolean isRunAccuracyLineForAlgorithm(
			String resultText,
			int algorithm)
	{
		if (resultText == null)
		{
			return false;
		}
		String trimmed = resultText.trim();
		if (trimmed.length() == 0)
		{
			return false;
		}
		if (trimmed.indexOf("%") < 0)
		{
			return false;
		}
		String lower = trimmed.toLowerCase(Locale.US);
		if (RecommendationModeSupport.isSvmFamilyAlgorithm(algorithm))
		{
			return (trimmed.indexOf("Accuracy of ") >= 0
					&& lower.indexOf("svm") >= 0)
					|| (lower.indexOf("test accuracy ") >= 0
							&& lower.indexOf("svm") >= 0);
		}
		if (algorithm == MLP)
		{
			return lower.indexOf("legacy neuroph mlp accuracy") >= 0
					|| lower.indexOf("sparse federated mlp accuracy") >= 0;
		}
		return false;
	}

	private boolean isGraphRelevantLog(String resultText)
	{
		if (resultText == null)
		{
			return false;
		}
		String lower = resultText.toLowerCase(Locale.US);
		return lower.indexOf("graph") >= 0
				|| lower.indexOf("persist recommendation choice") >= 0
				|| lower.indexOf("simulation completed") >= 0
				|| lower.indexOf("recommendations are complete") >= 0;
	}

	public void appendPreviousResult(String resultText)
	{
		previousResultArea.append(resultText+"\n");
	}

	public void appendRecommendation(String recommendationText)
	{
		final String textToAppend = recommendationText == null ? "" : recommendationText;
		final ArrayList<RecommendationResult> parsedResults = parseRecommendationResults(textToAppend);
		Runnable update = new Runnable() {
			public void run()
			{
				recommendationArea.append(textToAppend);
				for (RecommendationResult result : parsedResults)
				{
					addRecommendationResult(result);
				}
			}
		};
		if (SwingUtilities.isEventDispatchThread())
		{
			update.run();
		}
		else
		{
			SwingUtilities.invokeLater(update);
		}
	}

	public void appendRecommendationResult(
			final RecommendationResultPayload payload,
			final int recommendationLimit)
	{
		if (payload == null)
		{
			return;
		}
		Runnable update = new Runnable() {
			public void run()
			{
				addRecommendationResult(new RecommendationResult(
						payload, Math.max(1, recommendationLimit)));
			}
		};
		if (SwingUtilities.isEventDispatchThread())
		{
			update.run();
		}
		else
		{
			SwingUtilities.invokeLater(update);
		}
	}
	
	public void appendTwitterResult(String resultText)
	{
		grabTwitterResultArea.append(resultText+"\n");
	}
	
	public void appendUserGenTweetsResult(String resultText)
	{
		final String line = resultText == null ? "" : resultText;
		Runnable update = new Runnable() {
			public void run()
			{
				if (userGenTweetsResultArea == null)
				{
					return;
				}
				userGenTweetsResultArea.append(line + "\n");
				int excess = userGenTweetsResultArea.getDocument().getLength()
						- MAX_USER_GENERATION_LOG_CHARACTERS;
				if (excess > 0)
				{
					try
					{
						userGenTweetsResultArea.getDocument().remove(0, excess);
					}
					catch (javax.swing.text.BadLocationException ignored)
					{
						// The document changed between measuring and trimming.
					}
				}
				userGenTweetsResultArea.setCaretPosition(
						userGenTweetsResultArea.getDocument().getLength());
			}
		};
		if (SwingUtilities.isEventDispatchThread())
		{
			update.run();
		}
		else
		{
			SwingUtilities.invokeLater(update);
		}
	}

	public void appendClustersResult(String resultText)
	{
		final String line = resultText == null ? "" : resultText;
		Runnable update = new Runnable() {
			public void run()
			{
				if (clusterResultArea != null)
				{
					clusterResultArea.append(line + "\n");
				}
			}
		};
		if (SwingUtilities.isEventDispatchThread())
		{
			update.run();
		}
		else
		{
			SwingUtilities.invokeLater(update);
		}
	}

	public void updateUserGenerationProgress(
			final String stage,
			final String detail,
			final long completed,
			final long total)
	{
		String context = "Scalable generation pipeline";
		if (total > 0L)
		{
			long percent = Math.min(100L, completed * 100L / total);
			context = String.format(
					Locale.US,
					"%,d / %,d rows  |  %d%%",
					completed,
					total,
					percent);
		}
		setUserGenStatus(stage, detail, StatusTone.RUNNING, context);
		appendUserGenTweetsResult("[" + stage + "] " + detail);
	}

	public void showUserGenerationClusters(
			final java.util.List<UserGenerationEngine.ClusterResult> clusters)
	{
		Runnable update = new Runnable() {
			public void run()
			{
				if (clusterResultArea == null)
				{
					return;
				}
				clusterResultArea.setText("");
				clusterResultArea.append("HOMOPHILY VALIDATION CLUSTERS\n");
				clusterResultArea.append(
						"============================================================\n");
				if (clusters == null || clusters.isEmpty())
				{
					clusterResultArea.append("No generated users were available.\n");
					return;
				}
				for (UserGenerationEngine.ClusterResult cluster : clusters)
				{
					clusterResultArea.append(
							"Cluster " + (cluster.getClusterId() + 1)
							+ "  |  " + cluster.getUserNames().size()
							+ " users\n");
					clusterResultArea.append(
							String.join(", ", cluster.getUserNames()) + "\n\n");
				}
				clusterResultArea.setCaretPosition(0);
			}
		};
		if (SwingUtilities.isEventDispatchThread())
		{
			update.run();
		}
		else
		{
			SwingUtilities.invokeLater(update);
		}
	}

	public void completeUserGeneration(
			final UserGenerationEngine.Result result)
	{
		Runnable update = new Runnable() {
			public void run()
			{
				enableUserGenSimButtons();
				setUserGenStatus(
						"COMPLETE",
						String.format(
								Locale.US,
								"Generated and verified %,d artificial tweets.",
								result.getGeneratedRows()),
						StatusTone.SUCCESS,
						result.getOutputFile().getName()
						+ "  |  "
						+ formatDuration(result.getElapsedMillis()));
				appendUserGenTweetsResult(
						"[COMPLETE] Output: "
						+ result.getOutputFile().getAbsolutePath());
				appendUserGenTweetsResult(
						String.format(
								Locale.US,
								"[COMPLETE] %,d source rows, %,d usable rows, "
								+ "%,d generated rows, %,d users, %,d generated terms, seed %d.",
								result.getSourceRows(),
								result.getProcessedRows(),
								result.getGeneratedRows(),
								result.getGeneratedUsers(),
								result.getGeneratedVocabularySize(),
								result.getRandomSeed()));
				for (String warning : result.getWarnings())
				{
					appendUserGenTweetsResult("[WARNING] " + warning);
				}
				JOptionPane.showMessageDialog(
						ControllerAgentGui.this,
						String.format(
								Locale.US,
								"Generated and verified %,d rows.\n"
								+ "Random seed: %d\n\nSaved to:\n%s",
								result.getGeneratedRows(),
								result.getRandomSeed(),
								result.getOutputFile().getAbsolutePath()),
						"Artificial Dataset Ready",
						JOptionPane.INFORMATION_MESSAGE);
			}
		};
		if (SwingUtilities.isEventDispatchThread())
		{
			update.run();
		}
		else
		{
			SwingUtilities.invokeLater(update);
		}
	}

	public void failUserGeneration(final String message)
	{
		Runnable update = new Runnable() {
			public void run()
			{
				enableUserGenSimButtons();
				setUserGenStatus(
						"FAILED",
						"Artificial dataset generation stopped safely.",
						StatusTone.ERROR,
						message);
				appendUserGenTweetsResult("[FAILED] " + message);
				JOptionPane.showMessageDialog(
						ControllerAgentGui.this,
						"Artificial dataset generation failed safely.\n\n" + message
						+ "\n\nNo partial dataset was published.",
						"Generation Failed",
						JOptionPane.ERROR_MESSAGE);
			}
		};
		if (SwingUtilities.isEventDispatchThread())
		{
			update.run();
		}
		else
		{
			SwingUtilities.invokeLater(update);
		}
	}

	private static String formatDuration(long millis)
	{
		long seconds = millis / 1000L;
		long minutes = seconds / 60L;
		seconds %= 60L;
		if (minutes > 0L)
		{
			return minutes + "m " + seconds + "s";
		}
		return seconds + "s";
	}

	private static String formatCount(long value)
	{
		return String.format(Locale.US, "%,d", Long.valueOf(Math.max(0L, value)));
	}

	private static String formatBytes(long bytes)
	{
		if (bytes < 1024L)
		{
			return bytes + " B";
		}
		double value = bytes;
		String[] units = {"B", "KB", "MB", "GB", "TB"};
		int unit = 0;
		while (value >= 1024.0 && unit < units.length - 1)
		{
			value /= 1024.0;
			unit++;
		}
		return String.format(Locale.US, "%.1f %s", value, units[unit]);
	}

	public void showMessageBox(String phase)
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			final String phaseForEdt = phase;
			SwingUtilities.invokeLater(new Runnable() {
				public void run()
				{
					showMessageBox(phaseForEdt);
				}
			});
			return;
		}
		if (phase.equals("initialize"))
		{
			simulatorInitialized = true;
			simulatorInitializationInProgress = false;
			enableAllButtons();
			enableList();
			updateBatchUsersFieldState();
			recommendationTargetModeBox.setEnabled(true);
			generateGraphOutput.setEnabled(true);
			setPerformanceStatus(
					"INITIALIZED",
					"Agents and model resources are ready. Press Run Simulation.",
					StatusTone.SUCCESS);
			refreshSimulatorDoc2VecCacheStatus();
			if (notifyObservatoryAutomationInitialized())
			{
				return;
			}
			JOptionPane.showMessageDialog(this,"Finished initializing with given parameters", "Finished Step 2", JOptionPane.INFORMATION_MESSAGE);
		}
		else if (phase.equals("get users"))
		{
			setPerformanceStatus(
					"USERS READY",
					"User discovery finished. Select targets and initialize the experiment.",
					StatusTone.SUCCESS);
			JOptionPane.showMessageDialog(this,"Finished getting users", "Finished Step 1", JOptionPane.INFORMATION_MESSAGE);
		}
			else if (phase.equals("finished simulation"))
			{
				printTimingResults();
				boolean noResults = recommendationUserCount > 0
						&& recommendationItemCount == 0;
				boolean partialResults = !noResults && recommendationIssueCount > 0;
				String statusLabel = noResults ? "NO RESULTS"
						: partialResults ? "PARTIAL" : "COMPLETE";
				StatusTone statusTone = noResults || partialResults
						? StatusTone.WARNING : StatusTone.SUCCESS;
				String statusMessage = noResults
						? "The run finished, but no recommendation candidates were produced. "
								+ "Review Live Timing for the model diagnostic."
						: partialResults
						? "The run finished with recommendations for some targets and "
								+ "warnings for others. Review the recommendation cards."
						: "Recommendations are complete"
								+ (isGraphGenerationEnabled()
								? " and final graph processing has finished."
								: ". Graph display and export were skipped.");
				setPerformanceStatus(statusLabel, statusMessage, statusTone);
				if (outputTabs != null && recommendationUserCount > 0)
				{
					outputTabs.setSelectedIndex(2);
				}
				refreshSimulatorDoc2VecCacheStatus();
				if (notifyObservatoryAutomationFinished(statusMessage))
				{
					return;
				}
				JOptionPane.showMessageDialog(
						this,
						statusMessage,
						noResults ? "Simulation Finished Without Results"
								: partialResults ? "Simulation Finished With Warnings"
								: "Finished Step 3",
						noResults || partialResults
								? JOptionPane.WARNING_MESSAGE
								: JOptionPane.INFORMATION_MESSAGE);
			
		}
		else if (phase.equals("finished collecting tweets"))
		{
			setUserGenStatus(
					"COMPLETE",
					"Twitter collection finished successfully.",
					StatusTone.SUCCESS,
					"Collected dataset is ready");
			JOptionPane.showMessageDialog(this,"Finished collecting tweets", "Finished Collection Step", JOptionPane.INFORMATION_MESSAGE);
		}
		else if (phase.equals("finished user generated simulation"))
		{
			setUserGenStatus(
					"COMPLETE",
					"Artificial tweet generation and homophily validation finished.",
					StatusTone.SUCCESS,
					"Generated dataset is ready");
			JOptionPane.showMessageDialog(this,"Finished user generated simulation", "Finished User Generation Simulation Step", JOptionPane.INFORMATION_MESSAGE);
		}
		
		}

	public void handleSimulationStartFailure(final String message)
	{
		SwingUtilities.invokeLater(new Runnable() {
			public void run()
			{
				enableAllButtons();
				enableList();
				appendResult(message);
				setPerformanceStatus(
						"FAILED",
						"Simulation could not start: " + message,
						StatusTone.ERROR);
				if (notifyObservatoryAutomationFailure("start", message))
				{
					return;
				}
				JOptionPane.showMessageDialog(
						ControllerAgentGui.this,
						message,
						"Simulation Start Failed",
						JOptionPane.ERROR_MESSAGE);
			}
		});
	}

	public void handleSimulationRunFailure(final String message)
	{
		SwingUtilities.invokeLater(new Runnable() {
			public void run()
			{
				enableAllButtons();
				enableList();
				appendResult("[FAILED] " + message);
				setPerformanceStatus(
						"FAILED",
						"The recommendation run stopped safely. No partial or "
						+ "zero-score results were accepted.",
						StatusTone.ERROR);
				if (notifyObservatoryAutomationFailure("run", message))
				{
					return;
				}
				JOptionPane.showMessageDialog(
						ControllerAgentGui.this,
						message
						+ "\n\nThe run was stopped before querying users, "
						+ "updating edges, or generating a graph.",
						"Recommendation Run Failed Safely",
						JOptionPane.ERROR_MESSAGE);
			}
		});
	}

	public void handleInitializationFailure(final String message)
	{
		SwingUtilities.invokeLater(new Runnable() {
			public void run()
			{
				simulatorInitialized = false;
				simulatorInitializationInProgress = false;
				getUsersButton.setEnabled(true);
				initializeButton.setEnabled(!agentsList.isEmpty());
				startButton.setEnabled(false);
					showAgentsList.setEnabled(true);
						algorithmSelectionBox.setEnabled(true);
						algorithmSettingsButton.setEnabled(true);
						recommendationTargetModeBox.setEnabled(true);
					generateGraphOutput.setEnabled(true);
					updateBatchUsersFieldState();
				appendResult(message);
				setPerformanceStatus(
						"FAILED",
						"Initialization failed: " + message,
						StatusTone.ERROR);
				if (notifyObservatoryAutomationFailure("initialize", message))
				{
					return;
				}
				JOptionPane.showMessageDialog(
						ControllerAgentGui.this,
						message,
						"Initialization Failed",
						JOptionPane.ERROR_MESSAGE);
			}
		});
	}

	private void invalidateSimulatorInitializationState()
	{
		if (simulatorInitializationInProgress)
		{
			return;
		}
		simulatorInitialized = false;
		if (startButton != null)
		{
			startButton.setEnabled(false);
		}
	}

	public void enableAllButtons()
	{
		startButton.setEnabled(simulatorInitialized
				&& !simulatorInitializationInProgress);
		initializeButton.setEnabled(true);
		getUsersButton.setEnabled(true);
		algorithmSettingsButton.setEnabled(true);
		generateGraphOutput.setEnabled(true);
	}
	
	public void disableStartButton()
	{
		if (startButton.isEnabled())
		{
			startButton.setEnabled(false);
//			FileWriter writer;
//			try {
//				writer = new FileWriter("testEnablingStartButton.txt", true); //append
//				BufferedWriter bufferedWriter = new BufferedWriter(writer);
//				bufferedWriter.write("disabledStartButton");
//				bufferedWriter.newLine();
//				bufferedWriter.close();
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
		}
	}

	public void enableStartButton()
	{
		if (!simulatorInitialized || simulatorInitializationInProgress)
		{
			startButton.setEnabled(false);
			return;
		}
		if (!startButton.isEnabled())
		{
			startButton.setEnabled(true);
			FileWriter writer;
			try {
				writer = new FileWriter("testEnablingStartButton.txt", true); //append
				BufferedWriter bufferedWriter = new BufferedWriter(writer);
				bufferedWriter.write("enabledStartButton");
				bufferedWriter.newLine();
				bufferedWriter.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}
	
	public void disableUserGenSimButtons()
	{
		changePerformanceButton.setEnabled(false);
		dataCollectionButton.setEnabled(false);
		loadCorpusButton.setEnabled(false);
		setSimulatedTweetsButton.setEnabled(false);
		startUserSimButton.setEnabled(false);
	}
	
	public void enableUserGenSimButtons()
	{
		changePerformanceButton.setEnabled(true);
		dataCollectionButton.setEnabled(true);
		loadCorpusButton.setEnabled(true);
		setSimulatedTweetsButton.setEnabled(true);
		startUserSimButton.setEnabled(true);
	}
	
	public void changeRecommendUserTitle()
	{
		String recommendingUserName = agentsList.get(indexToRecommend);
		String newRecommendationTitle = "Recommendations for User: " + recommendingUserName;
		recommendationTitle.setTitle(newRecommendationTitle);
		this.repaint();
	}
	
	public void changeUserListTitle(int readFrom)
	{
		if (readFrom == FROM_GENERATION)
		{
			agentsListTitle.setTitle("List of Generated Users");
		}
		else if (readFrom == FROM_ARTIFICIAL)
		{
			agentsListTitle.setTitle("List of Artificial Users");
		}
		else
		{
			agentsListTitle.setTitle("List of Real Users");
		}
		this.repaint();
	}

	public void testPrint()
	{
		System.out.println("I AM PRINTING");
	}

	public void disableList()
	{
		showAgentsList.setEnabled(false);
	}

	public void enableList()
	{
		showAgentsList.setEnabled(true);
	}

	public void updateList(ArrayList<String> listOfAgents)
	{
		agentsList.clear();
		System.out.println("listOfAgents.size(): "+listOfAgents.size());
		for (int i = 0; i < listOfAgents.size(); i++){
			agentsList.addElement(listOfAgents.get(i));
		}
			setPerformanceStatus(
					"USERS READY",
					"Loaded " + listOfAgents.size()
							+ " users. Select recommendation targets, review settings, then initialize.",
					StatusTone.SUCCESS);
			refreshSimulatorHelpPanel();
		}
	
	public void reselectRecommendee()
	{
		if (agentsList.get(indexToRecommend).equals(recommendeeName))  // after && was added by Sepide
		{
			showAgentsList.setSelectedIndex(indexToRecommend);
			/*S showAgentsList.setSelectedIndex(indexToRecommend2);   // added by Sepide
			a = new int[] {indexToRecommend, indexToRecommend2};   // added by Sepide
			showAgentsList.setSelectedIndices(a);  */ // added by Sepide
		}
		else
		{
			if (agentsList.contains(recommendeeName))  // after && was added by Sepide
			{
				showAgentsList.setSelectedIndex(agentsList.indexOf(recommendeeName));
				//S showAgentsList.setSelectedIndex(agentsList.indexOf(recommendeeName2));  // added by Sepide
				//S int b1 = agentsList.indexOf(recommendeeName); // added by Sepide
				//S int b2 = agentsList.indexOf(recommendeeName2); // added by Sepide
				 /* S b = new int[] {b1, b2};  // added by Sepide
				showAgentsList.setSelectedIndices(b);  // added by Sepide    */ 
			}
			else
			{
				showAgentsList.setSelectedIndex(0);
				 /* S c = new int[] {0,1};               // added by Sepide
				showAgentsList.setSelectedIndices(c);   // added by Sepide  */ 
			}
			
		}
	}

	
	
	public void helpUserSimText()
	{
		JOptionPane.showMessageDialog(this,"1. Collect Data from Twitter by entering followee name(s) separated by commas then enter the number of followers you want for them. All followees will get try to get the same number of followers. The collected data to be used in the simulator will be stored in Dataset/TwitterObtained/allFolloweeNames_yyyy-MM-dd HH-mm-ss/*_final.txt.\n***Skip if you already have a correctly-formatted corpus.\n2. Load a corpus that was collected by Step 1 or a correctly-formatted corpus.\n3. Set Number of Artificial Tweets. 0 is default which means use the number of original processed corpus tweets. A number bigger than the original corpus size will try to keep the same proportions in the generated corpus.\n4. Start User Gen Simulation. Generated tweets will be saved under Dataset/TwitterObtained/GENERATED/ORIGINALFILENAME_yyyy-MM-dd HH-mm-ss_GENERATED.txt. Clustering results will appear at the end after clustering the generated tweets", "How To Use", JOptionPane.INFORMATION_MESSAGE);
	}
	
	public void helpPerformanceText()
	{
		JOptionPane.showMessageDialog(this,"1. Load a text file from the File Menu.\n2. Get Users after selecting a text file.\n3. Change any parameters then Initialize.\n4. Run simulation.\n5. If you want to run another simulation with different parameters, make sure to re-Initialize again before running the simulation.\n6. Press Reset Experiment before beginning a brand new experiment.", "How To Use", JOptionPane.INFORMATION_MESSAGE);
	}
	
	private void resetCurrentTiming()
	{
		countRecServersTP = 0;
		countRecServersTfidf = 0;
		countRecServersAlgorithm = 0;
		currentMaxTPTime = 0;
		currentMaxTfidfTime = 0;
		currentMaxAlgorithmTime = 0;
		currentMessagePassingTime = 0;
		currentTiming = new Timings(
				currentMaxTPTime,
				currentMaxTfidfTime,
				currentMaxAlgorithmTime,
				timingAlgorithmName(algorithmRec));
	}

	private void ensureCurrentTiming()
	{
		if (currentTiming == null)
		{
			currentTiming = new Timings(
					currentMaxTPTime,
					currentMaxTfidfTime,
					currentMaxAlgorithmTime,
					timingAlgorithmName(algorithmRec));
		}
	}

	private String timingAlgorithmName(int algorithm)
	{
		if (algorithm == COS_SIM)
		{
			return "CosSim";
		}
		if (algorithm == K_MEANS)
		{
			return "K-means";
		}
		if (algorithm == SVM)
		{
			return "SVM";
		}
		if (algorithm == SCALABLE_SVM)
		{
			return "Scalable Linear SVM";
		}
		if (algorithm == MLP)
		{
			return "MLP";
		}
		if (algorithm == Doc2Vec)
		{
			return "Doc2Vec";
		}
		if (algorithm == CommonNeighbors)
		{
			return "Common-Neighbors";
		}
		if (algorithm == K_MEANSEUCLIDEAN)
		{
			return "K-meansEuclidean";
		}
		return RecommendationModeSupport.algorithmName(algorithm);
	}

	public void setTPTime(double tpTime)
	{
		ensureCurrentTiming();
		countRecServersTP++;
		if (countRecServersTP <= numNodes)
		    currentMaxTPTime = getMax(currentMaxTPTime,tpTime);
		currentTiming.setTPTime(currentMaxTPTime);
	}

	public void setTfidfTime(double tfidfTime)
	{
		ensureCurrentTiming();
		countRecServersTfidf++;
		if (countRecServersTfidf <= numNodes)
			currentMaxTfidfTime = getMax(currentMaxTfidfTime,tfidfTime);
		currentTiming.setTFIDFTime(currentMaxTfidfTime);
	}

	public void setAlgorithmTime(double algorithmTime)
	{
		ensureCurrentTiming();
		countRecServersAlgorithm++;
		if (countRecServersAlgorithm <= numNodes)
			currentMaxAlgorithmTime = getMax(currentMaxAlgorithmTime,algorithmTime);
		currentTiming.setAlgorithmTime(currentMaxAlgorithmTime);
	}

	public double getMax(double time1, double time2)
	{
		if (time1 < time2)
			return time2;
		else
			return time1;
	}

	public void addMessagePassingTime(long messagePassingTime)
	{
		String messagePassingResult = "";
		messagePassingResult = "MAX Message Passing: " + messagePassingTime + " ms";
		currentMessagePassingTime = messagePassingTime;
		appendResult(messagePassingResult);
	}

	public void addMessagePassingTime(long[] messagePassingTimes)
	{
		String messagePassingResult = "";
		for (int i = 0; i < messagePassingTimes.length; i++)
		{
			messagePassingResult = "Message Passing: " + messagePassingTimes[i] + " ms";
			currentMessagePassingTime = messagePassingTimes[i];
			appendResult(messagePassingResult);
		}
	}
	
	public void addMessagePassingCost(int messagePassingCost)
	{
		String messagePassingResult = "";
		messagePassingResult = "TOTAL Messages Cost: " + messagePassingCost + " bytes";
		currentMessagePassingCost = messagePassingCost;
		appendResult(messagePassingResult);
	}

	public void addMessagePassingCost(int[] messagePassingCosts)
	{
		String messagePassingResult = "";
		for (int i = 0; i < messagePassingCosts.length; i++)
		{
			messagePassingResult = "Messages Cost: " + messagePassingCosts[i] + " bytes";
			currentMessagePassingTime = messagePassingCosts[i];
			appendResult(messagePassingResult);
		}
	}

	//Also works for cosine similarity too, just naming needs changing
	public void addKmeansMergeTime(long kmeansMergeTime)
	{
		long kmeansMergeTimeMs = kmeansMergeTime / 1000000;
		System.out.println("nanoseconds: " + kmeansMergeTime);
		System.out.println("merge time: " + kmeansMergeTimeMs+ " ms");
		String kmeansMergeResult = "Merge Message Time: ";
		kmeansMergeResult += kmeansMergeTimeMs + " ms";
		appendResult(kmeansMergeResult);
	}

	//Also works for cosine similarity too, just naming needs changing
	public void addKmeansMergeTimeNano(long kmeansMergeTime)
	{
		String kmeansMergeResult = "Merge Message Time: ";
		kmeansMergeResult += kmeansMergeTime + " ns";
		appendResult(kmeansMergeResult);
	}

	public void addTiming()
	{
		ensureCurrentTiming();
		timings.add(currentTiming);
		if (timings.size() < 2)
		{
			System.out.println(
					"Timing comparison skipped: only "
							+ timings.size()
							+ " completed timing record(s) are available.");
			return;
		}

		if (simulationIteration > 1)
		{
			double differenceTP, differenceTfidf, differenceAlgorithm;
			double currentTP, currentTfidf, currentAlgorithm;
			double previousTP, previousTfidf, previousAlgorithm;
			DecimalFormat df = new DecimalFormat("#.##");

			currentTP = timings.get(timings.size()-1).getTPTime();
			currentTfidf = timings.get(timings.size()-1).getTFIDFTime();
			currentAlgorithm = timings.get(timings.size()-1).getAlgorithmTime();
			previousTP = timings.get(timings.size()-2).getTPTime();
			previousTfidf = timings.get(timings.size()-2).getTFIDFTime();
			previousAlgorithm = timings.get(timings.size()-2).getAlgorithmTime();

			System.out.println("currentTP: "+currentTP + " currentTfidf: "+currentTfidf + " currentAlgorithm: "+currentAlgorithm);
			System.out.println("previousTP: "+previousTP+" previousTfidf: "+previousTfidf+ " previousAlgorithm: "+previousAlgorithm);
			System.out.println("(currentTP - previousTP): "+(currentTP - previousTP));
			System.out.println("(currentTfidf - previousTfidf): "+(currentTfidf - previousTfidf));
			System.out.println("(currentAlgorithm - previousAlgorithm): "+(currentAlgorithm - previousAlgorithm));

			if (previousTP <= 0.0 || previousTfidf <= 0.0
					|| previousAlgorithm <= 0.0)
			{
				System.out.println(
						"Timing comparison skipped: previous timing contains zero values.");
				return;
			}

			differenceTP = ((currentTP - previousTP) / previousTP)*100*-1;
			differenceTfidf = ((currentTfidf - previousTfidf) / previousTfidf)*100*-1;
			differenceAlgorithm = ((currentAlgorithm - previousAlgorithm) / previousAlgorithm)*100*-1;

			if (differenceTP == -0)
				differenceTP = 0.0;
			if (differenceTfidf == -0)
				differenceTfidf = 0.0;
			if (differenceAlgorithm == -0)
				differenceAlgorithm = 0.0;

			resultArea.append("Improvements - TP: "+df.format(differenceTP)+"% TFIDF: "+df.format(differenceTfidf)+"% Algorithm: "+df.format(differenceAlgorithm)+"%\n");
		}
	}

	public void printTimingResults()
	{
			FileWriter writer;
			String resultTextArea = resultArea.getText();
			String removeText = resultTextArea;
			
			if (removeText.contains("Improvement"))
			{
				removeText = resultTextArea.substring(0, resultTextArea.indexOf("Improvement"));
			}
			String finalText = removeText.substring(removeText.lastIndexOf("=")+1).trim()+"\n";
			
			try {
				writer = new FileWriter("resultsTiming.txt", true); //append
				BufferedWriter bufferedWriter = new BufferedWriter(writer);
				bufferedWriter.write(finalText);
				bufferedWriter.newLine();
				bufferedWriter.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	}
	
	public void updateProgressLabel(String updateText)
	{
		setPerformanceStatus(
				"WORKING",
				updateText == null || updateText.trim().length() == 0
						? "Experiment is running."
						: updateText,
				StatusTone.RUNNING);
	}

	public void updateUserGenProgressLabel(String updateText)
	{
		setUserGenStatus(
				"WORKING",
				updateText == null || updateText.trim().length() == 0
						? "User generation task is running."
						: updateText,
				StatusTone.RUNNING,
				"User Generation Studio");
	}
	
}

