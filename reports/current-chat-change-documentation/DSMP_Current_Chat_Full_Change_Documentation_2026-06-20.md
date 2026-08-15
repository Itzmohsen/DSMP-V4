# DSMP Current Chat Full Change Documentation

Generated: 2026-06-20
Repository: `/Users/mohsen11/Desktop/June 10 session final/test`
Patch appendix: `/Users/mohsen11/Desktop/June 10 session final/test/reports/current-chat-change-documentation/DSMP_Current_Chat_Source_Diff_Appendix_2026-06-20.patch`

## Document Scope And Evidence Standard

This document records the changes made during the current Codex working session on the DSMP simulator repository at `/Users/mohsen11/Desktop/June 10 session final/test`. It intentionally separates source-code changes from generated runtime outputs. The source baseline for the first major block is `origin/main` at commit `06dabbb` (`fix bug on node generation`). The current branch head is `cb47942` (`Add Doc2Vec batch mode and experiment reset`), and there is a large post-commit working tree containing additional source files and modifications.

The document uses three evidence levels:

1. **Narrative implementation record:** what was changed, why, what problem it addressed, what changed now, and what side effects/risks remain.
2. **Exact code excerpts:** targeted before/after snippets are embedded in this report for the highest-impact changes.
3. **Full code appendix:** the complete source-level before/after patch is written to `/Users/mohsen11/Desktop/June 10 session final/test/reports/current-chat-change-documentation/DSMP_Current_Chat_Source_Diff_Appendix_2026-06-20.patch`. For tracked files the appendix compares against `origin/main`; for untracked source files the appendix shows `/dev/null -> new file`, which is the exact before/after for files that did not exist previously.

Generated datasets, cached Doc2Vec models, ARFF training files, timing logs, compiled `.class` files, Gephi graph outputs, and recommendation result files are not treated as hand-authored source changes. They are documented as runtime artifacts only.

## Repository Timeline

The source history relevant to this chat has two layers.

```text
cb47942 (HEAD -> v3.2, origin/V3.2) Add Doc2Vec batch mode and experiment reset
6a00b58 Fixed GUI not opening
fe57855 Add v3.2 simulator updates
06dabbb (origin/main, origin/HEAD) fix bug on node generation
88d102f fix gephi graph generation process on each simulation
876ab32 fix2
5c1a884 fix pathdir
ddc52f4 fix: windows command-line length fix for java compilation
4414852 Revise README
ec41c89 Miscellaneous File Changes (#1)
c725d06 Update README.md
a4f2702 Update verbose logging
```

The first layer was committed as `cb47942` and includes the initial Doc2Vec batch mode and experiment reset work. The second layer is the uncommitted working tree after `cb47942`, which contains the GUI redesign, dataset import lab, graph insights, help tab, runtime resource scheduler, exact multi-node lab, scalable/isolated SVM work, bootstrap hardening, streaming ingestion, and new self-tests.

The clean source-diff scale relative to `origin/main` is:

```text
.gitignore                                         |   12 +-
 README.md                                          |  312 +-
 .../Test/BatchRecommendationSelectorSelfTest.java  |   73 +
 .../Test/Doc2VecBatchRunnerSelfTest.java           |  184 +
 .../Test/ExperimentResetFilesSelfTest.java         |   88 +
 .../Test/GraphGmlBuilderSelfTest.java              |  144 +
 .../Test/SvmReproducibilitySelfTest.java           |  119 +
 TwitterGatherDataFollowers/Test/TestClass.class    |  Bin 2563 -> 2715 bytes
 .../userRyersonU/BatchRecommendationSelector.java  |  112 +
 .../userRyersonU/CleanText.class                   |  Bin 16503 -> 16948 bytes
 .../userRyersonU/ClearWhiteSpacesLeaveWords.class  |  Bin 1764 -> 2001 bytes
 .../userRyersonU/ControllerAgent.java              | 2847 ++++++---
 .../userRyersonU/ControllerAgentGui.java           | 6622 +++++++++++++++++---
 .../userRyersonU/Doc2VecBatchRunner.java           |  349 ++
 .../userRyersonU/ExperimentResetFiles.java         |   73 +
 .../userRyersonU/GraphGmlBuilder.java              |  196 +
 .../userRyersonU/InMemoryDb.java                   |  124 +-
 .../userRyersonU/MobileAgent.java                  | 1471 +++--
 .../userRyersonU/OrganizingAgent.java              |  625 +-
 TwitterGatherDataFollowers/userRyersonU/Point.java |   29 +-
 .../userRyersonU/RecommenderAgent.java             | 4793 +++++++++-----
 .../userRyersonU/StarterAgent.java                 | 1341 +++-
 .../userRyersonU/SvmReproducibility.java           |  105 +
 .../userRyersonU/Tweet.class                       |  Bin 2008 -> 2162 bytes
 .../userRyersonU/UserGenSimAgent.java              | 2009 +-----
 .../userRyersonU/doc2vec_batch.py                  |  106 +
 .../userRyersonU/doc2vec_shared.py                 |  583 ++
 build.py                                           | 1217 ++--
 build_and_start_modern.sh                          |   95 +-
 requirements.txt                                   |   17 +-
 start.bat                                          |    4 +-
 start_modern.bat                                   |  143 +-
 start_modern.sh                                    |   70 +-
 33 files changed, 16641 insertions(+), 7222 deletions(-)
```

## Source Inventory

The clean source inventory is stored in `/Users/mohsen11/Desktop/June 10 session final/test/reports/current-chat-change-documentation/DSMP_Current_Chat_Source_Inventory_2026-06-20.txt`. The important point is that the tree contains both tracked modifications and new source files. New files are not “modified old logic”; they are additive modules introduced to isolate new behavior.

Inventory excerpt:

```text
DSMP Current Chat Source Inventory
Generated: 2026-06-20
Baseline for tracked changes: origin/main (06dabbb)
Scope: source/config/docs/launcher files only. Generated datasets, caches, result files, jars, and .class files are excluded.

Tracked source/status changes relative to origin/main:
M	.gitignore
M	README.md
A	TwitterGatherDataFollowers/Test/BatchRecommendationSelectorSelfTest.java
A	TwitterGatherDataFollowers/Test/Doc2VecBatchRunnerSelfTest.java
A	TwitterGatherDataFollowers/Test/ExperimentResetFilesSelfTest.java
A	TwitterGatherDataFollowers/Test/GraphGmlBuilderSelfTest.java
A	TwitterGatherDataFollowers/Test/SvmReproducibilitySelfTest.java
A	TwitterGatherDataFollowers/userRyersonU/BatchRecommendationSelector.java
M	TwitterGatherDataFollowers/userRyersonU/ControllerAgent.java
M	TwitterGatherDataFollowers/userRyersonU/ControllerAgentGui.java
A	TwitterGatherDataFollowers/userRyersonU/Doc2VecBatchRunner.java
A	TwitterGatherDataFollowers/userRyersonU/ExperimentResetFiles.java
A	TwitterGatherDataFollowers/userRyersonU/GraphGmlBuilder.java
M	TwitterGatherDataFollowers/userRyersonU/InMemoryDb.java
M	TwitterGatherDataFollowers/userRyersonU/MobileAgent.java
M	TwitterGatherDataFollowers/userRyersonU/OrganizingAgent.java
M	TwitterGatherDataFollowers/userRyersonU/Point.java
M	TwitterGatherDataFollowers/userRyersonU/RecommenderAgent.java
M	TwitterGatherDataFollowers/userRyersonU/StarterAgent.java
A	TwitterGatherDataFollowers/userRyersonU/SvmReproducibility.java
M	TwitterGatherDataFollowers/userRyersonU/UserGenSimAgent.java
A	TwitterGatherDataFollowers/userRyersonU/doc2vec_batch.py
A	TwitterGatherDataFollowers/userRyersonU/doc2vec_shared.py
M	build.py
M	build_and_start_modern.sh
M	requirements.txt
M	start.bat
M	start_modern.bat
M	start_modern.sh

Untracked source files added after current HEAD/cb47942:
.github/workflows/bootstrap-smoke.yml
DSMP_V3_2_FULL_CHANGE_DOCUMENTATION_2026-06-20.md
TwitterGatherDataFollowers/Test/AdaptiveModelConcurrencySelfTest.java
TwitterGatherDataFollowers/Test/AlgorithmParameterSettingsSelfTest.java
TwitterGatherDataFollowers/Test/ControllerStreamingAggregationSelfTest.java
TwitterGatherDataFollowers/Test/ExactPopulationSweepDatasetSmokeTest.java
TwitterGatherDataFollowers/Test/ExactPopulationSweepEngineSelfTest.java
TwitterGatherDataFollowers/Test/ExactSvmCorpusAdmissionSelfTest.java
TwitterGatherDataFollowers/Test/ExactSvmWorkerSelfTest.java
TwitterGatherDataFollowers/Test/ExtremeHeavyAlgorithmSmokeTest.java
TwitterGatherDataFollowers/Test/ExtremeHeavyDatasetTool.java
TwitterGatherDataFollowers/Test/GraphAnalysisEngineSelfTest.java
TwitterGatherDataFollowers/Test/GraphGenerationSettingSelfTest.java
TwitterGatherDataFollowers/Test/HeavyParallelDatasetTool.java
TwitterGatherDataFollowers/Test/InMemoryDbSelfTest.java
TwitterGatherDataFollowers/Test/Journal38kBoundedStreamSmokeTest.java
TwitterGatherDataFollowers/Test/KMeansTargetScoreRowsSelfTest.java
TwitterGatherDataFollowers/Test/LargeTextAgentProvisioningSelfTest.java
TwitterGatherDataFollowers/Test/MillionRowIngestionSelfTest.java
TwitterGatherDataFollowers/Test/MillionRowSparseSvmSelfTest.java
TwitterGatherDataFollowers/Test/MlpModelAveragerSelfTest.java
TwitterGatherDataFollowers/Test/ModelWorkerProcessSupportSelfTest.java
TwitterGatherDataFollowers/Test/NodeExecutionDashboardSelfTest.java
TwitterGatherDataFollowers/Test/NodeExecutionMonitorSelfTest.java
TwitterGatherDataFollowers/Test/PopulationSweepPanelSelfTest.java
TwitterGatherDataFollowers/Test/RecommendationPostProcessorSelfTest.java
TwitterGatherDataFollowers/Test/RecommendationRunAbortRegistrySelfTest.java
TwitterGatherDataFollowers/Test/RecommendationRunLifecycleSelfTest.java
TwitterGatherDataFollowers/Test/RecommendationScoreMergerSelfTest.java
TwitterGatherDataFollowers/Test/RecommenderStreamingAggregationSelfTest.java
TwitterGatherDataFollowers/Test/ReplayableCorpusStreamSelfTest.java
TwitterGatherDataFollowers/Test/ReplayableTweetBatchSourceSelfTest.java
TwitterGatherDataFollowers/Test/RuntimeResourceSettingsSelfTest.java
TwitterGatherDataFollowers/Test/ScalableSvmJournalArffSmokeTest.java
TwitterGatherDataFollowers/Test/ScalableSvmNodeInvarianceSelfTest.java
TwitterGatherDataFollowers/Test/ScalableSvmWorkerSelfTest.java
TwitterGatherDataFollowers/Test/SparseArffWriterSelfTest.java
TwitterGatherDataFollowers/Test/SparsePointDistanceSelfTest.java
TwitterGatherDataFollowers/Test/StarterAgentUserDiscoverySelfTest.java
TwitterGatherDataFollowers/Test/SvmTargetPreflightSelfTest.java
TwitterGatherDataFollowers/Test/TextBatchBackpressureSelfTest.java
TwitterGatherDataFollowers/Test/TweetBatchDeliveryLedgerSelfTest.java
TwitterGatherDataFollowers/Test/UciRetailDatasetImporterSelfTest.java
TwitterGatherDataFollowers/Test/UniversalHeavyAlgorithmSmokeTest.java
TwitterGatherDataFollowers/Test/UniversalHeavyDatasetTool.java
TwitterGatherDataFollowers/Test/UniversalHeavyParallelCoreBenchmark.java
TwitterGatherDataFollowers/Test/UserGenerationEngineSelfTest.java
TwitterGatherDataFol
```

## Committed Change Block: Doc2Vec Batch Mode And Experiment Reset

The first committed block fixed two fundamental simulator limitations.

Before this work, SVM had a real batch path, but Doc2Vec did not. The GUI could not simply expose the old SVM batch field for Doc2Vec because the Java Doc2Vec path only invoked Python for the first selected target user and then propagated that same score map to every selected user. That was not a performance issue; it was a correctness issue. A batch run would have appeared successful while silently giving multiple users the same first-user recommendations.

The committed implementation added a Doc2Vec-specific batch path instead of altering the legacy single-user route. The new Java runner starts the Python batch wrapper, passes the selected target list, receives structured per-target score records, and constructs one independent score map per target. The Python side still uses the shared Doc2Vec cache and the existing shard-scoring function, so the scoring logic for each target is the same as the single-user code path.

The same committed block added experiment reset support. Before this change, graph and recommendation-side files from a previous loaded dataset could contaminate a new text-dataset experiment. Resetting was either implicit or tied to unrelated actions. The new reset path deletes dataset-specific graph/recommendation artifacts intentionally and is exposed as a dedicated GUI action.

Side effects and risk management:

- SVM behavior and seeds were left alone.
- The legacy Doc2Vec single-user path remains available for compatibility.
- The batch path is only enabled where the algorithm supports it.
- Reset deletes only known generated files and reports failures instead of broad-deleting directories.
- The code path is covered by `Doc2VecBatchRunnerSelfTest.java` and `ExperimentResetFilesSelfTest.java`.

## Before: Doc2Vec Only Scored The First Selected User

**Old Doc2Vec batch bug: Python invoked with usersRec.get(0)**

File: `TwitterGatherDataFollowers/userRyersonU/RecommenderAgent.java` (origin/main)

```java
  323: 	private MultilayerPerceptron mlp; //added by Sepide
  324: 	private MultiLayerPerceptron averagedMLP; //MLP with averaged weights
  325: 	private NeuralNetwork averagedNN; //NN with averaged weights
  326: 	private String[] followeeNames;
  327: 	private String[] followerNames;    // Added by Sepide
  328: 	public List<String> datasetFollowees; //followees of whole dataset Sepide changed the private access modifier to public
  329: 	private List<String> centralTrainSetUsers;
  330: 	private List<String> centralTestSetUsers;
  331: 	//public File fileFromGui = myGui.fileChooser.getSelectedFile();  // added by Sepide
  332: 	//public String filePath = fileFromGui.getPath();   // added by Sepide 
  333:     //public String userREC = usersRec.get(0).toString();    // added by Sepide 
  334: 	
  335: 	protected void setup() 
  336: 	{
  337: 
  338: 
  339: 		Object[] args = getArguments();
  340: 		controllerAgent = (AID) args[0];
  341: 
  342: 		referenceUser = (String) args[1]; 
  343: 		tweetNum_temp2    = (String) args[2];
```

## After: Doc2Vec Batch Uses Structured Per-Target Results

**Current Doc2Vec batch call**

File: `TwitterGatherDataFollowers/userRyersonU/RecommenderAgent.java` (WORKTREE)

```java
 6269: 					return allowLegacyFallback
 6270: 							? buildDoc2VecBatchFailureScores(batchUsers, topn)
 6271: 							: buildEmptyDoc2VecScores(batchUsers);
 6272: 				}
 6273: 
 6274: 				int doc2vecWorkers = Math.min(Math.max(Runtime.getRuntime().availableProcessors() - 1, 1), 12);
 6275: 				File batchScript = new File("TwitterGatherDataFollowers/userRyersonU/doc2vec_batch.py");
 6276: 				int doc2vecNodeNumber = Integer.parseInt(nodeNumber);
 6277: 				boolean allowShardFallback =
 6278: 						allowLegacyFallback && numRecAgents == 1;
 6279: 				try
 6280: 				{
 6281: 					Doc2VecBatchRunner.Result result = Doc2VecBatchRunner.run(
 6282: 							getPythonCommand(), batchScript, topn, selectedDataset,
 6283: 								doc2vecNodeNumber,
 6284: 								numRecAgents,
 6285: 								doc2vecWorkers,
 6286: 								batchUsers,
 6287: 								algorithmSettings(),
 6288: 								new Doc2VecBatchRunner.ProgressListener()
 6289: 							{
 6290: 								public void onProgress(
 6291: 										String event, String detail)
 6292: 								{
 6293: 									handleDoc2VecProgress(event, detail);
```

**Python batch protocol**

File: `TwitterGatherDataFollowers/userRyersonU/doc2vec_batch.py` (WORKTREE)

```python
    6:     ensure_shared_model,
    7:     load_scoring_index,
    8:     parse_optional_model_params,
    9:     report_progress,
   10:     score_candidate_shard,
   11: )
   12: 
   13: 
   14: SCORE_PREFIX = "DOC2VEC_SCORE"
   15: ERROR_PREFIX = "DOC2VEC_ERROR"
   16: DONE_PREFIX = "DOC2VEC_DONE"
   17: 
   18: 
   19: def usage():
   20:     print(
   21:         "usage: doc2vec_batch.py <topn> <dataset_file> <node_number> "
   22:         "<num_nodes> <workers> <users_file> "
```

## GUI Redesign And Application Structure

The original Swing GUI was functional but largely flat: most controls lived in one large simulator window, important settings were hard to visually separate, and newer research workflows such as dataset import, exact multi-node evaluation, graph analysis, and help were either not present or were awkwardly mixed into the simulator screen.

The current GUI separates the application into first-class tabs:

- **Simulator:** the original JADE recommendation workflow, still connected to the same agents and recommendation logic.
- **User Studio:** user/corpus/artificial dataset generation, separated from the recommender so the two jobs no longer compete visually.
- **Exact Multi-Node Lab:** an isolated correctness/parallelism benchmark tab.
- **Dataset Import Lab:** a conversion/import workflow for retail, journal, tweet, or custom datasets.
- **Graph Insights:** read-only graph analysis over generated graph files.
- **Help:** dynamic visual explanation of the currently selected simulator configuration.

The GUI update changed presentation and operator ergonomics, not the base recommendation math. It added modern card layouts, status chips, live node panels, recommendation cards, a bottom status bar, dynamic algorithm descriptions, attribution blocks, a modern file picker, full-screen startup, and consistent animated headers.

Important side effects:

- The GUI is now heavier because it has multiple panels and live visual components.
- The panels are passive until refreshed; they should not launch models by themselves.
- The simulator tab remains the only path that runs the original JADE recommendation workflow.
- The exact lab is deliberately separate and does not write graph side effects.

## After: Full-Screen Startup And Managed Reveal

**Startup full-screen behavior**

File: `TwitterGatherDataFollowers/userRyersonU/ControllerAgentGui.java` (WORKTREE)

```java
  389: 			{
  390: 				heightRatio = heightScreen/FRAME_HEIGHT;
  391: 				widthRatio = heightRatio;
  392: 			}
  393: 
  394: 			setSize((int) (FRAME_WIDTH * widthRatio), (int)(FRAME_HEIGHT * heightRatio + 75));
  395: 		}
  396: 			this.setResizable(true);
  397: 			setLocationByPlatform(false);
  398: 			setLocationRelativeTo(null);
  399: 			applyStartupMaximizedWindow();
  400: 			}
  401: 
  402: 			void revealAfterLaunch()
  403: 			{
  404: 				Runnable reveal = new Runnable() {
  405: 					public void run() {
  406: 						setVisible(true);
  407: 						applyStartupMaximizedWindow();
  408: 						toFront();
  409: 						requestFocus();
```

## Modern File Picker

The old file selector used `JFileChooser` directly. After the GUI redesign on macOS, modal file dialogs were throwing `IllegalComponentStateException: The dialog is decorated` because the visual styling interacted badly with Swing dialog creation. The fix was not to keep styling the old dialog; a dedicated file picker component was introduced.

`ModernFilePicker.java` provides a searchable, styled picker with navigation, filtering, and selected-file confirmation. `ControllerAgentGui.selectFile()` now uses the picker and only writes back to the old `fileChooser` field after the user selects a real file. This preserves downstream code that still reads `fileChooser.getSelectedFile()`.

Side effects:

- Existing downstream dataset-selection logic is preserved because the selected file still lands in `fileChooser`.
- The file picker adds new GUI code but avoids the decorated dialog crash path.
- The picker is local-only; it does not change dataset parsing or recommendation logic.

## Before And After: File Selection

**Old GUI exposed JFileChooser directly**

File: `TwitterGatherDataFollowers/userRyersonU/ControllerAgentGui.java` (origin/main)

```java
  143: 	private JMenuItem fromDbMenu;
  144: 	private JMenuItem fromGenerationMenu;
  145: 	private JMenuItem fromArtificialMenu;
  146: 	private JMenuItem helpPerformanceMenu;
  147: 	private JMenuItem helpUserGenSimMenu;
  148: 	private JPanel cards; //panel to hold the cards in card layout
  149: 	public JFileChooser fileChooser;  // private access changed to public by Sepide
  150: 	public ArrayList<String> usersRec = new ArrayList<String>(); //Users looking for recommendation  added by Sepide
  151: 
  152: 	private ArrayList<Timings> timings;
  153: 	public Timings currentTiming;
  154: 
  155: 	private String referenceUser;
```

**Current selectFile uses ModernFilePicker**

File: `TwitterGatherDataFollowers/userRyersonU/ControllerAgentGui.java` (WORKTREE)

```java
 4949: 				"Select a dataset file before loading users.",
 4950: 				StatusTone.WARNING);
 4951: 		JOptionPane.showMessageDialog(this,"No file was selected. Please select a file before proceeding in simulation!","No File Selected", JOptionPane.INFORMATION_MESSAGE);
 4952: 	}
 4953: 	
 4954: 	//performance measurement file chooser
 4955: 	public void selectFile(int fileOption)
 4956: 	{
 4957: 		File selectedFile = ModernFilePicker.chooseDataset(
 4958: 				this,
 4959: 				fileChooser.getCurrentDirectory(),
 4960: 				fileChooser.getSelectedFile());
 4961: 		if (selectedFile != null)
 4962: 		{
 4963: 			fileChooser.setSelectedFile(selectedFile);
 4964: 			if (selectedFile.getParentFile() != null)
 4965: 			{
```

## Recommendation Target Modes

A major semantic issue surfaced during testing: different algorithms were not always recommending the same kind of thing. In the DSMP six-column text format, column 1 is the followee/reference label, column 5 is the user/entity name, and column 6 is text. Legacy SVM predicts or ranks followee/reference labels. Doc2Vec, Similarity, and K-Means naturally rank similar users/entities. This was correct historically, but confusing for retail/journal datasets because “recommendation” could mean either “which product/store/journal label” or “which similar customer/author/user.”

The new recommendation target mode makes this explicit. The GUI now lets the operator choose the target meaning rather than assuming it. The implementation introduces `RecommendationTargetMode`, `RecommendationCandidateType`, `RecommendationModeSupport`, `RecommendationDatasetContext`, `RecommendationCandidateEvidence`, and structured score payload classes. The default remains `ALGORITHM_DEFAULT`, so the old behavior is preserved unless the operator chooses a conversion mode.

Current meaning by mode:

- **Algorithm Default:** each algorithm behaves as it did historically.
- **Similar Users:** output candidates are users/entities from column 5.
- **Followee Accounts:** output candidates are followee/reference labels from column 1.

Why this was done:

- Retail data can be treated as similar customers or as product/store labels.
- Journal data can be treated as similar authors or as venues/journals.
- Twitter data can be treated as similar accounts/users or followed accounts.
- The GUI now describes what will be predicted before the run begins.

Risk management:

- Default mode is conservative and preserves legacy behavior.
- Conversion logic is isolated behind support classes.
- Score payloads carry candidate type/status so the organizer can fail safely rather than merging unknown score maps.

## After: Recommendation Mode Support Code

**Candidate type resolution**

File: `TwitterGatherDataFollowers/userRyersonU/RecommendationModeSupport.java` (WORKTREE)

```java
   42: 	{
   43: 		if (isUserRankingAlgorithm(algorithm))
   44: 		{
   45: 			return RecommendationCandidateType.USER;
   46: 		}
   47: 		if (isClassifierAlgorithm(algorithm))
   48: 		{
   49: 			return RecommendationCandidateType.FOLLOWEE;
   50: 		}
   51: 		return null;
   52: 	}
   53: 
   54: 	static RecommendationCandidateType resolveCandidateType(RecommendationTargetMode mode, int algorithm)
   55: 	{
   56: 		if (mode == RecommendationTargetMode.SIMILAR_USERS)
   57: 		{
   58: 			return RecommendationCandidateType.USER;
   59: 		}
   60: 		if (mode == RecommendationTargetMode.FOLLOWEE_ACCOUNTS)
   61: 		{
   62: 			return RecommendationCandidateType.FOLLOWEE;
   63: 		}
   64: 		return nativeCandidateType(algorithm);
   65: 	}
   66: 
```

**GUI sends selected target mode**

File: `TwitterGatherDataFollowers/userRyersonU/ControllerAgentGui.java` (WORKTREE)

```java
 5687: 		ge.addParameter(referenceUser);
 5688: 		ge.addParameter(hashTags);
 5689: 		ge.addParameter(retweets);
 5690: 		ge.addParameter(stopWords);
 5691: 		ge.addParameter(algorithmRec);
 5692: 		ge.addParameter(numFollowees);
 5693: 		ge.addParameter(numFollowers);
 5694: 			ge.addParameter(numTweetsGenerated);
 5695: 			ge.addParameter(getRecommendationTargetMode());
 5696: 			ge.addParameter(isGraphGenerationEnabled());
 5697: 				algorithmSelectionBox.setEnabled(false);
 5698: 				algorithmSettingsButton.setEnabled(false);
 5699: 				setBatchUsersControlEnabled(false);
 5700: 			recommendationTargetModeBox.setEnabled(false);
 5701: 			generateGraphOutput.setEnabled(false);
 5702: 			myAgent.postGuiEvent(ge);
 5703: 
```

## Recommendation Score Payloads And Merge Safety

The old organizer expected raw maps and assumed every recommender would send usable scores. That made failures dangerous: a dead node or malformed score map could cause the system to hang, merge partial results, or display zero-score recommendations as if they were valid.

The new score path introduces explicit result payloads and a `RecommendationScoreMerger`. Recommender agents send structured payloads with target name, candidate type, status, and score map. The organizer can detect failures and stop the run safely before querying users, updating edges, or graphing invalid results.

Effects:

- Nodes can fail closed instead of silently producing invalid recommendations.
- The GUI can show a meaningful failure dialog.
- Batch and multi-node paths can merge scores by target, not just by whichever raw map arrived.
- The original score normalization is still centralized after scores are computed.

Side effects:

- The protocol between recommender and organizer is richer.
- Old raw-map fallback must be handled carefully for compatibility.
- The structured payload makes bugs more visible because the system now refuses invalid partial results instead of continuing.

## After: Structured Merge

**Organizer merge of structured score payloads**

File: `TwitterGatherDataFollowers/userRyersonU/OrganizingAgent.java` (WORKTREE)

```java
  411: 						recommendationScorePayloads.add((RecommendationScorePayload) content);
  412: 					}
  413: 				}
  414: 				catch (UnreadableException e)
  415: 				{
  416: 					System.err.println(getLocalName() + " could not read converted recommendation scores.");
  417: 					e.printStackTrace();
  418: 				}
  419: 
  420: 				if (recMergeCount == allRecAgents.length)
  421: 				{
  422: 					recommendationResults =
  423: 							RecommendationScoreMerger.merge(recommendationScorePayloads);
  424: 					sendMergeCompleted();
  425: 				}
  426: 			}
  427: 
  428: 			if ("Merge Lists".equals(msg.getOntology()))
  429: 			{
  430: 				if (recommendationRunFailed)
  431: 				{
  432: 					return;
  433: 				}
  434: 				
  435: 				recMergeCount++;
```

**Merger normalizes and sorts candidate scores**

File: `TwitterGatherDataFollowers/userRyersonU/RecommendationScoreMerger.java` (WORKTREE)

```java
    5: import java.util.Comparator;
    6: import java.util.LinkedHashMap;
    7: import java.util.LinkedHashSet;
    8: import java.util.List;
    9: import java.util.Map;
   10: import java.util.Set;
   11: import java.util.TreeMap;
   12: 
   13: final class RecommendationScoreMerger
   14: {
   15: 	private RecommendationScoreMerger()
   16: 	{
   17: 	}
   18: 
   19: 	static Map<String,RecommendationResultPayload> merge(List<RecommendationScorePayload> payloads)
   20: 	{
   21: 		LinkedHashMap<String,RecommendationResultPayload> results =
   22: 				new LinkedHashMap<String,RecommendationResultPayload>();
   23: 		if (payloads == null || payloads.isEmpty())
   24: 		{
   25: 			return results;
   26: 		}
   27: 
   28: 		RecommendationScorePayload first = payloads.get(0);
   29: 		Set<String> targets = new LinkedHashSet<String>();
   30: 		for (RecommendationScorePayload payload : payloads)
   31: 		{
   32: 			if (payload != null)
   33: 			{
```

## Graph Generation Toggle

The original workflow always continued into graph visualization/export after recommendations. That was expensive for large datasets and confusing when the user only wanted recommendations. However, simply disabling graph output entirely would have been wrong: edge persistence is part of the experiment state. If the user runs ten users without graph output and then enables graph output on the eleventh, the final graph should include the previous ten recommendation edges.

The implemented behavior separates **edge persistence** from **Gephi visualization/export**:

- When the checkbox is checked, the program persists edges and then builds/displays/exports the graph as before.
- When unchecked, it still records node and edge data, but skips the Gephi visualization/export phase.
- The completion status explicitly says the graph display/export was skipped.

This preserves cumulative graph state while avoiding expensive visualization work when it is not needed.

## After: Graph Output Setting In GUI And Starter

**GUI graph output checkbox**

File: `TwitterGatherDataFollowers/userRyersonU/ControllerAgentGui.java` (WORKTREE)

```java
 3252: 			simulateTweetDelay.addItemListener(new ItemListener()
 3253: 			{
 3254: 				public void itemStateChanged(ItemEvent event)
 3255: 				{
 3256: 					refreshSimulatorHelpPanel();
 3257: 				}
 3258: 			});
 3259: 
 3260: 			generateGraphOutput = new JCheckBox("Generate Final Graph Output");
 3261: 			styleCheckBox(generateGraphOutput);
 3262: 			generateGraphOutput.setSelected(true);
 3263: 			generateGraphOutput.setToolTipText("Node and edge data is always saved. Selected: also build, export, and open the final graph after recommendations.");
 3264: 			generateGraphOutput.addItemListener(new ItemListener()
 3265: 			{
 3266: 				public void itemStateChanged(ItemEvent event)
 3267: 				{
 3268: 					refreshSimulatorHelpPanel();
```

**Starter builds final graph conditionally**

File: `TwitterGatherDataFollowers/userRyersonU/StarterAgent.java` (WORKTREE)

```java
    1: package TwitterGatherDataFollowers.userRyersonU;
    2: 
    3: import java.io.BufferedReader;
    4: import java.io.BufferedWriter;
    5: import java.io.DataInputStream;
    6: import java.io.FileInputStream;
    7: import java.io.FileNotFoundException;
    8: import java.io.FileWriter;
    9: import java.io.IOException;
   10: import java.io.InputStreamReader;
   11: import java.sql.Connection;
   12: import java.sql.DriverManager;
   13: import java.sql.PreparedStatement;
   14: import java.sql.ResultSet;
   15: import java.sql.SQLException;
```

## Recommendation Post-Processing Rewrite

The recommendation list used to appear in the GUI before the run was actually complete. The delay was not graph generation; it was legacy post-processing inside `MobileAgent`. After displaying recommendations, the old code repeatedly scanned the selected dataset, created or rewrote `Dataset-Test-*` files, rebuilt name-number maps, and appended edges. On a million-row dataset this could mean millions of row reads after the recommendation was already visible.

The new `RecommendationPostProcessor` keeps the necessary side effects but removes the waste:

- It writes the selected recommendation result.
- It writes followee-choice output where required.
- It appends the graph edge using existing mapping files.
- It avoids the obsolete full-dataset `Dataset-Test-*` rewrite where the active pipeline does not need it.
- It returns explicit counts/timing so the GUI/status bar can describe completion.

What stays the same:

- Recommendation scores are not recalculated here.
- The weighted random follow-selection behavior is still the same algorithmic side effect.
- Edge persistence remains active even when graph export is disabled.

Side effect:

- Runs finish much faster after visible recommendation output, especially with large datasets.

## Before And After: Post-Processing

**Old Dataset-Test rewrite path**

File: `TwitterGatherDataFollowers/userRyersonU/MobileAgent.java` (origin/main)

```java
  925: 							   }
  926: 							   br.close();
  927: 						}
  928: 						catch(IOException e){
  929: 						 e.printStackTrace();
  930: 					 }  */
  931: 					 
  932: 					 //String filepath="D:/important-stuff/Dataset-Journals-Authors-Titles-August6.txt";
  933: 					 //String filepath="D:/important-stuff/Reduced_94k.txt";
  934: 					 String filepath=importantStuffDirName + selectedFile.getName();
  935: 					 //June 12String filepath="D:/important-stuff/Reduced_14k.txt";
  936: 					 //String filepath="D:/important-stuff/Reduced_57k.txt";
  937: 					 //String tempFile="D:/important-stuff/Dataset-Test-August11.txt";
  938: 					 //String tempFile="D:/important-stuff/Dataset-Test-Oct15.txt";
  939: 					 //String tempFile="D:/important-stuff/Dataset-Test-sepide.txt";
  940: 					 String tempFile=importantStuffDirName +"Dataset-Test-" + selectedFile.getName();
  941: 					 //June 12String tempFile="D:/important-stuff/Dataset-Test-Reduced14k.txt";
  942: 					 //String tempFile="D:/important-stuff/Dataset-Test-Reduced57k.txt";
  943: 					 File oldFile=new File(filepath);
  944: 					 File newFile=new File(tempFile);
  945: 					 String sepFollowee = "";
  946: 					 String sepID = "";
  947: 					 String sepDate = "";
  948: 					 String sepID2 = "";
  949: 					 String sepFollower = "";
```

**Current post-processing call**

File: `TwitterGatherDataFollowers/userRyersonU/MobileAgent.java` (WORKTREE)

```java
  898: 						}
  899: 							else
  900: 							{
  901: 								myGui.appendRecommendationResult(structuredResult, kRecommend);
  902: 							}
  903: 							recCount = 0;
  904: 
  905: 							long postProcessingStartTime = System.currentTimeMillis();
  906: 							File selectedDataset = getConfiguredDatasetFile();
  907: 							String currentGraphEdgesBaseName = selectedDataset == null
  908: 									? null : getDatasetGraphBaseName(selectedDataset.getName());
  909: 							try
  910: 							{
  911: 								RecommendationPostProcessor.Result postProcessingResult =
  912: 										RecommendationPostProcessor.process(
  913: 												new RecommendationPostProcessor.Request(
  914: 														selectedDataset,
  915: 														new File("important-stuff"),
  916: 														currentGraphEdgesBaseName,
  917: 														getCurrentUserName(),
  918: 														followeeName,
  919: 														userScoresText,
  920: 														scoreReceived,
  921: 														kRecommend,
  922: 														algorithmRec == Doc2Vec,
  923: 														r,
  924: 														recommendationDatasetContext));
  925: 								int persistedSelectionBoxIndex =
  926: 										postProcessingResult.getSelectedRecommendationIndex() + 1;
```

**Current RecommendationPostProcessor**

File: `TwitterGatherDataFollowers/userRyersonU/RecommendationPostProcessor.java` (WORKTREE)

```java
   21: import java.util.Map;
   22: import java.util.Random;
   23: import java.util.Set;
   24: 
   25: final class RecommendationPostProcessor
   26: {
   27: 	private static final int LEGACY_RESULT_LIMIT = 5;
   28: 	private static final Object MAPPING_LOCK = new Object();
   29: 	private static final Object EDGE_WRITE_LOCK = new Object();
   30: 	private static final Map<String, MappingCacheEntry> MAPPING_CACHE =
   31: 			new HashMap<String, MappingCacheEntry>();
   32: 
   33: 	private RecommendationPostProcessor()
   34: 	{
   35: 	}
   36: 
   37: 	static Result process(Request request) throws IOException
   38: 	{
   39: 		if (request == null)
   40: 		{
   41: 			throw new IllegalArgumentException("request must not be null");
   42: 		}
   43: 		if (request.scores == null)
   44: 		{
   45: 			throw new IllegalArgumentException("scores must not be null");
   46: 		}
   47: 		if (request.random == null)
   48: 		{
   49: 			throw new IllegalArgumentException("random must not be null");
   50: 		}
   51: 
   52: 		ensureDirectory(request.importantStuffDirectory);
   53: 
```

## Idle CPU And Cleanup Fixes

The simulator previously consumed very high CPU even after apparent completion. The root causes were idle JADE behaviors that polled instead of blocking, recommendation query handlers that kept cycling, and Gephi projects that were not closed after export. A completion popup also delayed cleanup in some cases.

Changes made:

- Replaced active polling in user-agent/recommender paths with blocking JADE message delivery where possible.
- Added run lifecycle guards so stale messages from older runs are ignored instead of continuously processed.
- Added explicit stop/failure states so the node dashboard and organizer do not wait forever for dead agents.
- Made completion popup asynchronous so cleanup can finish immediately.
- Closed embedded Gephi project after graph export.

Effect:

- Idle CPU usage should drop dramatically after a run.
- Long-running app windows should no longer burn CPU just because agents are still alive.
- A failed run is more likely to stop visibly instead of spinning.

## After: Run Lifecycle Guards

**MobileAgent run lifecycle admission**

File: `TwitterGatherDataFollowers/userRyersonU/MobileAgent.java` (WORKTREE)

```java
  550: 						canQuery = false;
  551: 						System.out.println(this.getAgent().getLocalName()+" Denied Querying\tcanQuery = "+canQuery);
  552: 					}
  553: 				}
  554: 
  555: 				//Message from starter agent
  556: 				if (msg!=null
  557: 						&& "Start SIM".equals(msg.getOntology())
  558: 						&& msg.getPerformative() == ACLMessage.REQUEST)
  559: 				{
  560: 					RecommendationRunLifecycle.Admission admission =
  561: 							runLifecycle.admit(msg.getConversationId());
  562: 					if (admission != RecommendationRunLifecycle.Admission.NEW_RUN)
  563: 					{
  564: 						return;
  565: 					}
  566: 					conversationIDReceived = msg.getConversationId();
  567: 					requestnumber = 0;
  568: 					canQuery = !queryDenied;
  569: 
  570: 					beginTweetTime = System.currentTimeMillis();
```

**Run abort registry**

File: `TwitterGatherDataFollowers/userRyersonU/RecommendationRunAbortRegistry.java` (WORKTREE)

```java
   21: 	static void prepare(String runId)
   22: 	{
   23: 		if (runId == null)
   24: 		{
   25: 			return;
   26: 		}
   27: 		synchronized (LOCK)
   28: 		{
   29: 			ABORTED_RUNS.remove(runId);
   30: 		}
   31: 	}
   32: 
   33: 	static void abort(String runId, String reason)
   34: 	{
   35: 		if (runId == null)
   36: 		{
   37: 			return;
   38: 		}
   39: 		synchronized (LOCK)
   40: 		{
   41: 			if (!ABORTED_RUNS.containsKey(runId))
   42: 			{
   43: 				ABORTED_RUNS.put(
   44: 						runId,
   45: 						reason == null ? "Recommendation run aborted." : reason);
```

## Node Execution Dashboard

The original live output was mostly a text terminal. It did not show whether each recommender node was receiving data, building features, waiting for memory, training a model, publishing scores, complete, failed, or stopped. That made multi-node behavior hard to understand and made stuck/failure diagnosis dependent on terminal logs.

The new `NodeExecutionMonitor` is a centralized in-process state tracker. Recommender agents and controller/starter logic update it as the run moves through phases. `NodeExecutionDashboard` renders that state in the GUI with node cards, progress bars, status colors, and elapsed time.

Tracked states include provisioning, receiving records, preprocessing, waiting for peers, building features, waiting for shared model, waiting for memory, model running, publishing, complete, failed, and stopped.

Side effects:

- This is observational state; it should not change scores.
- It makes bugs visible earlier because failures get marked instead of staying invisible in the terminal.
- It adds GUI repaint work, but no model work.

## After: Node Monitor State Model

**NodeExecutionMonitor states and beginExperiment**

File: `TwitterGatherDataFollowers/userRyersonU/NodeExecutionMonitor.java` (WORKTREE)

```java
    1: package TwitterGatherDataFollowers.userRyersonU;
    2: 
    3: import java.util.ArrayList;
    4: import java.util.Collections;
    5: import java.util.Comparator;
    6: import java.util.LinkedHashMap;
    7: import java.util.List;
    8: import java.util.Map;
    9: import java.util.concurrent.CopyOnWriteArrayList;
   10: 
   11: /**
   12:  * Read-only telemetry for recommender-node execution. It never participates in
   13:  * JADE messaging or recommendation decisions.
   14:  */
   15: final class NodeExecutionMonitor
   16: {
   17: 	enum State
   18: 	{
   19: 		PROVISIONING,
   20: 			READY,
   21: 			RECEIVING,
   22: 			WAITING_FOR_INPUT,
   23: 			PREPROCESSING,
   24: 		WAITING_FOR_PEERS,
   25: 		FEATURES,
   26: 		WAITING_FOR_MEMORY,
   27: 		WAITING_FOR_SHARED_MODEL,
   28: 		MODEL_RUNNING,
   29: 		PUBLISHING,
   30: 		COMPLETE,
   31: 		FAILED,
   32: 		STOPPED
   33: 	}
   34: 
   35: 	interface Listener
```

**Dashboard receives snapshots**

File: `TwitterGatherDataFollowers/userRyersonU/NodeExecutionDashboard.java` (WORKTREE)

```java
   19: 
   20: import javax.swing.BorderFactory;
   21: import javax.swing.Box;
   22: import javax.swing.BoxLayout;
   23: import javax.swing.JLabel;
   24: import javax.swing.JPanel;
   25: import javax.swing.JProgressBar;
   26: import javax.swing.JScrollPane;
   27: import javax.swing.ScrollPaneConstants;
   28: import javax.swing.SwingConstants;
   29: import javax.swing.SwingUtilities;
   30: import javax.swing.Timer;
   31: 
   32: final class NodeExecutionDashboard extends JPanel
   33: 		implements NodeExecutionMonitor.Listener
   34: {
   35: 	private static final Color BACKGROUND = new Color(246, 249, 252);
   36: 	private static final Color SURFACE = Color.WHITE;
   37: 	private static final Color BORDER = new Color(211, 220, 231);
   38: 	private static final Color TEXT = new Color(24, 29, 36);
   39: 	private static final Color MUTED = new Color(91, 101, 116);
   40: 	private static final Color BLUE = new Color(0, 113, 227);
   41: 	private static final Color GREEN = new Color(20, 150, 105);
   42: 	private static final Color AMBER = new Color(207, 125, 31);
   43: 	private static final Color RED = new Color(211, 47, 47);
   44: 	private static final Color PURPLE = new Color(121, 78, 197);
   45: 
   46: 	private final JLabel contextLabel =
   47: 			new JLabel("No node experiment configured");
```

## Runtime Resource Controls And Safe-Wave Scheduling

Large datasets exposed a memory flaw: even if later model stages could be scheduled safely, early dataset ingestion and feature construction could still create too much in-memory state before the scheduler had a chance to intervene. This was especially obvious for SVM, K-Means, MLP, and journal/retail/generated million-row corpora.

The fix has two parts.

First, the GUI now has runtime heap controls. `RuntimeResourceSettings` estimates whether a selected dataset and algorithm can safely start with the current heap. If not, the GUI refuses to start the dangerous run and asks the user to apply the recommended heap and restart. This is deliberately different from wave scheduling: some early ingestion stages cannot be wave-scheduled because all recommender agents must receive their text before the algorithm phase can begin.

Second, safe-wave scheduling remains for later memory-intensive model phases where it is correct to run a subset of nodes at a time. `RecommendationMemoryCoordinator` estimates model reservations, grants leases, and makes extra nodes wait rather than allowing all nodes to allocate at once.

The important behavior now is:

- Unsafe early ingestion is blocked before creating agents.
- Later model phases can run in waves when the selected heap cannot hold all active models.
- Failed leases are released during cleanup so a failed run does not poison the next run.
- The GUI reports heap, model budget, system memory, and adaptive parallelism.

Side effects:

- The program may refuse to run with too-small heap settings instead of trying and crashing.
- Multi-node can still be parallel when the memory budget allows it.
- When the dataset is too large for even one safe worker, the program fails early instead of pretending it can schedule around the problem.

## After: Heap And Scheduler Code

**RuntimeResourceSettings estimates heap**

File: `TwitterGatherDataFollowers/userRyersonU/RuntimeResourceSettings.java` (WORKTREE)

```java
   87: 		{
   88: 			recommended = 2048L;
   89: 		}
   90: 		else if (sizeMb >= 20L)
   91: 		{
   92: 			recommended = 1024L;
   93: 		}
   94: 		else
   95: 		{
   96: 			recommended = MIN_HEAP_MB;
   97: 		}
   98: 		return roundHeapMb(Math.max(MIN_HEAP_MB, recommended));
   99: 	}
  100: 
  101: 	static int recommendedHeapForTextDatasetAndAlgorithmMb(
  102: 			File datasetFile,
  103: 			String algorithmName)
  104: 	{
  105: 		int recommended = recommendedHeapForTextDatasetMb(datasetFile);
  106: 		if (datasetFile == null || !datasetFile.isFile())
  107: 		{
  108: 			return recommended;
  109: 		}
  110: 		long sizeMb = Math.max(
  111: 				1L,
  112: 				(datasetFile.length() + RecommendationMemoryCoordinator.MEBIBYTE - 1L)
  113: 						/ RecommendationMemoryCoordinator.MEBIBYTE);
  114: 		String algorithm = algorithmName == null
  115: 				? "" : algorithmName.toLowerCase();
```

**GUI preflight before Get Users/Initialize**

File: `TwitterGatherDataFollowers/userRyersonU/ControllerAgentGui.java` (WORKTREE)

```java
 5383: 					requiredHeapMb,
 5384: 					"Applying the recommended heap before loading users.");
 5385: 		}
 5386: 		else
 5387: 		{
 5388: 			setPerformanceStatus(
 5389: 					"WAITING",
 5390: 					"Increase Java heap before loading this dataset.",
 5391: 					StatusTone.WARNING);
 5392: 			getUsersButton.setEnabled(true);
 5393: 		}
 5394: 		return false;
 5395: 	}
 5396: 
 5397: 	private boolean ensureHeapForInitialization()
 5398: 	{
 5399: 		if (selectedSourceMode != FROM_TEXT
 5400: 				|| fileChooser == null
 5401: 				|| fileChooser.getSelectedFile() == null
 5402: 				|| !fileChooser.getSelectedFile().isFile())
 5403: 		{
 5404: 			return true;
 5405: 		}
 5406: 
 5407: 		File selectedDataset = fileChooser.getSelectedFile();
 5408: 		Object selectedAlgorithm = algorithmSelectionBox == null
 5409: 				? null : algorithmSelectionBox.getSelectedItem();
 5410: 		String algorithmName = selectedAlgorithm == null
 5411: 				? "selected algorithm" : selectedAlgorithm.toString();
```

**Memory coordinator lease**

File: `TwitterGatherDataFollowers/userRyersonU/RecommendationMemoryCoordinator.java` (WORKTREE)

```java
   55: 	{
   56: 		for (Workload workload : Workload.values())
   57: 		{
   58: 			ACTIVE_BY_WORKLOAD.put(workload, Integer.valueOf(0));
   59: 		}
   60: 		System.out.println(
   61: 				"Adaptive model scheduler: maxHeap="
   62: 				+toMegabytes(MAX_HEAP_BYTES)+" MB modelBudget="
   63: 				+toMegabytes(MODEL_BUDGET_BYTES)+" MB parallelLimit="
   64: 				+CONFIGURED_PARALLEL_LIMIT);
   65: 	}
   66: 
   67: 	private RecommendationMemoryCoordinator()
   68: 	{
   69: 	}
   70: 
   71: 	static Lease acquire(String owner, String stage)
   72: 	{
   73: 		return acquire(owner, stage, Workload.GENERIC, 160L * MEBIBYTE);
   74: 	}
   75: 
   76: 	static Lease acquire(
   77: 			String owner,
   78: 			String stage,
   79: 			Workload workload,
   80: 			long estimatedBytes)
   81: 	{
   82: 		long reservation = normalizeReservation(estimatedBytes);
   83: 		long waitStart = System.nanoTime();
   84: 		boolean interrupted = false;
   85: 
   86: 		LOCK.lock();
   87: 		try
```

## Apply And Restart Relaunch Flow

Changing the Java heap from inside the GUI previously could leave the terminal running but the GUI missing, because the relaunch path reconstructed raw Java commands and did not reliably return through the managed launcher. The fix moved restart responsibility back into `build.py launch`.

Current behavior:

- Java saves the requested heap into `.simulator-runtime.properties`.
- Java exits with a managed restart code.
- `build.py launch` sees the restart code and launches again with the saved heap.
- The GUI explicitly restores and brings the window forward after launch.

This keeps restart behavior consistent with normal startup and avoids duplicating bootstrap logic in Java.

## Before And After: Launcher

**Old build.py version gate and launcher assumptions**

File: `build.py` (origin/main)

```python
   45: # --- Configuration ---
   46: PROJECT_ROOT = os.path.dirname(os.path.abspath(__file__))
   47: VENV_DIR = os.path.join(PROJECT_ROOT, ".venv")
   48: SRC_DIR = os.path.join(PROJECT_ROOT, "TwitterGatherDataFollowers", "userRyersonU")
   49: LIB_DIR = os.path.join(PROJECT_ROOT, "lib")
   50: CLASSES_DIR = os.path.join(PROJECT_ROOT, "classes")
   51: REQUIREMENTS_FILE = os.path.join(PROJECT_ROOT, "requirements.txt")
   52: POM_FILE = os.path.join(PROJECT_ROOT, "pom.xml")
   53: 
   54: JAVA_MAIN_CLASS = "jade.Boot"
   55: JAVA_MAIN_AGENT = "controller:TwitterGatherDataFollowers.userRyersonU.ControllerAgent"
   56: MIN_JAVA_VERSION = 8
   57: ALLOWED_PYTHON_VERSIONS = [(3, 9), (3, 10)]
   58: ALLOWED_PYTHON_VERSIONS_STR = " or ".join([".".join(map(str, v)) for v in ALLOWED_PYTHON_VERSIONS])
   59: 
   60: # --- Global State ---
   61: VERBOSE = False
   62: 
   63: # --- Helper Functions ---
   64: 
   65: def pinfo(message):
   66:     """Prints an informational message."""
   67:     print(f"[INFO] {message}")
   68: 
   69: def pwarn(message):
```

**Current restart-aware launcher**

File: `build.py` (WORKTREE)

```python
   38: LOG_DIR = os.path.join(PROJECT_ROOT, "logs")
   39: REQUIREMENTS_FILE = os.path.join(PROJECT_ROOT, "requirements.txt")
   40: LOCKED_REQUIREMENTS_FILE = os.path.join(PROJECT_ROOT, "requirements-lock.txt")
   41: DOC2VEC_PREFLIGHT = os.path.join(SRC_DIR, "doc2vec_preflight.py")
   42: RUNTIME_PROPERTIES = os.path.join(PROJECT_ROOT, ".simulator-runtime.properties")
   43: 
   44: JAVA_MAIN_CLASS = "jade.Boot"
   45: JAVA_MAIN_AGENT = "controller:TwitterGatherDataFollowers.userRyersonU.ControllerAgent"
   46: MIN_JAVA_VERSION = 8
   47: MIN_PYTHON_VERSION = (3, 9)
   48: SUPPORTED_PYTHON_VERSION_STR = "3.9 or newer"
   49: DEFAULT_HEAP_MB = 1424
   50: DEFAULT_JADE_PORT = 35240
   51: DEFAULT_DF_MAXRESULT = 100000
   52: RESTART_EXIT_CODE = 42
   53: 
   54: REQUIRED_JARS = {
   55:     "jade.jar": ["jade/Boot.class"],
   56:     "weka.jar": ["weka/classifiers/functions/SMO.class"],
   57:     "gephi-toolkit.jar": ["org/gephi/project/api/ProjectController.class"],
   58:     "neuroph-core-2.94.jar": ["org/neuroph/core/NeuralNetwork.class"],
   59:     "neuroph-contrib-2.94.jar": [],
   60:     "pdfbox-app-2.0.31.jar": ["org/apache/pdfbox/pdmodel/PDDocument.class"],
   61:     "opencsv-5.7.1.jar": ["com/opencsv/CSVReader.class"],
   62:     "mysql-connector-java-5.1.39-bin.jar": ["com/mysql/jdbc/Driver.class"],
   63:     "twitter4j-core-4.0.4.jar": ["twitter4j/Twitter.class"],
   64:     "libsvm.jar": [],
   65:     "LibSVM-1.0.10.jar": [],
   66:     "commons-math3-3.6.1.jar": [],
```

**Current restart handling**

File: `build.py` (WORKTREE)

```python
  588:     install()
  589:     compile_java(force=False)
  590:     run()
  591: 
  592: 
  593: def run():
  594:     java_major, _, env = preflight(require_classes=True)
  595:     restart_count = 0
  596:     while True:
  597:         log_file = startup_log_path()
  598:         java_opts, jade_opts = java_runtime_opts(java_major, log_file)
  599:         command = ["java"] + java_opts + ["-cp", runtime_classpath(), JAVA_MAIN_CLASS] + jade_opts + [JAVA_MAIN_AGENT]
  600:         pinfo("Starting DSMP simulator...")
  601:         code = run_java_process(command, env, log_file)
  602:         if code == RESTART_EXIT_CODE:
  603:             restart_count += 1
  604:             pinfo(
  605:                 "Simulator requested a managed restart. "
  606:                 "Relaunching with the saved runtime settings..."
  607:             )
  608:             time.sleep(0.75)
  609:             continue
  610:         if code == 0:
  611:             if restart_count:
  612:                 pinfo(f"DSMP simulator exited normally after {restart_count} managed restart(s).")
  613:             else:
  614:                 pinfo("DSMP simulator exited normally.")
  615:         else:
  616:             pwarn(f"DSMP simulator exited with code {code}. See {log_file}")
```

## Exact SVM Worker Isolation

The old SVM path trained Weka SMO models inside the main JADE JVM. On large high-cardinality datasets, every recommender node could build large ARFF structures and Weka model state in the same heap. This made heap exhaustion likely and made a failing node capable of poisoning the whole run.

The exact SVM work isolates Weka SMO model work into child JVMs. The main simulator prepares ARFF/corpus structures, then `ExactSvmWorkerRunner` launches `ExactSvmWorkerMain` with an estimated heap. `ExactSvmWorkerCoordinator` manages child-worker memory reservations so too many Weka workers do not run together. Heap-exhaustion diagnostics are detected and can trigger a larger retry when safe.

Important distinction:

- This keeps the exact Weka SMO algorithm for SVM.
- It changes process isolation and scheduling, not SVM scoring math.
- If the dataset is too large for one worker even after safe heap estimation, the run fails closed before query/edge/graph side effects.

Side effects:

- Child-process startup adds overhead on small datasets.
- Large datasets become more reliable because Weka is no longer sharing the main GUI/JADE heap.
- The scheduler’s estimate must stay conservative because underestimating Weka memory still causes worker OOM.

## After: Exact SVM Worker Runner

**Worker heap validation**

File: `TwitterGatherDataFollowers/userRyersonU/ExactSvmWorkerRunner.java` (WORKTREE)

```java
    9: 
   10: /**
   11:  * Performs exact-SVM preflight and launches the unchanged model in a bounded
   12:  * child JVM. A worker crash therefore cannot consume or corrupt the JADE JVM.
   13:  */
   14: final class ExactSvmWorkerRunner
   15: {
   16: 	private static final long MEBIBYTE = RecommendationMemoryCoordinator.MEBIBYTE;
   17: 	private static final long DEFAULT_MAX_WORKER_MB = calculateDefaultMaxWorkerMb();
   18: 
   19: 	private ExactSvmWorkerRunner()
   20: 	{
   21: 	}
   22: 
   23: 	static void validateBestCaseStructure(
   24: 			int totalInstanceCount,
   25: 			int totalClassCount,
   26: 			int requestedNodes)
   27: 	{
   28: 		if (totalClassCount < 2)
   29: 		{
   30: 			throw new IllegalArgumentException(
   31: 					"Exact SVM needs at least two followee classes.");
   32: 		}
   33: 		int nodes = Math.max(1, requestedNodes);
   34: 		int bestCaseClassesPerNode =
   35: 				(totalClassCount + nodes - 1) / nodes;
   36: 		int bestCaseInstancesPerNode =
   37: 				(totalInstanceCount + nodes - 1) / nodes;
```

**Child process launch and retry**

File: `TwitterGatherDataFollowers/userRyersonU/ExactSvmWorkerRunner.java` (WORKTREE)

```java
  133: 										workerHeapMb,
  134: 										settings),
  135: 								ModelWorkerProcessSupport.configuredTimeoutSeconds());
  136: 				}
  137: 
  138: 				if (processResult.isTimedOut())
  139: 				{
  140: 					throw new IllegalStateException(
  141: 							"Exact SVM worker exceeded the configured containment "
  142: 							+ "timeout and was stopped.");
  143: 				}
  144: 				if (processResult.getExitCode() == 0
  145: 						&& resultFile.isFile()
  146: 						&& resultFile.length() > 0L)
  147: 				{
  148: 					return readResult(resultFile);
  149: 				}
  150: 
  151: 				if (isHeapExhaustionDiagnostic(processResult.getOutput())
  152: 						&& heapRetries < configuredMaxHeapRetries()
  153: 						&& workerHeapMb < maximumWorkerHeapMb)
  154: 				{
  155: 					long nextHeapMb = nextRetryHeapMb(
  156: 							workerHeapMb, maximumWorkerHeapMb);
  157: 					if (nextHeapMb > workerHeapMb)
  158: 					{
  159: 						heapRetries++;
  160: 						String retryMessage = owner
  161: 								+ " exact SVM worker exhausted "
  162: 								+ RuntimeResourceSettings.formatMegabytes(workerHeapMb)
  163: 								+ "; retrying with "
  164: 								+ RuntimeResourceSettings.formatMegabytes(nextHeapMb)
  165: 								+ " (" + heapRetries + "/"
  166: 								+ configuredMaxHeapRetries() + ").";
  167: 						System.err.println(retryMessage);
  168: 						NodeExecutionMonitor.waitingForMemory(
  169: 								owner,
```

**Worker main invokes Weka support**

File: `TwitterGatherDataFollowers/userRyersonU/ExactSvmWorkerMain.java` (WORKTREE)

```java
   18: 		if (args.length != 4)
   19: 		{
   20: 			System.err.println(
   21: 					"Usage: ExactSvmWorkerMain <train.arff> <test.arff> "
   22: 					+ "<recommend.arff> <result.bin>");
   23: 			System.exit(2);
   24: 		}
   25: 
   26: 		try
   27: 		{
   28: 			ExactSvmWorkerResult result = ExactSvmModelSupport.trainAndScore(
   29: 					new File(args[0]),
   30: 					new File(args[1]),
   31: 					new File(args[2]));
   32: 			try (ObjectOutputStream output = new ObjectOutputStream(
   33: 					new FileOutputStream(args[3])))
   34: 			{
   35: 				output.writeObject(result);
   36: 			}
   37: 		}
   38: 		catch (Throwable failure)
```

## Scalable Linear SVM Mode

A separate algorithm option was added for scalable linear SVM. This is not the same as the exact Weka SMO SVM. The exact SVM remains the Weka SMO path. The scalable path uses a custom sparse linear model intended for high-dimensional datasets where Weka SMO is impractical.

The reason to keep it as a separate mode is academic honesty: it can be faster and more memory-friendly, but it is a different algorithm and should not be described as “the same SVM.” It exists to test large corpora while preserving the exact SVM option for Weka-based comparison.

Important caveat:

- The exact SVM and scalable linear SVM should not be compared as identical algorithms.
- The exact multi-node lab can verify equivalence where supported, but if a mode cannot guarantee one-node equivalence, it must fail closed or be clearly labeled.

## After: Scalable SVM Code

**Scalable SVM parameters**

File: `TwitterGatherDataFollowers/userRyersonU/AlgorithmParameterSettings.java` (WORKTREE)

```java
  348: 						+ effective.getSimilarityLocalTopMinimum();
  349: 			case RecommendationModeSupport.K_MEANS:
  350: 			case RecommendationModeSupport.K_MEANS_EUCLIDEAN:
  351: 				return effective.getKMeansClusters() + " clusters, "
  352: 						+ effective.getKMeansMaxIterations() + " max iterations";
  353: 			case RecommendationModeSupport.SVM:
  354: 				return "C=" + formatDouble(effective.getSvmC())
  355: 						+ ", calibration "
  356: 						+ (effective.isSvmCalibrationEnabled() ? "on" : "off");
  357: 			case RecommendationModeSupport.SCALABLE_SVM:
  358: 				return effective.getScalableSvmEpochs() + " epochs, aggressiveness "
  359: 						+ formatDouble(effective.getScalableSvmAggressiveness());
  360: 			case RecommendationModeSupport.MLP:
  361: 				return effective.getMlpHiddenNeurons() + " hidden neurons, rate "
  362: 						+ formatDouble(effective.getMlpLearningRate())
  363: 						+ ", max error " + formatDouble(effective.getMlpMaxError());
  364: 			case RecommendationModeSupport.DOC2VEC:
  365: 				return effective.getDoc2VecVectorSize() + " dimensions, "
  366: 						+ effective.getDoc2VecEpochs() + " epochs, window "
  367: 						+ effective.getDoc2VecWindow();
  368: 			default:
```

**Sparse model support**

File: `TwitterGatherDataFollowers/userRyersonU/ScalableSvmModelSupport.java` (WORKTREE)

```java
  316: 				}
  317: 			}
  318: 
  319: 			long weightValues = (long)featureCount * activeClassCount;
  320: 			if (weightValues > MAX_FLAT_WEIGHT_VALUES)
  321: 			{
  322: 				throw new IllegalArgumentException(
  323: 						"Scalable SVM requires too many feature/class weights: "
  324: 						+ String.format(java.util.Locale.US, "%,d", weightValues)
  325: 						+ ". Use more recommender nodes.");
  326: 			}
  327: 			weights = new float[(int)weightValues];
  328: 			biases = new float[activeClassCount];
  329: 			aggressiveness = configuredAggressiveness();
  330: 		}
  331: 
  332: 		private void train(File trainingFile, int epochs) throws Exception
  333: 		{
  334: 			for (int epoch = 0; epoch < epochs; epoch++)
  335: 			{
  336: 				final double[] scores = new double[activeClassCount];
  337: 				forEachInstance(trainingFile, structure, new InstanceConsumer()
  338: 				{
  339: 					public void accept(Instance instance)
  340: 					{
  341: 						if (!instance.classIsMissing())
  342: 						{
  343: 							update(instance, scores);
  344: 						}
  345: 					}
  346: 				});
  347: 			}
  348: 		}
```

## Exact Multi-Node Population Lab

The normal simulator batch mode and the exact multi-node lab solve different UI/experiment problems.

Normal simulator batch mode follows the original JADE workflow. It creates the selected recommender nodes, sends the corpus through the simulator path, and each node contributes score maps for the selected batch users. It still has graph/recommendation side effects because it is the real simulator run.

The exact multi-node lab is separate. It is designed as a controlled benchmark that assigns complete target-user jobs across workers: for example, with 8 workers and 618 targets, worker 1 receives a group of complete users, worker 2 receives another group, and so on. No target user is split between workers. The lab then compares the multi-worker results against a one-worker baseline and reports timing, speedup, efficiency, and fingerprint equality.

Why it was added:

- It visibly demonstrates multi-node parallelism.
- It avoids changing the original simulator path.
- It gives correctness evidence by verifying against a one-worker run.
- It avoids graph side effects so repeated benchmarks do not contaminate simulator graph files.

Supported algorithms are limited to those where this exact assignment can preserve correctness. Algorithms whose current multi-node mathematics cannot guarantee one-worker equivalence are excluded from the lab rather than shown misleadingly.

## After: Exact Population Sweep

**Supported algorithm list**

File: `TwitterGatherDataFollowers/userRyersonU/ExactPopulationSweepEngine.java` (WORKTREE)

```java
  527: 			if (preparedSvm != null)
  528: 			{
  529: 				preparedSvm.close();
  530: 			}
  531: 			if (schedulerChanged)
  532: 			{
  533: 				RecommendationMemoryCoordinator.configureRequestedParallelism(
  534: 						previousSchedulerParallelism);
  535: 			}
  536: 		}
  537: 	}
  538: 
  539: 	static List<String> supportedAlgorithmNames()
  540: 	{
  541: 		ArrayList<String> names = new ArrayList<String>();
  542: 		for (Algorithm algorithm : Algorithm.values())
  543: 		{
  544: 			names.add(algorithm.toString());
  545: 		}
  546: 		return names;
  547: 	}
  548: 
  549: 	private static void validateConfig(Config config)
  550: 	{
  551: 		if (config == null)
```

**Doc2Vec exact lab worker call**

File: `TwitterGatherDataFollowers/userRyersonU/ExactPopulationSweepEngine.java` (WORKTREE)

```java
  875: 						if (!baseline)
  876: 						{
  877: 							listener.onWorker(
  878: 									workerIndex,
  879: 									WorkerState.READY,
  880: 									null,
  881: 									0,
  882: 									assignedTargets.size(),
  883: 									0L,
  884: 									"Loading the shared Doc2Vec scoring index.");
  885: 						}
  886: 						token.throwIfCancelled();
  887: 						Doc2VecBatchRunner.Result batch = Doc2VecBatchRunner.run(
  888: 								config.pythonCommand,
  889: 								doc2VecScript(),
  890: 								config.topResults,
  891: 								config.datasetFile,
  892: 								1,
  893: 								1,
  894: 									doc2VecTrainingWorkers(),
  895: 									assignedTargets,
  896: 									config.algorithmSettings,
  897: 									new Doc2VecBatchRunner.ProgressListener()
  898: 								{
  899: 									public void onProgress(String event, String detail)
```

**Population panel metrics**

File: `TwitterGatherDataFollowers/userRyersonU/PopulationSweepPanel.java` (WORKTREE)

```java
  401: 
  402: 		doc2VecCachePanel.add(textStack, BorderLayout.CENTER);
  403: 		doc2VecCachePanel.add(actions, BorderLayout.EAST);
  404: 		applyDoc2VecCacheMessage(
  405: 				"CACHE UNKNOWN",
  406: 				"Choose a dataset to inspect whether training will run or be skipped.",
  407: 				MUTED);
  408: 		return doc2VecCachePanel;
  409: 	}
  410: 
  411: 	private JComponent createWorkArea()
  412: 	{
  413: 		JPanel left = new JPanel(new BorderLayout(0, 12));
  414: 		left.setOpaque(false);
  415: 		left.add(createMetricsPanel(), BorderLayout.NORTH);
  416: 		left.add(createWorkersPanel(), BorderLayout.CENTER);
  417: 
  418: 		JPanel right = new JPanel(new BorderLayout(0, 12));
  419: 		right.setOpaque(false);
  420: 		right.add(createResultsPanel(), BorderLayout.CENTER);
  421: 		right.add(createLimitationsPanel(), BorderLayout.SOUTH);
  422: 
  423: 		JSplitPane split = new JSplitPane(
  424: 				JSplitPane.HORIZONTAL_SPLIT, left, right);
  425: 		split.setBorder(null);
  426: 		split.setOpaque(false);
  427: 		split.setDividerSize(8);
  428: 		split.setResizeWeight(0.63);
  429: 		SwingUtilities.invokeLater(new Runnable()
```

## Streaming Dataset And Corpus Transport

Large datasets exposed another structural problem: sending or storing all rows in memory at once made the JVM vulnerable before any model was trained. The streaming work introduced replayable on-disk sources and batch delivery ledgers.

New classes include:

- `DatasetRow`: strict DSMP six-column parsing.
- `ReplayableTweetBatchSource`: replayable tweet batches for mobile/user agents.
- `ReplayableCorpusStream`: route-based corpus streaming for recommender agents.
- `CorpusStreamingAgent`: a JADE agent responsible for controlled corpus replay.
- `TweetBatchDeliveryLedger`: deduplication/ack tracking for batched delivery.

Effects:

- The program can process larger datasets without duplicating all text in memory at every layer.
- Message delivery can be bounded and acknowledged.
- Failed or duplicate batches can be detected.
- Scoring logic downstream is preserved because the same `Tweet`/document content eventually reaches the existing algorithm paths.

## After: Streaming Classes

**DatasetRow parser**

File: `TwitterGatherDataFollowers/userRyersonU/DatasetRow.java` (WORKTREE)

```java
   16: 			long userId,
   17: 			String userName,
   18: 			String text)
   19: 	{
   20: 		this.referenceUser = referenceUser;
   21: 		this.tweetId = tweetId;
   22: 		this.tweetDate = tweetDate;
   23: 		this.userId = userId;
   24: 		this.userName = userName;
   25: 		this.text = text;
   26: 	}
   27: 
   28: 	static DatasetRow parse(String line, long lineNumber)
   29: 	{
   30: 		if (lineNumber == 1 && line.startsWith("\uFEFF"))
   31: 		{
   32: 			line = line.substring(1);
   33: 		}
   34: 
   35: 		String[] fields = line.split("\t", 6);
   36: 		if (fields.length != 6)
   37: 		{
   38: 			throw new IllegalArgumentException(
   39: 					"Line " + lineNumber + " must contain exactly six tab-separated fields.");
   40: 		}
```

**ReplayableTweetBatchSource**

File: `TwitterGatherDataFollowers/userRyersonU/ReplayableTweetBatchSource.java` (WORKTREE)

```java
   22: {
   23: 	private static final int BUFFER_BYTES = 64 * 1024;
   24: 	private static final int MAX_RECORD_BYTES = 16 * 1024 * 1024;
   25: 
   26: 	private final File file;
   27: 	private final int rowCount;
   28: 	private boolean closed;
   29: 
   30: 	private ReplayableTweetBatchSource(File file, int rowCount)
   31: 	{
   32: 		this.file = file;
   33: 		this.rowCount = rowCount;
   34: 	}
   35: 
   36: 	static ReplayableTweetBatchSource create(
   37: 			List<Tweet> tweets,
   38: 			int totalTweetCount,
   39: 			String followeeName) throws IOException
   40: 	{
   41: 		File spoolFile = Files.createTempFile(
   42: 				"dsmp-mobile-tweets-", ".bin").toFile();
   43: 		boolean success = false;
   44: 		int written = 0;
   45: 		try (DataOutputStream output = new DataOutputStream(
   46: 				new BufferedOutputStream(
   47: 						new FileOutputStream(spoolFile), BUFFER_BYTES)))
   48: 		{
   49: 			for (int index = tweets.size() - 1; index >= 0; index--)
   50: 			{
```

**CorpusStreamingAgent starts replay**

File: `TwitterGatherDataFollowers/userRyersonU/CorpusStreamingAgent.java` (WORKTREE)

```java
   94: 				ACLMessage message = myAgent.receive();
   95: 				if (message == null)
   96: 				{
   97: 					block();
   98: 					return;
   99: 				}
  100: 
  101: 				if ("Start SIM".equals(message.getOntology())
  102: 						&& message.getPerformative() == ACLMessage.REQUEST)
  103: 				{
  104: 					RecommendationRunLifecycle.Admission admission =
  105: 							runLifecycle.admit(message.getConversationId());
  106: 					if (admission == RecommendationRunLifecycle.Admission.NEW_RUN)
  107: 					{
  108: 						startReplay(message.getConversationId());
  109: 					}
  110: 					return;
  111: 				}
  112: 
  113: 				if (TWEET_BATCH_ACK_ONTOLOGY.equals(message.getOntology())
  114: 						&& runLifecycle.isActive(message.getConversationId()))
  115: 				{
  116: 					acknowledgeBatch(message);
  117: 				}
  118: 			}
  119: 		});
  120: 		addBehaviour(new TickerBehaviour(
  121: 				this, Math.min(5000L, ACK_TIMEOUT_MILLIS))
  122: 		{
```

## User Generation Engine

The User Generation Studio previously could not reliably generate very large synthetic datasets. The old logic was concentrated in `UserGenSimAgent`, mixed GUI/agent behavior with generation logic, and was not structured for million-row validation, vocabulary control, or reproducible output checks.

The new `UserGenerationEngine` extracts generation into a reusable engine. It streams output, tracks generated users/rows/vocabulary, preserves cluster/followee structure, reports warnings, and returns a structured result for the GUI to display.

Effects:

- Million-row DSMP files can be generated without holding the full output in memory.
- Generated output can be counted and validated.
- The GUI can display a clean result summary.
- The agent remains a wrapper around the engine rather than a place where all generation logic lives.

Side effects:

- Generated files can still be too large for some recommendation algorithms; generation validity is separate from algorithm scalability.
- The report and self-tests make that distinction explicit.

## After: UserGenerationEngine

**UserGenerationEngine result accessors**

File: `TwitterGatherDataFollowers/userRyersonU/UserGenerationEngine.java` (WORKTREE)

```java
  141: 
  142: 		public File getOutputFile()
  143: 		{
  144: 			return outputFile;
  145: 		}
  146: 
  147: 		public long getSourceRows()
  148: 		{
  149: 			return sourceRows;
  150: 		}
  151: 
  152: 		public long getProcessedRows()
  153: 		{
  154: 			return processedRows;
  155: 		}
  156: 
  157: 		public long getGeneratedRows()
  158: 		{
  159: 			return generatedRows;
  160: 		}
  161: 
  162: 		public int getGeneratedUsers()
  163: 		{
  164: 			return generatedUsers;
  165: 		}
  166: 
  167: 		public int getGeneratedVocabularySize()
  168: 		{
  169: 			return generatedVocabularySize;
  170: 		}
  171: 
  172: 		public long getElapsedMillis()
  173: 		{
```

**GUI receives generation result**

File: `TwitterGatherDataFollowers/userRyersonU/ControllerAgentGui.java` (WORKTREE)

```java
    1: package TwitterGatherDataFollowers.userRyersonU;
    2: 
    3: 
    4: import java.text.DecimalFormat;
    5: import java.text.SimpleDateFormat;
    6: import java.text.DateFormat;
    7: import java.util.*;
    8: import java.awt.*;
    9: import java.awt.event.*;
   10: import java.io.BufferedWriter;
   11: import java.io.File;
   12: import java.io.FileWriter;
   13: import java.io.IOException;
```

## Dataset Import Lab

The professor’s UCI Online Retail request exposed a semantic conversion problem. Raw retail CSV/XLSX rows are transactions with columns such as invoice number, stock code, description, quantity, invoice date, unit price, customer ID, and country. DSMP expects a six-column follower/followee-like text file:

1. followee/reference label
2. numeric post id
3. date
4. numeric user id
5. user/entity name
6. text/document body

The new Dataset Import Lab converts retail, journal, tweet, or other delimited/workbook files into this DSMP format without changing any recommendation algorithm. It lets the user choose the dataset family, inspects headers and samples, detects likely columns, offers mapping profiles, supports custom DSMP column mapping, validates rows, shows live DSMP output preview, and writes audit information.

Supported family intent:

- **Retail:** customers, products, stock codes, invoices, dates, prices, descriptions.
- **Journal:** authors, paper titles/abstracts, journals/venues/conferences, optional dates.
- **Tweets:** Twitter-style users/accounts/tweet IDs/dates/text.
- **Other:** explicit custom DSMP mapping.

Why this is safer than making algorithms read arbitrary CSV:

- Conversion happens before the simulator pipeline.
- The output is still normal DSMP six-column text.
- The algorithms remain unchanged.
- The audit trail explains which raw rows were kept/skipped and why.

Side effects:

- The chosen mapping changes the meaning of recommendations. For example, retail “similar customers” is a different experiment from retail “stock/product group labels.”
- SVM/MLP need stable, meaningful class labels; user-similarity modes are more appropriate for many-to-many retail data.

## After: Importer Mapping And Preview

**Importer source inspection**

File: `TwitterGatherDataFollowers/userRyersonU/UciRetailDatasetImporter.java` (WORKTREE)

```java
  525: 
  526:         MappedRow(String followee, long postId, String date, long userId, String userName, String text, boolean accepted) {
  527:             this.followee = followee;
  528:             this.postId = postId;
  529:             this.date = date;
  530:             this.userId = userId;
  531:             this.userName = userName;
  532:             this.text = text;
  533:             this.accepted = accepted;
  534:         }
  535: 
  536:         static MappedRow rejected() {
  537:             return new MappedRow("", 0L, "", 0L, "", "", false);
  538:         }
  539:     }
  540: 
  541:     static SourceProfile inspect(File inputFile, ProgressListener listener) throws IOException {
  542:         return inspect(inputFile, DatasetKind.RETAIL, listener);
  543:     }
  544: 
  545:     static SourceProfile inspect(File inputFile, DatasetKind selectedKind, ProgressListener listener) throws IOException {
  546:         if (inputFile == null || !inputFile.isFile()) {
  547:             throw new IOException("Choose an existing CSV, TSV, or delimited text file.");
  548:         }
  549:         DatasetKind kind = selectedKind == null ? DatasetKind.RETAIL : selectedKind;
  550:         if (listener != null) {
  551:             listener.onProgress("Reading the source header and detecting delimiter.", 4);
  552:         }
  553: 
  554:         File scanFile = inputFile;
  555:         String sourceFormat = "Delimited text";
  556:         Delimiter forcedDelimiter = null;
  557:         if (isSpreadsheet(inputFile)) {
```

**Importer row mapping**

File: `TwitterGatherDataFollowers/userRyersonU/UciRetailDatasetImporter.java` (WORKTREE)

```java
  767:                     String rowLabel = mapped.followee;
  768:                     state.followees.add(rowLabel);
  769:                     Map<String, Long> counts = state.labelsByUser.get(mapped.userName);
  770:                     if (counts == null) {
  771:                         counts = new LinkedHashMap<String, Long>();
  772:                         state.labelsByUser.put(mapped.userName, counts);
  773:                     }
  774:                     Long old = counts.get(rowLabel);
  775:                     counts.put(rowLabel, old == null ? 1L : old.longValue() + 1L);
  776:                 } else if (rowWriter != null) {
  777:                     rowWriter.write(mapped);
  778:                 }
  779:             }
  780:         } finally {
  781:             reader.close();
  782:         }
  783:     }
  784: 
  785:     private static MappedRow mapRow(List<String> cells, long rowNumber, Options options, ImportReport report, boolean countStats) {
  786:         String invoice = get(cells, options.invoiceColumn);
  787:         if (options.skipCancelledInvoices && invoice.trim().toUpperCase(Locale.ENGLISH).startsWith("C")) {
  788:             if (countStats) {
  789:                 report.skippedCancelledInvoices++;
  790:             }
  791:             return MappedRow.rejected();
  792:         }
  793: 
  794:         String customer = get(cells, options.customerIdColumn);
  795:         if (options.profile == MappingProfile.CUSTOM && options.customUserIdColumn >= 0) {
  796:             customer = get(cells, options.customUserIdColumn);
  797:         }
  798:         if ((options.profile == MappingProfile.JOURNAL_AUTHORS
  799:                 || options.profile == MappingProfile.TWEET_USERS)
  800:                 && customer.trim().length() == 0
  801:                 && options.customUserNameColumn >= 0) {
  802:             customer = get(cells, options.customUserNameColumn);
  803:         }
```

**Import panel live preview**

File: `TwitterGatherDataFollowers/userRyersonU/UciRetailImportPanel.java` (WORKTREE)

```java
  427:                 "Followee", "Post id", "Date", "User id", "User name", "Text"
  428:         }, 0);
  429:         JTable outputTable = styledTable(outputPreviewModel);
  430:         tabs.addTab("DSMP Output Preview", new JScrollPane(outputTable));
  431: 
  432:         panel.add(tabs, BorderLayout.CENTER);
  433:         return panel;
  434:     }
  435: 
  436:     private JPanel createReportPanel() {
  437:         JPanel panel = new JPanel(new BorderLayout());
  438:         panel.setOpaque(false);
  439:         panel.setMinimumSize(new Dimension(420, 700));
  440: 
  441:         RoundedPanel livePreview = createLivePreviewPanel();
  442:         livePreview.setMinimumSize(new Dimension(420, 420));
  443: 
  444:         JPanel stack = new JPanel(new BorderLayout(0, 10));
  445:         stack.setOpaque(false);
  446:         stack.add(livePreview, BorderLayout.CENTER);
  447:         stack.add(createImportConsolePanel(), BorderLayout.SOUTH);
  448:         panel.add(stack, BorderLayout.CENTER);
  449:         return panel;
  450:     }
  451: 
  452:     private RoundedPanel createImportConsolePanel() {
  453:         RoundedPanel console = new RoundedPanel(new Color(8, 13, 28));
  454:         console.setLayout(new BorderLayout(10, 10));
  455:         console.setBorder(new EmptyBorder(14, 14, 14, 14));
```

## Doc2Vec Cache Transparency

Doc2Vec can look suspiciously fast when a warm cache exists. The GUI now makes that explicit. Instead of simply finishing quickly, the simulator and exact lab expose cache state: whether a dataset-specific cache exists, whether training was skipped, row/user/vector counts, cache key metadata, and a button to delete only the cache for the selected dataset.

Why this matters:

- A 618-user Doc2Vec run can finish in seconds if the model was already trained and only scoring is needed.
- That is valid behavior, but without UI transparency it looks like the model did not run.
- Clearing a specific dataset cache lets the user force retraining without deleting unrelated caches.

Implementation:

- `doc2vec_cache_admin.py` reports cache metadata and deletes cache files by dataset signature.
- `Doc2VecCacheAdmin.java` wraps that script for Java GUI use.
- The GUI surfaces warm-cache/cold-cache state near the Doc2Vec algorithm controls.

## After: Doc2Vec Cache Admin

**Python cache info protocol**

File: `TwitterGatherDataFollowers/userRyersonU/doc2vec_cache_admin.py` (WORKTREE)

```python
   13:     scoring_cache_paths,
   14:     valid_cached_model,
   15: )
   16: 
   17: 
   18: def usage():
   19:     print(
   20:         "usage: doc2vec_cache_admin.py <info|delete> <dataset_file> <workers> "
   21:         "[dm dbow_words min_count negative hs sample window vector_size epochs]",
   22:         file=sys.stderr,
   23:     )
   24: 
   25: 
   26: def emit(key, value):
   27:     print("CACHE_INFO\t%s\t%s" % (key, str(value)), flush=True)
   28: 
   29: 
   30: def parse_args():
   31:     if len(sys.argv) not in (4, 13):
   32:         usage()
   33:         sys.exit(2)
   34:     command = sys.argv[1].strip().lower()
   35:     if command not in ("info", "delete"):
   36:         usage()
   37:         sys.exit(2)
   38:     dataset_file = os.path.abspath(sys.argv[2])
   39:     workers = max(int(sys.argv[3]), 1)
   40:     parse_optional_model_params(sys.argv, 4)
   41:     return command, dataset_file, workers
```

**Java cache admin wrapper**

File: `TwitterGatherDataFollowers/userRyersonU/Doc2VecCacheAdmin.java` (WORKTREE)

```java
    7: import java.nio.charset.StandardCharsets;
    8: import java.util.ArrayList;
    9: import java.util.LinkedHashMap;
   10: import java.util.List;
   11: import java.util.Map;
   12: 
   13: final class Doc2VecCacheAdmin
   14: {
   15: 	private static final String INFO_PREFIX = "CACHE_INFO";
   16: 
   17: 	private Doc2VecCacheAdmin()
   18: 	{
   19: 	}
   20: 
   21: 	static CacheInfo inspect(
   22: 			String pythonCommand,
   23: 			File datasetFile,
   24: 			int workers) throws IOException, InterruptedException
   25: 	{
   26: 		return inspect(
   27: 				pythonCommand,
   28: 				datasetFile,
   29: 				workers,
   30: 				AlgorithmParameterSettings.defaults());
   31: 	}
   32: 
   33: 	static CacheInfo inspect(
   34: 			String pythonCommand,
   35: 			File datasetFile,
```

## Advanced Algorithm Parameters

An advanced settings dialog was added so algorithm parameters can be inspected and adjusted without hardcoding. The default state preserves current DSMP behavior. Reset-to-default restores the same parameter values that the program had before the settings UI was introduced.

The dialog was intentionally designed as an advanced feature because most students should not need it for normal runs. Validation is applied to prevent impossible settings such as negative cluster counts or invalid Doc2Vec parameters.

Examples of controlled settings include K-Means cluster/iteration behavior, Doc2Vec model parameters, scalable SVM epochs/aggressiveness, and safe values for model/runtime knobs where applicable.

Side effects:

- Parameter changes can change recommendations because they intentionally change algorithm configuration.
- The GUI indicates when defaults are active.
- The default path remains the compatibility path.

## After: Algorithm Parameter Settings

**Defaults and validation**

File: `TwitterGatherDataFollowers/userRyersonU/AlgorithmParameterSettings.java` (WORKTREE)

```java
   54: 	private int doc2VecDbowWords;
   55: 	private int doc2VecMinCount;
   56: 	private int doc2VecNegative;
   57: 	private int doc2VecHs;
   58: 	private double doc2VecSample;
   59: 	private int doc2VecWindow;
   60: 	private int doc2VecVectorSize;
   61: 	private int doc2VecEpochs;
   62: 
   63: 	private AlgorithmParameterSettings()
   64: 	{
   65: 		resetToDefaults();
   66: 	}
   67: 
   68: 	static AlgorithmParameterSettings defaults()
   69: 	{
   70: 		return new AlgorithmParameterSettings();
   71: 	}
   72: 
   73: 	static AlgorithmParameterSettings effective(
   74: 			AlgorithmParameterSettings settings)
   75: 	{
   76: 		if (settings == null || !settings.isCustomEnabled())
   77: 		{
   78: 			return defaults();
   79: 		}
   80: 		return settings.copy();
   81: 	}
   82: 
```

**Settings dialog footer/reset**

File: `TwitterGatherDataFollowers/userRyersonU/AlgorithmSettingsDialog.java` (WORKTREE)

```java
    1: package TwitterGatherDataFollowers.userRyersonU;
    2: 
    3: import java.awt.BorderLayout;
    4: import java.awt.Color;
    5: import java.awt.Component;
    6: import java.awt.Dimension;
    7: import java.awt.FlowLayout;
    8: import java.awt.Font;
    9: import java.awt.GridBagConstraints;
   10: import java.awt.GridBagLayout;
   11: import java.awt.Insets;
   12: import java.awt.Window;
   13: import java.awt.event.ActionEvent;
   14: import java.awt.event.ActionListener;
   15: import java.util.List;
```

## Graph Insights Tab

The simulator already produced graph files, but it did not explain them. The Graph Insights tab is a read-only analysis layer over existing generated graph artifacts. It reads mapping and edge files, computes statistics, and displays graph health, network shape, recommendation impact, hubs, warnings, and artifact paths.

Metrics include:

- mapped nodes
- active nodes
- valid edges
- duplicate edges
- unknown-node edges
- isolates
- connected components
- largest component size
- estimated communities
- density
- target count inferred from recommendation edges
- unique outputs and concentration
- top hubs/output labels

This helps explain outcomes like “Gephi shows only two communities” by showing whether recommendations collapsed into a few hubs or whether too few active nodes exist for meaningful statistics.

Important safety point: Graph Insights does not write graph edges or change recommendations. It only reads existing graph files and can export a text report.

## After: Graph Analysis Engine

**GraphAnalysisEngine entry point**

File: `TwitterGatherDataFollowers/userRyersonU/GraphAnalysisEngine.java` (WORKTREE)

```java
   20: import java.util.HashSet;
   21: import java.util.LinkedHashMap;
   22: import java.util.LinkedHashSet;
   23: import java.util.List;
   24: import java.util.Locale;
   25: import java.util.Map;
   26: import java.util.Set;
   27: import java.util.TreeMap;
   28: 
   29: final class GraphAnalysisEngine
   30: {
   31: 	private static final int TOP_LIMIT = 8;
   32: 	private static final int LABEL_PROPAGATION_ITERATIONS = 18;
   33: 
   34: 	private GraphAnalysisEngine()
   35: 	{
   36: 	}
   37: 
   38: 	static Analysis analyze(File mappingFile, File edgeFile) throws IOException
   39: 	{
   40: 		if (mappingFile == null || !mappingFile.isFile())
   41: 		{
   42: 			return Analysis.missing(
   43: 					mappingFile,
   44: 					edgeFile,
   45: 					"Graph node map is missing. Run Get Users and at least one recommendation first.");
   46: 		}
   47: 
   48: 		Mapping mapping = loadMapping(mappingFile);
   49: 		MutableAnalysis state = new MutableAnalysis(mappingFile, edgeFile, mapping);
   50: 		if (edgeFile == null || !edgeFile.isFile())
   51: 		{
   52: 			state.warnings.add("No edge list exists yet for this dataset and recommendation mode.");
   53: 			state.warnings.add("Recommendations will populate this file even when final graph export is unchecked.");
   54: 			state.computeGraphShape();
   55: 			state.computeWarnings();
   56: 			return state.finish();
```

**GraphInsightsPanel refresh**

File: `TwitterGatherDataFollowers/userRyersonU/GraphInsightsPanel.java` (WORKTREE)

```java
   86: 		titleLabel = new JLabel("Graph Insights");
   87: 		titleLabel.setFont(font(Font.BOLD, 26));
   88: 		titleLabel.setForeground(TEXT);
   89: 		subtitleLabel = new JLabel("Read-only analysis of the node and edge files produced by the simulator.");
   90: 		subtitleLabel.setFont(font(Font.PLAIN, 12));
   91: 		subtitleLabel.setForeground(MUTED);
   92: 		statusPill = pill("WAITING FOR GRAPH DATA", BLUE);
   93: 		artifactLabel = new JLabel("No graph artifact selected yet.");
   94: 		artifactLabel.setFont(font(Font.PLAIN, 11));
   95: 		artifactLabel.setForeground(MUTED);
   96: 
   97: 		refreshButton = actionButton("Analyze now", BLUE);
   98: 		refreshButton.addActionListener(new ActionListener()
   99: 		{
  100: 			public void actionPerformed(ActionEvent event)
  101: 			{
  102: 				refreshFromSimulator(true);
  103: 			}
  104: 		});
  105: 		exportButton = actionButton("Export report", GREEN);
  106: 		exportButton.addActionListener(new ActionListener()
  107: 		{
  108: 			public void actionPerformed(ActionEvent event)
  109: 			{
  110: 				exportReport();
  111: 			}
  112: 		});
  113: 
  114: 		headerSignalPanel = new HeaderSignalPanel();
  115: 		healthPanel = new HealthPanel();
  116: 		networkShapePanel = new NetworkShapePanel();
  117: 		impactPanel = new RecommendationImpactPanel();
  118: 		rankingsPanel = new RankingsPanel();
```

## Dynamic Help Tab

The Help tab was added because students need to understand what a selected algorithm/dataset/mode will do before running it. The first version was text-heavy, so it was redesigned into a visual guide similar to Graph Insights.

The Help tab now reads the current simulator controls and dataset preview, then shows:

- live dataset shape
- which DSMP column is model input
- which column will be ranked/predicted
- a preview target user
- sample text tokens from column 6
- sample ranked candidates from the dataset
- an execution lane showing read, clean, model, score, merge
- node/merge visualization
- current run contract and settings impact

It does not run a model. It previews meaning and flow using the selected dataset and GUI settings.

Side effects:

- It adds dataset preview reads, but bounded to samples.
- It makes recommendation semantics visible, which reduces misuse of retail/journal/tweet data.
- It should not change scoring because it is not in the model path.

## After: Simulator Help Panel

**Help snapshot from GUI**

File: `TwitterGatherDataFollowers/userRyersonU/ControllerAgentGui.java` (WORKTREE)

```java
 5850: 
 5851: 	public void setAlgorithmParameterSettings(
 5852: 			AlgorithmParameterSettings settings)
 5853: 	{
 5854: 		algorithmParameterSettings = settings == null
 5855: 				? AlgorithmParameterSettings.defaults()
 5856: 				: settings.copy();
 5857: 		updateAlgorithmSettingsSummary();
 5858: 		refreshSimulatorDoc2VecCacheStatus();
 5859: 		if (populationSweepPanel != null)
 5860: 		{
 5861: 			populationSweepPanel.refreshAlgorithmSettingsSummary();
 5862: 		}
 5863: 		refreshSimulatorHelpPanel();
 5864: 	}
 5865: 
 5866: 	SimulatorHelpPanel.Snapshot buildSimulatorHelpSnapshot()
 5867: 	{
 5868: 		int selectedAlgorithm = algorithmSelectionBox == null
 5869: 				? algorithmRec : algorithmSelectionBox.getSelectedIndex();
 5870: 		RecommendationTargetMode targetMode = getRecommendationTargetMode();
 5871: 		RecommendationCandidateType nativeType =
 5872: 				RecommendationModeSupport.nativeCandidateType(selectedAlgorithm);
 5873: 		RecommendationCandidateType requestedType =
 5874: 				RecommendationModeSupport.resolveCandidateType(
 5875: 						targetMode,
 5876: 						selectedAlgorithm);
 5877: 		boolean convertedPipeline =
 5878: 				RecommendationModeSupport.requiresConvertedPipeline(
 5879: 						targetMode,
 5880: 						selectedAlgorithm);
 5881: 		boolean batchEnabled =
 5882: 				RecommendationModeSupport.isSvmFamilyAlgorithm(selectedAlgorithm)
```

**Help panel preview loader**

File: `TwitterGatherDataFollowers/userRyersonU/SimulatorHelpPanel.java` (WORKTREE)

```java
  295: 				final DatasetPreview loaded = loadPreview(path, next.primaryTargetName());
  296: 				SwingUtilities.invokeLater(new Runnable()
  297: 				{
  298: 					public void run()
  299: 					{
  300: 						if (request == previewRequest && path.equals(previewPath))
  301: 						{
  302: 							preview = loaded;
  303: 							updateVisualPanels();
  304: 						}
  305: 					}
  306: 				});
  307: 			}
  308: 		}, "Simulator-Help-Dataset-Preview");
  309: 		loader.setDaemon(true);
  310: 		loader.start();
  311: 	}
  312: 
  313: 	private static DatasetPreview loadPreview(String path, String selectedTarget)
  314: 	{
  315: 		File file = new File(path);
  316: 		if (!file.isFile())
  317: 		{
  318: 			return DatasetPreview.empty("Dataset file could not be found.");
  319: 		}
  320: 		List<DsmpRow> sampledRows = new ArrayList<DsmpRow>();
  321: 		List<DsmpRow> targetRows = new ArrayList<DsmpRow>();
  322: 		List<DsmpRow> fallbackRows = new ArrayList<DsmpRow>();
  323: 		Set<String> followees = new LinkedHashSet<String>();
  324: 		Set<String> users = new LinkedHashSet<String>();
  325: 		Map<String, Integer> terms = new LinkedHashMap<String, Integer>();
  326: 		int totalRows = 0;
  327: 		int validRows = 0;
  328: 		int malformedRows = 0;
  329: 		String fallbackTarget = "";
  330: 		try
  331: 		{
```

## Bootstrap And Fresh-Clone Reliability

The old launch path expected too many local conditions to be correct: a compatible Python version, a manually usable Java/JDK, jars in the right place, Maven assumptions, a free JADE port, Python packages compatible with Gensim/Numpy, and GUI startup that could silently fail while JADE continued running.

The new bootstrap work centralizes launch in `build.py launch` and wrapper scripts. It validates required jars, creates/uses a project `.venv`, installs pinned Python dependency ranges, checks Doc2Vec runtime compatibility, compiles when needed, chooses a free JADE port when the default is busy, writes startup logs, and passes the managed Python command to Java.

Key changes:

- Python 3.9+ is accepted rather than only 3.9/3.10.
- Requirements are bounded in `requirements-lock.txt`.
- Required jars are checked by class entry, including PDFBox.
- `run.sh` and `run.bat` provide simple one-command entry points.
- `start_modern.sh` and `.bat` call `build.py launch`.
- GitHub Actions bootstrap smoke test checks Windows/macOS/Linux compile readiness.

Side effects:

- First launch can take longer because it creates/validates `.venv`.
- If a required jar is missing, the launcher fails early with a clear error instead of a hidden classpath crash.
- This does not package a full native `.app`/installer yet, but it makes clone-and-run much more reliable.

## Before And After: Bootstrap

**Old build.py required Python 3.9/3.10**

File: `build.py` (origin/main)

```python
   41: import venv
   42: import textwrap
   43: import datetime
   44: 
   45: # --- Configuration ---
   46: PROJECT_ROOT = os.path.dirname(os.path.abspath(__file__))
   47: VENV_DIR = os.path.join(PROJECT_ROOT, ".venv")
   48: SRC_DIR = os.path.join(PROJECT_ROOT, "TwitterGatherDataFollowers", "userRyersonU")
   49: LIB_DIR = os.path.join(PROJECT_ROOT, "lib")
   50: CLASSES_DIR = os.path.join(PROJECT_ROOT, "classes")
   51: REQUIREMENTS_FILE = os.path.join(PROJECT_ROOT, "requirements.txt")
   52: POM_FILE = os.path.join(PROJECT_ROOT, "pom.xml")
   53: 
   54: JAVA_MAIN_CLASS = "jade.Boot"
   55: JAVA_MAIN_AGENT = "controller:TwitterGatherDataFollowers.userRyersonU.ControllerAgent"
   56: MIN_JAVA_VERSION = 8
   57: ALLOWED_PYTHON_VERSIONS = [(3, 9), (3, 10)]
   58: ALLOWED_PYTHON_VERSIONS_STR = " or ".join([".".join(map(str, v)) for v in ALLOWED_PYTHON_VERSIONS])
   59: 
   60: # --- Global State ---
   61: VERBOSE = False
   62: 
   63: # --- Helper Functions ---
   64: 
   65: def pinfo(message):
   66:     """Prints an informational message."""
   67:     print(f"[INFO] {message}")
   68: 
   69: def pwarn(message):
   70:     """Prints a warning message."""
   71:     print(f"[WARN] {message}")
   72: 
   73: def perr(message, suggestion=None, exit_code=1):
```

**Current required jar checks**

File: `build.py` (WORKTREE)

```python
   36: CLASSES_DIR = os.path.join(PROJECT_ROOT, "classes")
   37: RUNTIME_DIR = os.path.join(PROJECT_ROOT, ".runtime")
   38: LOG_DIR = os.path.join(PROJECT_ROOT, "logs")
   39: REQUIREMENTS_FILE = os.path.join(PROJECT_ROOT, "requirements.txt")
   40: LOCKED_REQUIREMENTS_FILE = os.path.join(PROJECT_ROOT, "requirements-lock.txt")
   41: DOC2VEC_PREFLIGHT = os.path.join(SRC_DIR, "doc2vec_preflight.py")
   42: RUNTIME_PROPERTIES = os.path.join(PROJECT_ROOT, ".simulator-runtime.properties")
   43: 
   44: JAVA_MAIN_CLASS = "jade.Boot"
   45: JAVA_MAIN_AGENT = "controller:TwitterGatherDataFollowers.userRyersonU.ControllerAgent"
   46: MIN_JAVA_VERSION = 8
   47: MIN_PYTHON_VERSION = (3, 9)
   48: SUPPORTED_PYTHON_VERSION_STR = "3.9 or newer"
   49: DEFAULT_HEAP_MB = 1424
   50: DEFAULT_JADE_PORT = 35240
   51: DEFAULT_DF_MAXRESULT = 100000
   52: RESTART_EXIT_CODE = 42
   53: 
   54: REQUIRED_JARS = {
   55:     "jade.jar": ["jade/Boot.class"],
   56:     "weka.jar": ["weka/classifiers/functions/SMO.class"],
   57:     "gephi-toolkit.jar": ["org/gephi/project/api/ProjectController.class"],
   58:     "neuroph-core-2.94.jar": ["org/neuroph/core/NeuralNetwork.class"],
   59:     "neuroph-contrib-2.94.jar": [],
   60:     "pdfbox-app-2.0.31.jar": ["org/apache/pdfbox/pdmodel/PDDocument.class"],
   61:     "opencsv-5.7.1.jar": ["com/opencsv/CSVReader.class"],
   62:     "mysql-connector-java-5.1.39-bin.jar": ["com/mysql/jdbc/Driver.class"],
   63:     "twitter4j-core-4.0.4.jar": ["twitter4j/Twitter.class"],
   64:     "libsvm.jar": [],
   65:     "LibSVM-1.0.10.jar": [],
   66:     "commons-math3-3.6.1.jar": [],
   67:     "commons-lang3-3.9.jar": [],
   68: }
   69: 
   70: VERBOSE = False
   71: 
   72: 
```

**Current GitHub Actions smoke test**

File: `.github/workflows/bootstrap-smoke.yml` (WORKTREE)

```yaml
    1: name: DSMP Bootstrap Smoke Test
    2: 
    3: on:
    4:   push:
    5:   pull_request:
    6: 
    7: jobs:
    8:   bootstrap:
    9:     name: ${{ matrix.os }}
   10:     runs-on: ${{ matrix.os }}
   11:     strategy:
   12:       fail-fast: false
   13:       matrix:
   14:         os: [ubuntu-latest, macos-latest, windows-latest]
   15: 
   16:     steps:
   17:       - name: Check out repository
   18:         uses: actions/checkout@v4
   19: 
   20:       - name: Set up Python
   21:         uses: actions/setup-python@v5
```

## Graph And Recommendation Output Visualization

The recommendation output panel was redesigned from plain terminal text into visually structured cards. Each target user appears with ranked matches, scores, target counts, and candidate labels. This did not change the recommendation calculations; it changed the presentation of already-produced scores.

The bottom status bar was also redesigned to put status after “Current progress,” show more informative run stages, and display dataset/user context. This improves run observability without affecting agents.

The header and tab treatments were unified across Simulator, User Studio, Exact Multi-Node Lab, Dataset Import Lab, Help, and Graph Insights. Colorful tags were removed where they looked decorative rather than informative. Attribution lines were added consistently: faculty advisor Dr. Abdolreza Abhari and developer Mohsen Alavi.

Side effects:

- More GUI code and repaint work.
- Better first-time-user clarity.
- No intended scoring change.

## Journal Dataset Support

The professor described the journal dataset as real papers from authors, where authors are users/followers with sequence IDs and submitted journals/venues act as followees. The current implementation supports that structure in two ways.

First, the normal simulator can already read DSMP six-column journal files as long as they follow the expected format. Column 5 becomes author/user, column 6 becomes title/post text, and column 1 becomes journal/venue/reference label depending on mapping.

Second, the Dataset Import Lab now has a Journal family. It looks for author IDs/names, titles/abstracts, publication dates, journals, venues, conferences, or proceedings-like fields. It converts them into DSMP format explicitly rather than guessing inside the algorithms.

Meaning by algorithm/mode:

- Doc2Vec/Similarity/K-Means in similar-user mode rank authors with similar title/text profiles.
- SVM/MLP in followee mode rank/predict journals or venues when column 1 is a meaningful stable label.
- Algorithm Default preserves the historical behavior for each algorithm.

Side effects:

- Long author-name datasets can stress memory during user-agent creation and feature building, so heap preflight and streaming matter.
- Journal classification only makes sense if the followee/reference column is not artificially constant.

## UCI Online Retail Mapping Support

The UCI Online Retail dataset is not directly DSMP-ready. It is a transaction table, not a follower-followee text corpus. The import lab provides reproducible mapping profiles rather than forcing raw CSV into algorithms.

Recommended default mapping for similar-customer experiments:

- DSMP column 1 followee/reference: shared collection label or product/store grouping, depending on chosen profile.
- DSMP column 2 numeric post id: stable numeric invoice/row-derived ID.
- DSMP column 3 date: invoice date converted to `yyyy-MM-dd`.
- DSMP column 4 numeric user id: numeric customer ID or stable generated numeric ID.
- DSMP column 5 user name: customer/entity label such as `Customer15311`.
- DSMP column 6 text: cleaned product description.

Important warning:

- SVM/MLP need stable class labels in column 1. If all rows use the same label, classifier algorithms are not meaningful.
- Doc2Vec/Similarity/K-Means are better default choices for customer similarity because they use column 6 text and rank column 5 users/entities.

## Self-Tests And Smoke Tests Added

A substantial number of self-tests were added so individual subsystems can be checked without launching the full GUI. These tests cover Doc2Vec batch parsing, reset files, graph generation settings, graph analysis, runtime resources, post-processing, score merging, streaming sources, scalable/exact SVM support, import conversion, node dashboard state, user generation, and large-dataset ingestion behavior.

Representative test files include:

- `Doc2VecBatchRunnerSelfTest.java`
- `ExperimentResetFilesSelfTest.java`
- `GraphAnalysisEngineSelfTest.java`
- `GraphGenerationSettingSelfTest.java`
- `RecommendationPostProcessorSelfTest.java`
- `RecommendationScoreMergerSelfTest.java`
- `RuntimeResourceSettingsSelfTest.java`
- `UciRetailDatasetImporterSelfTest.java`
- `ExactPopulationSweepEngineSelfTest.java`
- `NodeExecutionMonitorSelfTest.java`
- `UserGenerationEngineSelfTest.java`

These are focused tests, not a complete integration test suite. They reduce the chance that a subsystem regression silently breaks the GUI.

## Preserved Logic And Explicit Non-Goals

The following logic was intentionally preserved unless the user explicitly selected a new mode/parameter:

- Existing algorithm default behavior remains the compatibility baseline.
- SVM exact mode remains Weka SMO.
- Doc2Vec scoring uses the same shared scoring function; batch mode changes orchestration, not scoring math.
- Graph edge persistence remains active even when final graph visualization/export is skipped.
- Mobile-agent recommendation side effects still happen after a valid run.
- The Dataset Import Lab writes DSMP-ready files before the simulator; it does not make algorithms parse arbitrary raw CSV.
- The Help and Graph Insights tabs are read-only explanatory/analysis features.

Known caveats:

- Parameter changes can intentionally change results.
- Scalable Linear SVM is a separate algorithm, not a faster exact Weka SVM.
- Warm Doc2Vec cache can make scoring extremely fast; the GUI now explains this.
- Very large datasets may still require a larger heap or may be unsuitable for certain algorithms.
- Some generated runtime artifacts remain in the working tree but are not source changes.

## Appendix A: Full Source Diff Appendix

The full source diff appendix is stored at:

`/Users/mohsen11/Desktop/June 10 session final/test/reports/current-chat-change-documentation/DSMP_Current_Chat_Source_Diff_Appendix_2026-06-20.patch`

It contains:

- tracked source/config/doc/launcher diffs from `origin/main` to the current working tree
- full additions for untracked source files introduced after `cb47942`
- no `.class` files
- no ARFF/runtime datasets
- no Doc2Vec model caches
- no recommendation result outputs

This appendix is the exact code-level before/after record. The main report embeds important excerpts, while the appendix carries the full patch.
