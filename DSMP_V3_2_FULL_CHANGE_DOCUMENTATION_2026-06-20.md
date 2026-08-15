# DSMP v3.2 Full Change Documentation

Generated: 2026-06-20  
Repository inspected: `/Users/mohsen11/Desktop/June 10 session final/test`  
Current branch: `v3.2`  
Baseline compared against: `origin/main` / `06dabbb` / `06dabbb9f7683d275a847a85ba624e5b65097e26`  
Current documented version: `cb47942` / `cb47942d7a429b3c45048c58a5d037761fde920c`

This document is intentionally not a short summary. It documents the source-code changes made during this DSMP/Retail50K work by comparing the final v3.2 branch against the original `aabhari/test` baseline. It explains the problem each change addressed, what the old behavior/code did, what the final code does now, the expected effects, and the side effects/tradeoffs. The final appendix contains the exact Git diff, so the before/after code is included line by line.

Generated runtime files are not treated as source changes. Examples: `.class` files, ARFF outputs, Doc2Vec model files, graph PDFs/images, `edges-numbers*.txt`, `name-number*.txt`, and experiment outputs. Those are produced by running DSMP and should be regenerated during testing.

## Commit Chain Documented

```text
cb47942 (HEAD -> v3.2, origin/V3.2) Add Doc2Vec batch mode and experiment reset
6a00b58 Fixed GUI not opening
fe57855 Add v3.2 simulator updates
```

## Source Files Changed

```text
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
M	TwitterGatherDataFollowers/userRyersonU/MobileAgent.java
M	TwitterGatherDataFollowers/userRyersonU/OrganizingAgent.java
M	TwitterGatherDataFollowers/userRyersonU/RecommenderAgent.java
M	TwitterGatherDataFollowers/userRyersonU/StarterAgent.java
A	TwitterGatherDataFollowers/userRyersonU/SvmReproducibility.java
A	TwitterGatherDataFollowers/userRyersonU/doc2vec_batch.py
A	TwitterGatherDataFollowers/userRyersonU/doc2vec_shared.py
M	build_and_start_modern.sh
A	lib/pdfbox-app-2.0.31.jar
```

## Diff Summary

```text
 .../Test/BatchRecommendationSelectorSelfTest.java  |   73 +
 .../Test/Doc2VecBatchRunnerSelfTest.java           |  109 +
 .../Test/ExperimentResetFilesSelfTest.java         |   78 +
 .../Test/GraphGmlBuilderSelfTest.java              |  122 +
 .../Test/SvmReproducibilitySelfTest.java           |  119 +
 .../userRyersonU/BatchRecommendationSelector.java  |  112 +
 .../userRyersonU/ControllerAgent.java              |  978 +++++---
 .../userRyersonU/ControllerAgentGui.java           |  342 ++-
 .../userRyersonU/Doc2VecBatchRunner.java           |  202 ++
 .../userRyersonU/ExperimentResetFiles.java         |   62 +
 .../userRyersonU/GraphGmlBuilder.java              |  196 ++
 .../userRyersonU/MobileAgent.java                  |  724 +++---
 .../userRyersonU/OrganizingAgent.java              |  152 +-
 .../userRyersonU/RecommenderAgent.java             | 2386 ++++++++++++--------
 .../userRyersonU/StarterAgent.java                 |  718 ++++--
 .../userRyersonU/SvmReproducibility.java           |  105 +
 .../userRyersonU/doc2vec_batch.py                  |  107 +
 .../userRyersonU/doc2vec_shared.py                 |  319 +++
 build_and_start_modern.sh                          |    5 +-
 lib/pdfbox-app-2.0.31.jar                          |  Bin 0 -> 12413748 bytes
 20 files changed, 4796 insertions(+), 2113 deletions(-)
```

## Numeric Diff Summary

```text
73	0	TwitterGatherDataFollowers/Test/BatchRecommendationSelectorSelfTest.java
109	0	TwitterGatherDataFollowers/Test/Doc2VecBatchRunnerSelfTest.java
78	0	TwitterGatherDataFollowers/Test/ExperimentResetFilesSelfTest.java
122	0	TwitterGatherDataFollowers/Test/GraphGmlBuilderSelfTest.java
119	0	TwitterGatherDataFollowers/Test/SvmReproducibilitySelfTest.java
112	0	TwitterGatherDataFollowers/userRyersonU/BatchRecommendationSelector.java
670	308	TwitterGatherDataFollowers/userRyersonU/ControllerAgent.java
276	66	TwitterGatherDataFollowers/userRyersonU/ControllerAgentGui.java
202	0	TwitterGatherDataFollowers/userRyersonU/Doc2VecBatchRunner.java
62	0	TwitterGatherDataFollowers/userRyersonU/ExperimentResetFiles.java
196	0	TwitterGatherDataFollowers/userRyersonU/GraphGmlBuilder.java
302	422	TwitterGatherDataFollowers/userRyersonU/MobileAgent.java
70	82	TwitterGatherDataFollowers/userRyersonU/OrganizingAgent.java
1382	1004	TwitterGatherDataFollowers/userRyersonU/RecommenderAgent.java
488	230	TwitterGatherDataFollowers/userRyersonU/StarterAgent.java
105	0	TwitterGatherDataFollowers/userRyersonU/SvmReproducibility.java
107	0	TwitterGatherDataFollowers/userRyersonU/doc2vec_batch.py
319	0	TwitterGatherDataFollowers/userRyersonU/doc2vec_shared.py
4	1	build_and_start_modern.sh
-	-	lib/pdfbox-app-2.0.31.jar
```

## Added Binary Library

- `pdfbox-app-2.0.31.jar: 12,413,748 bytes, sha256=385e382771ca1129f6dacc2508d5a479cf5cae9e5b6c4568c350511a2be601aa`


# Detailed Change Log

## 1. Retail50K `T&T Supermarket` Parsing Fix

### Problem Before
Retail50K contains the retailer/followee/class value `T&T Supermarket`. The old DSMP message and parsing code treated spaces as field delimiters. This was safe for small `Retail.txt` but unsafe for Retail50K because `T&T Supermarket` contains a space.

The failure was not that the Retail50K file was malformed. The file rows were tab-separated and valid. The crash came from losing part of the followee/class name after DSMP converted dataset rows into internal messages.

### Old Code Pattern
The original active message protocol was effectively:

```java
msg2.setContent(totalTweet + " " + whoTweeted + " " + tweetId + " " + followeeName + " " + tweetText);

tweetReceived = msg.getContent();
tweetUserReceived = tweetReceived.split(" ", 5)[1];
tweetIdReceived = Long.valueOf(tweetReceived.split(" ", 5)[2]);
tweetFolloweeName = tweetReceived.split(" ", 5)[3];
tweetTextReceived = tweetReceived.split(" ", 5)[4];
```

Mapping-file parsing also used the same unsafe idea:

```java
wordsFile3 = lineUser.split(" ");
wordsFile5 = lineOrigFollowee.split(" ");
wordsFile4 = lineSelectedFollowee.split(" ");
```

### Why It Broke
For a record with `T&T Supermarket`, the field was interpreted as two words. The recommender could receive `T&T` instead of `T&T Supermarket`. In the MLP path, the class map was built from the original dataset label, so it expected the full label. A lookup using the truncated label returned `null`, then the code crashed when that null value was used as a primitive class index.

### Final Change
The final code no longer depends on unsafe space splitting for this path. The message/batch protocol preserves fields, and graph mapping parsing preserves names with spaces by splitting only at the final numeric id. The full exact before/after code is in Appendix A under `MobileAgent.java`, `RecommenderAgent.java`, and graph-related helper changes.

### Effects
- `T&T Supermarket` remains one label.
- MLP/SVM/K-Means/Similarity/Doc2Vec no longer receive a truncated followee/class value from this path.
- Graph edge writing can map names with spaces correctly.

### Side Effects
- Old generated intermediate files from before the fix should be regenerated.
- This is a protocol/parsing fix, not an algorithm change.

## 2. Uploaded Dataset Path Fix

### Problem Before
When the user uploaded `/Users/mohsen11/Downloads/Retail50K.txt`, later code attempted to reopen `important-stuff/Retail50K.txt`. That caused:

```text
java.io.FileNotFoundException: important-stuff/Retail50K.txt (No such file or directory)
```

### Old Code Pattern
The old pattern assumed the selected file name existed inside `important-stuff/`:

```java
new RandomAccessFile(importantStuffDirName + selectedFile.getName(), "r");
new FileReader(importantStuffDirName + selectedFile.getName());
String filepath = importantStuffDirName + selectedFile.getName();
```

### Final Change
The code now reads the selected uploaded dataset using the actual selected `File`/absolute path. Generated outputs still go under `important-stuff/`, but the source dataset is read from wherever the user selected it.

### Effects
- Retail50K can be uploaded from Downloads or another folder.
- No manual copy into `important-stuff/` is required for reading.
- Dataset-specific output files are still produced in `important-stuff/`.

### Side Effects
If the selected dataset is moved or deleted after `Get Users`, the next run can fail because the stored file path points to the old location. That is normal file-picker behavior.

## 3. Dataset-Specific Graph Mapping and Edge Files

### Problem Before
The old visualization path depended on manually built files for the small retail dataset. Retail50K did not automatically have its own `name-number-Retail50K.txt`. When that file was missing, recommendations could finish but graph writing could be skipped or stale graph files could be reused.

This is why earlier Gephi screenshots showed the wrong node count, such as around 220 nodes instead of the Retail50K node count.

### Old Behavior
The program expected or reused generic/small-retail graph files such as:

```text
important-stuff/name-number-Retail50K.txt
important-stuff/edges-numbers.txt
important-stuff/edges-numbers.gml
```

If `name-number-Retail50K.txt` did not exist, the edge-writing path could not create a correct Retail50K graph.

### Final Change
The final code creates dataset-specific mapping and edge files. Examples:

```text
important-stuff/name-number-Retail50K.txt
important-stuff/edges-numbers-Retail50K.txt
important-stuff/edges-numbers-Retail50K.gml
```

For the 20x dataset, the output becomes:

```text
important-stuff/name-number-Retail50K_20x.txt
important-stuff/edges-numbers-Retail50K_20x.txt
important-stuff/edges-numbers-Retail50K_20x.gml
```

### Effects
- New six-column retail-format datasets can generate their own graph mapping.
- Retail50K shows the correct stable node count of 628 in tests.
- Switching datasets no longer reinterprets old edges using the wrong node mapping.
- Graph files become easier to audit because the dataset name is in the filename.

### Side Effects
The generated mapping order is dataset-dependent. Do not mix mapping files and edge files from different datasets.

## 4. Clean Experiment Reset

### Problem Before
Running DSMP repeatedly could reuse old generated files. That made it difficult to tell whether a graph represented the current dataset/run or older state.

### Final Change
A new utility, `ExperimentResetFiles.java`, resets generated outputs for text-dataset experiments. Controller paths call it before starting fresh experiments.

### Effects
- Cleaner experiment starts.
- Less stale graph/output contamination.
- Dataset-specific generated graph outputs can be rebuilt from the current run.

### Side Effects
Old generated outputs are deleted when a clean experiment starts. That is intended. Source files are not supposed to be deleted.

## 5. Per-Tweet JADE Message Bottleneck and Batch Tweet Delivery

### Problem Before
The original design sent one JADE ACL message per tweet. Retail50K has 52,061 rows, so this produced tens of thousands of messages. The actual SVM/K-Means computation could be fast, but total runtime was dominated by message passing and JADE scheduling.

Observed behavior before this change included runs where SVM itself took milliseconds, but the full simulation took far longer because every tweet traveled as a separate message.

### Old Flow
```text
MobileAgent sends tweet 1
MobileAgent sends tweet 2
MobileAgent sends tweet 3
...
RecommenderAgent receives thousands of separate ACL messages
RecommenderAgent then builds documents / TF-IDF / algorithm results
```

### Final Change
The final code keeps the same agent roles but groups tweet data into batch messages. RecommenderAgent still receives the raw tweet information and performs text processing, TF-IDF, and algorithm work; the difference is that the transport layer no longer pays the cost of one ACL message per row.

Related files:
- `MobileAgent.java`
- `RecommenderAgent.java`
- `StarterAgent.java`

### Effects
- Message passing cost drops dramatically.
- K-Means multi-node scaling becomes visible in the algorithm stage.
- DSMP still uses Controller, Starter, MobileAgents, RecommenderAgents, and OrganizingAgent.

### Side Effects
- Fewer messages, but each message is larger.
- Very large synthetic datasets can still create heap pressure if payloads are too large or too many reducers hold large data at once.

## 6. Raw Text-Processing Optimization

### Problem Before
Even after batch delivery, 8-node K-Means showed suspicious text-processing times. A reducer processing about 11k tweets could take longer than one node processing all 48k processed tweets. That meant the slowdown was not useful algorithm work; it was overhead/contention in the text-processing path.

The old code repeatedly did expensive work inside the tweet loop, such as compiling regex patterns, lowercasing the same text multiple times, and tokenizing more than once.

### Old Code Pattern
Representative expensive behaviors:

```java
Pattern.compile(...);          // inside repeated processing
currentText.toLowerCase();     // repeated for every stop word
currentText.split("\\s+");     // one tokenization pass
new Scanner(currentText);      // second tokenization pass
```

There was also unnecessary Doc2Vec-related file writing during non-Doc2Vec algorithms.

### Final Change
The raw text-processing path was optimized while keeping the same cleaning intent. Repeated regex/lowercase/tokenization work was reduced, unnecessary file writing/debug output was removed or guarded, and the batch parsing path was made cheaper.

### Effects Observed
- 8-node text processing dropped from roughly 8.8-9.7 seconds per reducer to about 250-275 ms per reducer on Retail50K.
- 1-node text processing also dropped substantially in later tests.
- K-Means speedup became much more visible.

### Side Effects
- Less terminal/debug spam.
- No intended change to recommendation logic.

## 7. Stage Timers

### Problem Before
The old timing output made it hard to separate algorithm computation from MobileAgent simulation wait, query time, merge time, graph generation, and UI/Gephi layout time.

### Final Change
The code now logs detailed `[StageTimer]` timings around:

```text
GET_USERS
INITIALIZE
START_SIM
WAIT all MobileAgents Tweeting Completed
WAIT all RecommenderAgents Text Processing Complete
WAIT all RecommenderAgents Algorithm/TFIDF Done
WAIT merge completed
START Querying broadcast
WAIT querying/recommendation completed
GRAPH visualization stages
```

### Effects
The timers showed that the full runtime has separate layers:

```text
MobileAgent tweet-delay wait: about 6.3 minutes when enabled
Graph AutoLayout: about 60 seconds
Actual recommender computation: seconds to tens of seconds depending on algorithm/nodes
```

### Side Effects
More log output appears in the UI, but that helps students copy and analyze timings.

## 8. Simulate Tweet Delay Checkbox / Bypass

### Problem Before
The original program included a fixed MobileAgent tweet-delay simulation. This made sense as a simulation behavior, but it dominated performance experiments and made node speedup look marginal.

### Final Change
The GUI now includes a `Simulate Tweet Delay` checkbox. If enabled, the original wait behavior remains. If disabled, the program bypasses the artificial fixed wait and proceeds after the data/agent stage is ready.

### Effects
- Original simulation timing is still available.
- Performance experiments can isolate actual recommender computation.
- With the wait disabled, larger datasets show algorithm/node differences more clearly.

### Side Effects
Timing comparisons must state whether the wait was enabled or disabled. Otherwise results are not comparable.

## 9. Graph GML Builder and Visualization Hardening

### Problem Before
Graph output depended on external conversion and generic graph filenames. This could import stale graphs, skip graph creation, or leave the user unsure whether the current graph matched the current dataset.

### Final Change
`GraphGmlBuilder.java` was added and graph output is generated using dataset-specific files. StarterAgent imports the correct dataset-specific graph file.

### Effects
- Graph generation is more deterministic.
- Node count remains stable when the dataset has the same unique user/followee identities.
- Edge counts grow according to the current experiment file, not stale old files.

### Side Effects
Gephi layout can visually place connected nodes far apart. The reliable checks are edge count, node count, and Data Laboratory, not only visual distance.

## 10. Weka ARFF Class Quoting Fix

### Problem Before
ARFF class labels were written raw. `T&T Supermarket` was interpreted incorrectly by Weka, causing confusion matrices to split it into `T&T` and `Supermarket`.

### Old ARFF Pattern
```text
@attribute result {Longos,Wal-Mart,...,T&T Supermarket}
...,T&T Supermarket
```

### Final ARFF Pattern
```text
@attribute result {'Longos','Wal-Mart',...,'T&T Supermarket'}
...,'T&T Supermarket'
```

### Final Change
ARFF class-label helpers were added in the Java code so labels are quoted/escaped correctly when written to Weka files.

### Effects
- Weka sees `T&T Supermarket` as one class.
- Confusion matrices no longer split the class.
- Existing generated ARFF files must be regenerated to reflect the fix.

### Side Effects
This fixes serialization correctness. It does not by itself make SVM more accurate or diverse.

## 11. SVM Train/Test Split Fix

### Problem Before
SVM trains on aggregated user documents, not raw dataset rows. Therefore, balancing tweet rows does not necessarily balance user-level training instances.

The old train/test split could put small classes entirely into the test set first, leaving zero training examples. If a class has zero training users, SVM cannot learn it.

### Example User-Level Imbalance
Even a row-balanced 20x dataset can still have user counts like:

```text
Wal-Mart:        322 users
COSTCO:           95 users
Loblaws:          87 users
Sobeys:           30 users
Nofrills:         24 users
Longos:           20 users
Safeway:          19 users
IGA:               9 users
Metro:             9 users
T&T Supermarket:   3 users
```

### Final Change
`SvmReproducibility.java` was added. The split is now reproducible/stratified so class coverage is preserved as much as possible.

### Effects
- Small classes are less likely to disappear from SVM training.
- SVM outputs are easier to compare between runs and node counts.
- The confusion matrix represents a more valid training/test setup.

### Side Effects
If a class has only a very small number of users, there is still a statistical limitation. The code cannot invent real training diversity that is not in the dataset.

## 12. SVM Hard-Class Scoring Limitation

### Observed Issue
SVM often returned score maps like:

```text
Wal-Mart: 100.0
COSTCO: 0.0
IGA: 0.0
```

### Exact Reason
The SVM path uses hard class prediction. It predicts one class, then the scoring logic effectively gives that class a vote and gives other classes zero. In multi-node runs, those hard votes are merged.

### Final Position
The current work fixes data/parsing/splitting bugs. It does not replace SVM hard-vote scoring with probability/confidence scoring, because that would be an algorithm-design change rather than a bug fix.

### Side Effects
SVM can still look less diverse than Similarity, K-Means, or Doc2Vec.

## 13. Batch Recommendation UI Support

### Problem Before
The original UI was built around one selected recommendee. The professor wanted batch recommendation as an additional option so students can run multiple recommendations and copy timing output.

### Final Change
The GUI now includes a batch-user count field. A value of `1` preserves the old one-user behavior. Larger values allow a group of sequential selected users to be processed in a single simulation workflow where supported.

### Effects
- Single-user mode remains the default/safest graph-evolution workflow.
- Batch mode is available for throughput/performance experiments.
- Timing logs still appear in the main UI log.

### Side Effects
Batch mode gives an accumulated graph for a group. It is not the same as saving/inspecting the graph after each individual user unless intermediate exports are added.

## 14. Java Recommendation Selection Helper

### Problem Before
Some paths depended on an external Python probability script and, in v2.5-style paths, could refer to a hardcoded Windows Python path. That was fragile and platform-specific.

### Final Change
`BatchRecommendationSelector.java` was added to perform weighted recommendation selection in Java.

### Effects
- Removes one fragile Python dependency from supported selection paths.
- Makes selection portable across macOS/Linux/Windows Java environments.
- Can be self-tested.

### Side Effects
Stochastic selection can still choose different recommendations across runs. That is expected.

## 15. Doc2Vec Shared/Batch Redesign

### Problem Before
Naive multi-node Doc2Vec is not mathematically valid if each node trains a separate Doc2Vec model and then scores are merged. Separate Doc2Vec models have separate vector spaces, so their scores are not directly comparable.

### Final Change
The final branch adds:

```text
Doc2VecBatchRunner.java
doc2vec_shared.py
doc2vec_batch.py
```

The direction is to use a shared/batch Doc2Vec model path so repeated runs can avoid retraining isolated per-node models while keeping a comparable vector space.

### Effects
- Better foundation for Doc2Vec speedup without invalid independent vector-space merging.
- Repeated/batch recommendations can reuse shared work when dataset/settings are unchanged.

### Side Effects
Doc2Vec remains memory-sensitive, especially on synthetic 20x datasets.

## 16. Out-of-Memory Finding

### Observed Error
Large Doc2Vec/SVM-like runs produced:

```text
java.lang.OutOfMemoryError: Java heap space
at java.base/java.io.BufferedReader.readLine(...)
at TwitterGatherDataFollowers.userRyersonU.RecommenderAgent$RecommenderServiceBehaviour.action(RecommenderAgent.java:1886)
```

### Meaning
This is not normal training time. It means Java heap was exhausted while multiple recommender agents were reading/loading large intermediate files or data structures.

### Fix Direction Reflected in Final Work
The final code reduces unnecessary repeated loading and improves shared/batch paths, but very large artificial datasets may still require heap tuning and careful memory use.

## 17. Build / GUI Launch Fix

### Problem Before
The final folder did not open the GUI because compilation failed on a corrupted/missing jar, but the script continued launching JADE anyway. The result was:

```text
error: error reading lib/commons-logging-1.2.jar; zip END header not found
SEVERE: Cannot create agent controller: Class TwitterGatherDataFollowers.userRyersonU.ControllerAgent ... not found
```

### Final Change
`build_and_start_modern.sh` now treats compilation failure as fatal. The branch also adds `lib/pdfbox-app-2.0.31.jar` for the PDF rendering path used by the final UI/reporting work.

### Effects
- The GUI does not launch with stale/incomplete classes.
- Build errors are visible immediately.
- The missing PDFBox dependency is present.

### Side Effects
The build script is stricter, which is correct for a distributable version.

## 18. Self-Tests Added

### New Test Files
```text
TwitterGatherDataFollowers/Test/BatchRecommendationSelectorSelfTest.java
TwitterGatherDataFollowers/Test/Doc2VecBatchRunnerSelfTest.java
TwitterGatherDataFollowers/Test/ExperimentResetFilesSelfTest.java
TwitterGatherDataFollowers/Test/GraphGmlBuilderSelfTest.java
TwitterGatherDataFollowers/Test/SvmReproducibilitySelfTest.java
```

### Purpose
These self-tests cover deterministic helper logic added during this work: weighted selection, Doc2Vec batch runner behavior, reset cleanup, graph GML building, and SVM reproducibility/splitting.

## 19. Timing Conclusions from Observed Runs

### K-Means on Retail50K
```text
1 node algorithm/TF-IDF stage: about 38,305 ms
8 nodes algorithm/TF-IDF stage: about 15,335 ms
```

Interpretation: the recommender computation became about 60% faster with 8 nodes. When the original 6.3-minute MobileAgent wait is enabled, total runtime only improves slightly because the fixed wait dominates.

### Similarity on Retail50K
Similarity is already fast on one node, so 8 nodes can lose to coordination overhead unless the dataset is much larger or the fixed wait is bypassed.

### SVM on Retail50K
SVM is extremely fast after TF-IDF, often milliseconds per reducer. Multi-node SVM does not naturally win for a single user because overhead dominates. Batch mode can improve throughput but changes the experiment style from step-by-step graph evolution to group accumulation.

### 20x Dataset
The 20x dataset increases row count but keeps the same unique users/followees, so 628 nodes is normal. It is useful for timing/scaling, not for creating new social-network identities.

## 20. What Was Preserved

The final version preserves:

- the DSMP agent architecture;
- the original single-user default when batch count is `1`;
- the same core algorithms;
- the same graph concept of users/followees as nodes;
- the ability to run original simulated tweet delay;
- the ability to inspect timing in the UI.

## 21. What Changed Functionally

The final version changes:

- message transport from per-tweet to batched delivery;
- class-label parsing/serialization to support spaces safely;
- graph mapping/output to dataset-specific files;
- reset handling to avoid stale outputs;
- timing logs to expose real bottlenecks;
- optional bypass for the fixed tweet-delay wait;
- optional batch recommendation count;
- SVM split reproducibility/stratification;
- Doc2Vec shared/batch infrastructure;
- build script strictness.

## 22. Historical Attempts That Were Reverted or Superseded

During the chat, a few ideas were implemented experimentally and then reverted or superseded:

- A processed-user-document cache path was tried. It reduced repeated cleaning but moved too much work away from RecommenderAgent, so the final direction became raw-batch optimization inside RecommenderAgent.
- A target-parallel SVM batch design was explored. It risked changing recommendation context/semantics, so the final branch keeps safer behavior.
- v2.5 temporary fixes were explored in a separate folder. They are not part of this final v3.2 diff unless the same idea appears in the final files.
- Synthetic 20x datasets were created for scaling tests. They are data artifacts, not source-code changes.

# Appendix A: Full Exact Git Diff

The following diff is the authoritative before/after source-code record for this final documented version. It includes all source/library files changed between the original baseline and the final v3.2 branch.

```diff
diff --git BEFORE/TwitterGatherDataFollowers/Test/BatchRecommendationSelectorSelfTest.java AFTER/TwitterGatherDataFollowers/Test/BatchRecommendationSelectorSelfTest.java
new file mode 100644
index 0000000..06005a0
--- /dev/null
+++ AFTER/TwitterGatherDataFollowers/Test/BatchRecommendationSelectorSelfTest.java
@@ -0,0 +1,73 @@
+package TwitterGatherDataFollowers.userRyersonU;
+
+import java.io.File;
+import java.io.StringWriter;
+import java.nio.charset.StandardCharsets;
+import java.nio.file.Files;
+import java.util.Arrays;
+import java.util.Random;
+
+public final class BatchRecommendationSelectorSelfTest {
+	private BatchRecommendationSelectorSelfTest() {
+	}
+
+	public static void main(String[] args) throws Exception {
+		preservesRecommendationNamesContainingSpaces();
+		writesFullSelectedRecommendation();
+		handlesInvalidWeightsWithoutHanging();
+		appliesDoc2VecAdjustmentSafely();
+		writesBoundedFolloweeHistory();
+		System.out.println("BatchRecommendationSelectorSelfTest passed");
+	}
+
+	private static void preservesRecommendationNamesContainingSpaces() {
+		int selectedIndex = BatchRecommendationSelector.selectIndex(
+				Arrays.asList(0.0, 10.0, 0.0), false, new Random(1L));
+		String selectedName = Arrays.asList("Sobeys", "T&T Supermarket", "Longos").get(selectedIndex);
+		require("T&T Supermarket".equals(selectedName), "recommendation name was truncated");
+	}
+
+	private static void writesFullSelectedRecommendation() throws Exception {
+		File outputFile = File.createTempFile("batch-recommendation-choice", ".txt");
+		try {
+			BatchRecommendationSelector.writeSelectedRecommendation(outputFile, "T&T Supermarket");
+			String selectedName = new String(Files.readAllBytes(outputFile.toPath()), StandardCharsets.UTF_8);
+			require("T&T Supermarket".equals(selectedName), "selected recommendation file was truncated");
+		} finally {
+			outputFile.delete();
+		}
+	}
+
+	private static void handlesInvalidWeightsWithoutHanging() {
+		int selectedIndex = BatchRecommendationSelector.selectIndex(
+				Arrays.asList(Double.NaN, -1.0, null), false, new Random(1L));
+		require(selectedIndex == 0, "invalid weights did not use the deterministic fallback");
+	}
+
+	private static void appliesDoc2VecAdjustmentSafely() {
+		int selectedIndex = BatchRecommendationSelector.selectIndex(
+				Arrays.asList(40.0, 10.0, 0.0), true, new Random(1L));
+		require(selectedIndex == 1, "Doc2Vec adjustment did not preserve weighted selection");
+	}
+
+	private static void writesBoundedFolloweeHistory() throws Exception {
+		StringWriter writer = new StringWriter();
+		BatchRecommendationSelector.writeFolloweeChoices(
+				writer,
+				Arrays.asList("RobertBlake"),
+				"Original Store",
+				Arrays.asList("Sobeys", "T&T Supermarket", "Longos"),
+				1);
+		String newline = System.lineSeparator();
+		String expected = "RobertBlake Followes Original Store" + newline
+				+ "RobertBlake Followes Sobeys" + newline
+				+ "RobertBlake Followes T&T Supermarket" + newline;
+		require(expected.equals(writer.toString()), "followee history was not written correctly");
+	}
+
+	private static void require(boolean condition, String message) {
+		if (!condition) {
+			throw new AssertionError(message);
+		}
+	}
+}
diff --git BEFORE/TwitterGatherDataFollowers/Test/Doc2VecBatchRunnerSelfTest.java AFTER/TwitterGatherDataFollowers/Test/Doc2VecBatchRunnerSelfTest.java
new file mode 100644
index 0000000..1882a93
--- /dev/null
+++ AFTER/TwitterGatherDataFollowers/Test/Doc2VecBatchRunnerSelfTest.java
@@ -0,0 +1,109 @@
+package TwitterGatherDataFollowers.userRyersonU;
+
+import java.io.File;
+import java.nio.charset.StandardCharsets;
+import java.util.Arrays;
+import java.util.Base64;
+import java.util.TreeMap;
+
+public final class Doc2VecBatchRunnerSelfTest
+{
+	private Doc2VecBatchRunnerSelfTest()
+	{
+	}
+
+	public static void main(String[] args) throws Exception
+	{
+		parsesDistinctUsersAndCandidatesWithoutTruncation();
+		recordsPerUserErrorsAndCompletion();
+		ignoresScoresForUnrequestedUsers();
+		runsOneProcessForMultipleUsers();
+		System.out.println("Doc2VecBatchRunnerSelfTest passed");
+	}
+
+	private static void parsesDistinctUsersAndCandidatesWithoutTruncation()
+	{
+		Doc2VecBatchRunner.Result result = new Doc2VecBatchRunner.Result(
+				Arrays.asList("First User", "Second User"));
+		Doc2VecBatchRunner.parseOutputLine(scoreLine("First User", "T&T Supermarket", 0.75), result);
+		Doc2VecBatchRunner.parseOutputLine(scoreLine("Second User", "Candidate, With Comma", 0.25), result);
+
+		TreeMap<String,Double> firstScores = result.getScores("First User");
+		TreeMap<String,Double> secondScores = result.getScores("Second User");
+		require(firstScores.size() == 1 && firstScores.get("T&T Supermarket") == 0.75,
+				"first user's score was not parsed exactly");
+		require(secondScores.size() == 1 && secondScores.get("Candidate, With Comma") == 0.25,
+				"second user's score was not parsed exactly");
+	}
+
+	private static void recordsPerUserErrorsAndCompletion()
+	{
+		Doc2VecBatchRunner.Result result = new Doc2VecBatchRunner.Result(Arrays.asList("First User"));
+		Doc2VecBatchRunner.parseOutputLine(
+				"DOC2VEC_ERROR\t" + encode("First User") + "\t" + encode("missing target"), result);
+		Doc2VecBatchRunner.parseOutputLine("DOC2VEC_DONE\t" + encode("First User"), result);
+
+		require("missing target".equals(result.getErrors().get("First User")), "per-user error was lost");
+		require(result.isCompleted("First User"), "completed user was not recorded");
+	}
+
+	private static void ignoresScoresForUnrequestedUsers()
+	{
+		Doc2VecBatchRunner.Result result = new Doc2VecBatchRunner.Result(Arrays.asList("Requested User"));
+		Doc2VecBatchRunner.parseOutputLine(scoreLine("Unexpected User", "Candidate", 1.0), result);
+		require(result.getScores("Requested User").isEmpty(), "unexpected user's score leaked into results");
+	}
+
+	private static void runsOneProcessForMultipleUsers() throws Exception
+	{
+		File scriptFile = File.createTempFile("doc2vec-batch-runner-test", ".py");
+		File datasetFile = File.createTempFile("doc2vec-batch-runner-dataset", ".txt");
+		try
+		{
+			String script = "import base64, sys\n"
+					+ "encode = lambda value: base64.b64encode(value.encode('utf-8')).decode('ascii')\n"
+					+ "with open(sys.argv[6], 'r', encoding='utf-8') as users_file:\n"
+					+ "    users = [line.rstrip('\\r\\n') for line in users_file]\n"
+					+ "for user in users:\n"
+					+ "    candidate = 'Candidate for ' + user\n"
+					+ "    print('DOC2VEC_SCORE\\t%s\\t%s\\t0.5' % (encode(user), encode(candidate)))\n"
+					+ "    print('DOC2VEC_DONE\\t%s' % encode(user))\n";
+			java.nio.file.Files.write(scriptFile.toPath(), script.getBytes(StandardCharsets.UTF_8));
+
+			Doc2VecBatchRunner.Result result = Doc2VecBatchRunner.run(
+					"python", scriptFile, 3, datasetFile, 1, 1, 1,
+					Arrays.asList("First User", "Second User"));
+
+			require(result.getExitCode() == 0, "batch process did not exit successfully");
+			require(result.isCompleted("First User") && result.isCompleted("Second User"),
+					"batch process did not complete every requested user");
+			require(result.getScores("First User").get("Candidate for First User") == 0.5,
+					"first user's process score was not preserved");
+			require(result.getScores("Second User").get("Candidate for Second User") == 0.5,
+					"second user's process score was not preserved");
+		}
+		finally
+		{
+			scriptFile.delete();
+			datasetFile.delete();
+		}
+	}
+
+	private static String scoreLine(String user, String candidate, double score)
+	{
+		return "DOC2VEC_SCORE\t" + encode(user) + "\t" + encode(candidate) + "\t" + score;
+	}
+
+	private static String encode(String value)
+	{
+		return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
+	}
+
+	private static void require(boolean condition, String message)
+	{
+		if (!condition)
+		{
+			throw new AssertionError(message);
+		}
+	}
+}
diff --git BEFORE/TwitterGatherDataFollowers/Test/ExperimentResetFilesSelfTest.java AFTER/TwitterGatherDataFollowers/Test/ExperimentResetFilesSelfTest.java
new file mode 100644
index 0000000..3ec7392
--- /dev/null
+++ AFTER/TwitterGatherDataFollowers/Test/ExperimentResetFilesSelfTest.java
@@ -0,0 +1,78 @@
+package TwitterGatherDataFollowers.userRyersonU;
+
+import java.io.File;
+import java.nio.charset.StandardCharsets;
+import java.nio.file.Files;
+import java.nio.file.Path;
+import java.util.List;
+
+public final class ExperimentResetFilesSelfTest {
+	private ExperimentResetFilesSelfTest() {
+	}
+
+	public static void main(String[] args) throws Exception {
+		resetsOnlyTransientExperimentOutputs();
+		cleansDatasetSelectedAfterReset();
+		System.out.println("ExperimentResetFilesSelfTest passed");
+	}
+
+	private static void resetsOnlyTransientExperimentOutputs() throws Exception {
+		Path project = Files.createTempDirectory("experiment-reset");
+		Path importantStuff = Files.createDirectories(project.resolve("important-stuff"));
+		File selectedDataset = write(project, "Retail 50K.txt");
+
+		File selectedEdges = write(importantStuff, "edges-numbers-Retail_50K.txt");
+		File selectedGraph = write(importantStuff, "edges-numbers-Retail_50K.gml");
+		File sharedGraph = write(importantStuff, "graph.gexf");
+		File recommendationChoices = write(importantStuff, "outPutSepFolloweeResults.txt");
+		File recommendations = write(project, "recommendations_lists.txt");
+		File tweetCounts = write(project, "tweetCounts.txt");
+
+		File generatedMapping = write(importantStuff, "name-number-Retail 50K.txt");
+		File unrelatedEdges = write(importantStuff, "edges-numbers-Other.txt");
+		File archivedResult = write(Files.createDirectories(project.resolve("Results")), "history.txt");
+		File doc2VecCache = write(Files.createDirectories(project.resolve("Stored_Doc2VecModel")), "model.bin");
+
+		List<File> failedDeletes = ExperimentResetFiles.resetExperimentOutputs(project.toFile(), selectedDataset);
+
+		require(failedDeletes.isEmpty(), "reset reported failed deletes");
+		require(!selectedEdges.exists(), "selected dataset edge list was preserved");
+		require(!selectedGraph.exists(), "selected dataset graph was preserved");
+		require(!sharedGraph.exists(), "shared graph output was preserved");
+		require(!recommendationChoices.exists(), "recommendation-side transient output was preserved");
+		require(!recommendations.exists(), "recommendations list was preserved");
+		require(!tweetCounts.exists(), "tweet counts were preserved");
+
+		require(selectedDataset.exists(), "selected dataset was deleted");
+		require(generatedMapping.exists(), "generated name-number mapping was deleted");
+		require(unrelatedEdges.exists(), "unrelated dataset edge list was deleted");
+		require(archivedResult.exists(), "archived result was deleted");
+		require(doc2VecCache.exists(), "Doc2Vec cache was deleted");
+	}
+
+	private static void cleansDatasetSelectedAfterReset() throws Exception {
+		Path project = Files.createTempDirectory("experiment-reset-selection");
+		Path importantStuff = Files.createDirectories(project.resolve("important-stuff"));
+		File selectedDataset = write(project, "Next Dataset.txt");
+		File selectedEdges = write(importantStuff, "edges-numbers-Next_Dataset.txt");
+		File sharedOutput = write(importantStuff, "outPutSep.txt");
+
+		List<File> failedDeletes = ExperimentResetFiles.resetDatasetOutputs(project.toFile(), selectedDataset);
+
+		require(failedDeletes.isEmpty(), "dataset selection cleanup reported failed deletes");
+		require(!selectedEdges.exists(), "newly selected dataset edge list was preserved");
+		require(sharedOutput.exists(), "dataset selection cleanup deleted shared output");
+	}
+
+	private static File write(Path directory, String fileName) throws Exception {
+		Path path = directory.resolve(fileName);
+		Files.write(path, "test".getBytes(StandardCharsets.UTF_8));
+		return path.toFile();
+	}
+
+	private static void require(boolean condition, String message) {
+		if (!condition) {
+			throw new AssertionError(message);
+		}
+	}
+}
diff --git BEFORE/TwitterGatherDataFollowers/Test/GraphGmlBuilderSelfTest.java AFTER/TwitterGatherDataFollowers/Test/GraphGmlBuilderSelfTest.java
new file mode 100644
index 0000000..5055b5d
--- /dev/null
+++ AFTER/TwitterGatherDataFollowers/Test/GraphGmlBuilderSelfTest.java
@@ -0,0 +1,122 @@
+package TwitterGatherDataFollowers.userRyersonU;
+
+import java.io.File;
+import java.io.IOException;
+import java.nio.charset.StandardCharsets;
+import java.nio.file.Files;
+import java.nio.file.Path;
+
+public final class GraphGmlBuilderSelfTest {
+	private GraphGmlBuilderSelfTest() {
+	}
+
+	public static void main(String[] args) throws Exception {
+		buildsACompleteValidatedGraph();
+		buildsGraphWhenThereAreNoEdges();
+		rejectsUnknownNodeIdsWithoutReplacingPreviousOutput();
+		rejectsMalformedEdges();
+		System.out.println("GraphGmlBuilderSelfTest passed");
+	}
+
+	private static void buildsACompleteValidatedGraph() throws Exception {
+		Path directory = Files.createTempDirectory("graph-gml-builder");
+		File mappings = write(directory, "name-number.txt",
+				"Wal-Mart\t1\nT&T Supermarket\t2\nDennis Harrison\t3\n");
+		File edges = write(directory, "edges.txt", "3 1\n3 2\n");
+		File output = directory.resolve("graph.gml").toFile();
+
+		GraphGmlBuilder.Result result = GraphGmlBuilder.build(edges, mappings, output);
+		String gml = read(output);
+
+		require(result.getNodeCount() == 3, "unexpected node count");
+		require(result.getEdgeCount() == 2, "unexpected edge count");
+		require(result.getOutputFile().equals(output), "unexpected output file");
+		require(count(gml, "  node\n") == 3, "GML did not contain all mapped nodes");
+		require(count(gml, "  edge\n") == 2, "GML did not contain all edges");
+		require(gml.contains("    source 3\n    target 2\n"), "GML edge endpoints were incorrect");
+	}
+
+	private static void buildsGraphWhenThereAreNoEdges() throws Exception {
+		Path directory = Files.createTempDirectory("graph-gml-builder-empty");
+		File mappings = write(directory, "name-number.txt", "First User\t1\nSecond User\t2\n");
+		File edges = write(directory, "edges.txt", "");
+		File output = directory.resolve("graph.gml").toFile();
+
+		GraphGmlBuilder.Result result = GraphGmlBuilder.build(edges, mappings, output);
+		require(result.getNodeCount() == 2, "empty-edge graph lost mapped nodes");
+		require(result.getEdgeCount() == 0, "empty-edge graph unexpectedly contained edges");
+		require(output.isFile(), "empty-edge graph was not written");
+	}
+
+	private static void rejectsUnknownNodeIdsWithoutReplacingPreviousOutput() throws Exception {
+		Path directory = Files.createTempDirectory("graph-gml-builder-atomic");
+		File mappings = write(directory, "name-number.txt", "Known User\t1\n");
+		File edges = write(directory, "edges.txt", "1 2\n");
+		File output = write(directory, "graph.gml", "previous valid graph");
+
+		expectIOException(new CheckedOperation() {
+			@Override
+			public void run() throws Exception {
+				GraphGmlBuilder.build(edges, mappings, output);
+			}
+		}, "unknown node ID was accepted");
+
+		require(read(output).equals("previous valid graph"), "invalid conversion replaced the previous graph");
+	}
+
+	private static void rejectsMalformedEdges() throws Exception {
+		Path directory = Files.createTempDirectory("graph-gml-builder-malformed");
+		File mappings = write(directory, "name-number.txt", "Known User\t1\n");
+		File edges = write(directory, "edges.txt", "1 not-a-node\n");
+		File output = directory.resolve("graph.gml").toFile();
+
+		expectIOException(new CheckedOperation() {
+			@Override
+			public void run() throws Exception {
+				GraphGmlBuilder.build(edges, mappings, output);
+			}
+		}, "malformed edge was accepted");
+
+		require(!output.exists(), "malformed conversion produced an output graph");
+	}
+
+	private static File write(Path directory, String fileName, String content) throws IOException {
+		Path path = directory.resolve(fileName);
+		Files.write(path, content.getBytes(StandardCharsets.UTF_8));
+		return path.toFile();
+	}
+
+	private static String read(File file) throws IOException {
+		return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
+	}
+
+	private static int count(String value, String target) {
+		int occurrences = 0;
+		int index = 0;
+		while ((index = value.indexOf(target, index)) >= 0) {
+			occurrences++;
+			index += target.length();
+		}
+		return occurrences;
+	}
+
+	private static void expectIOException(CheckedOperation operation, String failureMessage) throws Exception {
+		try {
+			operation.run();
+		}
+		catch (IOException expected) {
+			return;
+		}
+		throw new AssertionError(failureMessage);
+	}
+
+	private static void require(boolean condition, String message) {
+		if (!condition) {
+			throw new AssertionError(message);
+		}
+	}
+
+	private interface CheckedOperation {
+		void run() throws Exception;
+	}
+}
diff --git BEFORE/TwitterGatherDataFollowers/Test/SvmReproducibilitySelfTest.java AFTER/TwitterGatherDataFollowers/Test/SvmReproducibilitySelfTest.java
new file mode 100644
index 0000000..450e0d8
--- /dev/null
+++ AFTER/TwitterGatherDataFollowers/Test/SvmReproducibilitySelfTest.java
@@ -0,0 +1,119 @@
+package TwitterGatherDataFollowers.userRyersonU;
+
+import java.io.StringReader;
+import java.util.ArrayList;
+import java.util.Arrays;
+import java.util.LinkedHashMap;
+import java.util.List;
+import java.util.Map;
+
+import weka.classifiers.functions.SMO;
+import weka.core.Instances;
+
+public final class SvmReproducibilitySelfTest {
+	private SvmReproducibilitySelfTest() {
+	}
+
+	public static void main(String[] args) throws Exception {
+		producesTheSameSplitRepeatedly();
+		ignoresInputMapAndUserOrdering();
+		preservesStratifiedClassCounts();
+		keepsSingletonClassesInTraining();
+		sortsClassLabelsDeterministically();
+		appliesTheStableModelSeed();
+		seededModelsProduceIdenticalScores();
+		System.out.println("SvmReproducibilitySelfTest passed");
+	}
+
+	private static void producesTheSameSplitRepeatedly() {
+		Map<String,List<String>> usersByClass = standardUsersByClass();
+		SvmReproducibility.Split first = SvmReproducibility.stratifiedSplit(usersByClass, 0.30);
+		SvmReproducibility.Split second = SvmReproducibility.stratifiedSplit(usersByClass, 0.30);
+		require(first.getTrainUsers().equals(second.getTrainUsers()), "training split changed between calls");
+		require(first.getTestUsers().equals(second.getTestUsers()), "test split changed between calls");
+	}
+
+	private static void ignoresInputMapAndUserOrdering() {
+		Map<String,List<String>> firstInput = standardUsersByClass();
+		Map<String,List<String>> reorderedInput = new LinkedHashMap<String,List<String>>();
+		reorderedInput.put("T&T Supermarket", Arrays.asList("t4", "t3", "t2", "t1"));
+		reorderedInput.put("Safeway", Arrays.asList("s5", "s4", "s3", "s2", "s1"));
+
+		SvmReproducibility.Split first = SvmReproducibility.stratifiedSplit(firstInput, 0.30);
+		SvmReproducibility.Split reordered = SvmReproducibility.stratifiedSplit(reorderedInput, 0.30);
+		require(first.getTrainUsers().equals(reordered.getTrainUsers()), "training split depends on input ordering");
+		require(first.getTestUsers().equals(reordered.getTestUsers()), "test split depends on input ordering");
+	}
+
+	private static void preservesStratifiedClassCounts() {
+		SvmReproducibility.Split split = SvmReproducibility.stratifiedSplit(standardUsersByClass(), 0.30);
+		require(split.getTrainUsers().size() == 7, "unexpected training set size");
+		require(split.getTestUsers().size() == 2, "unexpected test set size");
+	}
+
+	private static void keepsSingletonClassesInTraining() {
+		Map<String,List<String>> usersByClass = new LinkedHashMap<String,List<String>>();
+		usersByClass.put("Only Class", Arrays.asList("only-user"));
+		SvmReproducibility.Split split = SvmReproducibility.stratifiedSplit(usersByClass, 0.30);
+		require(split.getTrainUsers().equals(Arrays.asList("only-user")),
+				"singleton class was not kept in training");
+		require(split.getTestUsers().isEmpty(), "singleton class was incorrectly placed in testing");
+	}
+
+	private static void sortsClassLabelsDeterministically() {
+		ArrayList<String> sorted = SvmReproducibility.sortedNonNullValues(
+				Arrays.asList("T&T Supermarket", "Safeway", null, "IGA"));
+		require(sorted.equals(Arrays.asList("IGA", "Safeway", "T&T Supermarket")),
+				"class labels were not sorted");
+	}
+
+	private static void appliesTheStableModelSeed() {
+		SMO svmModel = new SMO();
+		SvmReproducibility.applyModelSeed(svmModel);
+		require(svmModel.getRandomSeed() == SvmReproducibility.MODEL_SEED,
+				"stable SMO model seed was not applied");
+	}
+
+	private static void seededModelsProduceIdenticalScores() throws Exception {
+		String arff = "@relation deterministic\n"
+				+ "@attribute x numeric\n"
+				+ "@attribute result {'A','B'}\n"
+				+ "@data\n"
+				+ "0.0,'A'\n"
+				+ "0.1,'A'\n"
+				+ "0.9,'B'\n"
+				+ "1.0,'B'\n";
+		Instances data = new Instances(new StringReader(arff));
+		data.setClassIndex(data.numAttributes() - 1);
+
+		SMO first = configuredModel();
+		SMO second = configuredModel();
+		first.buildClassifier(data);
+		second.buildClassifier(data);
+
+		double[] firstScores = first.distributionForInstance(data.instance(0));
+		double[] secondScores = second.distributionForInstance(data.instance(0));
+		require(Arrays.equals(firstScores, secondScores), "seeded SMO scores changed between models");
+	}
+
+	private static SMO configuredModel() {
+		SMO svmModel = new SMO();
+		svmModel.setC(0.1);
+		svmModel.setBuildCalibrationModels(true);
+		SvmReproducibility.applyModelSeed(svmModel);
+		return svmModel;
+	}
+
+	private static Map<String,List<String>> standardUsersByClass() {
+		Map<String,List<String>> usersByClass = new LinkedHashMap<String,List<String>>();
+		usersByClass.put("Safeway", Arrays.asList("s1", "s2", "s3", "s4", "s5"));
+		usersByClass.put("T&T Supermarket", Arrays.asList("t1", "t2", "t3", "t4"));
+		return usersByClass;
+	}
+
+	private static void require(boolean condition, String message) {
+		if (!condition) {
+			throw new AssertionError(message);
+		}
+	}
+}
diff --git BEFORE/TwitterGatherDataFollowers/userRyersonU/BatchRecommendationSelector.java AFTER/TwitterGatherDataFollowers/userRyersonU/BatchRecommendationSelector.java
new file mode 100644
index 0000000..d6b7d4f
--- /dev/null
+++ AFTER/TwitterGatherDataFollowers/userRyersonU/BatchRecommendationSelector.java
@@ -0,0 +1,112 @@
+package TwitterGatherDataFollowers.userRyersonU;
+
+import java.io.BufferedWriter;
+import java.io.File;
+import java.io.FileWriter;
+import java.io.IOException;
+import java.io.Writer;
+import java.util.Collection;
+import java.util.List;
+import java.util.Random;
+
+final class BatchRecommendationSelector {
+	private static final int MAX_WEIGHTED_CHOICES = 3;
+	private static final double DOC2VEC_FIRST_WEIGHT_ADJUSTMENT = 50.0;
+
+	private BatchRecommendationSelector() {
+	}
+
+	static int selectIndex(List<Double> weights, boolean adjustDoc2VecFirstWeight, Random random) {
+		if (weights == null || weights.isEmpty()) {
+			return -1;
+		}
+		if (random == null) {
+			throw new IllegalArgumentException("random must not be null");
+		}
+
+		int choiceCount = Math.min(MAX_WEIGHTED_CHOICES, weights.size());
+		double[] usableWeights = new double[choiceCount];
+		double totalWeight = 0.0;
+
+		for (int i = 0; i < choiceCount; i++) {
+			Double weightObject = weights.get(i);
+			double weight = weightObject == null ? 0.0 : weightObject.doubleValue();
+			if (i == 0 && adjustDoc2VecFirstWeight) {
+				weight -= DOC2VEC_FIRST_WEIGHT_ADJUSTMENT;
+			}
+			if (!Double.isFinite(weight) || weight < 0.0) {
+				weight = 0.0;
+			}
+			usableWeights[i] = weight;
+			totalWeight += weight;
+		}
+
+		if (totalWeight <= 0.0) {
+			return 0;
+		}
+
+		double selectedWeight = random.nextDouble() * totalWeight;
+		double cumulativeWeight = 0.0;
+		for (int i = 0; i < choiceCount; i++) {
+			cumulativeWeight += usableWeights[i];
+			if (selectedWeight < cumulativeWeight) {
+				return i;
+			}
+		}
+		return choiceCount - 1;
+	}
+
+	static void writeSelectedRecommendation(File outputFile, String selectedRecommendation) throws IOException {
+		if (outputFile == null) {
+			throw new IllegalArgumentException("outputFile must not be null");
+		}
+		File parent = outputFile.getParentFile();
+		if (parent != null && !parent.exists()) {
+			parent.mkdirs();
+		}
+		try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile, false))) {
+			if (selectedRecommendation != null) {
+				writer.write(selectedRecommendation);
+			}
+		}
+	}
+
+	static void writeFolloweeChoices(
+			Writer writer,
+			Collection<String> users,
+			String originalFollowee,
+			List<String> recommendations,
+			int selectedRecommendationIndex) throws IOException {
+		if (writer == null) {
+			throw new IllegalArgumentException("writer must not be null");
+		}
+		if (users == null || users.isEmpty()) {
+			return;
+		}
+
+		int finalRecommendationIndex = -1;
+		if (recommendations != null && !recommendations.isEmpty() && selectedRecommendationIndex >= 0) {
+			finalRecommendationIndex = Math.min(selectedRecommendationIndex, recommendations.size() - 1);
+		}
+
+		for (String user : users) {
+			if (user == null || user.isEmpty()) {
+				continue;
+			}
+			writeFolloweeLine(writer, user, originalFollowee);
+			for (int i = 0; i <= finalRecommendationIndex; i++) {
+				writeFolloweeLine(writer, user, recommendations.get(i));
+			}
+		}
+	}
+
+	private static void writeFolloweeLine(Writer writer, String user, String followee) throws IOException {
+		if (followee == null || followee.isEmpty()) {
+			return;
+		}
+		writer.write(user);
+		writer.write(" Followes ");
+		writer.write(followee);
+		writer.write(System.lineSeparator());
+	}
+}
diff --git BEFORE/TwitterGatherDataFollowers/userRyersonU/ControllerAgent.java AFTER/TwitterGatherDataFollowers/userRyersonU/ControllerAgent.java
index 1894798..f6b4b53 100644
--- BEFORE/TwitterGatherDataFollowers/userRyersonU/ControllerAgent.java
+++ AFTER/TwitterGatherDataFollowers/userRyersonU/ControllerAgent.java
@@ -6,19 +6,22 @@ import java.sql.ResultSet;
 import java.sql.SQLException;
 import java.sql.Statement;
 import java.sql.Timestamp;
-import java.text.DateFormat;
-import java.text.ParseException;
-import java.text.SimpleDateFormat;
-import java.util.*;
+import java.text.DateFormat;
+import java.text.ParseException;
+import java.text.SimpleDateFormat;
+import java.time.LocalDateTime;
+import java.util.*;
 import java.lang.*;   //added by Sepide
 import java.util.regex.Matcher;
 import java.util.regex.Pattern;
 
 import org.apache.commons.math3.random.RandomDataGenerator;
 
-import java.io.*;
-
-import jade.lang.acl.*;
+import java.io.*;
+
+import javax.swing.SwingUtilities;
+
+import jade.lang.acl.*;
 import jade.wrapper.AgentContainer;
 import jade.wrapper.AgentController;
 import jade.wrapper.StaleProxyException;
@@ -147,9 +150,10 @@ public class ControllerAgent extends GuiAgent {
 	public static final int QUIT = 0;
 	public static final int START_SIM = 1;
 	public static final int INITIALIZE = 2;
-	public static final int GET_USERS = 3;
-	public static final int COLLECT_DATA =  4;
-	public static final int START_USER_GEN_SIM =  5;
+	public static final int GET_USERS = 3;
+	public static final int COLLECT_DATA =  4;
+	public static final int START_USER_GEN_SIM =  5;
+	public static final int RESET_EXPERIMENT = 6;
 	public static final int FROM_ARTIFICIAL = 3;
 	public static final int FROM_GENERATION = 2;
 	public static final int FROM_DB = 1;
@@ -202,8 +206,9 @@ public class ControllerAgent extends GuiAgent {
 
 	AgentController agentController = null;
 
-	private boolean initialized;
-	private boolean firstRun;
+	private boolean initialized;
+	private boolean firstRun;
+	private boolean resetPendingDatasetSelection;
 
 	private InMemoryDb localDb;
 	private InMemoryDb availableDb;
@@ -253,8 +258,8 @@ public class ControllerAgent extends GuiAgent {
 	
 	private int numArtificialTweets = 0;
 	
-	private Map<String,String> userFolloweeMap = new LinkedHashMap<String,String>(); //list of users and their followeeNames before processing (may have extra since processing can remove users) From reading text file 
-	private Map<String,Integer> followeeFollowerCounts = new LinkedHashMap<String,Integer>(); //number of followers for each followee/class
+	private Map<String,String> userFolloweeMap = new LinkedHashMap<String,String>(); //list of users and their followeeNames before processing (may have extra since processing can remove users) From reading text file 
+	private Map<String,Integer> followeeFollowerCounts = new LinkedHashMap<String,Integer>(); //number of followers for each followee/class
 	private Map<String,List<String>> followeeFollowers = new LinkedHashMap<String,List<String>>(); //list of followees and their followers
 	private List<String> testSetUsers; //list of users in test set
 	private List<String> trainSetUsers; //list of users in training set
@@ -288,9 +293,10 @@ public class ControllerAgent extends GuiAgent {
 		agentName = getLocalName();
 		agentNameAID = getAID();
 
-		initialized = false;
-		firstRun = true;
-		readFrom = FROM_TEXT;
+		initialized = false;
+		firstRun = true;
+		resetPendingDatasetSelection = false;
+		readFrom = FROM_TEXT;
 		textFile = null;
 		corpusGenFile = null;
 		//readFrom = FROM_DB;
@@ -374,10 +380,14 @@ public class ControllerAgent extends GuiAgent {
 		{
 			collectData(ev);
 		}
-		else if (actionToPerform == START_USER_GEN_SIM)
-		{
-			startUserGenSimulation();
-		}
+		else if (actionToPerform == START_USER_GEN_SIM)
+		{
+			startUserGenSimulation();
+		}
+		else if (actionToPerform == RESET_EXPERIMENT)
+		{
+			resetExperiment();
+		}
 	}
 
 	private void setUpWorkContainer()
@@ -463,8 +473,8 @@ public class ControllerAgent extends GuiAgent {
 				
 	}
 	
-	public void startUserGenSimulation()
-	{
+	public void startUserGenSimulation()
+	{
 		if (corpusGenFile != null)
 		{
 			Object[] userGenSimAgentArgs = new Object[3];
@@ -492,11 +502,31 @@ public class ControllerAgent extends GuiAgent {
 		{
 			myGui.noFileSelected();
 		}
-		
-	}
-	
-	public void loadUsers(GuiEvent ev)
-	{
+		
+	}
+	
+	private void logStageDuration(String label, long startTime, long endTime)
+	{
+		String line = "[StageTimer] " + label + ": " + (endTime - startTime) + " ms";
+		System.out.println(line);
+		if (myGui != null)
+		{
+			myGui.appendResult(line);
+		}
+	}
+	
+	private void logStagePoint(String label)
+	{
+		String line = "[StageTimer] " + label + " at " + LocalDateTime.now();
+		System.out.println(line);
+		if (myGui != null)
+		{
+			myGui.appendResult(line);
+		}
+	}
+	
+	public void loadUsers(GuiEvent ev)
+	{
 		boolean readyToInitialize = true;
 		
 		localDb.clearDb();
@@ -507,12 +537,13 @@ public class ControllerAgent extends GuiAgent {
 		beginDate = (String) ev.getParameter(2);
 		endDate = (String) ev.getParameter(3);
 		numFollowees = (Integer) ev.getParameter(4);
-		numFollowers = (Integer) ev.getParameter(5);
-		numTweetsGenerated = (Integer) ev.getParameter(6);
-		
-		myGui.disableList();
-
-		long startTime = System.currentTimeMillis();
+		numFollowers = (Integer) ev.getParameter(5);
+		numTweetsGenerated = (Integer) ev.getParameter(6);
+
+		myGui.disableList();
+
+		long startTime = System.currentTimeMillis();
+		logStagePoint("GET_USERS started");
 
 		if (readFrom == FROM_ARTIFICIAL)
 		{
@@ -645,14 +676,20 @@ public class ControllerAgent extends GuiAgent {
 		if (readFrom == FROM_TEXT)
 		{
 
-			if (textFile != null)
-			{
-				readFromTextFile();
-
-				ArrayList<Tweet> availableTweets;
-				availableTweets = getAvailableTweets();
-							
-				availableDb.setTweets(availableTweets);
+			if (textFile != null)
+			{
+				long readTextStartTime = System.currentTimeMillis();
+				readFromTextFile();
+				logStageDuration("GET_USERS read text dataset", readTextStartTime, System.currentTimeMillis());
+
+				ArrayList<Tweet> availableTweets;
+				long availableTweetsStartTime = System.currentTimeMillis();
+				availableTweets = getAvailableTweets();
+				logStageDuration("GET_USERS build available tweet/user list", availableTweetsStartTime, System.currentTimeMillis());
+							
+				long setAvailableStartTime = System.currentTimeMillis();
+				availableDb.setTweets(availableTweets);
+				logStageDuration("GET_USERS store available tweets in memory", setAvailableStartTime, System.currentTimeMillis());
 
 				//			for (Tweet t : datasetAvailable)
 				//			{
@@ -723,21 +760,148 @@ public class ControllerAgent extends GuiAgent {
 			myGui.enableAllButtons();
 			myGui.enableList();
 			myGui.changeUserListTitle(readFrom);
-			long endTime = System.currentTimeMillis();
-			System.out.println("Load time: "+(endTime-startTime)+"ms");
-			System.out.println("READY TO INITIALIZE MULTI-AGENT SYSTEM");
-			myGui.showMessageBox("get users");
+			long endTime = System.currentTimeMillis();
+			System.out.println("Load time: "+(endTime-startTime)+"ms");
+			logStageDuration("GET_USERS total", startTime, endTime);
+			System.out.println("READY TO INITIALIZE MULTI-AGENT SYSTEM");
+			resetPendingDatasetSelection = false;
+			myGui.showMessageBox("get users");
 		}
 		else
 		{
 			myGui.noFileSelected();
 			myGui.enableAllButtons();
 		}
-		
-	} //loadUsers
-
-	public void startSimulation()
-	{
+
+	} //loadUsers
+
+	private void resetExperiment()
+	{
+		System.out.println("Resetting experiment");
+
+		stopExperimentContainers();
+		File selectedTextDataset = readFrom == FROM_TEXT ? textFile : null;
+		reportFailedExperimentOutputDeletes(
+				ExperimentResetFiles.resetExperimentOutputs(new File("."), selectedTextDataset));
+		clearExperimentState();
+		resetPendingDatasetSelection = true;
+		setUpWorkContainer();
+		setUpUserSimContainer();
+
+		if (myGui != null)
+		{
+			SwingUtilities.invokeLater(new Runnable() {
+				@Override
+				public void run() {
+					myGui.resetForNewExperiment();
+				}
+			});
+		}
+
+		System.out.println("Experiment reset complete");
+	}
+
+	private void stopExperimentContainers()
+	{
+		for (AgentController currentAgentController : new ArrayList<AgentController>(listOfAgentControllers))
+		{
+			try
+			{
+				currentAgentController.kill();
+			}
+			catch (StaleProxyException e)
+			{
+				System.err.println("Could not stop experiment agent during reset: " + e.getMessage());
+			}
+		}
+
+		stopContainer(workContainer, "workContainer");
+		stopContainer(userSimContainer, "userSimContainer");
+		workContainer = null;
+		userSimContainer = null;
+		agentController = null;
+	}
+
+	private void stopContainer(AgentContainer container, String containerName)
+	{
+		if (container == null)
+		{
+			return;
+		}
+
+		try
+		{
+			container.kill();
+		}
+		catch (StaleProxyException e)
+		{
+			System.err.println("Could not stop " + containerName + " during reset: " + e.getMessage());
+		}
+	}
+
+	private void clearExperimentState()
+	{
+		initialized = false;
+		firstRun = true;
+		algorithmRec = K_MEANS;
+		readFrom = FROM_TEXT;
+		totalTweetLimit = 0;
+		numFollowers = 0;
+		numFollowees = 0;
+		numTweetsGenerated = 0;
+		numRecAgents = 1;
+		numArtificialTweets = 0;
+		totalUsers = 0;
+		totalDocuments = 0;
+		tweetsReadCount = 0;
+		tweetDelay = 0;
+		referenceUser = REFERENCE_USER;
+		beginDate = null;
+		endDate = null;
+		textFile = null;
+		corpusGenFile = null;
+		allRecAgents = null;
+
+		localDb = new InMemoryDb();
+		availableDb = new InMemoryDb();
+
+		usersRec.clear();
+		listOfUsers.clear();
+		listOfAgents.clear();
+		listOfAgentControllers.clear();
+		allUniqueDateTimes.clear();
+		allUserFollowTweetCounts.clear();
+		currentBagWords.clear();
+		userTweetCounts.clear();
+		userFolloweeMap.clear();
+		followeeFollowerCounts.clear();
+		followeeFollowers.clear();
+		allUniqueDocTerms.clear();
+		tweetIdUser.clear();
+		tweetIdText.clear();
+		tweetIdDocumentVector.clear();
+		allTermsDocumentFreq.clear();
+		allUserDocuments.clear();
+		aggregatedUserTweets.clear();
+
+		userFollowee = null;
+		testSetUsers = null;
+		trainSetUsers = null;
+		allUserDocumentsTFIDF = null;
+		datasetFollowees = null;
+		userTfidfVector = null;
+	}
+
+	private void reportFailedExperimentOutputDeletes(List<File> failedDeletes)
+	{
+		for (File failedDelete : failedDeletes)
+		{
+			System.err.println("Could not delete old experiment file: " + failedDelete.getPath());
+		}
+	}
+
+	public void startSimulation()
+	{
 		try{
 			Object[] starterAgentArgs = new Object[13];							
 			starterAgentArgs[0] = getAID();										
@@ -849,8 +1013,9 @@ public class ControllerAgent extends GuiAgent {
 		createDir(directoryNameTiming);
 		createDir(directoryNameRecommendation);
 		
-		long startTime = System.currentTimeMillis();
-		//@Jason
+		long startTime = System.currentTimeMillis();
+		logStagePoint("INITIALIZE started nodes="+numRecAgents+" algorithm="+algorithmRec+" users="+listOfUsers.size());
+		//@Jason
 		
 		if (readFrom == FROM_ARTIFICIAL)
 		{
@@ -964,7 +1129,7 @@ public class ControllerAgent extends GuiAgent {
 					
 
 
-					long userTweetDelay = calculateTweetDelay(user);
+					long userTweetDelay = getConfiguredTweetDelay(user);
 
 					
 //					userFollowTweetCounts = new LinkedHashMap<String,Integer>();
@@ -1208,7 +1373,7 @@ public class ControllerAgent extends GuiAgent {
 						recServers.add(smallestBinIndex);
 					}
 
-					long userTweetDelay = calculateTweetDelay(user);
+					long userTweetDelay = getConfiguredTweetDelay(user);
 
 					
 //					userFollowTweetCounts = new LinkedHashMap<String,Integer>();
@@ -1345,32 +1510,41 @@ public class ControllerAgent extends GuiAgent {
 		}
 		
 		///*IMPORTANT CODE FOR READING FROM TEXT FILE*/
-		if (readFrom == FROM_TEXT)
-		{
-			localDb.clearDb();
-			availableDb.clearDb();
-			allUserFollowTweetCounts.clear();
-			
-			readFromTextFile();
-
-			ArrayList<Tweet> availableTweets;
-			Classifier trainedSVM = null; //SVM to train
-			
-			availableTweets = getAvailableTweets();
-			myGui.updateList(listOfUsers);
-			
-			datasetFollowees = findListFollowees();
+		if (readFrom == FROM_TEXT)
+		{
+			long textInitializeStartTime = System.currentTimeMillis();
+			localDb.clearDb();
+			availableDb.clearDb();
+			allUserFollowTweetCounts.clear();
+			
+			long readTextStartTime = System.currentTimeMillis();
+			readFromTextFile();
+			logStageDuration("INITIALIZE read text dataset", readTextStartTime, System.currentTimeMillis());
+
+			ArrayList<Tweet> availableTweets;
+			Classifier trainedSVM = null; //SVM to train
+			
+			long availableTweetsStartTime = System.currentTimeMillis();
+			availableTweets = getAvailableTweets();
+			myGui.updateList(listOfUsers);
+			logStageDuration("INITIALIZE build available tweet/user list", availableTweetsStartTime, System.currentTimeMillis());
+			
+			long followeesStartTime = System.currentTimeMillis();
+			datasetFollowees = findListFollowees();
+			logStageDuration("INITIALIZE find dataset followees", followeesStartTime, System.currentTimeMillis());
 			
 			//Distributed classifiers
 			// if (algorithmRec == MLP && numRecAgents > 1)
 			// {
 				// findUniqueDocTerms(retweetedby_temp,hashtags_temp,stopWordFlag_temp);
 			// }
-			if (algorithmRec == MLP)
-			{
-				processTweets(retweetedby_temp,hashtags_temp,stopWordFlag_temp);
-				determineTrainingTestSet();
-			}
+			if (algorithmRec == MLP)
+			{
+				long mlpPrepStartTime = System.currentTimeMillis();
+				processTweets(retweetedby_temp,hashtags_temp,stopWordFlag_temp);
+				determineTrainingTestSet();
+				logStageDuration("INITIALIZE MLP central text/training-test preparation", mlpPrepStartTime, System.currentTimeMillis());
+			}
 			
 			// if (algorithmRec == SVM && numRecAgents > 1)
 			// {
@@ -1389,15 +1563,17 @@ public class ControllerAgent extends GuiAgent {
 				// /*To set availableDb based on test set Users*/
 				// availableTweets = getDistributedTestSet();
 			// }
-			
-			availableDb.setTweets(availableTweets);		
-
-			
-			System.out.println("controller availableDb.getTweetsSize(): "+ availableDb.getTotalTweets());
-			//Making agents
-			try{
-
-				//@Jason added algorithmRec arg
+			
+			long setAvailableStartTime = System.currentTimeMillis();
+			availableDb.setTweets(availableTweets);		
+			logStageDuration("INITIALIZE store available tweets in memory", setAvailableStartTime, System.currentTimeMillis());
+			
+			System.out.println("controller availableDb.getTweetsSize(): "+ availableDb.getTotalTweets());
+			//Making agents
+			try{
+				long textAgentCreationStartTime = System.currentTimeMillis();
+
+				//@Jason added algorithmRec arg
 				//Object[] orgAgentArgs = new Object[16];
 				Object[] orgAgentArgs = new Object[19];
 				orgAgentArgs[0] = getAID();										
@@ -1423,16 +1599,18 @@ public class ControllerAgent extends GuiAgent {
 
 				orgAgentArgs[18] = myGui;
 
-				for(int i=1; i<numOrgAgents+1; i++)
-				{
-					String orgAgentName = "Organizing Agent" + i;
+				long organizingAgentStartTime = System.currentTimeMillis();
+				for(int i=1; i<numOrgAgents+1; i++)
+				{
+					String orgAgentName = "Organizing Agent" + i;
 					agentController = workContainer.createNewAgent(orgAgentName, OrganizingAgent.class.getName(), orgAgentArgs);
 					agentController.start();
 					listOfAgents.add(orgAgentName);
 					listOfAgentControllers.add(agentController);
-					System.out.println("Made agent: "+orgAgentName);
-
-				}
+					System.out.println("Made agent: "+orgAgentName);
+
+				}
+				logStageDuration("INITIALIZE create organizing agents", organizingAgentStartTime, System.currentTimeMillis());
 
 
 				//Create bins for recommender agents
@@ -1442,72 +1620,36 @@ public class ControllerAgent extends GuiAgent {
 //					bins.add(0);
 //				}
 				
-				int[] bins = new int[numRecAgents]; //bins for recommender agents
-
-				int currentTweetCount;
-				int totalRecBin = 0; //Number of tweets in a recommender agent's bin
-				int smallestBinIndex = 0;
-
-				totalUsers = 0;
-				ArrayList<Integer> recServers;
-				
-				for (String user : listOfUsers)
-				{
-					totalUsers++;
-
-					recServers = new ArrayList<Integer>(); //need to make new list for each user
-					
-					//INCORRECT implementation of distributed SVM since this replicates dataset
-					if (algorithmRec == CommonNeighbors && numRecAgents > 1)
-					{
-						for (int i = 0; i < numRecAgents; i++)
-						{
-							bins[i] = availableDb.getTotalTweets();
-							recServers.add(i+1);
-						}
-						System.out.println("Controller ENTERD HERE if condition: "+user);
-					}
-					//ORIGINAL CORRECT evenly distributed data
-					else
-					{
-						System.out.println("Controller ENTERD HERE else condition: "+user);
-						currentTweetCount = availableDb.getTweetCountFromUser(user);
-						smallestBinIndex = findSmallestBin(bins);
-	//					totalRecBin = bins.get(smallestBinIndex);
-						totalRecBin = bins[smallestBinIndex];
-						totalRecBin += currentTweetCount;
-	//					bins.set(smallestBinIndex, totalRecBin);
-						bins[smallestBinIndex] = totalRecBin;
-						smallestBinIndex++; //add 1 to index to make index start from 1 instead of 0
-
-						//Add list of recommender servers and increase bins accordingly if distributed
-						//ie. add the tweet count to all recommenders if there are 2 for the user looking for recommendation
-						// K-means Euclidean was added by Sepide 
-						if (usersRec.contains(user) || ((algorithmRec == K_MEANS || algorithmRec == MLP || algorithmRec == K_MEANSEUCLIDEAN || algorithmRec == Doc2Vec || algorithmRec == SVM) && numRecAgents > 1 && datasetFollowees.contains(user)))
-						{	
-							for (int i = 0; i < numRecAgents; i++)
-							{
-								if (smallestBinIndex-1 != i)
-								{
-	//								totalRecBin = bins.get(i);
-									totalRecBin = bins[i];
-									totalRecBin += currentTweetCount;
-	//								bins.set(i,totalRecBin);
-									bins[i] = totalRecBin;
-								}
-								recServers.add(i+1);
-							}
-						}
-						else
-						{
-							recServers.add(smallestBinIndex);
-						}	
-						
-					}
-					
-					System.out.println("Controller recServers: "+user+" "+recServers+" smallestBinIndex: "+smallestBinIndex);
+					int[] bins = new int[numRecAgents]; //bins for recommender agents
+					long[] balanceBins = new long[numRecAgents];
+					Map<String,UserNodeAssignment> textNodeAssignments = buildBalancedTextNodeAssignments(bins, balanceBins);
+	
+					int smallestBinIndex = 0;
+	
+					totalUsers = 0;
+					ArrayList<Integer> recServers;
+					
+					long userAgentStartTime = System.currentTimeMillis();
+					for (String user : listOfUsers)
+					{
+						totalUsers++;
+	
+						UserNodeAssignment textNodeAssignment = textNodeAssignments.get(user);
+						if (textNodeAssignment != null)
+						{
+							recServers = new ArrayList<Integer>(textNodeAssignment.recServers);
+							smallestBinIndex = textNodeAssignment.primaryRecServer;
+						}
+						else
+						{
+							recServers = new ArrayList<Integer>();
+							smallestBinIndex = 1;
+							recServers.add(smallestBinIndex);
+						}
+						
+						System.out.println("Controller recServers: "+user+" "+recServers+" smallestBinIndex: "+smallestBinIndex);
 					
-					long userTweetDelay = calculateTweetDelay(user);
+					long userTweetDelay = getConfiguredTweetDelay(user);
 
 					
 //					userFollowTweetCounts = new LinkedHashMap<String,Integer>();
@@ -1531,7 +1673,7 @@ public class ControllerAgent extends GuiAgent {
 //					System.out.println("user: "+user+" userFollowTweetCounts: " + userFollowTweetCounts);
 					
 					//Object[] userAgentArgs = new Object[15];							
-					Object[] userAgentArgs = new Object[30];
+					Object[] userAgentArgs = new Object[30];
 					userAgentArgs[0] = getAID();										
 					userAgentArgs[1] = referenceUser;							
 
@@ -1570,35 +1712,38 @@ public class ControllerAgent extends GuiAgent {
 					else
 					{	
 						userAgentArgs[25] = false;
-					}
-					userAgentArgs[29] = userFollowee.get(user); //followee of User
-					
-					String userAgentName = user + "-UserAgent";
+					}
+					userAgentArgs[29] = userFollowee.get(user); //followee of User
+					
+					String userAgentName = user + "-UserAgent";
 					agentController = workContainer.createNewAgent(userAgentName, MobileAgent.class.getName(), userAgentArgs);   
 					agentController.start();
 					listOfAgents.add(userAgentName);
 					listOfAgentControllers.add(agentController);
-					//System.out.println("Made agent: "+userAgentName);
-//					System.out.println("totalUsers: "+totalUsers);
-
-				}
+					//System.out.println("Made agent: "+userAgentName);
+//					System.out.println("totalUsers: "+totalUsers);
+
+				}
+				logStageDuration("INITIALIZE assign bins and create user agents", userAgentStartTime, System.currentTimeMillis());
 
 				int totalnumberoftweets =0;
 //				for(int k=0; k<bins.size();k++)
-				for(int k=0; k<bins.length;k++)
-				{
-					//totalnumberoftweets += bins.get(k);
-//					System.out.println("bin "+k+": "+bins.get(k));
-					System.out.println("bin "+k+": "+bins[k]);
-				}
+					for(int k=0; k<bins.length;k++)
+					{
+						//totalnumberoftweets += bins.get(k);
+	//					System.out.println("bin "+k+": "+bins.get(k));
+						System.out.println("bin "+k+": "+bins[k]);
+						System.out.println("balance bin "+k+": "+balanceBins[k]);
+					}
 
 				//Only update if it is not distributed SVM
 				if ((algorithmRec != SVM) || (algorithmRec == SVM && numRecAgents == 1))
 					myGui.updateList(listOfUsers); //update before creating recommender agents
 				
-				int j =0;
-				for(int i=1; i<numRecAgents+1; i++)
-				{
+				long recommenderAgentStartTime = System.currentTimeMillis();
+				int j =0;
+				for(int i=1; i<numRecAgents+1; i++)
+				{
 
 					Object[] recAgentArgs = new Object[24];
 					recAgentArgs[0] = getAID();										
@@ -1645,9 +1790,11 @@ public class ControllerAgent extends GuiAgent {
 					agentController.start();
 					listOfAgents.add(recAgentName);
 					listOfAgentControllers.add(agentController);
-					System.out.println("Made agent: "+recAgentName);
-					j++;
-				}
+					System.out.println("Made agent: "+recAgentName);
+					j++;
+				}
+				logStageDuration("INITIALIZE create recommender agents", recommenderAgentStartTime, System.currentTimeMillis());
+				logStageDuration("INITIALIZE total text agent creation", textAgentCreationStartTime, System.currentTimeMillis());
 
 				/*//@Jason added algorithmRec arg
 				//Object[] orgAgentArgs = new Object[16];
@@ -1689,10 +1836,11 @@ public class ControllerAgent extends GuiAgent {
 				System.err.println("Text Read Initialize Error: " + e.getMessage());
 			}  
 
-			System.out.println("FROM TEXTFILE TotalUsers: "+totalUsers);
-			System.out.println("allUserFollowTweetCounts: "+allUserFollowTweetCounts);
-			
-		}
+			System.out.println("FROM TEXTFILE TotalUsers: "+totalUsers);
+			System.out.println("allUserFollowTweetCounts: "+allUserFollowTweetCounts);
+			logStageDuration("INITIALIZE text-file branch total", textInitializeStartTime, System.currentTimeMillis());
+			
+		}
 
 
 		if (readFrom == FROM_DB)
@@ -1808,7 +1956,7 @@ public class ControllerAgent extends GuiAgent {
 
 					userAgentArgs[12] = 1;
 					userAgentArgs[13] = 1;	
-					userAgentArgs[14] = tweetDelay;
+					userAgentArgs[14] = getConfiguredTweetDelay(tweetDelay);
 
 					//@Jason added limit and referenceUser;
 					userAgentArgs[15]=totalTweetLimit;
@@ -1932,9 +2080,10 @@ public class ControllerAgent extends GuiAgent {
 		// myGui.enableAllButtons();
 		// myGui.reselectRecommendee();
 		// myGui.enableList();
-		long endTime = System.currentTimeMillis();
-		System.out.println("Initialize time: "+(endTime-startTime)+"ms");
-		// myGui.showMessageBox("initialize");
+		long endTime = System.currentTimeMillis();
+		System.out.println("Initialize time: "+(endTime-startTime)+"ms");
+		logStageDuration("INITIALIZE total", startTime, endTime);
+		// myGui.showMessageBox("initialize");
 
 	} //inititalize
 
@@ -2066,10 +2215,10 @@ public class ControllerAgent extends GuiAgent {
 
 	//Finds the smallest bin to fill in when using distributed system
 //	public int findSmallestBin(ArrayList<Integer> numbers) { 
-	public int findSmallestBin(int[] currentBins) {
-		int smallestIndex = 0;
-//		int smallest = numbers.get(0);
-		int smallest = currentBins[0];
+		public int findSmallestBin(int[] currentBins) {
+			int smallestIndex = 0;
+	//		int smallest = numbers.get(0);
+			int smallest = currentBins[0];
 //		for(int i = 0; i < numbers.size(); i++) { 
 //			if(numbers.get(i) < smallest) { 
 //				smallest = numbers.get(i); 
@@ -2081,12 +2230,206 @@ public class ControllerAgent extends GuiAgent {
 				smallest = currentBins[i]; 
 				smallestIndex = i;
 			} 
-		} 
-		return smallestIndex;
-	}
-	
-	public void generateUserNames()
-	{
+			} 
+			return smallestIndex;
+		}
+
+		private int findSmallestBin(long[] currentBins) {
+			int smallestIndex = 0;
+			long smallest = currentBins[0];
+			for(int i = 0; i < currentBins.length; i++) { 
+				if(currentBins[i] < smallest) { 
+					smallest = currentBins[i]; 
+					smallestIndex = i;
+				} 
+			} 
+			return smallestIndex;
+		}
+
+		private Map<String,UserNodeAssignment> buildBalancedTextNodeAssignments(int[] tweetBins, long[] balanceBins)
+		{
+			LinkedHashMap<String,UserNodeAssignment> assignments = new LinkedHashMap<String,UserNodeAssignment>();
+			if (numRecAgents < 1)
+			{
+				return assignments;
+			}
+
+			if (algorithmRec == CommonNeighbors && numRecAgents > 1)
+			{
+				for (int i = 0; i < numRecAgents; i++)
+				{
+					tweetBins[i] = availableDb.getTotalTweets();
+					balanceBins[i] = availableDb.getTotalTweets();
+				}
+
+				for (String user : listOfUsers)
+				{
+					assignments.put(user, new UserNodeAssignment(createAllRecommenderServerList(), 1));
+				}
+				System.out.println("Controller balanced text assignment skipped for CommonNeighbors because all users are replicated to every recommender.");
+				return assignments;
+			}
+
+			final LinkedHashMap<String,Integer> originalUserOrder = new LinkedHashMap<String,Integer>();
+			for (int i = 0; i < listOfUsers.size(); i++)
+			{
+				originalUserOrder.put(listOfUsers.get(i), i);
+			}
+			
+			final LinkedHashMap<String,Long> userAssignmentLoads = new LinkedHashMap<String,Long>();
+			for (String user : listOfUsers)
+			{
+				userAssignmentLoads.put(user, estimateTextUserAssignmentLoad(user));
+			}
+
+			ArrayList<String> usersSortedByEstimatedLoad = new ArrayList<String>(listOfUsers);
+			Collections.sort(usersSortedByEstimatedLoad, new Comparator<String>() {
+				public int compare(String userA, String userB)
+				{
+					long userALoad = userAssignmentLoads.get(userA);
+					long userBLoad = userAssignmentLoads.get(userB);
+					if (userALoad < userBLoad)
+						return 1;
+					if (userALoad > userBLoad)
+						return -1;
+
+					Integer userAOrder = originalUserOrder.get(userA);
+					Integer userBOrder = originalUserOrder.get(userB);
+					if (userAOrder == null)
+						userAOrder = 0;
+					if (userBOrder == null)
+						userBOrder = 0;
+					return userAOrder.compareTo(userBOrder);
+				}
+			});
+
+			for (String user : usersSortedByEstimatedLoad)
+			{
+				int currentTweetCount = availableDb.getTweetCountFromUser(user);
+				long userAssignmentLoad = userAssignmentLoads.get(user);
+				int selectedBinIndex = findSmallestBin(balanceBins);
+				ArrayList<Integer> recServers;
+
+				addUserLoadToTextBin(tweetBins, balanceBins, selectedBinIndex, currentTweetCount, userAssignmentLoad);
+				if (shouldReplicateTextUserToAllRecommenders(user))
+				{
+					recServers = createAllRecommenderServerList();
+					for (int i = 0; i < numRecAgents; i++)
+					{
+						if (i != selectedBinIndex)
+						{
+							addUserLoadToTextBin(tweetBins, balanceBins, i, currentTweetCount, userAssignmentLoad);
+						}
+					}
+				}
+				else
+				{
+					recServers = new ArrayList<Integer>();
+					recServers.add(selectedBinIndex + 1);
+				}
+
+				assignments.put(user, new UserNodeAssignment(recServers, selectedBinIndex + 1));
+			}
+
+			System.out.println("Controller balanced text assignment loads: "+Arrays.toString(balanceBins));
+			System.out.println("Controller balanced text assignment tweet bins: "+Arrays.toString(tweetBins));
+			return assignments;
+		}
+
+		private boolean isSvmBatchMode()
+		{
+			return algorithmRec == SVM && myGui != null && myGui.getSvmBatchUserCount() > 1;
+		}
+
+		private void addUserLoadToTextBin(int[] tweetBins, long[] balanceBins, int binIndex, int tweetCount, long assignmentLoad)
+		{
+			tweetBins[binIndex] += tweetCount;
+			balanceBins[binIndex] += assignmentLoad;
+		}
+
+		private boolean shouldReplicateTextUserToAllRecommenders(String user)
+		{
+			if (usersRec.contains(user))
+			{
+				return true;
+			}
+
+			if (numRecAgents <= 1)
+			{
+				return false;
+			}
+
+			return (algorithmRec == K_MEANS || algorithmRec == MLP || algorithmRec == K_MEANSEUCLIDEAN || algorithmRec == Doc2Vec || algorithmRec == SVM)
+				&& datasetFollowees != null
+				&& datasetFollowees.contains(user);
+		}
+
+		private ArrayList<Integer> createAllRecommenderServerList()
+		{
+			ArrayList<Integer> recServers = new ArrayList<Integer>();
+			for (int i = 0; i < numRecAgents; i++)
+			{
+				recServers.add(i + 1);
+			}
+			return recServers;
+		}
+
+		private long estimateTextUserAssignmentLoad(String user)
+		{
+			int currentTweetCount = availableDb.getTweetCountFromUser(user);
+			long fallbackLoad = Math.max(1, currentTweetCount);
+			ArrayList<Tweet> userTweets = availableDb.getTweetsFromUser(user);
+			if (userTweets == null || userTweets.size() == 0)
+			{
+				return fallbackLoad;
+			}
+
+			HashSet<String> approximateTerms = new HashSet<String>();
+			long characterCount = 0L;
+			for (Tweet tweet : userTweets)
+			{
+				String tweetText = tweet.getTweetText();
+				if (tweetText == null)
+				{
+					continue;
+				}
+				characterCount += tweetText.length();
+				addApproximateTerms(approximateTerms, tweetText);
+			}
+
+			long termCount = Math.max(1, approximateTerms.size());
+			long denseDocumentLoad = termCount * termCount;
+			long textLengthLoad = characterCount / 8L;
+			long tweetLoad = Math.max(1, userTweets.size()) * 10L;
+			return Math.max(fallbackLoad, denseDocumentLoad + textLengthLoad + tweetLoad);
+		}
+
+		private void addApproximateTerms(Set<String> approximateTerms, String tweetText)
+		{
+			String[] tokens = tweetText.toLowerCase().split("[^a-zA-Z]+");
+			for (String token : tokens)
+			{
+				if (token.length() > 0)
+				{
+					approximateTerms.add(token);
+				}
+			}
+		}
+
+		private static class UserNodeAssignment
+		{
+			private final ArrayList<Integer> recServers;
+			private final int primaryRecServer;
+
+			private UserNodeAssignment(ArrayList<Integer> recServers, int primaryRecServer)
+			{
+				this.recServers = recServers;
+				this.primaryRecServer = primaryRecServer;
+			}
+		}
+		
+		public void generateUserNames()
+		{
 		int totalUsers = numFollowees + numFollowers;
 		listOfUsers.clear(); //Clear list of usernames if it already exists
 		String username = "";
@@ -2302,10 +2645,10 @@ public class ControllerAgent extends GuiAgent {
 		return generatedWord;
 	}
 	
-	public long calculateTweetDelay(String username)
-	{
-		int numUserTweets = availableDb.getTweetCountFromUser(username);
-		int totalTweets = availableDb.getTotalTweets();
+	public long calculateTweetDelay(String username)
+	{
+		int numUserTweets = availableDb.getTweetCountFromUser(username);
+		int totalTweets = availableDb.getTotalTweets();
 		double ratioOfTweets = (double) numUserTweets/totalTweets;
 		double tweetDelay = TWEET_DELAY - (ratioOfTweets)*TWEET_DELAY;
 		long tweetDelayLong;
@@ -2317,11 +2660,29 @@ public class ControllerAgent extends GuiAgent {
 		
 		if (tweetDelayLong < 1)
 			tweetDelayLong = 1;
-		
-		return tweetDelayLong;
-	}
-
-	//Returns the index of the followee
+		
+		return tweetDelayLong;
+	}
+
+	public long getConfiguredTweetDelay(String username)
+	{
+		if (myGui != null && !myGui.isSimulateTweetDelayEnabled())
+		{
+			return 1L;
+		}
+		return calculateTweetDelay(username);
+	}
+
+	public long getConfiguredTweetDelay(long defaultDelay)
+	{
+		if (myGui != null && !myGui.isSimulateTweetDelayEnabled())
+		{
+			return 1L;
+		}
+		return defaultDelay;
+	}
+
+	//Returns the index of the followee
 	public int calculateWhoToFollow(int numFollowees)
 	{
 //		Random r = new Random();
@@ -2475,9 +2836,9 @@ public class ControllerAgent extends GuiAgent {
 
 	}
 	
-	public ArrayList<Tweet> getAvailableTweets()
-	{
-		ArrayList<Tweet> tweetsFromDb = localDb.getTweets();
+	public ArrayList<Tweet> getAvailableTweets()
+	{
+		ArrayList<Tweet> tweetsFromDb = localDb.getTweets();
 		ArrayList<Tweet> datasetAvailable = new ArrayList<Tweet>();
 
 		Collections.sort(tweetsFromDb); //Sort by tweetId
@@ -2521,12 +2882,12 @@ public class ControllerAgent extends GuiAgent {
 			if (tweetToLimitCount == totalTweetLimit)
 				break;
 		}
-		
-		return datasetAvailable;
-	}
-	
-	public void findUniqueDocTerms(int retweetsFlag, int hashTagsFlag, int stopWordsFlag)
-	{
+		
+		return datasetAvailable;
+	}
+	
+	public void findUniqueDocTerms(int retweetsFlag, int hashTagsFlag, int stopWordsFlag)
+	{
 		
 		System.out.println("Controller Finding Unique Doc Terms");
 		int tweetIdCount = 0;
@@ -2962,73 +3323,45 @@ public class ControllerAgent extends GuiAgent {
 		
 	}
 	
-	public void determineTrainingTestSet()
-	{
-		int numUsers = allUserDocuments.keySet().size();
-		int numTestUsers = (int) Math.floor(numUsers * TEST_SET_PERCENT);
-		int numTrainUsers = numUsers - numTestUsers;
-		int currTestUsers = 0;
-		int currTrainUsers = 0;
-		testSetUsers = new ArrayList<String>(); //list of users in test set
-		trainSetUsers = new ArrayList<String>(); //list of users in training set
-		List<String> currFollowers; //list of followers for the current followee
-		Map<String,List<String>> tempFolloweeFollowers = new LinkedHashMap<String,List<String>>();
-		tempFolloweeFollowers.putAll(followeeFollowers);
-		
-		System.out.println("Entered determineTrainingTestSet");
-		System.out.println("numTestUsers: "+numTestUsers+" numTrainUsers: "+numTrainUsers);
-		
-		int countTestUser = 0;
-		//loop through each followee for 1 follower at a time until numTestUsers is reached
-		while (currTestUsers < numTestUsers)
-		{
-			for (String followeeName: tempFolloweeFollowers.keySet())
-			{
-				System.out.println("countTestUser: "+countTestUser);
-				currFollowers = tempFolloweeFollowers.get(followeeName);
-				// if a followee set runs out of followers before another
-				if (currFollowers.size() > 0)
-				{
-					testSetUsers.add(currFollowers.remove(0));
-					currTestUsers++;
-					countTestUser++;
-				}
-				
-				tempFolloweeFollowers.put(followeeName,currFollowers);
-				
-				if (currTestUsers == numTestUsers)
-					break;
-			}
-		}
-		
-		System.out.println("Determined Test Set");
-		
-		int countTrainUser = 0;
-		while (currTrainUsers < numTrainUsers)
-		{
-			
-			for (String followeeName: tempFolloweeFollowers.keySet())
-			{
-				System.out.println("countTrainUser: "+countTrainUser);
-				currFollowers = tempFolloweeFollowers.get(followeeName);
-				//if a followee set runs out of followers before another
-				if (currFollowers.size() > 0)
-				{
-					trainSetUsers.add(currFollowers.remove(0));
-					currTrainUsers++;
-					countTrainUser++;
-				}
-				
-				tempFolloweeFollowers.put(followeeName,currFollowers);
-				
-				if (currTrainUsers == numTrainUsers)
-					break;
-			}
-		}
-		
-		System.out.println("Determined Training Set");
-		
-	}
+	public void determineTrainingTestSet()
+	{
+		int numUsers = allUserDocuments.keySet().size();
+		
+		System.out.println("Entered determineTrainingTestSet");
+		System.out.println("numUsers: "+numUsers);
+
+		SvmReproducibility.Split split = SvmReproducibility.stratifiedSplit(
+				followeeFollowers, TEST_SET_PERCENT);
+		trainSetUsers = split.getTrainUsers();
+		testSetUsers = split.getTestUsers();
+		
+		System.out.println("Determined Training Set");
+		System.out.println("Controller SVM reproducibility split seed: "
+				+ SvmReproducibility.SPLIT_SEED + " model seed: " + SvmReproducibility.MODEL_SEED);
+		System.out.println("Controller SVM train class counts: "+countUsersByFollowee(trainSetUsers));
+		System.out.println("Controller SVM test class counts: "+countUsersByFollowee(testSetUsers));
+		
+	}
+
+	private Map<String,Integer> countUsersByFollowee(List<String> users)
+	{
+		Map<String,Integer> counts = new LinkedHashMap<String,Integer>();
+		for (String user : users)
+		{
+			String followeeName = userFollowee.get(user);
+			if (followeeName == null)
+			{
+				followeeName = "UNKNOWN";
+			}
+			Integer count = counts.get(followeeName);
+			if (count == null)
+			{
+				count = 0;
+			}
+			counts.put(followeeName, count + 1);
+		}
+		return counts;
+	}
 	
 	public void createTrainingFile(String pathTrainSet)
 	{
@@ -3090,14 +3423,7 @@ public class ControllerAgent extends GuiAgent {
 					uniqueWordCount++;
 				}
 				
-				String attributeClass = "@attribute result ";
-				StringJoiner classJoiner = new StringJoiner(",","{","}");
-				for (String className: followeeFollowers.keySet())
-				{
-					classJoiner.add(className);
-				}
-				
-				attributeClass += classJoiner.toString();
+				String attributeClass = buildArffClassAttribute(followeeFollowers.keySet());
 				
 				bufferedWriterTrain.write(attributeClass);
 				bufferedWriterTrain.newLine();
@@ -3157,7 +3483,7 @@ public class ControllerAgent extends GuiAgent {
 					Map<String,Double> currDocTfidf = allUserDocumentsTFIDF.get(currUser);
 					tfidfJoiner = vectorArffFormat(currDocTfidf,allUniqueDocTerms);
 					
-					bufferedWriterTrain.write(tfidfJoiner.toString() + "," + userFollowee.get(currUser));
+					bufferedWriterTrain.write(tfidfJoiner.toString() + "," + formatArffClassValue(userFollowee.get(currUser)));
 					bufferedWriterTrain.newLine();
 					
 				}
@@ -3180,10 +3506,10 @@ public class ControllerAgent extends GuiAgent {
 		}
 	}
 	
-	public StringJoiner vectorArffFormat(Map<String,Double> currDocTfidf, Set<String> uniqueDocTerms)
-	{
-		StringJoiner tfidfJoinerTemp = new StringJoiner(",");
-		double currTfidf;
+	public StringJoiner vectorArffFormat(Map<String,Double> currDocTfidf, Set<String> uniqueDocTerms)
+	{
+		StringJoiner tfidfJoinerTemp = new StringJoiner(",");
+		double currTfidf;
 		
 		for (String uniqueWord: uniqueDocTerms)
 		{
@@ -3193,12 +3519,40 @@ public class ControllerAgent extends GuiAgent {
 				currTfidf = 0.0;
 			
 			tfidfJoinerTemp.add(String.valueOf(currTfidf));
-		}
-		return tfidfJoinerTemp;
-	}
-	
-	public ArrayList<Tweet> getDistributedTestSet()
-	{
+		}
+		return tfidfJoinerTemp;
+	}
+
+	private String buildArffClassAttribute(Set<String> classNames)
+	{
+		StringJoiner classJoiner = new StringJoiner(",","{","}");
+		Collection<String> orderedClassNames = classNames;
+		if (algorithmRec == SVM)
+		{
+			orderedClassNames = SvmReproducibility.sortedNonNullValues(classNames);
+		}
+		for (String className: orderedClassNames)
+		{
+			classJoiner.add(formatArffClassValue(className));
+		}
+		return "@attribute result " + classJoiner.toString();
+	}
+
+	private String formatArffClassValue(String className)
+	{
+		if (className == null)
+		{
+			return "?";
+		}
+		return "'" + className
+				.replace("\\", "\\\\")
+				.replace("'", "\\'")
+				.replace("\r", " ")
+				.replace("\n", " ") + "'";
+	}
+
+	public ArrayList<Tweet> getDistributedTestSet()
+	{
 		ArrayList<Tweet> distributedTestSet = new ArrayList<Tweet>();
 		listOfUsers.clear();
 		
@@ -3235,10 +3589,13 @@ public class ControllerAgent extends GuiAgent {
 		catch (IOException e)
 		{
 			e.printStackTrace();
-		}
-		
-		data.setClassIndex(data.numAttributes() - 1);
-		Classifier svmModel = new SMO();
+		}
+
+		data.setClassIndex(data.numAttributes() - 1);
+		SMO svmModel = new SMO();
+		svmModel.setC(0.1);
+		svmModel.setBuildCalibrationModels(true);
+		SvmReproducibility.applyModelSeed(svmModel);
 							
 		try{
 			svmModel.buildClassifier(data);
@@ -3294,12 +3651,17 @@ public class ControllerAgent extends GuiAgent {
 			dir.mkdirs();
 	}
 
-	public void setFile(File textFile)
-	{
-		this.textFile = textFile;
-		System.out.println("textFile.getName(): "+textFile.getName());
-		// myGui.appendResult("Loaded File: "+textFile.getName());
-	}
+	public void setFile(File textFile)
+	{
+		this.textFile = textFile;
+		if (resetPendingDatasetSelection)
+		{
+			reportFailedExperimentOutputDeletes(
+					ExperimentResetFiles.resetDatasetOutputs(new File("."), textFile));
+		}
+		System.out.println("textFile.getName(): "+textFile.getName());
+		// myGui.appendResult("Loaded File: "+textFile.getName());
+	}
 	
 	public void setCorpusGenFile(File corpusGenFile)
 	{
diff --git BEFORE/TwitterGatherDataFollowers/userRyersonU/ControllerAgentGui.java AFTER/TwitterGatherDataFollowers/userRyersonU/ControllerAgentGui.java
index 8f59c39..397a226 100644
--- BEFORE/TwitterGatherDataFollowers/userRyersonU/ControllerAgentGui.java
+++ AFTER/TwitterGatherDataFollowers/userRyersonU/ControllerAgentGui.java
@@ -66,8 +66,9 @@ public class ControllerAgentGui extends JFrame implements ActionListener {
 	public static final int ALGORITHM_CLUSTERING = 1;
 	public static final int CHANGE_TO_PERFORMANCE = 0;
 	public static final int CHANGE_TO_USERSIM = 1;
-	public static final int COLLECT_DATA = 4;
-	public static final int START_USER_GEN_SIM =  5;
+	public static final int COLLECT_DATA = 4;
+	public static final int START_USER_GEN_SIM =  5;
+	public static final int RESET_EXPERIMENT = 6;
 	public static final String USERSIM_PANEL_ID = "Change to User Sim";
 	public static final String PERFORMANCE_PANEL_ID = "Performance Measurement";
 	private int simulationNo;    // added by Sepide 
@@ -83,8 +84,9 @@ public class ControllerAgentGui extends JFrame implements ActionListener {
 	private JLabel tweetLimitLabel;
 	private JLabel beginDateLabel;
 	private JLabel endDateLabel;
-	private JLabel recommendationLabel;
-	private JLabel algorithmLabel;
+	private JLabel recommendationLabel;
+	private JLabel svmBatchUsersLabel;
+	private JLabel algorithmLabel;
 	private JLabel numReducersLabel;
 	private JLabel numMappersLabel;
 	private JLabel reducerChoiceLabel;
@@ -99,12 +101,14 @@ public class ControllerAgentGui extends JFrame implements ActionListener {
 	private JTextField tweetLimitField;
 	private JTextField beginDateField;
 	private JTextField endDateField;
-	public JTextField recommendationField;
-	private JTextField numReducersField;
+	public JTextField recommendationField;
+	public JTextField svmBatchUsersField;
+	private JTextField numReducersField;
 	private JTextField numMappersField;
 	public JButton initializeButton;
-	private JButton quitButton;
-	public JButton simulationButton;  // added by Sepide 
+	private JButton quitButton;
+	private JButton resetExperimentButton;
+	public JButton simulationButton;  // added by Sepide
 	public JButton startButton;
 	private JButton getUsersButton;
 	private JButton changePerformanceButton;
@@ -133,9 +137,10 @@ public class ControllerAgentGui extends JFrame implements ActionListener {
 	private TitledBorder clusterTitle;
 	private TitledBorder userGenTweetsResultTitle;
 	private TitledBorder grabTwitterResultTitle;
-	private JCheckBox removeHashTags;
-	private JCheckBox removeRetweets;
-	private JCheckBox removeStopWords;
+	private JCheckBox removeHashTags;
+	private JCheckBox removeRetweets;
+	private JCheckBox removeStopWords;
+	private JCheckBox simulateTweetDelay;
 	private DefaultListModel<String> agentsList;
 	public JList showAgentsList;
 	private Border blackBorder;
@@ -406,12 +411,20 @@ public class ControllerAgentGui extends JFrame implements ActionListener {
 		recommendationLabel.setHorizontalAlignment(JLabel.RIGHT);
 		recommendationLabel.setForeground(Color.WHITE);
 		recommendationLabel.setFont(new Font("Arial",Font.BOLD,12));
-		recommendationField = new JTextField(FIELD_WIDTH);
-		recommendationField.setText("3");
-		recommendationField.setHorizontalAlignment(JTextField.CENTER);
-		recommendationField.addActionListener(new RecommendationListener());
-
-		algorithmLabel = new JLabel("Algorithm: ");
+			recommendationField = new JTextField(FIELD_WIDTH);
+			recommendationField.setText("3");
+			recommendationField.setHorizontalAlignment(JTextField.CENTER);
+			recommendationField.addActionListener(new RecommendationListener());
+			
+			svmBatchUsersLabel = new JLabel("Batch Users: ");
+			svmBatchUsersLabel.setHorizontalAlignment(JLabel.RIGHT);
+			svmBatchUsersLabel.setForeground(Color.WHITE);
+			svmBatchUsersLabel.setFont(new Font("Arial",Font.BOLD,12));
+			svmBatchUsersField = new JTextField(FIELD_WIDTH);
+			svmBatchUsersField.setText("1");
+			svmBatchUsersField.setHorizontalAlignment(JTextField.CENTER);
+
+			algorithmLabel = new JLabel("Algorithm: ");
 		algorithmLabel.setForeground(Color.WHITE);
 		algorithmLabel.setHorizontalAlignment(JLabel.RIGHT);
 		algorithmLabel.setFont(new Font("Arial",Font.BOLD,12));
@@ -671,11 +684,17 @@ public class ControllerAgentGui extends JFrame implements ActionListener {
 		removeRetweets.addItemListener(new RemoveBoxListener());
 
 		removeStopWords = new JCheckBox("Remove Stop Words");
-		removeStopWords.setForeground(Color.WHITE);
-		removeStopWords.setFont(new Font("Arial",Font.BOLD,12));
-		removeStopWords.setSelected(true);
-		removeStopWords.addItemListener(new RemoveBoxListener());
-	}
+		removeStopWords.setForeground(Color.WHITE);
+		removeStopWords.setFont(new Font("Arial",Font.BOLD,12));
+		removeStopWords.setSelected(true);
+		removeStopWords.addItemListener(new RemoveBoxListener());
+
+		simulateTweetDelay = new JCheckBox("Simulate Tweet Delay");
+		simulateTweetDelay.setForeground(Color.WHITE);
+		simulateTweetDelay.setFont(new Font("Arial",Font.BOLD,12));
+		simulateTweetDelay.setSelected(true);
+		simulateTweetDelay.setToolTipText("Selected: original DSMP timing simulation. Unselected: skip the artificial MobileAgent tweet-delay wait after all tweet data is delivered.");
+	}
 
 	class RemoveBoxListener implements ItemListener
 	{
@@ -726,9 +745,13 @@ public class ControllerAgentGui extends JFrame implements ActionListener {
 		//		startButton.setVisible(false);
 		startButton.addActionListener(this);
 
-		quitButton = new JButton("Quit");
-		quitButton.setFont(new Font("Arial",Font.BOLD,12));
-		quitButton.addActionListener(this);
+		quitButton = new JButton("Quit");
+		quitButton.setFont(new Font("Arial",Font.BOLD,12));
+		quitButton.addActionListener(this);
+
+		resetExperimentButton = new JButton("Reset Experiment");
+		resetExperimentButton.setFont(new Font("Arial",Font.BOLD,12));
+		resetExperimentButton.addActionListener(this);
 		
 		// added by Sepide
 		simulationButton = new JButton("Number of Simulations");
@@ -1081,22 +1104,26 @@ public class ControllerAgentGui extends JFrame implements ActionListener {
 		initializationsPanel.add(beginDateField);
 		initializationsPanel.add(endDateLabel);
 		initializationsPanel.add(endDateField);
-		initializationsPanel.add(recommendationLabel);
-		initializationsPanel.add(recommendationField);
-		initializationsPanel.add(algorithmLabel);
-		initializationsPanel.add(algorithmSelectionBox);
+			initializationsPanel.add(recommendationLabel);
+			initializationsPanel.add(recommendationField);
+			initializationsPanel.add(svmBatchUsersLabel);
+			initializationsPanel.add(svmBatchUsersField);
+			initializationsPanel.add(algorithmLabel);
+			initializationsPanel.add(algorithmSelectionBox);
 		initializationsPanel.add(simulationNumber);      // added by Sepide
 		initializationsPanel.add(simulationSelectionBox);        // added by Sepide
 		initializationsPanel.setBorder(initializationTitle);
 
-		textProcessingPanel.add(removeHashTags);
-		textProcessingPanel.add(removeRetweets);
-		textProcessingPanel.add(removeStopWords);
+		textProcessingPanel.add(removeHashTags);
+		textProcessingPanel.add(removeRetweets);
+		textProcessingPanel.add(removeStopWords);
+		textProcessingPanel.add(simulateTweetDelay);
 		
 		commandsPanel.add(getUsersButton);
-		commandsPanel.add(initializeButton);
-		commandsPanel.add(startButton);
-		commandsPanel.add(quitButton);
+		commandsPanel.add(initializeButton);
+		commandsPanel.add(startButton);
+		commandsPanel.add(resetExperimentButton);
+		commandsPanel.add(quitButton);
 		commandsPanel.add(simulationButton);   // added by Sepide 
 		commandsPanel.add(changeUserSimButton);
 		commandsPanel.setBorder(commandsTitle);
@@ -1160,10 +1187,14 @@ public class ControllerAgentGui extends JFrame implements ActionListener {
 		{
 			shutDown();
 		}	
-		else if (event.getSource() == getUsersButton)
-		{
-			getUsers();
-		}
+		else if (event.getSource() == getUsersButton)
+		{
+			getUsers();
+		}
+		else if (event.getSource() == resetExperimentButton)
+		{
+			requestExperimentReset();
+		}
 		else if (event.getSource() == initializeButton)
 		{
 			initializeAgents();
@@ -1361,8 +1392,8 @@ public class ControllerAgentGui extends JFrame implements ActionListener {
 			cl.show(cards,USERSIM_PANEL_ID);
 	}
 	
-	public void getUsers()
-	{
+	public void getUsers()
+	{
 		//		String enteredData = enterDatasetField.getText();
 		//		if (!enteredData.equals(""))
 		//		{
@@ -1415,8 +1446,130 @@ public class ControllerAgentGui extends JFrame implements ActionListener {
 		ge.addParameter(numFollowers);
 		ge.addParameter(numTweetsGenerated);
 		myAgent.postGuiEvent(ge);
-
-	}
+
+	}
+
+	private void requestExperimentReset()
+	{
+		int choice = JOptionPane.showConfirmDialog(
+				this,
+				"Reset the current experiment?\n\n"
+						+ "Running experiment agents will stop and current on-screen results will be cleared.\n"
+						+ "Datasets, model caches, and archived results will be preserved.",
+				"Reset Experiment",
+				JOptionPane.YES_NO_OPTION,
+				JOptionPane.WARNING_MESSAGE);
+		if (choice != JOptionPane.YES_OPTION)
+		{
+			return;
+		}
+
+		resetExperimentButton.setEnabled(false);
+		getUsersButton.setEnabled(false);
+		initializeButton.setEnabled(false);
+		startButton.setEnabled(false);
+		showAgentsList.setEnabled(false);
+
+		GuiEvent ge = new GuiEvent(this, myAgent.RESET_EXPERIMENT);
+		myAgent.postGuiEvent(ge);
+	}
+
+	public void resetForNewExperiment()
+	{
+		simulationNo = 0;
+		sepNum = 0;
+		listSize = 0;
+		simulationIteration = 0;
+		indexToRecommend = 0;
+		indexToRecommend2 = 0;
+		tweetLimit = 0;
+		numNodes = 1;
+		kRecommend = DEFAULT_K_REC;
+		hashTags = HASH_TAGS;
+		retweets = RE_TWEETS;
+		stopWords = STOP_WORDS;
+		algorithmRec = K_MEANS;
+		numFollowees = FOLLOWEES;
+		numFollowers = FOLLOWERS;
+		numTweetsGenerated = DEFAULT_TWEETS_GENERATED;
+		numReducers = DEFAULT_REDUCERS;
+		numMappers = DEFAULT_MAPPERS;
+		reducerChoice = ALGORITHM_CLUSTERING;
+		mapperChoice = FEATURE_EXTRACTION;
+		numArtificialTweets = DEFAULT_NUM_ARTIFICIAL_TWEETS;
+		numFollowersToGrab = 0;
+		referenceUser = DEFAULT_DATASET;
+		beginDate = BEGIN_DATE;
+		endDate = END_DATE;
+		recommendeeName = null;
+		recommendeeName2 = null;
+		nameFolloweesToGrab = null;
+		indexArray = null;
+		a = null;
+		b = null;
+		c = null;
+		names = null;
+
+		countRecServersTP = 0;
+		countRecServersTfidf = 0;
+		countRecServersAlgorithm = 0;
+		currentMaxTPTime = 0;
+		currentMaxTfidfTime = 0;
+		currentMaxAlgorithmTime = 0;
+		currentMessagePassingTime = 0;
+		currentMessagePassingCost = 0;
+		currentTiming = null;
+		timings.clear();
+		usersRec.clear();
+
+		enterDatasetField.setText(DEFAULT_DATASET);
+		numNodesField.setText("1");
+		tweetLimitField.setText("0");
+		beginDateField.setText(BEGIN_DATE);
+		endDateField.setText(END_DATE);
+		recommendationField.setText(Integer.toString(DEFAULT_K_REC));
+		svmBatchUsersField.setText("1");
+		numReducersField.setText(Integer.toString(DEFAULT_REDUCERS));
+		numMappersField.setText(Integer.toString(DEFAULT_MAPPERS));
+		algorithmSelectionBox.setSelectedIndex(K_MEANS);
+		simulationSelectionBox.setSelectedIndex(zero);
+		mapperSelectionBox.setSelectedIndex(FEATURE_EXTRACTION);
+		reducerSelectionBox.setSelectedIndex(ALGORITHM_CLUSTERING);
+		removeHashTags.setSelected(true);
+		removeRetweets.setSelected(true);
+		removeStopWords.setSelected(true);
+		simulateTweetDelay.setSelected(true);
+		fileChooser.setSelectedFile(null);
+
+		resultArea.setText("");
+		previousResultArea.setText("");
+		recommendationArea.setText("");
+		clusterResultArea.setText("");
+		userGenTweetsResultArea.setText("");
+		grabTwitterResultArea.setText("");
+		agentsList.clear();
+		showAgentsList.clearSelection();
+		showAgentsList.setEnabled(true);
+		agentsListTitle.setTitle("List of Users");
+		recommendationTitle.setTitle("Recommendations for User");
+		performanceProgressLabel.setText("Current Progress: ");
+		userGenProgressLabel.setText("Current Progress: ");
+
+		getUsersButton.setEnabled(true);
+		initializeButton.setEnabled(false);
+		startButton.setEnabled(false);
+		resetExperimentButton.setEnabled(true);
+		quitButton.setEnabled(true);
+		enableUserGenSimButtons();
+		changeCardPanel(CHANGE_TO_PERFORMANCE);
+		repaint();
+
+		JOptionPane.showMessageDialog(
+				this,
+				"Experiment reset. Select a dataset and press Get Users to begin.",
+				"New Experiment Ready",
+				JOptionPane.INFORMATION_MESSAGE);
+	}
 
 	public void initializeAgents()
 	{
@@ -1549,29 +1702,86 @@ public class ControllerAgentGui extends JFrame implements ActionListener {
 		myAgent.postGuiEvent(ge);
 	}
 
-	public ArrayList<String> getUsersRec()
-	{
-		String userToRecommend;
-		//S String userToRecommend2;  // add the second user by Sepide
-		// S ArrayList<String> usersToRec = new ArrayList<String>();
-		System.out.println("indexToRecommend: "+indexToRecommend);
+	public ArrayList<String> getUsersRec()
+	{
+		String userToRecommend;
+		//S String userToRecommend2;  // add the second user by Sepide
+		// S ArrayList<String> usersToRec = new ArrayList<String>();
+		System.out.println("indexToRecommend: "+indexToRecommend);
 		//S System.out.println("indexToRecommend: "+indexToRecommend2);  // added by Sepide
 		// code added by Sepide to allow selecting two users from the GUI
 		/*S agentsList.add(indexToRecommend,usersToRec);
 		agentsList.add(indexToRecommend2,usersToRec); */  // added by Sepide 
-		userToRecommend = agentsList.getElementAt(indexToRecommend);
-		//S userToRecommend2 = agentsList.getElementAt(indexToRecommend2);  // added by Sepide
-		userToRecommend = userToRecommend.split("-",2)[0];
-		//S userToRecommend2 = userToRecommend2.split("-",2)[0]; // added by Sepide
-		ArrayList<String> usersRec = new ArrayList<String>();
-		usersRec.add(userToRecommend);
-		//S usersRec.add(userToRecommend2);  // added by Sepide
-		System.out.println("controllerGUI usersRec: "+usersRec);
-		return usersRec;
-		
-	}
-
-	public void shutDown() 
+		ArrayList<String> usersRec = new ArrayList<String>();
+		int batchCount = getRecommendationBatchUserCount();
+		int startIndex = indexToRecommend;
+		if (startIndex < 0)
+		{
+			startIndex = 0;
+		}
+		for (int i = startIndex; i < agentsList.size() && usersRec.size() < batchCount; i++)
+		{
+			userToRecommend = agentsList.getElementAt(i);
+			//S userToRecommend2 = userToRecommend2.split("-",2)[0]; // added by Sepide
+			userToRecommend = userToRecommend.split("-",2)[0];
+			usersRec.add(userToRecommend);
+		}
+		//S usersRec.add(userToRecommend2);  // added by Sepide
+		System.out.println("controllerGUI usersRec: "+usersRec);
+		return usersRec;
+		
+	}
+
+	public int getSvmBatchUserCount()
+	{
+		if (algorithmSelectionBox == null || algorithmSelectionBox.getSelectedIndex() != SVM)
+		{
+			return 1;
+		}
+		return parseBatchUserCount();
+	}
+
+	public int getRecommendationBatchUserCount()
+	{
+		if (algorithmSelectionBox == null)
+		{
+			return 1;
+		}
+		int selectedAlgorithm = algorithmSelectionBox.getSelectedIndex();
+		if (selectedAlgorithm != SVM && selectedAlgorithm != Doc2Vec)
+		{
+			return 1;
+		}
+		return parseBatchUserCount();
+	}
+
+	private int parseBatchUserCount()
+	{
+		if (svmBatchUsersField == null)
+		{
+			return 1;
+		}
+		try
+		{
+			int batchCount = Integer.parseInt(svmBatchUsersField.getText().trim());
+			if (batchCount < 1)
+			{
+				return 1;
+			}
+			return batchCount;
+		}
+		catch (NumberFormatException e)
+		{
+			return 1;
+		}
+	}
+
+	public boolean isSimulateTweetDelayEnabled()
+	{
+		return simulateTweetDelay == null || simulateTweetDelay.isSelected();
+	}
+
+	public void shutDown() 
 	{
 		System.exit(0);
 		GuiEvent ge = new GuiEvent(this, myAgent.QUIT);
@@ -1785,10 +1995,10 @@ public class ControllerAgentGui extends JFrame implements ActionListener {
 		JOptionPane.showMessageDialog(this,"1. Collect Data from Twitter by entering followee name(s) separated by commas then enter the number of followers you want for them. All followees will get try to get the same number of followers. The collected data to be used in the simulator will be stored in Dataset/TwitterObtained/allFolloweeNames_yyyy-MM-dd HH-mm-ss/*_final.txt.\n***Skip if you already have a correctly-formatted corpus.\n2. Load a corpus that was collected by Step 1 or a correctly-formatted corpus.\n3. Set Number of Artificial Tweets. 0 is default which means use the number of original processed corpus tweets. A number bigger than the original corpus size will try to keep the same proportions in the generated corpus.\n4. Start User Gen Simulation. Generated tweets will be saved under Dataset/TwitterObtained/GENERATED/ORIGINALFILENAME_yyyy-MM-dd HH-mm-ss_GENERATED.txt. Clustering results will appear at the end after clustering the generated tweets", "How To Use", JOptionPane.INFORMATION_MESSAGE);
 	}
 	
-	public void helpPerformanceText()
-	{
-		JOptionPane.showMessageDialog(this,"1. Load a text file from the File Menu.\n2. Get Users after selecting a text file.\n3. Change any parameters then Initialize.\n4. Run simulation.\n5. If you want to run another simulation with different parameters, make sure to re-Initialize again before running the simulation.\n6. Loading in a new text file requires starting from step 1.", "How To Use", JOptionPane.INFORMATION_MESSAGE);
-	}
+	public void helpPerformanceText()
+	{
+		JOptionPane.showMessageDialog(this,"1. Load a text file from the File Menu.\n2. Get Users after selecting a text file.\n3. Change any parameters then Initialize.\n4. Run simulation.\n5. If you want to run another simulation with different parameters, make sure to re-Initialize again before running the simulation.\n6. Press Reset Experiment before beginning a brand new experiment.", "How To Use", JOptionPane.INFORMATION_MESSAGE);
+	}
 	
 	public void setTPTime(double tpTime)
 	{
diff --git BEFORE/TwitterGatherDataFollowers/userRyersonU/Doc2VecBatchRunner.java AFTER/TwitterGatherDataFollowers/userRyersonU/Doc2VecBatchRunner.java
new file mode 100644
index 0000000..d425280
--- /dev/null
+++ AFTER/TwitterGatherDataFollowers/userRyersonU/Doc2VecBatchRunner.java
@@ -0,0 +1,202 @@
+package TwitterGatherDataFollowers.userRyersonU;
+
+import java.io.BufferedReader;
+import java.io.File;
+import java.io.IOException;
+import java.io.InputStreamReader;
+import java.nio.charset.StandardCharsets;
+import java.nio.file.Files;
+import java.nio.file.Path;
+import java.util.ArrayList;
+import java.util.Base64;
+import java.util.Collection;
+import java.util.HashSet;
+import java.util.List;
+import java.util.Map;
+import java.util.Set;
+import java.util.TreeMap;
+
+final class Doc2VecBatchRunner
+{
+	private static final String SCORE_PREFIX = "DOC2VEC_SCORE";
+	private static final String ERROR_PREFIX = "DOC2VEC_ERROR";
+	private static final String DONE_PREFIX = "DOC2VEC_DONE";
+
+	private Doc2VecBatchRunner()
+	{
+	}
+
+	static Result run(String pythonCommand, File scriptFile, int topn, File datasetFile,
+			int nodeNumber, int numNodes, int workers, List<String> users) throws IOException, InterruptedException
+	{
+		if (users == null || users.isEmpty())
+		{
+			throw new IllegalArgumentException("At least one Doc2Vec batch user is required.");
+		}
+
+		Path usersFile = Files.createTempFile("doc2vec-batch-users-node-" + nodeNumber + "-", ".txt");
+		try
+		{
+			Files.write(usersFile, users, StandardCharsets.UTF_8);
+			List<String> command = new ArrayList<String>();
+			command.add(pythonCommand);
+			command.add(scriptFile.getPath());
+			command.add(String.valueOf(topn));
+			command.add(datasetFile.getAbsolutePath());
+			command.add(String.valueOf(nodeNumber));
+			command.add(String.valueOf(numNodes));
+			command.add(String.valueOf(workers));
+			command.add(usersFile.toAbsolutePath().toString());
+
+			ProcessBuilder processBuilder = new ProcessBuilder(command);
+			processBuilder.redirectErrorStream(true);
+			Process process = processBuilder.start();
+			Result result = new Result(users);
+			BufferedReader outputReader = new BufferedReader(new InputStreamReader(
+					process.getInputStream(), StandardCharsets.UTF_8));
+			try
+			{
+				String line;
+				while ((line = outputReader.readLine()) != null)
+				{
+					if (!parseOutputLine(line, result))
+					{
+						System.out.println(line);
+					}
+				}
+			}
+			finally
+			{
+				outputReader.close();
+			}
+			result.exitCode = process.waitFor();
+			return result;
+		}
+		finally
+		{
+			Files.deleteIfExists(usersFile);
+		}
+	}
+
+	static boolean parseOutputLine(String line, Result result)
+	{
+		if (line == null)
+		{
+			return false;
+		}
+
+		try
+		{
+			if (line.startsWith(SCORE_PREFIX + "\t"))
+			{
+				String[] fields = line.split("\t", 4);
+				if (fields.length != 4)
+				{
+					return false;
+				}
+				String user = decodeField(fields[1]);
+				String candidate = decodeField(fields[2]);
+				double score = Double.parseDouble(fields[3]);
+				if (!Double.isNaN(score) && !Double.isInfinite(score))
+				{
+					result.addScore(user, candidate, score);
+				}
+				return true;
+			}
+			if (line.startsWith(ERROR_PREFIX + "\t"))
+			{
+				String[] fields = line.split("\t", 3);
+				if (fields.length != 3)
+				{
+					return false;
+				}
+				result.addError(decodeField(fields[1]), decodeField(fields[2]));
+				return true;
+			}
+			if (line.startsWith(DONE_PREFIX + "\t"))
+			{
+				String[] fields = line.split("\t", 2);
+				if (fields.length != 2)
+				{
+					return false;
+				}
+				result.markCompleted(decodeField(fields[1]));
+				return true;
+			}
+		}
+		catch (RuntimeException e)
+		{
+			System.out.println("Could not parse Doc2Vec batch output line: " + line);
+			e.printStackTrace();
+			return true;
+		}
+		return false;
+	}
+
+	private static String decodeField(String encoded)
+	{
+		return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
+	}
+
+	static final class Result
+	{
+		private final TreeMap<String,TreeMap<String,Double>> scores = new TreeMap<String,TreeMap<String,Double>>();
+		private final TreeMap<String,String> errors = new TreeMap<String,String>();
+		private final Set<String> requestedUsers = new HashSet<String>();
+		private final Set<String> completedUsers = new HashSet<String>();
+		private int exitCode = -1;
+
+		Result(Collection<String> users)
+		{
+			requestedUsers.addAll(users);
+			for (String user : users)
+			{
+				scores.put(user, new TreeMap<String,Double>());
+			}
+		}
+
+		private void addScore(String user, String candidate, double score)
+		{
+			if (requestedUsers.contains(user))
+			{
+				scores.get(user).put(candidate, score);
+			}
+		}
+
+		private void addError(String user, String error)
+		{
+			if (requestedUsers.contains(user))
+			{
+				errors.put(user, error);
+			}
+		}
+
+		private void markCompleted(String user)
+		{
+			if (requestedUsers.contains(user))
+			{
+				completedUsers.add(user);
+			}
+		}
+
+		TreeMap<String,Double> getScores(String user)
+		{
+			return scores.get(user);
+		}
+
+		Map<String,String> getErrors()
+		{
+			return errors;
+		}
+
+		boolean isCompleted(String user)
+		{
+			return completedUsers.contains(user);
+		}
+
+		int getExitCode()
+		{
+			return exitCode;
+		}
+	}
+}
diff --git BEFORE/TwitterGatherDataFollowers/userRyersonU/ExperimentResetFiles.java AFTER/TwitterGatherDataFollowers/userRyersonU/ExperimentResetFiles.java
new file mode 100644
index 0000000..64311e0
--- /dev/null
+++ AFTER/TwitterGatherDataFollowers/userRyersonU/ExperimentResetFiles.java
@@ -0,0 +1,62 @@
+package TwitterGatherDataFollowers.userRyersonU;
+
+import java.io.File;
+import java.util.ArrayList;
+import java.util.List;
+
+final class ExperimentResetFiles {
+	private ExperimentResetFiles() {
+	}
+
+	static List<File> resetExperimentOutputs(File projectDirectory, File selectedDataset) {
+		List<File> failedDeletes = resetDatasetOutputs(projectDirectory, selectedDataset);
+		File importantStuffDirectory = new File(projectDirectory, "important-stuff");
+
+		deleteIfExists(new File(importantStuffDirectory, "graph.gexf"), failedDeletes);
+		deleteIfExists(new File(importantStuffDirectory, "layout.jpg"), failedDeletes);
+		deleteIfExists(new File(importantStuffDirectory, "autolayout.pdf"), failedDeletes);
+		deleteIfExists(new File(importantStuffDirectory, "partition.pdf"), failedDeletes);
+		deleteIfExists(new File(importantStuffDirectory, "test-file-name.txt"), failedDeletes);
+		deleteIfExists(new File(importantStuffDirectory, "outPutSepFolloweeResults.txt"), failedDeletes);
+		deleteIfExists(new File(importantStuffDirectory, "outPutSepFollowee.txt"), failedDeletes);
+		deleteIfExists(new File(importantStuffDirectory, "outPutSep.txt"), failedDeletes);
+		deleteIfExists(new File(importantStuffDirectory, "outPutSepResult.txt"), failedDeletes);
+		deleteIfExists(new File(importantStuffDirectory, "outputChoice.txt"), failedDeletes);
+		deleteIfExists(new File(projectDirectory, "recommendations_lists.txt"), failedDeletes);
+		deleteIfExists(new File(projectDirectory, "tweetCounts.txt"), failedDeletes);
+
+		return failedDeletes;
+	}
+
+	static List<File> resetDatasetOutputs(File projectDirectory, File selectedDataset) {
+		List<File> failedDeletes = new ArrayList<File>();
+		if (selectedDataset == null) {
+			return failedDeletes;
+		}
+
+		File importantStuffDirectory = new File(projectDirectory, "important-stuff");
+		String graphEdgesBaseName = getDatasetGraphBaseName(selectedDataset.getName());
+		deleteIfExists(new File(importantStuffDirectory, graphEdgesBaseName + ".txt"), failedDeletes);
+		deleteIfExists(new File(importantStuffDirectory, graphEdgesBaseName + ".gml"), failedDeletes);
+		return failedDeletes;
+	}
+
+	private static void deleteIfExists(File file, List<File> failedDeletes) {
+		if (file.exists() && !file.delete()) {
+			failedDeletes.add(file);
+		}
+	}
+
+	private static String getDatasetGraphBaseName(String selectedFileName) {
+		String datasetName = selectedFileName;
+		int extensionIndex = datasetName.lastIndexOf('.');
+		if (extensionIndex > 0) {
+			datasetName = datasetName.substring(0, extensionIndex);
+		}
+		datasetName = datasetName.replaceAll("[^A-Za-z0-9._-]", "_");
+		if (datasetName.length() == 0) {
+			datasetName = "uploaded-dataset";
+		}
+		return "edges-numbers-" + datasetName;
+	}
+}
diff --git BEFORE/TwitterGatherDataFollowers/userRyersonU/GraphGmlBuilder.java AFTER/TwitterGatherDataFollowers/userRyersonU/GraphGmlBuilder.java
new file mode 100644
index 0000000..5ccd5aa
--- /dev/null
+++ AFTER/TwitterGatherDataFollowers/userRyersonU/GraphGmlBuilder.java
@@ -0,0 +1,196 @@
+package TwitterGatherDataFollowers.userRyersonU;
+
+import java.io.BufferedReader;
+import java.io.BufferedWriter;
+import java.io.File;
+import java.io.IOException;
+import java.nio.charset.StandardCharsets;
+import java.nio.file.AtomicMoveNotSupportedException;
+import java.nio.file.Files;
+import java.nio.file.Path;
+import java.nio.file.StandardCopyOption;
+
+final class GraphGmlBuilder {
+	private GraphGmlBuilder() {
+	}
+
+	static Result build(File edgeListFile, File nameNumberFile, File outputFile) throws IOException {
+		requireReadableFile(edgeListFile, "Graph edge list");
+		requireReadableFile(nameNumberFile, "Graph name-number map");
+		if (outputFile == null) {
+			throw new IllegalArgumentException("outputFile must not be null");
+		}
+
+		int maxNodeId = readMaxNodeId(nameNumberFile);
+		if (maxNodeId < 1) {
+			throw new IOException("Graph name-number map contains no valid node IDs: " + nameNumberFile.getPath());
+		}
+
+		File outputParent = outputFile.getAbsoluteFile().getParentFile();
+		if (outputParent != null && !outputParent.exists() && !outputParent.mkdirs()) {
+			throw new IOException("Could not create graph output directory: " + outputParent.getPath());
+		}
+
+		Path tempPath = Files.createTempFile(outputParent.toPath(), outputFile.getName() + ".", ".part");
+		long edgeCount = 0;
+		try {
+			try (BufferedWriter writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8)) {
+				writer.write("Creator \"DSMP - Source file: " + escapeGmlString(edgeListFile.getPath()) + "\"");
+				writer.newLine();
+				writer.write("graph");
+				writer.newLine();
+				writer.write("[");
+				writer.newLine();
+
+				for (int nodeId = 1; nodeId <= maxNodeId; nodeId++) {
+					writer.write("  node");
+					writer.newLine();
+					writer.write("  [");
+					writer.newLine();
+					writer.write("    id " + nodeId);
+					writer.newLine();
+					writer.write("  ]");
+					writer.newLine();
+				}
+
+				try (BufferedReader reader = Files.newBufferedReader(edgeListFile.toPath(), StandardCharsets.UTF_8)) {
+					String line;
+					int lineNumber = 0;
+					while ((line = reader.readLine()) != null) {
+						lineNumber++;
+						String trimmedLine = line.trim();
+						if (trimmedLine.isEmpty()) {
+							continue;
+						}
+						String[] fields = trimmedLine.split("\\s+");
+						if (fields.length != 2) {
+							throw invalidEdgeLine(edgeListFile, lineNumber, "expected exactly two node IDs");
+						}
+						int source = parsePositiveId(fields[0], edgeListFile, lineNumber, "source");
+						int target = parsePositiveId(fields[1], edgeListFile, lineNumber, "target");
+						if (source > maxNodeId || target > maxNodeId) {
+							throw invalidEdgeLine(edgeListFile, lineNumber,
+									"node ID exceeds maximum mapped node ID " + maxNodeId);
+						}
+
+						writer.write("  edge");
+						writer.newLine();
+						writer.write("  [");
+						writer.newLine();
+						writer.write("    source " + source);
+						writer.newLine();
+						writer.write("    target " + target);
+						writer.newLine();
+						writer.write("  ]");
+						writer.newLine();
+						edgeCount++;
+					}
+				}
+
+				writer.write("]");
+				writer.newLine();
+			}
+
+			moveIntoPlace(tempPath, outputFile.toPath());
+			return new Result(outputFile, maxNodeId, edgeCount);
+		}
+		finally {
+			Files.deleteIfExists(tempPath);
+		}
+	}
+
+	private static int readMaxNodeId(File nameNumberFile) throws IOException {
+		int maxNodeId = 0;
+		try (BufferedReader reader = Files.newBufferedReader(nameNumberFile.toPath(), StandardCharsets.UTF_8)) {
+			String line;
+			int lineNumber = 0;
+			while ((line = reader.readLine()) != null) {
+				lineNumber++;
+				String trimmedLine = line.trim();
+				if (trimmedLine.isEmpty()) {
+					continue;
+				}
+				int separatorIndex = lastWhitespaceIndex(trimmedLine);
+				if (separatorIndex < 1 || separatorIndex == trimmedLine.length() - 1) {
+					throw new IOException("Invalid name-number mapping at " + nameNumberFile.getPath()
+							+ ":" + lineNumber + ": expected a name followed by a numeric node ID");
+				}
+				String idField = trimmedLine.substring(separatorIndex + 1).trim();
+				int nodeId = parsePositiveId(idField, nameNumberFile, lineNumber, "mapped node");
+				maxNodeId = Math.max(maxNodeId, nodeId);
+			}
+		}
+		return maxNodeId;
+	}
+
+	private static int lastWhitespaceIndex(String value) {
+		for (int i = value.length() - 1; i >= 0; i--) {
+			if (Character.isWhitespace(value.charAt(i))) {
+				return i;
+			}
+		}
+		return -1;
+	}
+
+	private static int parsePositiveId(String value, File file, int lineNumber, String fieldName) throws IOException {
+		try {
+			int nodeId = Integer.parseInt(value);
+			if (nodeId < 1) {
+				throw new NumberFormatException("node ID must be positive");
+			}
+			return nodeId;
+		}
+		catch (NumberFormatException ex) {
+			throw new IOException("Invalid " + fieldName + " ID at " + file.getPath() + ":" + lineNumber
+					+ ": " + value, ex);
+		}
+	}
+
+	private static IOException invalidEdgeLine(File edgeListFile, int lineNumber, String reason) {
+		return new IOException("Invalid graph edge at " + edgeListFile.getPath() + ":" + lineNumber + ": " + reason);
+	}
+
+	private static void requireReadableFile(File file, String description) throws IOException {
+		if (file == null || !file.isFile() || !file.canRead()) {
+			throw new IOException(description + " is missing or unreadable: "
+					+ (file == null ? "null" : file.getPath()));
+		}
+	}
+
+	private static void moveIntoPlace(Path tempPath, Path outputPath) throws IOException {
+		try {
+			Files.move(tempPath, outputPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
+		}
+		catch (AtomicMoveNotSupportedException ex) {
+			Files.move(tempPath, outputPath, StandardCopyOption.REPLACE_EXISTING);
+		}
+	}
+
+	private static String escapeGmlString(String value) {
+		return value.replace("\\", "\\\\").replace("\"", "\\\"");
+	}
+
+	static final class Result {
+		private final File outputFile;
+		private final int nodeCount;
+		private final long edgeCount;
+
+		private Result(File outputFile, int nodeCount, long edgeCount) {
+			this.outputFile = outputFile;
+			this.nodeCount = nodeCount;
+			this.edgeCount = edgeCount;
+		}
+
+		File getOutputFile() {
+			return outputFile;
+		}
+
+		int getNodeCount() {
+			return nodeCount;
+		}
+
+		long getEdgeCount() {
+			return edgeCount;
+		}
+	}
+}
diff --git BEFORE/TwitterGatherDataFollowers/userRyersonU/MobileAgent.java AFTER/TwitterGatherDataFollowers/userRyersonU/MobileAgent.java
index 3b19170..d942971 100644
--- BEFORE/TwitterGatherDataFollowers/userRyersonU/MobileAgent.java
+++ AFTER/TwitterGatherDataFollowers/userRyersonU/MobileAgent.java
@@ -27,11 +27,7 @@ import jade.domain.FIPAAgentManagement.DFAgentDescription;
 import jade.domain.FIPAAgentManagement.ServiceDescription;
 import jade.domain.JADEAgentManagement.*;
 import jade.gui.*;
-import java.util.Scanner;    // added by Sepide 
-import java.util.Random;     // added by Sepide 
-import org.junit.Assert;   // Sepide
-
-import org.apache.commons.math3.random.RandomDataGenerator;
+import org.apache.commons.math3.random.RandomDataGenerator;
 
 
 public class MobileAgent extends Agent {
@@ -143,12 +139,14 @@ public class MobileAgent extends Agent {
 	//public Map<String,String> userFollowee = new LinkedHashMap<String,String>(); //list of users and their followee names before processing (may have extra since processing can remove users)   // added by Sepide 
 	
 	private static Random r = new Random(); //should not construct in method, make it static
-	private static RandomDataGenerator randomDataGenerator = new RandomDataGenerator(); //should not construct in method, make it static
-	
-	private String followeeName;
-
-
-	protected void setup() {
+	private static RandomDataGenerator randomDataGenerator = new RandomDataGenerator(); //should not construct in method, make it static
+	
+	private String followeeName;
+	private static final String TWEET_BATCH_ONTOLOGY = "Tweet Batch From User Agent";
+	private static final String TWEET_BATCH_PRIMARY_REC_SERVER_PREFIX = "__PRIMARY_REC_SERVER__\t";
+
+
+	protected void setup() {
 
 		followerNames = new ArrayList<String>();
 		
@@ -191,10 +189,10 @@ public class MobileAgent extends Agent {
 			userTfidfWordsBins = (LinkedHashMap<Double,ArrayList<String>>) args[28];
 			generateArtificialTweets();
 		}
-
-		followeeName = (String) args[29];
-		
-		System.out.println(getLocalName()+"'s followee:	"+followeeName);
+
+			followeeName = (String) args[29];
+			
+			System.out.println(getLocalName()+"'s followee:	"+followeeName);
 		
 		recServerEndMessagePassingTimes = new long[listRecServers.size()];
 		userMessagePassingTimes = new long[listRecServers.size()];;
@@ -335,7 +333,7 @@ public class MobileAgent extends Agent {
 
 					ACLMessage msg2 = new ACLMessage( ACLMessage.INFORM );
 					//ACLMessage msg24 = new ACLMessage( ACLMessage.INFORM );   // added by Sepide 
-					msg2.setContent(totalTweet + " " + whoTweeted+" "+ tweetId + " " + followeeName +" "+ tweetText);
+					msg2.setContent(totalTweet + "\t" + whoTweeted + "\t" + tweetId + "\t" + followeeName + "\t" + tweetText);
 					//msg24.setContent(totalTweet + " " + whoTweeted+" "+ tweetId + " " + followeeName +" "+ tweetText);  // added by Sepide 
 					// msg2.setContent(totalTweet + " " + whoTweeted+" "+ tweetId + " " + tweetText);
 					for (int i = 0; i < listRecServers.size(); i++)
@@ -492,10 +490,17 @@ public class MobileAgent extends Agent {
 					tweetCounter = 0;
 					finishTweeting = false;
 
-					if (readFrom == FROM_DB)
-						addBehaviour(TweetingFromDb);
-					else //read from text
-						addBehaviour(TweetingFromText);
+						if (readFrom == FROM_DB)
+							addBehaviour(TweetingFromDb);
+						else if (useTextBatchMode())
+						{
+							if (!sendTweetsFromTextAsBatch())
+							{
+								addBehaviour(TweetingFromText);
+							}
+						}
+						else //read from text
+							addBehaviour(TweetingFromText);
 				}
 
 				//Msg starter agent
@@ -504,22 +509,22 @@ public class MobileAgent extends Agent {
 					endTweetTime = System.currentTimeMillis();
 					
 					//System.out.println(getLocalName()+" received Stop Tweeting");
-					if (readFrom == FROM_DB)
-						removeBehaviour(TweetingFromDb);
-					else //read from text
-						removeBehaviour(TweetingFromText);
-				}
+						if (readFrom == FROM_DB)
+							removeBehaviour(TweetingFromDb);
+						else if (!useTextBatchMode()) //read from text
+							removeBehaviour(TweetingFromText);
+					}
 
 
 				//Msg sent from no one
-				if (msg!=null && msg.getOntology() == "Stop Querying" && msg.getPerformative() == ACLMessage.REQUEST) 
+				if (msg!=null && "Stop Querying".equals(msg.getOntology()) && msg.getPerformative() == ACLMessage.REQUEST)
 				{
 					System.out.println(getLocalName()+" received Stop Querying");
 					removeBehaviour( Querying );
 				}
 
 				//@Jason added canQuery condition, msg from starter agent
-				if (msg!=null && msg.getOntology() == "Start Querying" && msg.getPerformative() == ACLMessage.REQUEST && canQuery==true)
+				if (msg!=null && "Start Querying".equals(msg.getOntology()) && msg.getPerformative() == ACLMessage.REQUEST && canQuery==true)
 					//if (msg!=null && msg.getOntology() == "Start Querying" && msg.getPerformative() == ACLMessage.REQUEST) 
 				{
 					//removeBehaviour( Tweeting );
@@ -656,12 +661,13 @@ public class MobileAgent extends Agent {
 
 				ACLMessage msg = myAgent.receive(mt_organizingAgent);
 
-				if (msg!=null && msg.getOntology() == "Scores for User") 
-				{
-					System.out.println(myAgent.getLocalName()+" Scores for User Received");
-
-					int recCount = 0;
-					LinkedHashMap<String,Double> scoreReceived;
+				if (msg!=null && "Scores for User".equals(msg.getOntology()))
+				{
+					System.out.println(myAgent.getLocalName()+" Scores for User Received");
+
+					try {
+					int recCount = 0;
+					LinkedHashMap<String,Double> scoreReceived;
 					
 					String outputFileName = outputFileName = "Results/Recommendations/" + referenceUser + "/Recommendations_" + referenceUser + ".txt";
 
@@ -750,13 +756,13 @@ public class MobileAgent extends Agent {
 							writerSep.write(userScoresText);
 							writerSep.newLine();
 							writerSep.close();
-							usersRec = myGui.getUsersRec();
-							System.out.println("UsersRec:" +usersRec);
+							usersRec = getCurrentQueryUserList();
+							System.out.println("UsersRec:" +usersRec);
 							BufferedWriter writerSep3 = new BufferedWriter(new FileWriter(importantStuffDirName+"outPutSepResult.txt",false));
 							BufferedWriter writerSepFollowee2 = new BufferedWriter(new FileWriter(importantStuffDirName+"outPutSepFolloweeResults.txt",true));
 							//BufferedReader readerSep = new BufferedReader(new FileReader("D:/important-stuff/Dataset-Journals-Authors-Titles-August6.txt"));	
 							//RandomAccessFile fileSep = new RandomAccessFile("D:/important-stuff/Dataset-Journals-Authors-Titles-August6.txt", "r");
-							RandomAccessFile fileSep = new RandomAccessFile(importantStuffDirName + selectedFile.getName(), "r");
+							RandomAccessFile fileSep = new RandomAccessFile(selectedFile, "r");
 							//RandomAccessFile fileSep = new RandomAccessFile("D:/important-stuff/Reduced_94k.txt", "r");
 							//June 12 RandomAccessFile fileSep = new RandomAccessFile("D:/important-stuff/Reduced_14k.txt", "r");
 							//RandomAccessFile fileSep = new RandomAccessFile("D:/important-stuff/Reduced_57k.txt", "r");
@@ -835,13 +841,13 @@ public class MobileAgent extends Agent {
 							
 							//BufferedReader readerSepi1 = new BufferedReader(new FileReader("D:/important-stuff/Dataset-Journals-Authors-Titles-August6.txt"));	
 							//BufferedReader readerSepi1 = new BufferedReader(new FileReader("D:/important-stuff/Reduced_94k.txt"));
-							BufferedReader readerSepi1 = new BufferedReader(new FileReader(importantStuffDirName + selectedFile.getName()));
+							BufferedReader readerSepi1 = new BufferedReader(new FileReader(selectedFile));
 							//June 12BufferedReader readerSepi1 = new BufferedReader(new FileReader("D:/important-stuff/Reduced_14k.txt"));
 							//BufferedReader readerSepi1 = new BufferedReader(new FileReader("D:/important-stuff/Reduced_57k.txt"));
 							//BufferedWriter writerSepi1 = new BufferedWriter(new FileWriter("D:/important-stuff/Dataset-Journals-Authors-Titles-August6.txt"));	
 							//BufferedReader readerSepi2 = new BufferedReader(new FileReader("D:/important-stuff/Dataset-Journals-Authors-Titles-August6.txt"));
 							//BufferedReader readerSepi2 = new BufferedReader(new FileReader("D:/important-stuff/Reduced_94k.txt"));
-							BufferedReader readerSepi2 = new BufferedReader(new FileReader(importantStuffDirName + selectedFile.getName()));
+							BufferedReader readerSepi2 = new BufferedReader(new FileReader(selectedFile));
 							//June 12BufferedReader readerSepi2 = new BufferedReader(new FileReader("D:/important-stuff/Reduced_14k.txt"));
 							//BufferedReader readerSepi2 = new BufferedReader(new FileReader("D:/important-stuff/Reduced_57k.txt"));
 							String[] words1 = null;
@@ -931,7 +937,7 @@ public class MobileAgent extends Agent {
 					 
 					 //String filepath="D:/important-stuff/Dataset-Journals-Authors-Titles-August6.txt";
 					 //String filepath="D:/important-stuff/Reduced_94k.txt";
-					 String filepath=importantStuffDirName + selectedFile.getName();
+					 String filepath=selectedFile.getAbsolutePath();
 					 //June 12String filepath="D:/important-stuff/Reduced_14k.txt";
 					 //String filepath="D:/important-stuff/Reduced_57k.txt";
 					 //String tempFile="D:/important-stuff/Dataset-Test-August11.txt";
@@ -1027,296 +1033,45 @@ public class MobileAgent extends Agent {
 						}
 						
 						
-						// Code added for calculating the probability of following a recommendation by the user 
-						
-						int kRecommend = Integer.parseInt(myGui.recommendationField.getText());
-						int min = 1;
-						int max = kRecommend;
-						Random rand = new Random();
-						int randomNum = rand.nextInt((max - min) + 1) + min;
-						System.out.println("Random number generated: " + randomNum);
-						
-						
-						// Code added for calculating the weighted probability of following a recommendation by the user
-						ArrayList<String> KeYs = new ArrayList<String>();
-						ArrayList<Double> vaLues = new ArrayList<Double>();
+						int kRecommend = Integer.parseInt(myGui.recommendationField.getText());
+						ArrayList<String> recommendationNames = new ArrayList<String>();
+						ArrayList<Double> recommendationWeights = new ArrayList<Double>();
 
 						for (String otherUser: scoreReceived.keySet())
 							{
 									recCount++;
 									if (recCount <= kRecommend)
 									{
-										KeYs.add(otherUser);
-										vaLues.add(scoreReceived.get(otherUser));
-										
-									}
-								
-							}
-							
-							/* for(int i = 0; i < KeYs.size(); i++) {   
-								System.out.print("Keys: " + KeYs.get(i)+ "\n");
-							}
-							for(int i = 0; i < vaLues.size(); i++) {   
-								System.out.print("Values: " + vaLues.get(i)+ "\n");
-							} */
-							
-							
-							
-							String lineC = "";
-							String[] option = null;
-							
-							try {
-								
-								String doc2vecDirLoc = "TwitterGatherDataFollowers/userRyersonU/";
-								File doc2vecLocDir = new File(doc2vecDirLoc);
-								if (!doc2vecLocDir.exists())
-								{
-										doc2vecLocDir.mkdirs();
-								}
-								//java.lang.ProcessBuilder pb = new ProcessBuilder("C:/Program Files/Python39/python.exe","D:/Simulator-S-15-May-2020/TwitterGatherDataFollowers/userRyersonU/probab.py",""+KeYs.get(0),""+KeYs.get(1),""+KeYs.get(2),""+vaLues.get(0),""+vaLues.get(1),""+vaLues.get(2)).inheritIO();
-                                java.lang.ProcessBuilder pb = new ProcessBuilder("python",doc2vecDirLoc + "probab.py",""+KeYs,""+vaLues,""+myGui.algorithmSelectionBox.getSelectedIndex()).inheritIO();
-								Process p = pb.start();
-								
-								int exitCode = p.waitFor();
-								Assert.assertEquals("No errors should be detected", 0, exitCode);
-					
-					             try 
-									 {
-										  Thread.sleep(500);
-									 } 
-								  catch(InterruptedException e)
-									{
-									  e.printStackTrace();
-									}
-									
-									System.out.println("Probability Calculation is finished");
-									BufferedReader readerChoice = new BufferedReader(new FileReader(importantStuffDirName +"outputChoice.txt"));
-									lineC = readerChoice.readLine();
-									
-									while (lineC != null ) {
+										recommendationNames.add(otherUser);
+										recommendationWeights.add(scoreReceived.get(otherUser));
 										
-										
-										option = lineC.split(" ");
-										lineC = readerChoice.readLine();
-										System.out.println("the option with probability selected is: " +option[0]);
 									}
-									readerChoice.close();
-									 
-									
 								
 							}
-							catch(InterruptedException e)
-							{
-								 // this part is executed when an exception (in this example InterruptedException) occurs
-							}
-						
-					
-                    BufferedWriter writerFollowee = new BufferedWriter(new FileWriter(importantStuffDirName + "outPutSepFollowee.txt"));					
-					File file = new File(importantStuffDirName + "outPutSepFollowee.txt");	
-					try {
-					
-				   Scanner scanner = new Scanner(file);
-				   Scanner myObj = new Scanner(System.in); 
-				   String line = null;   
-				   BufferedWriter writerSepFollowee = new BufferedWriter(new FileWriter(importantStuffDirName + "outPutSepFollowee.txt",true));
-				   usersRec = myGui.getUsersRec();
-				   //Commented out by Sepide in Des.30 int iterNum = myGui.simulationSelectionBox.getSelectedIndex();
-				   //commented out on Feb17 int iterNum = (sepTest.indexOf(maxKey) + 2);
-				   //Commented out on Feb. 17myGui.simulationSelectionBox.setSelectedIndex(iterNum);
-				   //int iterNum  = randomNum;
-				   int iterNum  = KeYs.indexOf(option[0]) + 1;  // added on March 13
-				   myGui.simulationSelectionBox.setSelectedIndex(iterNum);
-                   //String iterNum = myObj.nextLine();
-                   //System.out.println("number of iteration for this user is: " + iterNum);				   
-				   //int lineNum = 1;
-				   /* if (file.exists()){
-					   
-				   }  */
-				   something: for(String str: usersRec) {
-						  
-						 while (true) { 
-                            if(iterNum == 1) {
-								writerSepFollowee.write(str);
-						        writerSepFollowee.write(" Followes ");
-								writerSepFollowee.write(someTest3);
-								writerSepFollowee.newLine();
-								writerSepFollowee.write(str);
-							    writerSepFollowee.write(" Followes ");
-							    writerSepFollowee.write(sepTest.get(0));
-								writerSepFollowee.newLine();
-								break something;
-			                }							
-							else if(iterNum == 2) {
-								
-								for (int i = 1; scanner.hasNext()== true; i++){
-									    line = scanner.nextLine();
-								    // if (line.contains(str + " Followes " + sepTest.get(0)))		
-									if (line.contains(str + " Followes " + sepTest.get(0))){
-										//writerSepFollowee.write("\n");
-										writerSepFollowee.write("\n" + str);
-							            writerSepFollowee.write(" Followes ");
-							            writerSepFollowee.write(sepTest.get(1));
-								        //writerSepFollowee.newLine();
-								        break something;
-									}else continue;
-								}
-								writerSepFollowee.write(str);
-						        writerSepFollowee.write(" Followes ");
-								writerSepFollowee.write(someTest3);
-								writerSepFollowee.newLine();
-								writerSepFollowee.write(str);
-							    writerSepFollowee.write(" Followes ");
-							    writerSepFollowee.write(sepTest.get(0));
-								writerSepFollowee.newLine();
-								writerSepFollowee.write(str);
-							    writerSepFollowee.write(" Followes ");
-							    writerSepFollowee.write(sepTest.get(1));
-								writerSepFollowee.newLine();
-								break something;
-	
-			                }
-							else if (iterNum == 3){
-								for (int i = 1; scanner.hasNext()== true; i++){
-									    line = scanner.nextLine();
-									if (line.contains(str + " Followes " + sepTest.get(1))){
-										writerSepFollowee.write(str);
-							            writerSepFollowee.write(" Followes ");
-							            writerSepFollowee.write(sepTest.get(2));
-								        writerSepFollowee.newLine();
-								        break something;
-									}else continue;
-								}
-								writerSepFollowee.write(str);
-						        writerSepFollowee.write(" Followes ");
-								writerSepFollowee.write(someTest3);
-								writerSepFollowee.newLine();
-								writerSepFollowee.write(str);
-							    writerSepFollowee.write(" Followes ");
-							    writerSepFollowee.write(sepTest.get(0));
-								writerSepFollowee.newLine();
-								writerSepFollowee.write(str);
-							    writerSepFollowee.write(" Followes ");
-							    writerSepFollowee.write(sepTest.get(1));
-								writerSepFollowee.newLine();
-								writerSepFollowee.write(str);
-							    writerSepFollowee.write(" Followes ");
-							    writerSepFollowee.write(sepTest.get(2));
-								writerSepFollowee.newLine();
-								break something;
-			                }
-							
-							else if (iterNum == 4){
-								for (int i = 1; scanner.hasNext()== true; i++){
-									    line = scanner.nextLine();
-									if (line.contains(str + " Followes " + sepTest.get(2))){
-										writerSepFollowee.write(str);
-							            writerSepFollowee.write(" Followes ");
-							            writerSepFollowee.write(sepTest.get(3));
-								        writerSepFollowee.newLine();
-								        break something;
-									}else continue;
-								}
-								writerSepFollowee.write(str);
-						        writerSepFollowee.write(" Followes ");
-								writerSepFollowee.write(someTest3);
-								writerSepFollowee.newLine();
-								writerSepFollowee.write(str);
-							    writerSepFollowee.write(" Followes ");
-							    writerSepFollowee.write(sepTest.get(0));
-								writerSepFollowee.newLine();
-								writerSepFollowee.write(str);
-							    writerSepFollowee.write(" Followes ");
-							    writerSepFollowee.write(sepTest.get(1));
-								writerSepFollowee.newLine();
-								writerSepFollowee.write(str);
-							    writerSepFollowee.write(" Followes ");
-							    writerSepFollowee.write(sepTest.get(2));
-								writerSepFollowee.newLine();
-								writerSepFollowee.write(str);
-							    writerSepFollowee.write(" Followes ");
-							    writerSepFollowee.write(sepTest.get(3));
-								writerSepFollowee.newLine();
-								break something;
-			                }
-							else if (iterNum == 5){
-								for (int i = 1; scanner.hasNext()== true; i++){
-									    line = scanner.nextLine();
-									if (line.contains(str + " Followes " + sepTest.get(3))){
-										writerSepFollowee.write(str);
-							            writerSepFollowee.write(" Followes ");
-							            writerSepFollowee.write(sepTest.get(4));
-								        writerSepFollowee.newLine();
-								        break something;
-									}else continue;
-								}
-								writerSepFollowee.write(str);
-						        writerSepFollowee.write(" Followes ");
-								writerSepFollowee.write(someTest3);
-								writerSepFollowee.newLine();
-								writerSepFollowee.write(str);
-							    writerSepFollowee.write(" Followes ");
-							    writerSepFollowee.write(sepTest.get(0));
-								writerSepFollowee.newLine();
-								writerSepFollowee.write(str);
-							    writerSepFollowee.write(" Followes ");
-							    writerSepFollowee.write(sepTest.get(1));
-								writerSepFollowee.newLine();
-								writerSepFollowee.write(str);
-							    writerSepFollowee.write(" Followes ");
-							    writerSepFollowee.write(sepTest.get(2));
-								writerSepFollowee.newLine();
-								writerSepFollowee.write(str);
-							    writerSepFollowee.write(" Followes ");
-							    writerSepFollowee.write(sepTest.get(3));
-								writerSepFollowee.newLine();
-								writerSepFollowee.write(str);
-							    writerSepFollowee.write(" Followes ");
-							    writerSepFollowee.write(sepTest.get(4));
-								writerSepFollowee.newLine();
-								break something;
-			                }
-						/* else if (scanner.hasNextLine() == true) {
-							      line = scanner.nextLine();
-							if (line.contains(str + " Followes " + someTest3)){
-								if (scanner.hasNextLine() == true){
-									line = scanner.nextLine();
-									if (line.contains(str + " Followes " + sepTest.get(0))){
-										if (scanner.hasNextLine() == true){
-											line = scanner.nextLine();
-											if (line.contains(str + " Followes " + sepTest.get(1))){
-												if (scanner.hasNextLine() == true){
-													line = scanner.nextLine();
-													if (line.contains(str + " Followes " + sepTest.get(2))){
-														break something;
-													}
-												}
-											}
-										}
-									}
-								}
-							}
-							
-			             }  */
-							
-					}		
-							
-						  //String followeeNameSep = followeeFollowers.getKey("Germany");
-							//Commented out Oct. 23 String followeeSep = userFollowee.get(str);
-							// Commented out Oct 23 writerSepFollowee.newLine();
-							//String followeeSep = userFollowee.get(str);
-							//writerSepFollowee.write(" Followes ");							
-						    
-						  		
-					}
-				   
-				   writerSepFollowee.close();
-				   scanner.close();  
-				   }
-				   
-				   catch (IOException e) {
-						// TODO Auto-generated catch block
-						e.printStackTrace();
-					}
+							int selectedRecommendationIndex = BatchRecommendationSelector.selectIndex(
+									recommendationWeights,
+									myGui.algorithmSelectionBox.getSelectedIndex() == Doc2Vec,
+									r);
+							String selectedRecommendation = selectedRecommendationIndex >= 0
+									? recommendationNames.get(selectedRecommendationIndex)
+									: null;
+							System.out.println("The option selected by weighted probability is: " + selectedRecommendation);
+							BatchRecommendationSelector.writeSelectedRecommendation(
+									new File(importantStuffDirName + "outputChoice.txt"),
+									selectedRecommendation);
+
+							int selectionBoxIndex = selectedRecommendationIndex + 1;
+							myGui.simulationSelectionBox.setSelectedIndex(selectionBoxIndex);
+							usersRec = getCurrentQueryUserList();
+							try (BufferedWriter writerSepFollowee = new BufferedWriter(
+									new FileWriter(importantStuffDirName + "outPutSepFollowee.txt", false))) {
+								BatchRecommendationSelector.writeFolloweeChoices(
+										writerSepFollowee,
+										usersRec,
+										someTest3,
+										sepTest,
+										selectedRecommendationIndex);
+							}
 						
 						//Iterator itr2 = sepTest.iterator(); 
 						  /* while(itr2.hasNext())
@@ -1334,28 +1089,58 @@ public class MobileAgent extends Agent {
 						String[] wordsFile4 = null;
 						String[] wordsFile5 = null;
 						String userFolloweeNum = "";
-						if (selectedFile != null) {
-							String selectedFileName = selectedFile.getName();
-							// Construct the target filename by prepending "name-number-"
-							String targetNameNumberFileName = importantStuffDirName + "name-number-" + selectedFileName;
-
-							File nameNumberFile = new File(targetNameNumberFileName);
-
-							System.out.println("Checking for file: " + nameNumberFile.getPath()); // Debugging output
-
-							// Check if the constructed file path exists and is a file
-							if (nameNumberFile.exists() && nameNumberFile.isFile()) {
-								System.out.println("Found file: " + targetNameNumberFileName); // Debugging output
-								try {
+						if (selectedFile != null) {
+							String selectedFileName = selectedFile.getName();
+							// Construct the target filename by prepending "name-number-"
+							String targetNameNumberFileName = importantStuffDirName + "name-number-" + selectedFileName;
+							String graphEdgesBaseName = getDatasetGraphBaseName(selectedFileName);
+							String graphEdgesFileName = importantStuffDirName + graphEdgesBaseName + ".txt";
+
+							File nameNumberFile = new File(targetNameNumberFileName);
+
+							System.out.println("Checking for file: " + nameNumberFile.getPath());
+							if (!nameNumberFile.exists()) {
+								LinkedHashSet<String> nodeNames = new LinkedHashSet<String>();
+								try {
+									BufferedReader datasetReader = new BufferedReader(new FileReader(selectedFile));
+									String datasetLine;
+									while ((datasetLine = datasetReader.readLine()) != null) {
+										String[] datasetFields = datasetLine.split("\t", 6);
+										if (datasetFields.length >= 5) {
+											nodeNames.add(datasetFields[0]);
+											nodeNames.add(datasetFields[4]);
+										}
+									}
+									datasetReader.close();
+
+									BufferedWriter mapWriter = new BufferedWriter(new FileWriter(nameNumberFile));
+									int nodeNumber = 1;
+									for (String nodeName : nodeNames) {
+										mapWriter.write(nodeName + "\t" + nodeNumber);
+										mapWriter.newLine();
+										nodeNumber++;
+									}
+									mapWriter.close();
+									System.out.println("Created name-number file: " + targetNameNumberFileName);
+								} catch (IOException e) {
+									System.err.println("Error creating name-number file: " + targetNameNumberFileName);
+									e.printStackTrace();
+								}
+							}
+
+							// Check if the constructed file path exists and is a file
+							if (nameNumberFile.exists() && nameNumberFile.isFile()) {
+								System.out.println("Found file: " + targetNameNumberFileName);
+								try {
 									// 1. Find User's Number
 									BufferedReader readerUserNum = new BufferedReader(new FileReader(targetNameNumberFileName));
-									String lineUser;
-									String userNum = null; // Initialize user number for this scope
-									outerUser: while ((lineUser = readerUserNum.readLine()) != null) {
-										wordsFile3 = lineUser.split(" "); // Split line
-										if (wordsFile3.length > 1) { // Basic check for valid line format
-											for(String str: usersRec) { // usersRec needs to be accessible
-												if (wordsFile3[0].equals(str)){
+									String lineUser;
+									String userNum = null;
+									outerUser: while ((lineUser = readerUserNum.readLine()) != null) {
+										wordsFile3 = lineUser.split("\\s+(?=\\d+$)", 2); // Keep names containing spaces intact
+										if (wordsFile3.length > 1) {
+											for(String str: usersRec) {
+												if (wordsFile3[0].equals(str)){
 													userNum = wordsFile3[1]; // Store the found user number
 													System.out.println("User's Number ("+str+"):" + userNum);
 													break outerUser; // Found the user, exit loop
@@ -1367,32 +1152,31 @@ public class MobileAgent extends Agent {
 
 									// 2. Find User's Original Followee's Number
 									BufferedReader readerOrigFollowee = new BufferedReader(new FileReader(targetNameNumberFileName));
-									String lineOrigFollowee;
-//									String[] wordsFile5; // Declare here
-									outerOrigFollowee: while ((lineOrigFollowee = readerOrigFollowee.readLine()) != null) {
-										wordsFile5 = lineOrigFollowee.split(" ");
-										if (wordsFile5.length > 1 && wordsFile5[0].equals(someTest3)){ // someTest3 needs to be accessible
-											System.out.println("User's Original Followee Number:" + wordsFile5[1]);
-											userFolloweeNum = wordsFile5[1]; // Assign to class/outer scope variable if needed elsewhere
+									String lineOrigFollowee;
+									outerOrigFollowee: while ((lineOrigFollowee = readerOrigFollowee.readLine()) != null) {
+										wordsFile5 = lineOrigFollowee.split("\\s+(?=\\d+$)", 2);
+										if (wordsFile5.length > 1 && wordsFile5[0].equals(someTest3)){
+											System.out.println("User's Original Followee Number:" + wordsFile5[1]);
+											userFolloweeNum = wordsFile5[1]; // Preserve the original edge target.
 											break outerOrigFollowee;
 										}
 									}
 									readerOrigFollowee.close();
 
 									// 3. Find Selected Followee's Number
-									int iterNum = myGui.simulationSelectionBox.getSelectedIndex(); // Needs myGui
+									int iterNum = myGui.simulationSelectionBox.getSelectedIndex();
 									System.out.println("iterNum (Recommendation choice):  " + iterNum);
 									String selectedFolloweeNum = null; // Initialize
 									BufferedReader readerSelectedFollowee = new BufferedReader(new FileReader(targetNameNumberFileName));
-									String lineSelectedFollowee;
-									outerSelectedFollowee: while ((lineSelectedFollowee = readerSelectedFollowee.readLine()) != null) {
-										wordsFile4 = lineSelectedFollowee.split(" ");
-										if (wordsFile4.length > 1) {
+									String lineSelectedFollowee;
+									outerSelectedFollowee: while ((lineSelectedFollowee = readerSelectedFollowee.readLine()) != null) {
+										wordsFile4 = lineSelectedFollowee.split("\\s+(?=\\d+$)", 2);
+										if (wordsFile4.length > 1) {
 											// Determine the target followee name based on iterNum and sepTest
 											String targetFolloweeName = null;
 											if (iterNum == 0 && someTest3 != null) {
 												targetFolloweeName = someTest3; // 0 means original followee
-											} else if (iterNum > 0 && iterNum <= sepTest.size()) { // sepTest needs to be accessible (e.g., ArrayList<String>)
+											} else if (iterNum > 0 && iterNum <= sepTest.size()) {
 												targetFolloweeName = sepTest.get(iterNum - 1); // Get from recommendations list
 											}
 
@@ -1406,45 +1190,33 @@ public class MobileAgent extends Agent {
 									}
 									readerSelectedFollowee.close();
 
-									// 4. Write Edges
-									if (userNum != null && userFolloweeNum != null && !userFolloweeNum.isEmpty() && selectedFolloweeNum != null) {
-										try {
-											BufferedWriter writeredges = new BufferedWriter(new FileWriter(importantStuffDirName + "edges-numbers.txt",true));
-											writeredges.write(userNum + " " + userFolloweeNum + "\n"); // Original edge
-											writeredges.write(userNum + " " + selectedFolloweeNum); // New edge based on recommendation
-											writeredges.newLine();
-											writeredges.close();
-											System.out.println("Wrote edges for user " + userNum + " to edges-numbers.txt");
-										} catch (IOException e_edge) {
-											System.err.println("Error writing edges to edges-numbers.txt");
-											e_edge.printStackTrace();
-										}
-										// Trigger conversion script with name-number file for complete node set
-										try {
-											 Process p = java.lang.Runtime.getRuntime().exec( "python "+ importantStuffDirName +"TXT2GMLv1.0/conversion.py" + "  " + importantStuffDirName +"edges-numbers" + " " + targetNameNumberFileName);
-											p.waitFor();
-										} catch (Exception e_conv) {
-											System.err.println("Error executing conversion script");
-											e_conv.printStackTrace();
-										}
-
-									} else {
-										System.out.println("Skipping edge writing - required numbers not found.");
-									}
-
-
-								} catch (FileNotFoundException e) {
-									System.err.println("Error: Could not find or open file: " + targetNameNumberFileName);
-									e.printStackTrace();
-								} catch (IOException e) {
-									System.err.println("Error reading file: " + targetNameNumberFileName);
-									e.printStackTrace();
-								} catch (ArrayIndexOutOfBoundsException e) {
-									System.err.println("Error parsing line in file: " + targetNameNumberFileName + ". Check format (expected space delimiter).");
-									e.printStackTrace();
-								}
-							} else {
-								System.out.println("File not found or not a file: " + targetNameNumberFileName);
+										// 4. Write Edges
+										if (userNum != null && userFolloweeNum != null && !userFolloweeNum.isEmpty() && selectedFolloweeNum != null) {
+											try (BufferedWriter writeredges = new BufferedWriter(new FileWriter(graphEdgesFileName,true))) {
+												writeredges.write(userNum + " " + userFolloweeNum + "\n"); // Original edge
+												writeredges.write(userNum + " " + selectedFolloweeNum); // New edge based on recommendation
+												writeredges.newLine();
+												System.out.println("Wrote edges for user " + userNum + " to " + graphEdgesFileName);
+											} catch (IOException e_edge) {
+												System.err.println("Error writing edges to " + graphEdgesFileName);
+												e_edge.printStackTrace();
+											}
+										} else {
+											System.out.println("Skipping edge writing - required numbers not found.");
+										}
+
+								} catch (FileNotFoundException e) {
+									System.err.println("Error: Could not find or open file: " + targetNameNumberFileName);
+									e.printStackTrace();
+								} catch (IOException e) {
+									System.err.println("Error reading file: " + targetNameNumberFileName);
+									e.printStackTrace();
+								} catch (ArrayIndexOutOfBoundsException e) {
+									System.err.println("Error parsing line in file: " + targetNameNumberFileName + ". Check format (expected space delimiter).");
+									e.printStackTrace();
+								}
+							} else {
+								System.out.println("File not found or not a file: " + targetNameNumberFileName);
 								// Handle cases where the expected name-number file doesn't exist
 							}
 						} else {
@@ -2033,23 +1805,103 @@ public class MobileAgent extends Agent {
 					// End of code added by Sepide
 					
 				
-					ACLMessage msg4 = new ACLMessage( ACLMessage.INFORM );
-					msg4.addReceiver( new AID("Organizing Agent1", AID.ISLOCALNAME) ); 
-					msg4.setContent(Integer.toString(requestnumber));
-					msg4.setOntology("Scores Received");
-					send(msg4);
-					
-					 
-				}			  
-			}
-		};				
-
-		addBehaviour( Communication );
-	}	
-
-	//generate tweets based on individual users
-	public String generateTweetText4(String tweetUserName)
-	{
+					} catch (RuntimeException e) {
+						System.err.println(getLocalName() + " failed while applying recommendation side effects.");
+						e.printStackTrace();
+					} finally {
+						sendScoresReceivedAcknowledgement();
+					}
+				}
+			}
+		};
+
+		addBehaviour( Communication );
+	}	
+
+	private void sendScoresReceivedAcknowledgement()
+	{
+		ACLMessage acknowledgement = new ACLMessage(ACLMessage.INFORM);
+		acknowledgement.addReceiver(new AID("Organizing Agent1", AID.ISLOCALNAME));
+		acknowledgement.setContent(Integer.toString(requestnumber));
+		acknowledgement.setOntology("Scores Received");
+		send(acknowledgement);
+	}
+
+	private boolean useTextBatchMode()
+	{
+		return readFrom == FROM_TEXT && algorithmRec != MLP;
+	}
+	
+	private boolean sendTweetsFromTextAsBatch()
+	{
+		ArrayList<String> tweetBatch = new ArrayList<String>(usersTweetFromDb.size() + 1);
+		tweetBatch.add(TWEET_BATCH_PRIMARY_REC_SERVER_PREFIX + connectedtoTfidfservernumber);
+		for (int i = usersTweetFromDb.size() - 1; i >= 0; i--)
+		{
+			tweetBatch.add(formatTweetMessage(usersTweetFromDb.get(i)));
+		}
+		
+		ACLMessage msgBatch = new ACLMessage(ACLMessage.INFORM);
+		for (int i = 0; i < listRecServers.size(); i++)
+		{
+			msgBatch.addReceiver(new AID("Recommender-ServiceAgent"+listRecServers.get(i), AID.ISLOCALNAME));
+		}
+		msgBatch.setConversationId(conversationIDReceived);
+		msgBatch.setOntology(TWEET_BATCH_ONTOLOGY);
+		
+		try
+		{
+			msgBatch.setContentObject(tweetBatch);
+		}
+		catch (IOException e)
+		{
+			e.printStackTrace();
+			return false;
+		}
+		
+		recServerBeginMessagePassingTime = System.nanoTime();
+		send(msgBatch);
+		
+		tweetCounter = usersTweetFromDb.size();
+		if (followAfterTweet > 0 && tweetCounter >= followAfterTweet)
+		{
+			sendFollowedFromUserAgentMessage();
+		}
+		
+		tweetCount = 0;
+		finishTweeting = true;
+		sendTweetingCompletedMessage();
+		return true;
+	}
+		
+	private String formatTweetMessage(Tweet currentTweet)
+	{
+		return totalTweet + "\t" + currentTweet.getUser() + "\t" + currentTweet.getTweetId() + "\t" + followeeName + "\t" + currentTweet.getTweetText();
+	}
+	
+	private void sendFollowedFromUserAgentMessage()
+	{
+		ACLMessage msg3 = new ACLMessage( ACLMessage.INFORM );
+		msg3.addReceiver( new AID(userToFollow+"-UserAgent", AID.ISLOCALNAME) );
+		msg3.setOntology("Followed From User Agent");			
+		send(msg3);
+	}
+	
+	private void sendTweetingCompletedMessage()
+	{
+		ACLMessage msg = new ACLMessage( ACLMessage.INFORM );
+		String temp1 = Integer.toString(totalTweet);
+		String temp2 = Integer.toString(connectedtoTfidfservernumber);
+		msg.setContent("Tweets Send(" + temp1 + ") connected to TFIDF" + temp2 + " ConversionID: " + conversationIDReceived);
+		msg.addReceiver( new AID("Starter Agent", AID.ISLOCALNAME) ); 
+		msg.setConversationId(conversationIDReceived);
+		msg.setOntology("Tweeting Completed");
+		send(msg);
+	}
+
+	//generate tweets based on individual users
+	public String generateTweetText4(String tweetUserName)
+	{
 
 		String generatedTweetText = "";
 		double userShape = shapeParameter; //Average shape parameter from original corpus
@@ -2164,11 +2016,11 @@ public class MobileAgent extends Agent {
 		return generatedWord;
 	}
 	
-	public void generateArtificialTweets()
-	{
-		ArrayList<Tweet> artificialTweets = new ArrayList<Tweet>();
-		for (Tweet currentRealTweet : usersTweetFromDb )
-		{
+	public void generateArtificialTweets()
+	{
+		ArrayList<Tweet> artificialTweets = new ArrayList<Tweet>();
+		for (Tweet currentRealTweet : usersTweetFromDb )
+		{
 			String artificialTweetText;
 			Tweet artificialTweet = new Tweet();
 			artificialTweet.setTweetId(currentRealTweet.getTweetId());
@@ -2180,15 +2032,43 @@ public class MobileAgent extends Agent {
 			
 			artificialTweets.add(artificialTweet);
 		}
-		
-		usersTweetFromDb.clear();
-		usersTweetFromDb = artificialTweets;
-	}
-	
-	protected void takeDown() 
-	{
-		try {
-			DFService.deregister(this);
+
+		usersTweetFromDb.clear();
+		usersTweetFromDb = artificialTweets;
+	}
+
+	private ArrayList<String> getCurrentQueryUserList()
+	{
+		ArrayList<String> currentUsers = new ArrayList<String>();
+		currentUsers.add(getCurrentUserName());
+		return currentUsers;
+	}
+
+	private String getCurrentUserName()
+	{
+		return getLocalName().split("-",2)[0];
+	}
+
+	private String getDatasetGraphBaseName(String selectedFileName)
+	{
+		String datasetName = selectedFileName;
+		int extensionIndex = datasetName.lastIndexOf('.');
+		if (extensionIndex > 0)
+		{
+			datasetName = datasetName.substring(0, extensionIndex);
+		}
+		datasetName = datasetName.replaceAll("[^A-Za-z0-9._-]", "_");
+		if (datasetName.length() == 0)
+		{
+			datasetName = "uploaded-dataset";
+		}
+		return "edges-numbers-" + datasetName;
+	}
+
+	protected void takeDown()
+	{
+		try {
+			DFService.deregister(this);
 			System.out.println(getLocalName()+" DEREGISTERED WITH THE DF");
 			//doDelete();
 		} catch (FIPAException e) {
diff --git BEFORE/TwitterGatherDataFollowers/userRyersonU/OrganizingAgent.java AFTER/TwitterGatherDataFollowers/userRyersonU/OrganizingAgent.java
index ca87719..6ae2e5c 100644
--- BEFORE/TwitterGatherDataFollowers/userRyersonU/OrganizingAgent.java
+++ AFTER/TwitterGatherDataFollowers/userRyersonU/OrganizingAgent.java
@@ -69,11 +69,12 @@ public class OrganizingAgent extends Agent
 
 	private static final int COS_SIM = 0;
 	private static final int K_MEANS = 1;
-	private static final int SVM = 2;
-	private static final int MLP = 3;
-	public static final int Doc2Vec = 4;             // added by Sepide
-    public static final int CommonNeighbors = 5;        // added by Sepide	
-	public static final int K_MEANSEUCLIDEAN = 6;   // added by Sepide 
+	private static final int SVM = 2;
+	private static final int MLP = 3;
+	public static final int Doc2Vec = 4;             // added by Sepide
+    public static final int CommonNeighbors = 5;        // added by Sepide	
+	public static final int K_MEANSEUCLIDEAN = 6;   // added by Sepide 
+	private static final boolean WRITE_MERGE_DEBUG_FILES = false;
 	
 	private ArrayList<String> global_interestlistData = new ArrayList<String>();		
 
@@ -163,21 +164,7 @@ public class OrganizingAgent extends Agent
 		agent_name = getLocalName();
 		AID_agent_name = getAID();
 
-		DFAgentDescription template = new DFAgentDescription();
-		ServiceDescription sd = new ServiceDescription();
-		sd.setType("Recommender Agent");
-		template.addServices(sd);
-		try {
-			DFAgentDescription[] result = DFService.search(this, template);
-			allRecAgents = new AID[result.length];
-			//allRecAgents = new AID[2];   // added by Sepide 
-			for (int i = 0; i < result.length; ++i) {
-				allRecAgents[i] = result[i].getName();
-			}
-		}
-		catch (FIPAException fe) {
-			fe.printStackTrace();
-		}
+		setExpectedRecommenderAgents();
 
 
 		DFAgentDescription template2 = new DFAgentDescription();
@@ -195,24 +182,38 @@ public class OrganizingAgent extends Agent
 			fe.printStackTrace();
 		}
 
-		InitializeBehaviour initialBehaviour = new InitializeBehaviour(this);
-		addBehaviour(initialBehaviour);
-		
-		setQueueSize(0);
-	}
-
-	private class InitializeBehaviour extends CyclicBehaviour {
+			InitializeBehaviour initialBehaviour = new InitializeBehaviour(this);
+			addBehaviour(initialBehaviour);
+			
+			setQueueSize(0);
+		}
+		
+		private void setExpectedRecommenderAgents()
+		{
+			allRecAgents = new AID[numNodes];
+			for (int i = 0; i < numNodes; i++)
+			{
+				allRecAgents[i] = new AID("Recommender-ServiceAgent" + (i + 1), AID.ISLOCALNAME);
+			}
+		}
+	
+		private class InitializeBehaviour extends CyclicBehaviour {
 		private static final long serialVersionUID = 1L;
 
 		public InitializeBehaviour(Agent a) {
 			super(a);
 		}
 
-		public void action() {
-			ACLMessage msg = this.myAgent.receive();
-			//Message from user agents
-			if (msg!=null && msg.getOntology() == "Ready")
-			{
+			public void action() {
+				ACLMessage msg = this.myAgent.receive();
+				if (msg == null)
+				{
+					block();
+					return;
+				}
+				//Message from user agents
+				if ("Ready".equals(msg.getOntology()))
+				{
 //				++numberofusers;
 
 //				String user_name1[] = msg.getSender().getLocalName().split("-",2);
@@ -247,25 +248,9 @@ public class OrganizingAgent extends Agent
 //						fe.printStackTrace();
 //					}
 					
-					while (allRecAgents.length < numNodes)
-					{
-						DFAgentDescription template = new DFAgentDescription();
-						ServiceDescription sd = new ServiceDescription();
-						sd.setType("Recommender Agent");
-						template.addServices(sd);
-						try {
-							DFAgentDescription[] result = DFService.search(myAgent, template);
-							allRecAgents = new AID[result.length];
-							for (int i = 0; i < result.length; ++i) {
-								allRecAgents[i] = result[i].getName();
-							}
-						}
-						catch (FIPAException fe) {
-							fe.printStackTrace();
-						}
-					}
-					
-					ACLMessage msg2 = new ACLMessage( ACLMessage.REQUEST );
+					setExpectedRecommenderAgents();
+						
+					ACLMessage msg2 = new ACLMessage( ACLMessage.REQUEST );
 					for(int i=0; i<allRecAgents.length; i++)
 					{
 						msg2.addReceiver(allRecAgents[i]);  
@@ -287,7 +272,7 @@ public class OrganizingAgent extends Agent
 
 			}
 
-			if (msg != null && msg.getOntology()== "Merge Lists")
+			if ("Merge Lists".equals(msg.getOntology()))
 			{
 				
 				recMergeCount++;
@@ -351,33 +336,36 @@ public class OrganizingAgent extends Agent
 
 					//System.out.println("finalScores: "+finalScores);
 					
-					try {
-						FileWriter writer = new FileWriter("finalScores_Not_Normalized.txt", true); //append
-						BufferedWriter bufferedWriter = new BufferedWriter(writer);
-						
-						for (String userRec: finalScores.keySet())
-						{
-							bufferedWriter.write(userRec+" Scores: [\t");
-							
-							TreeMap<String,Double> otherUserScores = finalScores.get(userRec);
-							
-							for (String otherUser: otherUserScores.keySet())
-							{
-								double oldScore = otherUserScores.get(otherUser);
-								
-								bufferedWriter.write(otherUser+":"+oldScore+"\t");				
-							}
-							
-						}
-						bufferedWriter.write("]");
-						bufferedWriter.newLine();
-						bufferedWriter.close();
-					} catch (IOException e) {
-						// TODO Auto-generated catch block
-						e.printStackTrace();
-					}
-
-					//Normalize the scores
+					if (WRITE_MERGE_DEBUG_FILES)
+					{
+						try {
+							FileWriter writer = new FileWriter("finalScores_Not_Normalized.txt", true); //append
+							BufferedWriter bufferedWriter = new BufferedWriter(writer);
+							
+							for (String userRec: finalScores.keySet())
+							{
+								bufferedWriter.write(userRec+" Scores: [\t");
+								
+								TreeMap<String,Double> otherUserScores = finalScores.get(userRec);
+								
+								for (String otherUser: otherUserScores.keySet())
+								{
+									double oldScore = otherUserScores.get(otherUser);
+									
+									bufferedWriter.write(otherUser+":"+oldScore+"\t");				
+								}
+								
+							}
+							bufferedWriter.write("]");
+							bufferedWriter.newLine();
+							bufferedWriter.close();
+						} catch (IOException e) {
+							// TODO Auto-generated catch block
+							e.printStackTrace();
+						}
+					}
+
+					//Normalize the scores
 					double maxScore;
 					for (String userRec: finalScores.keySet())
 					{
@@ -426,7 +414,7 @@ public class OrganizingAgent extends Agent
 			}
 
 			//Message from user agent to get scores
-			if (msg!=null && msg.getOntology() == "Get Score List")		
+			if ("Get Score List".equals(msg.getOntology()))		
 			{
 				usersRequestedCount++;
 
@@ -474,7 +462,7 @@ public class OrganizingAgent extends Agent
 			}
 
 			//Message from user agent that user received score list
-			if (msg!=null && msg.getOntology()=="Scores Received")
+			if ("Scores Received".equals(msg.getOntology()))
 			{
 				long messagePassTime;
 				String requestedBy = msg.getSender().getLocalName().split("-",2)[0];
@@ -496,7 +484,7 @@ public class OrganizingAgent extends Agent
 			
 			//Message from multiple rec agents to average weight for general MLP
 						
-			if (msg!=null && msg.getOntology()=="Average Weights MLP")
+			if ("Average Weights MLP".equals(msg.getOntology()))
 			{   
 				averageWeightCount++;
 				MultiLayerPerceptron receivedMLP = null;
diff --git BEFORE/TwitterGatherDataFollowers/userRyersonU/RecommenderAgent.java AFTER/TwitterGatherDataFollowers/userRyersonU/RecommenderAgent.java
index f33534f..d660a15 100644
--- BEFORE/TwitterGatherDataFollowers/userRyersonU/RecommenderAgent.java
+++ AFTER/TwitterGatherDataFollowers/userRyersonU/RecommenderAgent.java
@@ -25,9 +25,11 @@ import java.math.RoundingMode;
 import java.sql.PreparedStatement;
 import java.sql.ResultSet;
 import java.sql.Statement;
-import java.util.ArrayList;
-import java.util.Collections;
-import java.util.Iterator;
+import java.util.ArrayList;
+import java.util.Collection;
+import java.util.Collections;
+import java.util.Comparator;
+import java.util.Iterator;
 import java.util.LinkedHashMap;
 import java.util.LinkedList;
 import java.util.List;
@@ -117,11 +119,23 @@ public class RecommenderAgent extends Agent
 	public static final int COS_SIM = 0;
 	public static final int K_MEANS = 1;
 	public static final int SVM = 2;
-	public static final int MLP = 3;
-	public static final int Doc2Vec = 4;     //added by Sepide
-	public static final int CommonNeighbors = 5;        // added by Sepide
-	public static final int K_MEANSEUCLIDEAN = 6;     // added by Sepide 
-	private static final double TEST_SET_PERCENT = 0.30;
+	public static final int MLP = 3;
+	public static final int Doc2Vec = 4;     //added by Sepide
+	public static final int CommonNeighbors = 5;        // added by Sepide
+	public static final int K_MEANSEUCLIDEAN = 6;     // added by Sepide 
+	private static final String TWEET_BATCH_ONTOLOGY = "Tweet Batch From User Agent";
+	private static final String TWEET_BATCH_PRIMARY_REC_SERVER_PREFIX = "__PRIMARY_REC_SERVER__\t";
+	private static final boolean VERBOSE_TEXT_PROCESSING_DEBUG = false;
+	private static final boolean WRITE_TFIDF_DEBUG_FILES = false;
+	private static final int COS_SIM_LOCAL_TOP_MIN_RESULTS = 20;
+	private static final int COS_SIM_LOCAL_TOP_MULTIPLIER = 10;
+	private static final Pattern RETWEET_PATTERN = Pattern.compile("RT @");
+	private static final Pattern PUNCTUATION_PATTERN = Pattern.compile("[\\p{P}]");
+	private static final Pattern LINK_PATTERN = Pattern.compile("http[a-zA-Z0-9]*|bitly[a-zA-Z0-9]*|www[a-zA-Z0-9]*");
+	private static final Pattern SPECIAL_CHARACTER_PATTERN = Pattern.compile("[^a-zA-Z\\p{Z}]");
+	private static final Pattern NON_ALPHA_PATTERN = Pattern.compile("[^a-zA-Z ]");
+	private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile(" +");
+	private static final double TEST_SET_PERCENT = 0.30;
 	private static final double TRAIN_SET_PERCENT = 0.70;
 	public static final int HIDDEN_NEURONS = 10;
 	public static final double LEARNING_RATE_MLP = 0.1;
@@ -280,13 +294,14 @@ public class RecommenderAgent extends Agent
 	private long startTimeTrain,endTimeTrain,completionTimeTrain;
 	private long startTimeTest,endTimeTest,completionTimeTest;
 
-	private LinkedHashMap<Long,String> tweetIdUser = new LinkedHashMap<Long,String>();
-	private LinkedHashMap<Long,String> tweetIdText = new LinkedHashMap<Long,String>();
-	private LinkedHashMap<Long,LinkedHashMap<String,Double>> tweetIdDocumentVector = new LinkedHashMap<Long,LinkedHashMap<String,Double>>();
-	// private LinkedHashMap<String,ArrayList<Long>> usersTweetIdsList = new LinkedHashMap<String,ArrayList<Long>>();
+	private LinkedHashMap<Long,String> tweetIdUser = new LinkedHashMap<Long,String>();
+	private LinkedHashMap<Long,String> tweetIdText = new LinkedHashMap<Long,String>();
+	private LinkedHashMap<Long,LinkedHashMap<String,Double>> tweetIdDocumentVector = new LinkedHashMap<Long,LinkedHashMap<String,Double>>();
+	// private LinkedHashMap<String,ArrayList<Long>> usersTweetIdsList = new LinkedHashMap<String,ArrayList<Long>>();
 	
-	private ArrayList<String> userRegisteredInRecAgent = new ArrayList<String>();
-	private ArrayList<String> usersRec; //Users to be given recommendations
+	private ArrayList<String> userRegisteredInRecAgent = new ArrayList<String>();
+	private Map<String,Integer> userPrimaryRecServer = new LinkedHashMap<String,Integer>();
+	private ArrayList<String> usersRec; //Users to be given recommendations
 	private int[] usersRecTweetCountsReceived;
 	
 	private Map<String,TreeMap<String,Double>> allUserScores = new TreeMap<String,TreeMap<String,Double>>();
@@ -481,134 +496,21 @@ public class RecommenderAgent extends Agent
 				}
 			}
 
-			if (msg!=null && msg.getOntology() == "Tweet From User Agent")
-			{
-				tweetCount++;
-				if (tweetCount == 1)
-					firstTweetTime = System.nanoTime();
-				
-				ArrayList<String> currUserDocuments;
-				ArrayList<Long> currUserTweetIdList;
-				String tweetReceived;
-				String tweetUserReceived;
-				long tweetIdReceived;
-				String tweetTextReceived;
-				int totalTweetFromUser;
-				final byte[] utf16MessageBytes;
-				String tweetFolloweeName;
-				
-				tweetReceived = msg.getContent();
-				// tweetUserReceived = tweetReceived.split(" ",4)[1];
-				// tweetIdReceived = Long.valueOf(tweetReceived.split(" ",4)[2]);
-				// tweetTextReceived = tweetReceived.split(" ",4)[3];
-				// totalTweetFromUser = Integer.parseInt(tweetReceived.split(" ",4)[0]);
-				tweetUserReceived = tweetReceived.split(" ",5)[1];
-				tweetIdReceived = Long.valueOf(tweetReceived.split(" ",5)[2]);
-				tweetTextReceived = tweetReceived.split(" ",5)[4];
-				totalTweetFromUser = Integer.parseInt(tweetReceived.split(" ",5)[0]);
-				tweetFolloweeName = tweetReceived.split(" ",5)[3];
-				
-				// System.out.println("1tweetReceived:" +tweetReceived);
-				// System.out.println("2tweetReceived:" +totalTweetFromUser+","+tweetUserReceived+","+tweetIdReceived+","+tweetTextReceived+","+tweetFolloweeName);
-				
-				if (tweetUserReceived.equals("sageryereson"))
-					System.out.println("sageryerson: "+tweetReceived);
-				
-				if (!userFollowee.containsKey(tweetUserReceived))
-					userFollowee.put(tweetUserReceived,tweetFolloweeName);
-							
-				try{
-					utf16MessageBytes= tweetReceived.getBytes("UTF-16BE");
-				} catch (UnsupportedEncodingException e) {
-					throw new AssertionError("UTF-16BE not supported");
-					
-				}
-				totalMessageBytes += utf16MessageBytes.length;
-				// System.out.println("totalMessageBytes: "+totalMessageBytes);
-				
-				// if (tweetUserReceived.equals("TetraRyerson"))
-				// {
-					// System.out.println("From TetraRyerson: "+tweetTextReceived);
-				// }
-				
-				// try {
-					// FileWriter writer = new FileWriter("tweetsReceived"+String.valueOf(systemTimeName)+".txt", true); //append
-
-					// BufferedWriter bufferedWriter = new BufferedWriter(writer);
-		
-					// bufferedWriter.write(tweetUserReceived + "\t" + tweetIdReceived + "\t" + tweetTextReceived);
-					// bufferedWriter.newLine();
-
-					// bufferedWriter.close();
-				// } catch (IOException e) {
-					// e.printStackTrace();
-				// }
-				
-				if (usersRec.contains(tweetUserReceived))
-				{
-					int userIndex = usersRec.indexOf(tweetUserReceived);
-					usersRecTweetCountsReceived[userIndex]++;
-					
-					if (usersRecTweetCountsReceived[userIndex] == totalTweetFromUser)
-					{
-						ACLMessage msgLastTweetFromRecUser = new ACLMessage( ACLMessage.INFORM );
-						msgLastTweetFromRecUser.addReceiver( new AID(tweetUserReceived+"-UserAgent", AID.ISLOCALNAME) );
-						msgLastTweetFromRecUser.setPerformative( ACLMessage.INFORM );
-						msgLastTweetFromRecUser.setContent("Received Last Tweet");
-						msgLastTweetFromRecUser.setOntology("Last Tweet Received From Rec Agent");
-						send(msgLastTweetFromRecUser);
-					}
-					
-				}
-				
-				/*if (!allUserDocuments.containsKey(tweetUserReceived))
-				{
-					currUserDocuments = new ArrayList<String>();
-					userRegisteredInRecAgent.add(tweetUserReceived);
-				}
-				else
-					currUserDocuments = allUserDocuments.get(tweetUserReceived);
-
-				currUserDocuments.add(tweetTextReceived);
-				allUserDocuments.put(tweetUserReceived, currUserDocuments);
-				 */
-				
-				if (!userRegisteredInRecAgent.contains(tweetUserReceived))
-				{
-					userRegisteredInRecAgent.add(tweetUserReceived);
-				}
-				
-				tweetIdText.put(tweetIdReceived, tweetTextReceived);
-				tweetIdUser.put(tweetIdReceived, tweetUserReceived);
-
-				//				if (!usersTweetIdsList.containsKey(tweetUserReceived))
-				//					currUserTweetIdList = new ArrayList<Long>();
-				//				else	
-				//					currUserTweetIdList = usersTweetIdsList.get(tweetUserReceived);
-				//
-				//				currUserTweetIdList.add(tweetIdReceived);
-				//				usersTweetIdsList.put(tweetUserReceived, currUserTweetIdList);
-
-				//@Jason see tweets before processing
-				/*try {
-					FileWriter writer = new FileWriter("tweetsRec.txt", true); //append
-
-					BufferedWriter bufferedWriter = new BufferedWriter(writer);
-
-					bufferedWriter.write(myAgent.getLocalName()+" "+msg.getContent()+" tweetCount: "+tweetCount);
-					bufferedWriter.newLine();
-
-					bufferedWriter.close();
-				} catch (IOException e) {
-					e.printStackTrace();
-				}*/
-
-//				System.out.println(myAgent.getLocalName()+" "+msg.getContent()+" tweetCount: "+tweetCount+"/"+tweetsToReceive);
-//				System.out.println(myAgent.getLocalName()+" tweetCount: "+tweetCount+"/"+tweetsToReceive);
-
-				if(tweetCount == tweetsToReceive)
-				{
-					long lastTweetTime = System.nanoTime();
+				boolean receivedTweetData = false;
+				
+				if (msg!=null && "Tweet From User Agent".equals(msg.getOntology()))
+				{
+					receivedTweetData = recordTweetFromUserAgent(msg.getContent());
+				}
+				
+				if (msg!=null && TWEET_BATCH_ONTOLOGY.equals(msg.getOntology()))
+				{
+					receivedTweetData = recordTweetBatchFromUserAgent(msg);
+				}
+				
+					if(receivedTweetData && tweetCount >= tweetsToReceive)
+				{
+					long lastTweetTime = System.nanoTime();
 					/*System.out.println("tweetIdText: "+tweetIdText);
 					System.out.println("tweetIdUser: "+tweetIdText);
 					System.out.println("usersTweetIdsList: "+usersTweetIdsList);
@@ -631,145 +533,45 @@ public class RecommenderAgent extends Agent
 					System.out.println("############### tweetCount: "+tweetCount);
 					System.out.println("@@@@@@@@@@@@@@@ tweetIdText.size(): "+ tweetIdText.size());
 
-					ArrayList<Long> tweetIdsToRemove = new ArrayList<Long>(); //tweetIdsToRemove because no useful info
-
-					startTimeTextProcessing = System.nanoTime();
-
-					for (Long currTweetId : tweetIdText.keySet())
-					{
-						LinkedHashMap<String,Double> tweetDocumentVector = new LinkedHashMap<String,Double>();
-						String currentText = tweetIdText.get(currTweetId);
-						
-						//pad out spaces before and after word for parsing 
-						currentText = String.format(" %s ",currentText);
-
-						// System.out.println("Original text: "+currentText);
-
-
-						//Remove Photo: tweets
-						if (currentText.contains("Photo:"))
-							currentText = currentText.substring(0,currentText.indexOf("Photo:"));
-
-						//Remove Photoset: tweets
-						if (currentText.contains("Photoset:"))
-							currentText = currentText.substring(0,currentText.indexOf("Photoset:"));
-
-						//Remove all retweets if flagged
-						Matcher matcher; //a matcher
-						if (currentText.contains("RT @") && retweetedby_temp == RE_TWEETS)
-							currentText="";
-						else
-						{
-							//Remove RT @, conserve the text from retweets
-							Pattern retweet = Pattern.compile("RT @");
-
-							matcher = retweet.matcher(currentText);
-							currentText = matcher.replaceAll("RT ");
-						}
-						//System.out.println("After RT @: " + currentText);
-
-						//Remove punctuations
-						Pattern punctuations = Pattern.compile("[\\p{P}]");
-						matcher = punctuations.matcher(currentText);
-						currentText = matcher.replaceAll("");
-
-						//System.out.println("After punctuations: "+ currentText);
-
-						//Remove url links
-						Pattern links = Pattern.compile("http[a-zA-Z0-9]*|bitly[a-zA-Z0-9]*|www[a-zA-Z0-9]*");
-						matcher = links.matcher(currentText);
-						currentText = matcher.replaceAll(" ");
-
-						//System.out.println("After url links: "+ currentText);
-
-						//Remove special characters including hash tags if flagged
-						if (hashtags_temp == HASH_TAGS)
-						{
-							Pattern specialCharacters = Pattern.compile("[^a-zA-Z\\p{Z}]");
-							matcher = specialCharacters.matcher(currentText);
-							currentText = matcher.replaceAll(" ");
-						}
-						// System.out.println("After special characters: " + currentText);
-
-						//Remove stop words if flagged
-						if (stopWordFlag_temp == STOP_WORDS)
-						{
-							for (String stopWord : stopWordsArray){	
-								if (currentText.toLowerCase().contains(" "+stopWord+" "))
-									//System.out.println("FOUND STOPWORD: "+stopWord);
-									currentText = currentText.toLowerCase().replaceAll(" "+stopWord+" "," ");
-							}
-						}
-						//Change all text to lowercase
-						currentText=currentText.toLowerCase();
-
-						//Trim leading and ending white space
-						currentText=currentText.trim();
-						//Remove any non-alphabetical characters
-						currentText=currentText.replaceAll("[^a-zA-Z ]","");
-						//Shorten any spaces to just 1 single space
-						currentText=currentText.replaceAll(" +", " ");
-						//currentText=currentText.trim().replaceAll(" +", " ");
-
-						//System.out.println(getLocalName()+" Removed junk: "+currentText);
-
-						//******@Begin making vectors*************************************************
-						//Add processed texts to a list
-						Scanner sc = new Scanner(currentText);
-						//List<String> list = new ArrayList<String>();
-						String stringToken;
-						double wordFreq = 0.0;
-						int wordCount = 0;
-						wordCount = currentText.split("\\s+").length;
-
-						//System.out.println("currentText: "+ currentText);
-						//If processed text is a blank line with 1 single space or less than 3 words
-						if (wordCount < 3)
-						{
-							//System.out.println("DO NOT ADD");
-							tweetIdsToRemove.add(currTweetId);
-						}
-						else
-						{
-//							try {
-//								FileWriter writer = new FileWriter("demoTweetsVerification1000_tweet_words_users.txt", true); //append	
-//								BufferedWriter bufferedWriter = new BufferedWriter(writer);
-//								bufferedWriter.write(tweetIdUser.get(currTweetId)+": "+currentText);					
-//								bufferedWriter.newLine();
-//								bufferedWriter.close();
-//							} catch (IOException e) {
-//								e.printStackTrace();
-//							}
-							// System.out.println("final text processed: "+currentText);
-							
-							while (sc.hasNext()){
-								stringToken = sc.next();
-								//list.add(stringToken);
-
-								//Add all unique terms to allUniqueDocTerms
-								allUniqueDocTerms.add(stringToken);
-
-								//Count frequency of words in a document
-								if (tweetDocumentVector.get(stringToken)!=null) //already exists in vector
-									wordFreq = tweetDocumentVector.get(stringToken)+1;
-								else //does not exist in vector yet
-									wordFreq = 1.0;
-
-								tweetDocumentVector.put(stringToken, wordFreq);
-							}
-							/*for (String s : list){
-									System.out.print(s+" ");
-								}
-								System.out.println();*/
-							sc.close();
-							//System.out.println();
-							//System.out.println("The length of string: "+currentText.length());
-
-							tweetIdDocumentVector.put(currTweetId, tweetDocumentVector);
-
-						}
-
-					} //end for (Long currTweetId : usersTweetIdsList.get(curName))					
+						startTimeTextProcessing = System.nanoTime();
+
+						for (Long currTweetId : new ArrayList<Long>(tweetIdText.keySet()))
+						{
+							String currentText = tweetIdText.get(currTweetId);
+							ArrayList<String> processedTokens = tokenizeProcessedTweet(currentText);
+							if (processedTokens.size() < 3)
+							{
+								tweetIdText.remove(currTweetId);
+								tweetIdUser.remove(currTweetId);
+								continue;
+							}
+
+							String currUserName = tweetIdUser.get(currTweetId);
+							if (currUserName == null)
+							{
+								tweetIdText.remove(currTweetId);
+								continue;
+							}
+
+							aggregatedUserTweets = allUserDocuments.get(currUserName);
+							if (aggregatedUserTweets == null)
+							{
+								aggregatedUserTweets = new LinkedHashMap<String,Double>();
+								allUserDocuments.put(currUserName, aggregatedUserTweets);
+							}
+
+							for (String stringToken : processedTokens)
+							{
+								allUniqueDocTerms.add(stringToken);
+								double wordFreq = 1.0;
+								if (aggregatedUserTweets.containsKey(stringToken))
+								{
+									wordFreq = aggregatedUserTweets.get(stringToken) + 1.0;
+								}
+								aggregatedUserTweets.put(stringToken, wordFreq);
+							}
+
+						} //end for (Long currTweetId : usersTweetIdsList.get(curName))					
 
 					//					for (String curName: usersTweetIdsList.keySet())
 					//					{
@@ -909,119 +711,25 @@ public class RecommenderAgent extends Agent
 
 					//System.out.println("tweetIdText.size(): "+tweetIdText.size());
 
-					//Remove all tweetIds that are not useful							
-					for (Long tweetIdToRemove : tweetIdsToRemove)
-					{
-						if (tweetIdDocumentVector.containsKey(tweetIdToRemove))
-							tweetIdDocumentVector.remove(tweetIdToRemove);
-						if (tweetIdText.containsKey(tweetIdToRemove))
-							tweetIdText.remove(tweetIdToRemove);
-						if (tweetIdUser.containsKey(tweetIdToRemove))
-							tweetIdUser.remove(tweetIdToRemove);
-
-						//						Iterator<Map.Entry<String,ArrayList<Long>>> iterator = usersTweetIdsList.entrySet().iterator();
-						//						while(iterator.hasNext()){
-						//							Map.Entry<String,ArrayList<Long>> entry = iterator.next();
-						//							for (int i = 0; i < entry.getValue().size(); i++)
-						//							{
-						//								if (entry.getValue().get(i) == tweetIdToRemove)
-						//								{
-						//									entry.getValue().remove(i);
-						//								}
-						//							}    
-						//							if (entry.getValue().size() == 0)
-						//								iterator.remove();
-						//						}
-
-
-					}
-
-					System.out.println("XXXXXXXXXXXXX tweetIdText.size(): " + tweetIdText.size());
-
-					long beforeAggregateTime = System.nanoTime();
-					//Aggregate each users' tweets into one document
-					for (Long currTweetId : tweetIdDocumentVector.keySet())
-					{
-						String currUserName = tweetIdUser.get(currTweetId);
-						LinkedHashMap<String,Double> currTweetIdDocumentVector = tweetIdDocumentVector.get(currTweetId);
-
-						if (!allUserDocuments.containsKey(currUserName))
-							aggregatedUserTweets = new LinkedHashMap<String,Double>();
-						else
-							aggregatedUserTweets = allUserDocuments.get(currUserName);
-
-						for (String currTerm : currTweetIdDocumentVector.keySet())
-						{
-							double termFreq = 0.0;
-							if (aggregatedUserTweets.containsKey(currTerm))
-							{
-								termFreq = aggregatedUserTweets.get(currTerm);
-							}
-
-							termFreq += currTweetIdDocumentVector.get(currTerm);
-							aggregatedUserTweets.put(currTerm,termFreq);
-						}
-						allUserDocuments.put(currUserName, aggregatedUserTweets);
-					}
-					
-					//Write to file  code added by Sepide
-					BufferedWriter bf = null;
-                     
-                    String doc2vecDirName = "Dataset/424k/";
-					File doc2vecDir = new File(doc2vecDirName);
-					if (!doc2vecDir.exists())
-					{
-							doc2vecDir.mkdirs();
-					}
-					
-					try {
-					//bf = new BufferedWriter(new FileWriter("D:\\important-stuff\\Reduced_57-1-june12.txt"));  // commented out on Nov. 3
-					bf = new BufferedWriter(new FileWriter(doc2vecDirName+ "userdoc.txt"));
-					for (Map.Entry<String,LinkedHashMap<String,Double>> entry : allUserDocuments.entrySet()) {
-                          //for (Map.Entry<String,Double> entry2 : aggregatedUserTweets.entrySet()) {
-						// put key and value separated by a colon
-						//bf.write("\"" + entry.getValue() + "\"" + "," + entry.getKey());  // commented out on Nov.3
-			              LinkedHashMap<String,Double> entry2 = entry.getValue();
-						  
-						  for (Map.Entry<String,Double> entry3 : entry2.entrySet()){
-						    //String userUser = entry.getKey();
-						   //if (entry.getKey())
-						  //bf.write(entry.get(userUser).get()+ " ");
-						  bf.write(entry3.getKey()+ " ");
-						 // if (entry.getKey == )
-                          //bf.write(entry.getKey() + "\n" + entry.getValues.keySet());
-						// new line
-						
-					  }
-					    bf.write(entry.getKey());
-					  //bf.write(entry.getKey());
-					   bf.newLine();
-                       }
-					bf.flush();
-						
-					}
-					catch (IOException e) {
-					  System.out.println("An error occurred.");
-					  e.printStackTrace();
-					}
-					
-					finally {
-
-					try {
-
-						// always close the writer
-						bf.close();
-					}
-					catch (Exception e) {
-					}
-				  }
-                    
-					// End of code added by Sepide 
-					
-					for (String u : allUserDocuments.keySet())
-					{
-						System.out.println(getLocalName()+" u: "+u);
-					}
+						System.out.println("XXXXXXXXXXXXX tweetIdText.size(): " + tweetIdText.size());
+
+						long beforeAggregateTime = System.nanoTime();
+						
+						//Write to file  code added by Sepide
+						if (algorithmRec == Doc2Vec)
+						{
+							writeDoc2VecUserDocumentFile();
+						}
+	                    
+						// End of code added by Sepide 
+						
+						if (VERBOSE_TEXT_PROCESSING_DEBUG)
+						{
+							for (String u : allUserDocuments.keySet())
+							{
+								System.out.println(getLocalName()+" u: "+u);
+							}
+						}
 					int followerCount = 0;
 					String followeeName;
 					List<String> followerNames;
@@ -1053,12 +761,15 @@ public class RecommenderAgent extends Agent
 						followeeFollowerCounts.put(followeeName,followerCount);
 					}
 					
-					for (String f: followeeFollowers.keySet())
-					{
-						List<String> fNames = followeeFollowers.get(f);
-						System.out.println(f+": "+followeeFollowerCounts.get(f));
-						System.out.println(fNames);
-					}
+						for (String f: followeeFollowers.keySet())
+						{
+							List<String> fNames = followeeFollowers.get(f);
+							System.out.println(f+": "+followeeFollowerCounts.get(f));
+							if (VERBOSE_TEXT_PROCESSING_DEBUG)
+							{
+								System.out.println(fNames);
+							}
+						}
 					
 					//-------------PRINTING OUT TF TO FILE***********************
 					// FileWriter writer;
@@ -1198,13 +909,14 @@ public class RecommenderAgent extends Agent
 					//					}
 
 
-					endTimeTextProcessing = System.nanoTime();
-					completionTimeTextProcessing = endTimeTextProcessing - startTimeTextProcessing;
-					System.out.println(getLocalName()+" completionTimeTextProcessing: "+convertMs(completionTimeTextProcessing)+" ms");
-					System.out.println(getLocalName()+ " After processing, tweets: "+ tweetIdText.size());
-
-					myGui.appendResult(getLocalName()+"completionTimeTextProcessing: "+convertMs(completionTimeTextProcessing)+" ms");
-					myGui.appendResult(getLocalName()+ "After processing, tweets: "+ tweetIdText.size());
+						endTimeTextProcessing = System.nanoTime();
+						completionTimeTextProcessing = endTimeTextProcessing - startTimeTextProcessing;
+						int processedTweetCountForDisplay = tweetIdText.size();
+						System.out.println(getLocalName()+" completionTimeTextProcessing: "+convertMs(completionTimeTextProcessing)+" ms");
+					System.out.println(getLocalName()+ " After processing, tweets: "+ processedTweetCountForDisplay);
+
+					myGui.appendResult(getLocalName()+"completionTimeTextProcessing: "+convertMs(completionTimeTextProcessing)+" ms");
+					myGui.appendResult(getLocalName()+ "After processing, tweets: "+ processedTweetCountForDisplay);
 					
 					//@Jason added code to deny querying for any user who have no tweet in database after text processing and tweeting simulation is complete 					
 
@@ -1235,10 +947,10 @@ public class RecommenderAgent extends Agent
 
 					
 					try {
-						FileWriter writer10;
-						writer10 = new FileWriter("numTextProcessed.txt", true); //append
-						BufferedWriter bufferedWriter = new BufferedWriter(writer10);
-						bufferedWriter.write(getLocalName()+ " Tweets After Processing: "+ tweetIdText.size()+" Tweets Before Processing: "+ tweetsToReceive);
+						FileWriter writer10;
+						writer10 = new FileWriter("numTextProcessed.txt", true); //append
+						BufferedWriter bufferedWriter = new BufferedWriter(writer10);
+						bufferedWriter.write(getLocalName()+ " Tweets After Processing: "+ processedTweetCountForDisplay+" Tweets Before Processing: "+ tweetsToReceive);
 						bufferedWriter.newLine();
 						bufferedWriter.close();
 					} catch (IOException e) {
@@ -1334,10 +1046,9 @@ public class RecommenderAgent extends Agent
 
 					System.out.println(getLocalName()+ " Text Processing Complete");
 
-				}
-			}
-
-			//@Jason added new message to start recommending
+					}
+
+				//@Jason added new message to start recommending
 			if (msg!=null && msg.getOntology()=="Start Recommend Algorithms" && msg.getPerformative()==ACLMessage.REQUEST && calculateAlready==false)
 			{
 
@@ -1381,7 +1092,10 @@ public class RecommenderAgent extends Agent
 				for (String curName : allUserDocuments.keySet())
 				{
 					LinkedHashMap<String,Double> curDoc = allUserDocuments.get(curName);
-					System.out.println("============================================");
+						if (VERBOSE_TEXT_PROCESSING_DEBUG)
+						{
+							System.out.println("============================================");
+						}
 					//System.out.println(" Sepide testing if the document includes the users with their aggregated tweets" + allUserDocuments.get(curName));  // added by Sepide
 					for (String docTerm : curDoc.keySet())
 					{
@@ -1609,10 +1323,13 @@ public class RecommenderAgent extends Agent
 				System.out.println(getLocalName()+" completionTimeTFIDF: "+convertMs(completionTimeTFIDF)+" ms");
 				myGui.appendResult(getLocalName()+" completionTimeTFIDF: "+convertMs(completionTimeTFIDF)+" ms");
 
-				for (String userHere: allUserDocumentsTFIDF.keySet())
-				{
-					System.out.println(getLocalName()+" userHere: "+userHere+" "+allUserDocumentsTFIDF.get(userHere).size());
-				}
+					if (VERBOSE_TEXT_PROCESSING_DEBUG)
+					{
+						for (String userHere: allUserDocumentsTFIDF.keySet())
+						{
+							System.out.println(getLocalName()+" userHere: "+userHere+" "+allUserDocumentsTFIDF.get(userHere).size());
+						}
+					}
 				
 				//WORD CHECKING
 				// FileWriter writerWords;
@@ -1680,57 +1397,60 @@ public class RecommenderAgent extends Agent
 				
 
 				
-				FileWriter writer11;
-				    try {   // from line 1602 to 1626 is uncommented  
-					 writer11 = new FileWriter("tfidf_matrix.txt", true); //append
-					 BufferedWriter bufferedWriter = new BufferedWriter(writer11);
-					 bufferedWriter.write(totalDocuments+"\t");
-					 for (String userNames : allUserDocumentsTFIDF.keySet())
-					 {
-						 bufferedWriter.write(userNames+"\t");
-					  }
-					   bufferedWriter.newLine();
-					   for (String uniqueTerm: allUniqueDocTerms)
-					  {
-						  bufferedWriter.write(uniqueTerm+"\t");
-						  for (String userNames : allUserDocumentsTFIDF.keySet())
-						  {
-							  double tfidfValue = 0.0;
-							  if (allUserDocumentsTFIDF.get(userNames).containsKey(uniqueTerm))
-								  tfidfValue = allUserDocumentsTFIDF.get(userNames).get(uniqueTerm);
-							  bufferedWriter.write(tfidfValue+"\t");
-						  }
-						   bufferedWriter.newLine();
-					   }
-					   bufferedWriter.close();
-				   } catch (IOException e) {
-					  e.printStackTrace();
-				  } 
-				
-				try {
-					int uniqueWordsSize = allUniqueDocTerms.size();
-					writer11 = new FileWriter("uniqueWords.txt", true); //append
-					BufferedWriter bufferedWriter = new BufferedWriter(writer11);
-					bufferedWriter.write(String.valueOf(uniqueWordsSize));
-					bufferedWriter.newLine();
-					bufferedWriter.close();
-				} catch (IOException e) {
-					e.printStackTrace();
-				}
-				
-				try {
-					writer11 = new FileWriter("docFreqWords.txt", true); //append
-					BufferedWriter bufferedWriter = new BufferedWriter(writer11);
-					for (String docTerm: allTermsDocumentFreq.keySet())
-					{
-						bufferedWriter.write(docTerm+"\t"+allTermsDocumentFreq.get(docTerm));
-						bufferedWriter.newLine();
-					}
-					
-					bufferedWriter.close();
-				} catch (IOException e) {
-					e.printStackTrace();
-				}
+				if (WRITE_TFIDF_DEBUG_FILES)
+				{
+					FileWriter writer11;
+				    try {   // from line 1602 to 1626 is uncommented  
+						 writer11 = new FileWriter("tfidf_matrix.txt", true); //append
+						 BufferedWriter bufferedWriter = new BufferedWriter(writer11);
+						 bufferedWriter.write(totalDocuments+"\t");
+						 for (String userNames : allUserDocumentsTFIDF.keySet())
+						 {
+							 bufferedWriter.write(userNames+"\t");
+						  }
+						   bufferedWriter.newLine();
+						   for (String uniqueTerm: allUniqueDocTerms)
+						  {
+							  bufferedWriter.write(uniqueTerm+"\t");
+							  for (String userNames : allUserDocumentsTFIDF.keySet())
+							  {
+								  double tfidfValue = 0.0;
+								  if (allUserDocumentsTFIDF.get(userNames).containsKey(uniqueTerm))
+									  tfidfValue = allUserDocumentsTFIDF.get(userNames).get(uniqueTerm);
+								  bufferedWriter.write(tfidfValue+"\t");
+							  }
+							   bufferedWriter.newLine();
+						   }
+						   bufferedWriter.close();
+					   } catch (IOException e) {
+						  e.printStackTrace();
+					  } 
+					
+					try {
+						int uniqueWordsSize = allUniqueDocTerms.size();
+						writer11 = new FileWriter("uniqueWords.txt", true); //append
+						BufferedWriter bufferedWriter = new BufferedWriter(writer11);
+						bufferedWriter.write(String.valueOf(uniqueWordsSize));
+						bufferedWriter.newLine();
+						bufferedWriter.close();
+					} catch (IOException e) {
+						e.printStackTrace();
+					}
+					
+					try {
+						writer11 = new FileWriter("docFreqWords.txt", true); //append
+						BufferedWriter bufferedWriter = new BufferedWriter(writer11);
+						for (String docTerm: allTermsDocumentFreq.keySet())
+						{
+							bufferedWriter.write(docTerm+"\t"+allTermsDocumentFreq.get(docTerm));
+							bufferedWriter.newLine();
+						}
+						
+						bufferedWriter.close();
+					} catch (IOException e) {
+						e.printStackTrace();
+					}
+				}
 			
 				//-------------PRINTING OUT TF TO FILE***********************
 				
@@ -1779,7 +1499,7 @@ public class RecommenderAgent extends Agent
 				
 				
 				//Create Arff files for Weka, *INCORRECT*Only determineTrainingTestSet when it is a single node and algorithmRec == SVM
-				 if (algorithmRec == SVM || algorithmRec == MLP )
+				 if ((algorithmRec == SVM && !isSvmBatchMode()) || algorithmRec == MLP )
 				{
 					
 					System.out.println("Test Sepide to see if the arff file is created");
@@ -1834,14 +1554,7 @@ public class RecommenderAgent extends Agent
 								uniqueWordCountTrain++;
 							}
 							
-							String attributeClass = "@attribute result ";
-							StringJoiner classJoiner = new StringJoiner(",","{","}");
-							for (String className: followeeFollowers.keySet())
-							{
-								classJoiner.add(className);
-							}
-							
-							attributeClass += classJoiner.toString();
+							String attributeClass = buildArffClassAttribute(followeeFollowers.keySet());
 							
 							bufferedWriterTrain.write(attributeClass);
 							bufferedWriterData.write(attributeClass);
@@ -1891,8 +1604,8 @@ public class RecommenderAgent extends Agent
 								// }
 								tfidfJoiner = vectorArffFormat(currDocTfidf,allUniqueDocTerms);
 								
-								bufferedWriterTrain.write(tfidfJoiner.toString() + "," + userFollowee.get(currUser));
-								bufferedWriterData.write(tfidfJoiner.toString() + "," + userFollowee.get(currUser));
+								bufferedWriterTrain.write(tfidfJoiner.toString() + "," + formatArffClassValue(userFollowee.get(currUser)));
+								bufferedWriterData.write(tfidfJoiner.toString() + "," + formatArffClassValue(userFollowee.get(currUser)));
 								//S bufferedWriterData.write(tfidfJoiner.toString());
 								bufferedWriterTrain.newLine();
 								bufferedWriterData.newLine();
@@ -1920,7 +1633,7 @@ public class RecommenderAgent extends Agent
 								tfidfJoiner = vectorArffFormat(currDocTfidf,allUniqueDocTerms);
 								
 								
-								bufferedWriterData.write(tfidfJoiner.toString() + "," + userFollowee.get(currUser));
+								bufferedWriterData.write(tfidfJoiner.toString() + "," + formatArffClassValue(userFollowee.get(currUser)));
 								//S bufferedWriterData.write(tfidfJoiner.toString());
 								
 								bufferedWriterData.newLine();
@@ -2072,14 +1785,7 @@ public class RecommenderAgent extends Agent
 							uniqueWordCountTest++;
 						}
 						
-						String attributeClass = "@attribute result ";
-						StringJoiner classJoiner = new StringJoiner(",","{","}");
-						for (String className: followeeFollowers.keySet())
-						{
-							classJoiner.add(className);
-						}
-						
-						attributeClass += classJoiner.toString();
+						String attributeClass = buildArffClassAttribute(followeeFollowers.keySet());
 						
 						
 						bufferedWriterTest.write(attributeClass);
@@ -2129,7 +1835,7 @@ public class RecommenderAgent extends Agent
 							
 							tfidfJoiner = vectorArffFormat(currDocTfidf,allUniqueDocTerms);
 								
-							bufferedWriterTest.write(tfidfJoiner.toString() + "," + userFollowee.get(currUser));
+							bufferedWriterTest.write(tfidfJoiner.toString() + "," + formatArffClassValue(userFollowee.get(currUser)));
 							bufferedWriterTest.newLine();	
 						}
 						bufferedWriterTest.close();
@@ -2139,7 +1845,7 @@ public class RecommenderAgent extends Agent
 							Map<String,Double> currDocTfidf = allUserDocumentsTFIDF.get(currUser);							
 							tfidfJoiner = vectorArffFormat(currDocTfidf,allUniqueDocTerms);
 							
-							bufferedWriterRecommend.write(tfidfJoiner.toString() + "," + userFollowee.get(currUser));
+							bufferedWriterRecommend.write(tfidfJoiner.toString() + "," + formatArffClassValue(userFollowee.get(currUser)));
 							bufferedWriterRecommend.newLine();
 						}
 						bufferedWriterRecommend.close();	
@@ -2175,76 +1881,21 @@ public class RecommenderAgent extends Agent
 						followeeRecName.delete();
 					}  */
 					
-					List<String> tweets = new ArrayList<>();
-					try (BufferedReader reader = new BufferedReader(new FileReader(myGui.fileChooser.getSelectedFile()))) {
-						String line;
-						while ((line = reader.readLine()) != null) {
-							tweets.add(line);
-						}
-					}
-					catch (IOException e) {
-					  System.out.println("An error occurred.");
-					  e.printStackTrace();
-					}
-					
-					int numFiles = Integer.parseInt(myGui.numNodesField.getText());
-					try {
-					// Create a BufferedWriter for each file
-					List<BufferedWriter> writers = new ArrayList<>();
-					for (int i = 0; i < numFiles; i++) {
-						writers.add(new BufferedWriter(new FileWriter(doc2vecDirName + "part" + (i + 1) + "_" + ".txt")));
-					}
-					
-					// Specify which column contains the user ID (0-based index)
-					int userColumn = 4;
-					
-					// Specify which user's tweets should be written to all files
-					String specificUser = usersRec.get(0).toString();
-					
-					 // Specify which columns to include in the output (0-based index)
-					int[] columnsToInclude = {5, 4};
-					
-					// Iterate over each row in the dataset
-					int fileIndex = 0;
-					
-					for (String row : tweets) {
-						// Split row into columns
-						String[] columns = row.split("\t");
-
-						// Create a new row with only desired columns
-						StringBuilder newRow = new StringBuilder();
-						for (int j = 0; j < columnsToInclude.length; j++) {
-							newRow.append(columns[columnsToInclude[j]]);
-							if (j < columnsToInclude.length - 1) {
-								newRow.append("\t");
-							}
-						}
-
-						// Check if row belongs to specific user
-						if (columns[userColumn].equals(specificUser)) {
-							// Write new row to all files
-							for (BufferedWriter writer : writers) {
-								writer.write(newRow.toString());
-								writer.newLine();
-							}
-						} else {
-							// Write new row to one file using round-robin approach
-							writers.get(fileIndex).write(newRow.toString());
-							writers.get(fileIndex).newLine();
-							fileIndex = (fileIndex + 1) % numFiles;
-						}
-					}
-
-					// Close all writers
-					for (BufferedWriter writer : writers) {
-						writer.close();
-					}
-					
-					}
-					catch (IOException e) {
-					  System.out.println("An error occurred.");
-					  e.printStackTrace();
-					}
+						int numFiles = Integer.parseInt(myGui.numNodesField.getText());
+						try {
+							if ("1".equals(nodeNumber))
+							{
+								writeDoc2VecCompatibilityPartFiles(myGui.fileChooser.getSelectedFile(), doc2vecDirName, numFiles, usersRec.get(0).toString());
+							}
+							else
+							{
+								System.out.println(getLocalName()+" skipping Doc2Vec compatibility file split; Recommender-ServiceAgent1 handles it");
+							}
+						}
+						catch (IOException e) {
+						  System.out.println("An error occurred.");
+						  e.printStackTrace();
+						}
 					
 				    determineTrainingTestSet();
 					dataSetUsers = new ArrayList<String>(trainSetUsers);
@@ -2314,206 +1965,43 @@ public class RecommenderAgent extends Agent
 					//setup array of users
 					String[] users = new String[allUserDocumentsTFIDF.keySet().size()];
 					String[] usersForRec = new String[usersRec.size()];
-					users = allUserDocumentsTFIDF.keySet().toArray(users);
-					usersForRec = usersRec.toArray(usersForRec);
-
-					allUserScores = new TreeMap<String,TreeMap<String,Double>>();
-					Map<String,Double> userScore1 = new TreeMap<String,Double>();
-					Map<String,Double> userScore2 = new TreeMap<String,Double>();
-					double magnitudeVector1=0.0,magnitudeVector2=0.0; //magnitude of vectors
-					double dpVectors=0.0; //dot product of vectors
-					double score=0.0,prevScore=0.0,newScore=0.0;
-					Set<String> lowerTermsVector; 
-					Set<String> higherTermsVector;		
-					int docTermCount=0;
-					int higherTermsUserIndex, higherTermsUserDocIndex, lowerTermsUserIndex, lowerTermsUserDocIndex;
-
-					//initialize scores to 0.0
-					System.out.println(getLocalName()+ "Initialized Scores to 0.0");
-
-					for (int i = 0; i < usersForRec.length; i++)
-					{
-						for (int j = 0; j < users.length; j++)
-						{
-							if (!usersForRec[i].equals(users[j]))
-							{
-								userScore1.put(users[j], 0.0);
-								allUserScores.put(usersForRec[i],(TreeMap<String,Double>) userScore1);
-							}
-						}
-						userScore1 = new TreeMap<String,Double>();
-					}
-
-					/*
-						for (int i = 0; i < users.length; i++)
-						{
-							for (int j = 0; j < users.length; j++)
-							{
-								if (!users[i].equals(users[j]))
-								{
-									userScore1.put(users[j], 0.0);
-									allUserScores.put(users[i],(TreeMap<String,Double>) userScore1);
-								}
-							}
-							//userScore1 = new LinkedHashMap<String,Double>();
-							userScore1 = new TreeMap<String,Double>();
-						}
-					 */
-
-					//System.out.println(getLocalName()+" allUserScores: "+allUserScores);
-
-
-					System.out.println(getLocalName()+ "CALCULATING COS-SIM SCORES");
-					//System.out.println();
-
-					/*
-					//cos Sim with each individual tweets
-					for (int i = 0; i < usersForRec.length; i++)
-					{
-						for (int j = 0; j < users.length; j++)
-						{
-							if (!usersForRec[i].equals(users[j]))
-							{
-								int size1 = allUserDocumentsTFIDF.get(usersForRec[i]).size();
-								int size2 = allUserDocumentsTFIDF.get(users[j]).size();
-								ArrayList<LinkedHashMap<String, Double>> doc1 = allUserDocumentsTFIDF.get(usersForRec[i]);
-								ArrayList<LinkedHashMap<String, Double>> doc2 = allUserDocumentsTFIDF.get(users[j]);
-
-								//get documents from users[i]
-								for (int k=0; k < size1; k++)
-								{
-									//get documents from users[j]				
-									for (int l=0; l < size2; l++)
-									{
-										//System.out.println("COSSIM: "+allUserDocumentsTFIDF.get(users[i]).get(k)+"\t"+allUserDocumentsTFIDF.get(users[j]).get(l));
-
-										Set<String> terms1 = doc1.get(k).keySet();
-										Set<String> terms2 = doc2.get(l).keySet();
-										LinkedHashMap<String,Double> docTerms1 = doc1.get(k);
-										LinkedHashMap<String,Double> docTerms2 = doc2.get(l);
-
-										for (String termUser1 : terms1)
-										{
-											//keeps count of when document k has gone through all its terms
-											docTermCount++;
-											for (String termUser2 : terms2)
-											{
-
-												if (termUser1.equals(termUser2))
-												{
-													//System.out.print("SAME TERMS "+termUser1+" "+termUser2+" ");
-													//System.out.print("dp: "+allUserDocumentsTFIDF.get(users[j]).get(l).get(termUser2)+"*"+allUserDocumentsTFIDF.get(users[i]).get(k).get(termUser1)+"\t");
-													dpVectors+=docTerms2.get(termUser2)*docTerms1.get(termUser1);
-												}
-											}
-										}
-
-										score=dpVectors;
-
-										userScore1 = allUserScores.get(usersForRec[i]);
-										//userScore2 = allUserScores.get(users[j]);
-										if (userScore1.containsKey(users[j]))
-										{
-											prevScore = userScore1.get(users[j]);
-											newScore = prevScore + score;
-											userScore1.put(users[j], newScore);
-										}
-										else if (!userScore1.containsKey(users[j]))
-										{
-											userScore1.put(users[j], score);
-										}
-										/*
-											if (userScore2.containsKey(users[i]))
-											{
-												prevScore = userScore2.get(users[i]);
-												newScore = prevScore + score;
-												userScore2.put(users[i], newScore);
-											}
-											else if (!userScore2.containsKey(users[i]))
-											{
-												userScore1.put(users[i], score);
-											}
-					 */
-					//System.out.println("score: "+score);
-					/*			dpVectors=0.0;
-										docTermCount=0;
-										score=0.0;
-									}
-									allUserScores.put(usersForRec[i],(TreeMap<String,Double>)userScore1);
-									//allUserScores.put(users[j],(TreeMap<String,Double>)userScore2);
-								}
-							}
-						} //end for users.length
-					} //end for usersForRec.length
-					 */
-					//cosSim for aggregated tweets
-					for (int i = 0; i < usersForRec.length; i++)
-					{
-						for (int j = 0; j < users.length; j++)
-						{
-							if (!usersForRec[i].equals(users[j]))
-							{
-								LinkedHashMap<String, Double> doc1 = allUserDocumentsTFIDF.get(usersForRec[i]);
-								LinkedHashMap<String, Double> doc2 = allUserDocumentsTFIDF.get(users[j]);
-
-								//System.out.println("COSSIM: "+allUserDocumentsTFIDF.get(usersForRec[i])+"\t"+allUserDocumentsTFIDF.get(users[j]));
-
-								Set<String> terms1 = doc1.keySet();
-								Set<String> terms2 = doc2.keySet();
-
-								for (String termUser1 : terms1)
-								{
-									for (String termUser2 : terms2)
-									{
-
-										if (termUser1.equals(termUser2))
-										{
-											//System.out.print("SAME TERMS "+termUser1+" "+termUser2+" ");
-											//System.out.print("dp: "+allUserDocumentsTFIDF.get(users[j]).get(termUser2)+"*"+allUserDocumentsTFIDF.get(usersForRec[i]).get(termUser1)+"\t");
-											dpVectors+=doc2.get(termUser2)*doc1.get(termUser1);
-										}
-									}
-								}
-
-								score=dpVectors;
-
-								userScore1 = allUserScores.get(usersForRec[i]);
-								//userScore2 = allUserScores.get(users[j]);
-								if (userScore1.containsKey(users[j]))
-								{
-									prevScore = userScore1.get(users[j]);
-									newScore = prevScore + score;
-									userScore1.put(users[j], newScore);
-								}
-								else if (!userScore1.containsKey(users[j]))
-								{
-									userScore1.put(users[j], score);
-								}
-								/*
-											if (userScore2.containsKey(users[i]))
-											{
-												prevScore = userScore2.get(users[i]);
-												newScore = prevScore + score;
-												userScore2.put(users[i], newScore);
-											}
-											else if (!userScore2.containsKey(users[i]))
-											{
-												userScore1.put(users[i], score);
-											}
-								 */
-								//System.out.println("score: "+score);
-								dpVectors=0.0;
-								score=0.0;
-
-								allUserScores.put(usersForRec[i],(TreeMap<String,Double>)userScore1);
-								//allUserScores.put(users[j],(TreeMap<String,Double>)userScore2);
-
-							}
-						} //end for users.length
-					} //end for usersForRec.length
-
-
-					// System.out.println(getLocalName()+" After COS SIM scores: "+allUserScores);
+					users = allUserDocumentsTFIDF.keySet().toArray(users);
+					usersForRec = usersRec.toArray(usersForRec);
+
+					allUserScores = new TreeMap<String,TreeMap<String,Double>>();
+					System.out.println(getLocalName()+ "CALCULATING COS-SIM SCORES");
+
+					int localResultLimit = getCosSimLocalResultLimit();
+
+					for (int i = 0; i < usersForRec.length; i++)
+					{
+						String recUser = usersForRec[i];
+						LinkedHashMap<String, Double> recDoc = allUserDocumentsTFIDF.get(recUser);
+						TreeMap<String,Double> userScores = new TreeMap<String,Double>();
+
+						if (recDoc != null)
+						{
+							for (int j = 0; j < users.length; j++)
+							{
+								String candidateUser = users[j];
+								if (!recUser.equals(candidateUser))
+								{
+									LinkedHashMap<String, Double> candidateDoc = allUserDocumentsTFIDF.get(candidateUser);
+									double score = calculateSparseCosineScore(recDoc, candidateDoc);
+									userScores.put(candidateUser, score);
+								}
+							}
+						}
+
+						if (numRecAgents > 1)
+						{
+							userScores = keepTopCosSimScores(userScores, localResultLimit);
+						}
+						allUserScores.put(recUser, userScores);
+					}
+
+
+					// System.out.println(getLocalName()+" After COS SIM scores: "+allUserScores);
 
 					endTimeAlgorithm = System.nanoTime();
 					completionTimeAlgorithm = endTimeAlgorithm - startTimeAlgorithm;
@@ -4350,10 +3838,21 @@ public class RecommenderAgent extends Agent
 				}	
 				
 				//SVM Recommendation?
-				else if (algorithmRec == SVM)
-				{
-					
-					SMO svmModel = null;
+				else if (algorithmRec == SVM)
+				{
+					if (isSvmBatchMode())
+					{
+						startTimeAlgorithm = System.nanoTime();
+						allUserScores = runIndependentSvmBatchRecommendations();
+						endTimeAlgorithm = System.nanoTime();
+						completionTimeAlgorithm = endTimeAlgorithm - startTimeAlgorithm;
+						textprocessing_wb_or_tfidf_Data.add("SVM-Batch=TP+TFIDF+SVM" + "\t" + agentName + "\t" + tweetCount + "\t" + completionTimeTextProcessing + "\t" + completionTimeTFIDF    + "\t" + completionTimeAlgorithm + "\t" + System.getProperty("line.separator"));
+						System.out.println("Mapper"+nodeNumber+"- Total Tweets Processed: " + tweetCount + " TP: " + convertMs(completionTimeTextProcessing) + " ms TFIDF: " + convertMs(completionTimeTFIDF) + " ms Reducer"+ nodeNumber+ " SVM Batch: " + convertMs(completionTimeAlgorithm) + " ms Total: " + convertMs(completionTimeTextProcessing+completionTimeTFIDF+completionTimeAlgorithm)+" ms");
+						myGui.appendResult("Mapper"+nodeNumber+"- Total Tweets Processed: " + tweetCount + " TP: " + convertMs(completionTimeTextProcessing) + " ms TFIDF: " + convertMs(completionTimeTFIDF) + " ms Reducer"+ nodeNumber+ " SVM Batch: " + convertMs(completionTimeAlgorithm) + " ms Total: " + convertMs(completionTimeTextProcessing+completionTimeTFIDF+completionTimeAlgorithm)+" ms");
+					}
+					else
+					{
+					SMO svmModel = null;
 					SMO smoModel = null;
 					LibSVM libSVMModel = null;
 					BufferedReader datafile = readDataFile(trainSetFilePath);
@@ -4371,11 +3870,11 @@ public class RecommenderAgent extends Agent
 					// {
 					//Train the SVM
 					data.setClassIndex(data.numAttributes() - 1);
-					svmModel = new SMO();
-					smoModel = new SMO();
-					libSVMModel = new LibSVM();
-					svmModel.setC(0.1);   // added by Sepide  Jan.17
-					smoModel.setC(0.1);   // added by Sepide  Jan. 17
+					svmModel = new SMO();
+					smoModel = new SMO();
+					libSVMModel = new LibSVM();
+					configureSvmModel(svmModel);   // added by Sepide  Jan.17
+					configureSvmModel(smoModel);   // added by Sepide  Jan. 17
 					
 										
 					try{
@@ -4483,11 +3982,12 @@ public class RecommenderAgent extends Agent
 					// label instances
 					// for SMO/SVM
 					// for (int i = 0; i < test.numInstances(); i++) {
-					double clsLabel = 0.0;
-					Map<String,String> userPredictedFollowee = new LinkedHashMap<String,String>();
-					String predictedClass;
-					//For just recommended user
-					for (int i = 0; i < recommendInstances.numInstances(); i++) {
+					double clsLabel = 0.0;
+					Map<String,String> userPredictedFollowee = new LinkedHashMap<String,String>();
+					Map<String,TreeMap<String,Double>> userSvmScores = new LinkedHashMap<String,TreeMap<String,Double>>();
+					String predictedClass;
+						//For just recommended user
+						for (int i = 0; i < recommendInstances.numInstances(); i++) {
 						
 						try {
 							clsLabel = svmModel.classifyInstance(recommendInstances.instance(i));
@@ -4500,14 +4000,16 @@ public class RecommenderAgent extends Agent
 						
 						predictedLabelsRec.instance(i).setClassValue(clsLabel);
 						
-						if (i == recommendInstances.numInstances()-1)
-						{
-							predictedClass = predictedLabelsRec.instance(i).stringValue(data.numAttributes()-1);
-			//				System.out.println(predictedLabels.instance(i).stringValue(data.numAttributes()-2));
-							System.out.println(getLocalName()+" Recommended Class: "+predictedLabelsRec.instance(i).stringValue(data.numAttributes()-1));
-							userPredictedFollowee.put(usersRec.get(i),predictedClass);
-						}
-					}
+							predictedClass = predictedLabelsRec.instance(i).stringValue(data.numAttributes()-1);
+							//				System.out.println(predictedLabels.instance(i).stringValue(data.numAttributes()-2));
+							System.out.println(getLocalName()+" Recommended Class: "+predictedLabelsRec.instance(i).stringValue(data.numAttributes()-1));
+							if (i < usersRec.size())
+							{
+								userPredictedFollowee.put(usersRec.get(i),predictedClass);
+								double[] classDistribution = getSvmClassDistribution(svmModel, recommendInstances, i);
+								userSvmScores.put(usersRec.get(i), buildFolloweeScoreMapFromDistribution(recommendInstances, classDistribution, predictedClass));
+							}
+						}
 
 					//For entire test set
 					for (int i = 0; i < test.numInstances(); i++) {
@@ -4556,23 +4058,8 @@ public class RecommenderAgent extends Agent
 					//S myGui.appendResult("Accuracy: " + eval.pctCorrect() + "%");
 					// end of code added Jan 14
 					
-					allUserScores = new TreeMap<String,TreeMap<String,Double>>();
-					Map<String,Double> userScore1 = new TreeMap<String,Double>();
-					double followeeScore = 0.0;
-					for (String recUser : userPredictedFollowee.keySet())
-					{
-						for (String followeeUser: followeeFollowers.keySet())
-						{
-							if (userPredictedFollowee.get(recUser).equals(followeeUser))
-								followeeScore = 1.0;
-							else
-								followeeScore = 0.0;
-							
-							userScore1.put(followeeUser,followeeScore);
-						}
-						allUserScores.put(recUser,(TreeMap<String,Double>)userScore1);
-						userScore1 = new TreeMap<String,Double>();
-					}
+					allUserScores = new TreeMap<String,TreeMap<String,Double>>();
+					allUserScores.putAll(userSvmScores);
 					
 					try {
 						System.out.println(eval.toMatrixString());
@@ -4591,12 +4078,24 @@ public class RecommenderAgent extends Agent
 //					System.out.println(agentName+"- Total Tweets Processed: " + tweetCount + " TP:" + convertMs(completionTimeTextProcessing) + "ms TFIDF:" + convertMs(completionTimeTFIDF) + "ms K-means:" + convertMs(completionTimeAlgorithm) + "ms Total:" + convertMs(completionTimeTextProcessing+completionTimeTFIDF+completionTimeAlgorithm)+"ms");
 					System.out.println("Mapper"+nodeNumber+"- Total Tweets Processed: " + tweetCount + " TP: " + convertMs(completionTimeTextProcessing) + " ms TFIDF: " + convertMs(completionTimeTFIDF) + " ms Reducer"+ nodeNumber+ " SVM: " + convertMs(completionTimeAlgorithm) + " ms Total: " + convertMs(completionTimeTextProcessing+completionTimeTFIDF+completionTimeAlgorithm)+" ms");
 //					myGui.appendResult(agentName+"\nTotal Tweets Processed: " + tweetCount + " TP:" + round(completionTimeTextProcessing/1000000.00,2) + "ms TFIDF:" + round(completionTimeTFIDF/1000000.00,2) + "ms K-means:" + round(completionTimeAlgorithm/1000000.00,2) + "ms Total:" + round((completionTimeTextProcessing+completionTimeTFIDF+completionTimeAlgorithm)/1000000.00,2)+"ms");
-					myGui.appendResult("Mapper"+nodeNumber+"- Total Tweets Processed: " + tweetCount + " TP: " + convertMs(completionTimeTextProcessing) + " ms TFIDF: " + convertMs(completionTimeTFIDF) + " ms Reducer"+ nodeNumber+ " SVM: " + convertMs(completionTimeAlgorithm) + " ms Total: " + convertMs(completionTimeTextProcessing+completionTimeTFIDF+completionTimeAlgorithm)+" ms");
-				}
+					myGui.appendResult("Mapper"+nodeNumber+"- Total Tweets Processed: " + tweetCount + " TP: " + convertMs(completionTimeTextProcessing) + " ms TFIDF: " + convertMs(completionTimeTFIDF) + " ms Reducer"+ nodeNumber+ " SVM: " + convertMs(completionTimeAlgorithm) + " ms Total: " + convertMs(completionTimeTextProcessing+completionTimeTFIDF+completionTimeAlgorithm)+" ms");
+					}
+				}
 				
 				// Code added by Sepide
 				
-				else if (algorithmRec == Doc2Vec)
+				else if (isDoc2VecBatchMode())
+				{
+					startTimeAlgorithm = System.nanoTime();
+					int topn = Integer.parseInt(myGui.recommendationField.getText());
+					allUserScores = runDoc2VecBatchRecommendations(topn);
+					endTimeAlgorithm = System.nanoTime();
+					completionTimeAlgorithm = endTimeAlgorithm - startTimeAlgorithm;
+
+					System.out.println("Mapper"+nodeNumber+"- Total Tweets Processed: " + tweetCount + " TP: " + convertMs(completionTimeTextProcessing) + " ms Reducer"+ nodeNumber+ " Doc2Vec Batch: " + convertMs(completionTimeAlgorithm) + " ms Total: " + convertMs(completionTimeTextProcessing+completionTimeAlgorithm)+" ms");
+					myGui.appendResult("Mapper"+nodeNumber+"- Total Tweets Processed: " + tweetCount + " TP: " + convertMs(completionTimeTextProcessing) + " ms Reducer"+ nodeNumber+ " Doc2Vec Batch: " + convertMs(completionTimeAlgorithm) + " ms Total: " + convertMs(completionTimeTextProcessing+completionTimeAlgorithm)+" ms");
+				}
+				else if (algorithmRec == Doc2Vec)
 				{
 					startTimeAlgorithm = System.nanoTime();
 					Map<String,Double> predictedFollowerScore = new LinkedHashMap<String,Double>();
@@ -4613,31 +4112,30 @@ public class RecommenderAgent extends Agent
 					Map<String,Double> userScore1 = new TreeMap<String,Double>();
 					double followeeScore = 0.0;
 					
-					try {
-						File fileFromGui = myGui.fileChooser.getSelectedFile();
-						//System.out.println("File path" + fileFromGui);
-						//System.out.println("File path" + myGui.fileChooser.getSelectedFile());
-						//File txtFile = new File((myGui.fileChooser.getSelectedFile()));
-						//String contents = FileUtils.readFileToString(fileFromGui);
-						String filePath = fileFromGui.getPath();
-						System.out.println("File Path for Sepide:" + filePath);
-                        String contents = FileUtils.readFileToString(fileFromGui);
-                        String doc2vecDirName = "Dataset/424k/";
-					    File doc2vecDir = new File(doc2vecDirName);
-						if (!doc2vecDir.exists())
-						{
-								doc2vecDir.mkdirs();
+					try {
+						File fileFromGui = myGui.fileChooser.getSelectedFile();
+						//System.out.println("File path" + fileFromGui);
+						//System.out.println("File path" + myGui.fileChooser.getSelectedFile());
+						//File txtFile = new File((myGui.fileChooser.getSelectedFile()));
+						//String contents = FileUtils.readFileToString(fileFromGui);
+						String filePath = fileFromGui.getAbsolutePath();
+						System.out.println("File Path for Sepide:" + filePath);
+                        String doc2vecDirName = "Dataset/424k/";
+					    File doc2vecDir = new File(doc2vecDirName);
+						if (!doc2vecDir.exists())
+						{
+								doc2vecDir.mkdirs();
 						}	
                         
-						//myCSV = myGui.fileChooser.getSelectedFile();
-						//nodeNumInt = Integer.parseInt((myGui.numNodesField.getText()));
-						//out_file_pattern = doc2vecDirName + "data_set_doc2vec_"+nodeNumber+".txt";
-						out_file_pattern = doc2vecDirName + "part" + nodeNumber + "_" + ".txt";
-						//out_file_pattern = dataSetFilePath;
-						//out_file_pattern = doc2vecDirName+"data_set_doc2vec_"+getLocalName()+".txt";
-						//System.out.println("File path: "+ txtFile);
-						
-					}
+						//myCSV = myGui.fileChooser.getSelectedFile();
+						//nodeNumInt = Integer.parseInt((myGui.numNodesField.getText()));
+						//out_file_pattern = doc2vecDirName + "data_set_doc2vec_"+nodeNumber+".txt";
+						out_file_pattern = filePath;
+						//out_file_pattern = dataSetFilePath;
+						//out_file_pattern = doc2vecDirName+"data_set_doc2vec_"+getLocalName()+".txt";
+						//System.out.println("File path: "+ txtFile);
+						
+					}
 					
 					catch(Exception e)
 						{
@@ -4654,14 +4152,24 @@ public class RecommenderAgent extends Agent
 				  try {
 					  
 						
-					//usersRec = myGui.getUsersRec();
-					
-					//java.lang.ProcessBuilder pb = new ProcessBuilder("C:/Program Files/Python39/python.exe","D:/Simulator-S-15-May-2020/TwitterGatherDataFollowers/userRyersonU/doc2vec.py",""+usersRec.get(0).toString(),""+topn,""+myCSV).inheritIO();
-					//java.lang.ProcessBuilder pb = new ProcessBuilder("C:/Program Files/Python39/python.exe",doc2vecDirLoc + "doc2vec.py",""+usersRec.get(0).toString(),""+topn,""+out_file_pattern,""+nodeNumber).inheritIO();
-					java.lang.ProcessBuilder pb = new ProcessBuilder("python",doc2vecDirLoc + "doc2vec.py",""+usersRec.get(0).toString(),""+topn,""+out_file_pattern,""+nodeNumber);
-					//java.lang.ProcessBuilder pb = new ProcessBuilder("C:/Program Files/Python39/python.exe","D:/Simulator-S-15-May-2020/TwitterGatherDataFollowers/userRyersonU/doc2vec3.py",""+usersRec.get(0).toString(),""+topn,""+myGui.fileChooser.getSelectedFile()).inheritIO();
-                    
-					Process p = pb.start();
+					//usersRec = myGui.getUsersRec();
+					
+					//java.lang.ProcessBuilder pb = new ProcessBuilder("C:/Program Files/Python39/python.exe","D:/Simulator-S-15-May-2020/TwitterGatherDataFollowers/userRyersonU/doc2vec.py",""+usersRec.get(0).toString(),""+topn,""+myCSV).inheritIO();
+					//java.lang.ProcessBuilder pb = new ProcessBuilder("C:/Program Files/Python39/python.exe",doc2vecDirLoc + "doc2vec.py",""+usersRec.get(0).toString(),""+topn,""+out_file_pattern,""+nodeNumber).inheritIO();
+					int doc2vecWorkers = Math.min(Math.max(Runtime.getRuntime().availableProcessors() - 1, 1), 12);
+					java.lang.ProcessBuilder pb = new ProcessBuilder(
+						"python",
+						doc2vecDirLoc + "doc2vec_shared.py",
+						""+usersRec.get(0).toString(),
+						""+topn,
+						""+out_file_pattern,
+						""+nodeNumber,
+						""+numRecAgents,
+						""+doc2vecWorkers
+					);
+					//java.lang.ProcessBuilder pb = new ProcessBuilder("C:/Program Files/Python39/python.exe","D:/Simulator-S-15-May-2020/TwitterGatherDataFollowers/userRyersonU/doc2vec3.py",""+usersRec.get(0).toString(),""+topn,""+myGui.fileChooser.getSelectedFile()).inheritIO();
+                    
+					Process p = pb.start();
 					InputStream errorStream = p.getErrorStream();
 					BufferedReader errorReader = new BufferedReader(new InputStreamReader(errorStream));
 					BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
@@ -5312,8 +4820,9 @@ public class RecommenderAgent extends Agent
 					
 					BackPropagation nodeLearningRule = (BackPropagation) nodeMLP.getLearningRule();
 					nodeLearningRule.setLearningRate(LEARNING_RATE_MLP);
-					nodeLearningRule.setMaxError(MAX_ERROR_MLP);  
-					
+					nodeLearningRule.setMaxError(MAX_ERROR_MLP);
+					//nodeLearningRule.setMaxIterations(100);
+
 					System.out.println(getLocalName()+" training MLP");
 					
 					startTimeTrain = System.nanoTime();
@@ -6039,144 +5548,39 @@ public class RecommenderAgent extends Agent
 	
 	// End of methods added by Sepide 
 		
-		private void determineCentralTrainingTestSet()
-		{
-			testSetUsers = new ArrayList<String>(); //list of users in test set
-			trainSetUsers = new ArrayList<String>(); //list of users in training set
-			for (String userInstance : allUserDocuments.keySet())
-			{
-				if (centralTrainSetUsers.contains(userInstance))
-					trainSetUsers.add(userInstance);
-				else if (centralTestSetUsers.contains(userInstance))
-					testSetUsers.add(userInstance);
-			}
-		}
-		
-		private void determineTrainingTestSet()
-		{
-			int numUsers = allUserDocuments.keySet().size();
-			System.out.println("For sepideee the all userDocuments size " + numUsers + "\n");
-			int numTestUsers = (int) Math.floor(numUsers * TEST_SET_PERCENT);
-			int numTrainUsers = numUsers - numTestUsers;
-			int currTestUsers = 0;
-			int currTrainUsers = 0;
-			testSetUsers = new ArrayList<String>(); //list of users in test set
-			trainSetUsers = new ArrayList<String>(); //list of users in training set
-			List<String> currFollowers; //list of followers for the current followee
-			Map<String,List<String>> tempFolloweeFollowers = new LinkedHashMap<String,List<String>>();
-			tempFolloweeFollowers.putAll(followeeFollowers);
-			
-			if (numTestUsers < 1)
-			{
-				numTestUsers = 1;
-				numTrainUsers = numUsers - numTestUsers;
-			}
-				
-			
-			int nodeNumInt = Integer.parseInt(nodeNumber);
-			// if (nodeNumInt % 2 == 0)
-			// {
-				//loop through each followee for 1 follower at a time until numTestUsers is reached
-				while (currTestUsers < numTestUsers)
-				{
-					for (String followeeName: tempFolloweeFollowers.keySet())
-					{
-						currFollowers = tempFolloweeFollowers.get(followeeName);
-						// if a followee set runs out of followers before another
-						if (currFollowers.size() > 0)
-						{
-							Collections.shuffle(currFollowers);
-							System.out.println(getLocalName()+" test shuffledList: "+currFollowers);
-							testSetUsers.add(currFollowers.remove(0));
-							currTestUsers++;
-						}
-						
-						tempFolloweeFollowers.put(followeeName,currFollowers);
-						
-						if (currTestUsers == numTestUsers)
-							break;
-					}
-				}
-				//loop through each followee for 1 follower at a time until numTrainUsers is reached
-				while (currTrainUsers < numTrainUsers)
-				{
-					for (String followeeName: tempFolloweeFollowers.keySet())
-					{
-						currFollowers = tempFolloweeFollowers.get(followeeName);
-						
-						//if a followee set runs out of followers before another
-						if (currFollowers.size() > 0)
-						{
-							Collections.shuffle(currFollowers);
-							System.out.println(getLocalName()+" training shuffledList: "+currFollowers);
-							trainSetUsers.add(currFollowers.remove(0));
-							currTrainUsers++;
-						}
-						
-						tempFolloweeFollowers.put(followeeName,currFollowers);
-						
-						if (currTrainUsers == numTrainUsers)
-							break;
-					}
-				}
-			// }
-			// else
-			// {
-				// loop through each followee for 1 follower at a time until numTrainUsers is reached
-				// while (currTrainUsers < numTrainUsers)
-				// {
-					// for (String followeeName: tempFolloweeFollowers.keySet())
-					// {
-						// currFollowers = tempFolloweeFollowers.get(followeeName);
-						
-						// /*if a followee set runs out of followers before another*/
-						// if (currFollowers.size() > 0)
-						// {
-							// Collections.shuffle(currFollowers);
-							// System.out.println(getLocalName()+" training shuffledList: "+currFollowers);
-							// trainSetUsers.add(currFollowers.remove(0));
-							// currTrainUsers++;
-						// }
-						
-						// tempFolloweeFollowers.put(followeeName,currFollowers);
-						
-						// if (currTrainUsers == numTrainUsers)
-							// break;
-					// }
-				// }
-				// loop through each followee for 1 follower at a time until numTestUsers is reached
-				// while (currTestUsers < numTestUsers)
-				// {
-					// for (String followeeName: tempFolloweeFollowers.keySet())
-					// {
-						// currFollowers = tempFolloweeFollowers.get(followeeName);
-						// /*if a followee set runs out of followers before another*/
-						// if (currFollowers.size() > 0)
-						// {
-							// Collections.shuffle(currFollowers);
-							// System.out.println(getLocalName()+" test shuffledList: "+currFollowers);
-							// testSetUsers.add(currFollowers.remove(0));
-							// currTestUsers++;
-						// }
-						
-						// tempFolloweeFollowers.put(followeeName,currFollowers);
-						
-						// if (currTestUsers == numTestUsers)
-							// break;
-					// }
-				// }
-
-			// }		
-			
-		}
-		
-		private double[] vectorArrayFormat(Map<String,Double> currDocTfidf, TreeSet<String> uniqueDocTerms)
-		{
-			double[] vectorArray = new double[uniqueDocTerms.size()];
-			double currTfidf;
-			
-			int uniqueWordCount = 0;
-			for (String uniqueWord: uniqueDocTerms)
+		private void determineCentralTrainingTestSet()
+		{
+			testSetUsers = new ArrayList<String>(); //list of users in test set
+			trainSetUsers = new ArrayList<String>(); //list of users in training set
+			for (String userInstance : allUserDocuments.keySet())
+			{
+				if (centralTrainSetUsers.contains(userInstance))
+					trainSetUsers.add(userInstance);
+				else if (centralTestSetUsers.contains(userInstance))
+					testSetUsers.add(userInstance);
+			}
+			Collections.sort(trainSetUsers);
+			Collections.sort(testSetUsers);
+		}
+
+		private void determineTrainingTestSet()
+		{
+			int numUsers = allUserDocuments.keySet().size();
+			System.out.println(getLocalName()+" classifier input users: "+numUsers);
+			SvmDataSplit split = determineSvmDataSplit(followeeFollowers);
+			trainSetUsers = split.trainUsers;
+			testSetUsers = split.testUsers;
+			String splitLabel = algorithmRec == SVM ? "SVM split" : "Classifier split";
+			logSvmSplitSummary(splitLabel, trainSetUsers, testSetUsers);
+		}
+		
+		private double[] vectorArrayFormat(Map<String,Double> currDocTfidf, TreeSet<String> uniqueDocTerms)
+		{
+			double[] vectorArray = new double[uniqueDocTerms.size()];
+			double currTfidf;
+			
+			int uniqueWordCount = 0;
+			for (String uniqueWord: uniqueDocTerms)
 			{
 				
 				// System.out.println(getLocalName()+" uniqueWordCount: "+uniqueWordCount);
@@ -6190,17 +5594,17 @@ public class RecommenderAgent extends Agent
 				uniqueWordCount++;
 			}
 			
-			return vectorArray;
-		}
-		
-		
-		private StringJoiner vectorArffFormat(Map<String,Double> currDocTfidf, TreeSet<String> uniqueDocTerms)
-		{
-			StringJoiner tfidfJoinerTemp = new StringJoiner(",");
-			double currTfidf;
-			
-			// int uniqueWordCount = 0;
-			for (String uniqueWord: uniqueDocTerms)
+			return vectorArray;
+		}
+		
+		
+			private StringJoiner vectorArffFormat(Map<String,Double> currDocTfidf, TreeSet<String> uniqueDocTerms)
+			{
+				StringJoiner tfidfJoinerTemp = new StringJoiner(",");
+			double currTfidf;
+			
+			// int uniqueWordCount = 0;
+			for (String uniqueWord: uniqueDocTerms)
 			{
 				// uniqueWordCount++;
 				// System.out.println(getLocalName()+" uniqueWordCount: "+uniqueWordCount);
@@ -6208,13 +5612,821 @@ public class RecommenderAgent extends Agent
 					currTfidf = currDocTfidf.get(uniqueWord);
 				else
 					currTfidf = 0.0;
-				
-				tfidfJoinerTemp.add(String.valueOf(currTfidf));
-			}
-			return tfidfJoinerTemp;
-		}
-		
-		private void testNeuralNetwork(NeuralNetwork nnet, DataSet testSet) {
+				
+				tfidfJoinerTemp.add(String.valueOf(currTfidf));
+			}
+				return tfidfJoinerTemp;
+			}
+
+			private void configureSvmModel(SMO svmModel)
+			{
+				svmModel.setC(0.1);
+				svmModel.setBuildCalibrationModels(true);
+				SvmReproducibility.applyModelSeed(svmModel);
+			}
+
+			private double[] getSvmClassDistribution(SMO svmModel, Instances instances, int instanceIndex)
+			{
+				try
+				{
+					return svmModel.distributionForInstance(instances.instance(instanceIndex));
+				}
+				catch (Exception e)
+				{
+					e.printStackTrace();
+					return null;
+				}
+			}
+
+			private TreeMap<String,Double> buildFolloweeScoreMapFromDistribution(Instances instances, double[] distribution, String fallbackFollowee)
+			{
+				TreeMap<String,Double> userScore = new TreeMap<String,Double>();
+				for (String followeeUser : followeeFollowers.keySet())
+				{
+					userScore.put(followeeUser, 0.0);
+				}
+
+				if (distribution != null)
+				{
+					for (int i = 0; i < distribution.length; i++)
+					{
+						String className = instances.classAttribute().value(i);
+						if (userScore.containsKey(className))
+						{
+							double score = distribution[i];
+							if (Double.isNaN(score) || Double.isInfinite(score))
+							{
+								score = 0.0;
+							}
+							userScore.put(className, score);
+						}
+					}
+				}
+
+				boolean hasPositiveScore = false;
+				for (Double score : userScore.values())
+				{
+					if (score != null && score > 0.0)
+					{
+						hasPositiveScore = true;
+						break;
+					}
+				}
+				if (!hasPositiveScore && fallbackFollowee != null && userScore.containsKey(fallbackFollowee))
+				{
+					userScore.put(fallbackFollowee, 1.0);
+				}
+
+				return userScore;
+			}
+
+			private boolean isDoc2VecBatchMode()
+			{
+				return algorithmRec == Doc2Vec && usersRec != null && usersRec.size() > 1;
+			}
+
+			private TreeMap<String,TreeMap<String,Double>> runDoc2VecBatchRecommendations(int topn)
+			{
+				TreeMap<String,TreeMap<String,Double>> batchScores = new TreeMap<String,TreeMap<String,Double>>();
+				ArrayList<String> batchUsers = new ArrayList<String>(usersRec);
+				File selectedDataset = myGui == null || myGui.fileChooser == null
+						? null : myGui.fileChooser.getSelectedFile();
+				if (selectedDataset == null)
+				{
+					System.out.println(getLocalName()+" Doc2Vec batch cannot run because no dataset file is selected.");
+					return buildDoc2VecBatchFailureScores(batchUsers, topn);
+				}
+
+				int doc2vecWorkers = Math.min(Math.max(Runtime.getRuntime().availableProcessors() - 1, 1), 12);
+				File batchScript = new File("TwitterGatherDataFollowers/userRyersonU/doc2vec_batch.py");
+				try
+				{
+					Doc2VecBatchRunner.Result result = Doc2VecBatchRunner.run(
+							"python", batchScript, topn, selectedDataset,
+							Integer.parseInt(nodeNumber), numRecAgents, doc2vecWorkers, batchUsers);
+					for (Map.Entry<String,String> error : result.getErrors().entrySet())
+					{
+						System.out.println(getLocalName()+" Doc2Vec batch warning for "+error.getKey()+": "+error.getValue());
+					}
+					if (result.getExitCode() != 0)
+					{
+						System.out.println(getLocalName()+" Doc2Vec batch process exited with code "+result.getExitCode()+". Incomplete users will receive fallback scores.");
+					}
+
+					for (String recUser : batchUsers)
+					{
+						TreeMap<String,Double> userScores = result.getScores(recUser);
+						if (result.isCompleted(recUser) && userScores != null)
+						{
+							batchScores.put(recUser, new TreeMap<String,Double>(userScores));
+						}
+						else
+						{
+							System.out.println(getLocalName()+" Doc2Vec batch did not complete for "+recUser+". Writing fallback scores so querying can continue.");
+							batchScores.put(recUser, buildDoc2VecBatchFailureScoreMap(recUser, topn));
+						}
+					}
+				}
+				catch (IOException e)
+				{
+					e.printStackTrace();
+					return buildDoc2VecBatchFailureScores(batchUsers, topn);
+				}
+				catch (InterruptedException e)
+				{
+					Thread.currentThread().interrupt();
+					e.printStackTrace();
+					return buildDoc2VecBatchFailureScores(batchUsers, topn);
+				}
+
+				return batchScores;
+			}
+
+			private TreeMap<String,TreeMap<String,Double>> buildDoc2VecBatchFailureScores(List<String> batchUsers, int topn)
+			{
+				TreeMap<String,TreeMap<String,Double>> failureScores = new TreeMap<String,TreeMap<String,Double>>();
+				for (String recUser : batchUsers)
+				{
+					failureScores.put(recUser, buildDoc2VecBatchFailureScoreMap(recUser, topn));
+				}
+				return failureScores;
+			}
+
+			private TreeMap<String,Double> buildDoc2VecBatchFailureScoreMap(String recUser, int topn)
+			{
+				TreeMap<String,Double> fallbackScores = new TreeMap<String,Double>();
+				TreeSet<String> localCandidates = new TreeSet<String>(allUserDocuments.keySet());
+				localCandidates.remove(recUser);
+				int resultLimit = Math.max(topn, 1);
+				for (String candidate : localCandidates)
+				{
+					fallbackScores.put(candidate, -1.0);
+					if (fallbackScores.size() >= resultLimit)
+					{
+						break;
+					}
+				}
+				return fallbackScores;
+			}
+
+			private boolean isSvmBatchMode()
+			{
+				return algorithmRec == SVM && usersRec != null && usersRec.size() > 1;
+			}
+
+			private TreeMap<String,TreeMap<String,Double>> runIndependentSvmBatchRecommendations()
+			{
+				TreeMap<String,TreeMap<String,Double>> batchScores = new TreeMap<String,TreeMap<String,Double>>();
+				ArrayList<String> assignedUsers = getAssignedSvmBatchUsers();
+				System.out.println(getLocalName()+" SVM batch assigned users: "+assignedUsers);
+				System.out.println(getLocalName()+" SVM reproducibility split seed: "
+						+ SvmReproducibility.SPLIT_SEED + " model seed: " + SvmReproducibility.MODEL_SEED);
+				for (String recUser : assignedUsers)
+				{
+					TreeMap<String,Double> userScores = runIndependentSvmForUser(recUser);
+					if (userScores == null)
+					{
+						System.out.println(getLocalName()+" SVM batch could not produce a prediction for "+recUser+". Writing zero scores so querying can continue.");
+						batchScores.put(recUser, buildFolloweeScoreMap(null));
+					}
+					else
+					{
+						batchScores.put(recUser, userScores);
+					}
+				}
+				return batchScores;
+			}
+
+			private ArrayList<String> getAssignedSvmBatchUsers()
+			{
+				ArrayList<String> assignedUsers = new ArrayList<String>();
+				if (usersRec == null || usersRec.size() == 0)
+				{
+					return assignedUsers;
+				}
+
+				assignedUsers.addAll(usersRec);
+				return assignedUsers;
+			}
+
+			private TreeMap<String,Double> runIndependentSvmForUser(String recUser)
+			{
+				if (recUser == null || !allUserDocuments.containsKey(recUser))
+				{
+					return null;
+				}
+
+				LinkedHashMap<String,LinkedHashMap<String,Double>> documentsForTarget = buildSvmDocumentsForTarget(recUser);
+				if (!documentsForTarget.containsKey(recUser))
+				{
+					return null;
+				}
+
+				TreeSet<String> targetTerms = buildUniqueTermsForDocuments(documentsForTarget);
+				LinkedHashMap<String,Integer> targetDocFreq = buildDocumentFrequencyForDocuments(documentsForTarget);
+				LinkedHashMap<String,LinkedHashMap<String,Double>> targetTfidf = calculateTfidfForDocuments(documentsForTarget, targetDocFreq);
+				Map<String,List<String>> targetFolloweeFollowers = buildFolloweeFollowersForDocuments(documentsForTarget);
+				SvmDataSplit split = determineSvmDataSplit(targetFolloweeFollowers);
+
+				String arffDirName = "Dataset/Arff_files/";
+				File arffDir = new File(arffDirName);
+				if (!arffDir.exists())
+				{
+					arffDir.mkdirs();
+				}
+
+				String safeUser = sanitizeFileName(recUser);
+				String trainPath = arffDirName + "svm_batch_train_rec" + nodeNumber + "_" + safeUser + ".txt";
+				String testPath = arffDirName + "svm_batch_test_rec" + nodeNumber + "_" + safeUser + ".txt";
+				String recommendPath = arffDirName + "svm_batch_recommend_rec" + nodeNumber + "_" + safeUser + ".txt";
+
+				try
+				{
+					writeSvmBatchArffFiles(trainPath, testPath, recommendPath, targetTfidf, targetTerms, split, recUser);
+				}
+				catch (IOException e)
+				{
+					e.printStackTrace();
+					return null;
+				}
+
+				return trainAndPredictSvmForBatchUser(trainPath, testPath, recommendPath, recUser);
+			}
+
+			private LinkedHashMap<String,LinkedHashMap<String,Double>> buildSvmDocumentsForTarget(String targetUser)
+			{
+				LinkedHashMap<String,LinkedHashMap<String,Double>> documentsForTarget = new LinkedHashMap<String,LinkedHashMap<String,Double>>();
+				for (Map.Entry<String,LinkedHashMap<String,Double>> entry : allUserDocuments.entrySet())
+				{
+					String userName = entry.getKey();
+					if (usersRec.contains(userName) && !userName.equals(targetUser))
+					{
+						Integer primaryRecServer = userPrimaryRecServer.get(userName);
+						if (primaryRecServer == null || !String.valueOf(primaryRecServer).equals(nodeNumber))
+						{
+							continue;
+						}
+					}
+					documentsForTarget.put(userName, entry.getValue());
+				}
+				return documentsForTarget;
+			}
+
+			private TreeSet<String> buildUniqueTermsForDocuments(LinkedHashMap<String,LinkedHashMap<String,Double>> documents)
+			{
+				TreeSet<String> uniqueTerms = new TreeSet<String>();
+				for (LinkedHashMap<String,Double> document : documents.values())
+				{
+					uniqueTerms.addAll(document.keySet());
+				}
+				return uniqueTerms;
+			}
+
+			private LinkedHashMap<String,Integer> buildDocumentFrequencyForDocuments(LinkedHashMap<String,LinkedHashMap<String,Double>> documents)
+			{
+				LinkedHashMap<String,Integer> docFreq = new LinkedHashMap<String,Integer>();
+				for (LinkedHashMap<String,Double> document : documents.values())
+				{
+					for (String term : document.keySet())
+					{
+						Integer currentCount = docFreq.get(term);
+						if (currentCount == null)
+						{
+							currentCount = 0;
+						}
+						docFreq.put(term, currentCount + 1);
+					}
+				}
+				return docFreq;
+			}
+
+			private LinkedHashMap<String,LinkedHashMap<String,Double>> calculateTfidfForDocuments(LinkedHashMap<String,LinkedHashMap<String,Double>> documents, LinkedHashMap<String,Integer> docFreq)
+			{
+				LinkedHashMap<String,LinkedHashMap<String,Double>> tfidfDocuments = new LinkedHashMap<String,LinkedHashMap<String,Double>>();
+				int targetTotalDocuments = documents.size();
+				for (Map.Entry<String,LinkedHashMap<String,Double>> entry : documents.entrySet())
+				{
+					String userName = entry.getKey();
+					LinkedHashMap<String,Double> userDoc = entry.getValue();
+					LinkedHashMap<String,Double> userDocumentTFIDF = new LinkedHashMap<String,Double>();
+					double vectorMagnitude = 0.0;
+					for (String docTerm : userDoc.keySet())
+					{
+						double tf = userDoc.get(docTerm);
+						Integer dfValue = docFreq.get(docTerm);
+						double df = dfValue == null ? 0.0 : dfValue.doubleValue();
+						double tfidf = 0.0;
+						if (df > 0.0)
+						{
+							double idf = (double)Math.log10(targetTotalDocuments/df) / Math.log10(2);
+							tfidf = tf * idf;
+						}
+						if (Double.isNaN(tfidf))
+						{
+							tfidf = 0.0;
+						}
+						userDocumentTFIDF.put(docTerm, tfidf);
+						vectorMagnitude += tfidf * tfidf;
+					}
+
+					vectorMagnitude = Math.sqrt(vectorMagnitude);
+					for (String docTerm : userDoc.keySet())
+					{
+						double tfidf = userDocumentTFIDF.get(docTerm);
+						if (vectorMagnitude > 0.0)
+						{
+							tfidf = tfidf / vectorMagnitude;
+						}
+						else
+						{
+							tfidf = 0.0;
+						}
+						if (Double.isNaN(tfidf))
+						{
+							tfidf = 0.0;
+						}
+						userDocumentTFIDF.put(docTerm, tfidf);
+					}
+					tfidfDocuments.put(userName, userDocumentTFIDF);
+				}
+				return tfidfDocuments;
+			}
+
+			private Map<String,List<String>> buildFolloweeFollowersForDocuments(LinkedHashMap<String,LinkedHashMap<String,Double>> documents)
+			{
+				Map<String,List<String>> targetFolloweeFollowers = new LinkedHashMap<String,List<String>>();
+				for (String userName : documents.keySet())
+				{
+					String followeeName = userFollowee.get(userName);
+					if (followeeName == null)
+					{
+						continue;
+					}
+					List<String> followers = targetFolloweeFollowers.get(followeeName);
+					if (followers == null)
+					{
+						followers = new ArrayList<String>();
+						targetFolloweeFollowers.put(followeeName, followers);
+					}
+					followers.add(userName);
+				}
+				return targetFolloweeFollowers;
+			}
+
+			private SvmDataSplit determineSvmDataSplit(Map<String,List<String>> targetFolloweeFollowers)
+			{
+				SvmReproducibility.Split split = SvmReproducibility.stratifiedSplit(
+						targetFolloweeFollowers, TEST_SET_PERCENT);
+				return new SvmDataSplit(split.getTrainUsers(), split.getTestUsers());
+			}
+
+			private void logSvmSplitSummary(String label, List<String> trainUsers, List<String> testUsers)
+			{
+				System.out.println(getLocalName()+" "+label+" reproducibility split seed: "
+						+ SvmReproducibility.SPLIT_SEED + " model seed: " + SvmReproducibility.MODEL_SEED);
+				System.out.println(getLocalName()+" "+label+" train users: "+trainUsers.size()+" test users: "+testUsers.size());
+				System.out.println(getLocalName()+" "+label+" train class counts: "+countUsersByFollowee(trainUsers));
+				System.out.println(getLocalName()+" "+label+" test class counts: "+countUsersByFollowee(testUsers));
+			}
+
+			private Map<String,Integer> countUsersByFollowee(List<String> users)
+			{
+				Map<String,Integer> counts = new LinkedHashMap<String,Integer>();
+				for (String user : users)
+				{
+					String followeeName = userFollowee.get(user);
+					if (followeeName == null)
+					{
+						followeeName = "UNKNOWN";
+					}
+					Integer count = counts.get(followeeName);
+					if (count == null)
+					{
+						count = 0;
+					}
+					counts.put(followeeName, count + 1);
+				}
+				return counts;
+			}
+
+			private void writeSvmBatchArffFiles(String trainPath, String testPath, String recommendPath, LinkedHashMap<String,LinkedHashMap<String,Double>> tfidfDocuments, TreeSet<String> uniqueTerms, SvmDataSplit split, String recUser) throws IOException
+			{
+				writeSvmBatchArffFile(trainPath, "trainingSet", split.trainUsers, tfidfDocuments, uniqueTerms);
+				writeSvmBatchArffFile(testPath, "testSet", split.testUsers, tfidfDocuments, uniqueTerms);
+				ArrayList<String> recommendUsers = new ArrayList<String>();
+				recommendUsers.add(recUser);
+				writeSvmBatchArffFile(recommendPath, "recommendSet", recommendUsers, tfidfDocuments, uniqueTerms);
+			}
+
+			private void writeSvmBatchArffFile(String filePath, String relationName, List<String> rowUsers, LinkedHashMap<String,LinkedHashMap<String,Double>> tfidfDocuments, TreeSet<String> uniqueTerms) throws IOException
+			{
+				BufferedWriter writer = new BufferedWriter(new FileWriter(filePath, false));
+				writer.write("@relation " + relationName);
+				writer.newLine();
+				writer.newLine();
+				int uniqueWordCount = 0;
+				for (String uniqueWord : uniqueTerms)
+				{
+					writer.write("@attribute word"+ uniqueWordCount +" numeric");
+					writer.newLine();
+					uniqueWordCount++;
+				}
+				writer.write(buildArffClassAttribute(followeeFollowers.keySet()));
+				writer.newLine();
+				writer.newLine();
+				writer.newLine();
+				writer.write("@data");
+				writer.newLine();
+				for (String currUser : rowUsers)
+				{
+					Map<String,Double> currDocTfidf = tfidfDocuments.get(currUser);
+					if (currDocTfidf == null)
+					{
+						continue;
+					}
+					StringJoiner tfidfJoiner = vectorArffFormat(currDocTfidf, uniqueTerms);
+					writer.write(tfidfJoiner.toString() + "," + formatArffClassValue(userFollowee.get(currUser)));
+					writer.newLine();
+				}
+				writer.close();
+			}
+
+			private TreeMap<String,Double> trainAndPredictSvmForBatchUser(String trainPath, String testPath, String recommendPath, String recUser)
+			{
+				try
+				{
+					BufferedReader datafile = readDataFile(trainPath);
+					Instances data = new Instances(datafile);
+					data.setClassIndex(data.numAttributes() - 1);
+					if (data.numInstances() == 0)
+					{
+						return null;
+					}
+
+					SMO svmModel = new SMO();
+					configureSvmModel(svmModel);
+					svmModel.buildClassifier(data);
+
+					BufferedReader testfile = readDataFile(testPath);
+					Instances test = new Instances(testfile);
+					test.setClassIndex(test.numAttributes() - 1);
+					if (test.numInstances() > 0)
+					{
+						Evaluation eval = new Evaluation(data);
+						eval.evaluateModel(svmModel, test);
+						System.out.println(getLocalName()+" SVM batch accuracy for "+recUser+": "+String.format("%.2f%%", eval.pctCorrect()));
+					}
+
+					BufferedReader recommendFile = readDataFile(recommendPath);
+					Instances recommendInstances = new Instances(recommendFile);
+					recommendInstances.setClassIndex(recommendInstances.numAttributes() - 1);
+					if (recommendInstances.numInstances() == 0)
+					{
+						return null;
+					}
+
+					double clsLabel = svmModel.classifyInstance(recommendInstances.instance(0));
+					String predictedFollowee = recommendInstances.classAttribute().value((int) clsLabel);
+					double[] classDistribution = getSvmClassDistribution(svmModel, recommendInstances, 0);
+					TreeMap<String,Double> userScores = buildFolloweeScoreMapFromDistribution(recommendInstances, classDistribution, predictedFollowee);
+					System.out.println(getLocalName()+" SVM batch predicted "+recUser+" -> "+predictedFollowee+" scores: "+userScores);
+					return userScores;
+				}
+				catch (Exception e)
+				{
+					e.printStackTrace();
+					return null;
+				}
+			}
+
+			private TreeMap<String,Double> buildFolloweeScoreMap(String predictedFollowee)
+			{
+				TreeMap<String,Double> userScore = new TreeMap<String,Double>();
+				for (String followeeUser : followeeFollowers.keySet())
+				{
+					double followeeScore = 0.0;
+					if (predictedFollowee != null && predictedFollowee.equals(followeeUser))
+					{
+						followeeScore = 1.0;
+					}
+					userScore.put(followeeUser, followeeScore);
+				}
+				return userScore;
+			}
+
+			private String sanitizeFileName(String value)
+			{
+				if (value == null)
+				{
+					return "unknown";
+				}
+				String safeValue = value.replaceAll("[^A-Za-z0-9._-]", "_");
+				if (safeValue.length() == 0)
+				{
+					return "unknown";
+				}
+				return safeValue;
+			}
+
+			private static class SvmDataSplit
+			{
+				private final ArrayList<String> trainUsers;
+				private final ArrayList<String> testUsers;
+
+				private SvmDataSplit(ArrayList<String> trainUsers, ArrayList<String> testUsers)
+				{
+					this.trainUsers = trainUsers;
+					this.testUsers = testUsers;
+				}
+			}
+	
+			private ArrayList<String> tokenizeProcessedTweet(String tweetText)
+			{
+			String currentText = tweetText == null ? "" : tweetText;
+
+			if (currentText.contains("Photo:"))
+				currentText = currentText.substring(0,currentText.indexOf("Photo:"));
+
+			if (currentText.contains("Photoset:"))
+				currentText = currentText.substring(0,currentText.indexOf("Photoset:"));
+
+			if (currentText.contains("RT @") && retweetedby_temp == RE_TWEETS)
+				currentText="";
+			else
+				currentText = RETWEET_PATTERN.matcher(currentText).replaceAll("RT ");
+
+			currentText = PUNCTUATION_PATTERN.matcher(currentText).replaceAll("");
+			currentText = LINK_PATTERN.matcher(currentText).replaceAll(" ");
+
+			if (hashtags_temp == HASH_TAGS)
+				currentText = SPECIAL_CHARACTER_PATTERN.matcher(currentText).replaceAll(" ");
+
+			currentText = currentText.toLowerCase();
+
+			if (stopWordFlag_temp == STOP_WORDS)
+				currentText = removeStopWords(currentText);
+
+			currentText = currentText.trim();
+			currentText = NON_ALPHA_PATTERN.matcher(currentText).replaceAll("");
+			currentText = MULTI_SPACE_PATTERN.matcher(currentText).replaceAll(" ");
+			currentText = currentText.trim();
+
+			if (currentText.length() == 0)
+				return new ArrayList<String>();
+
+			ArrayList<String> tokens = new ArrayList<String>();
+			int start = 0;
+			while (start < currentText.length())
+			{
+				int spaceIndex = currentText.indexOf(' ', start);
+				if (spaceIndex < 0)
+				{
+					tokens.add(currentText.substring(start));
+					break;
+				}
+				if (spaceIndex > start)
+				{
+					tokens.add(currentText.substring(start, spaceIndex));
+				}
+				start = spaceIndex + 1;
+			}
+			return tokens;
+		}
+
+		private String removeStopWords(String currentText)
+		{
+			String paddedText = " " + currentText + " ";
+			for (String stopWord : stopWordsArray)
+			{
+				String stopWordPattern = " " + stopWord + " ";
+				if (paddedText.contains(stopWordPattern))
+				{
+					paddedText = paddedText.replace(stopWordPattern, " ");
+				}
+			}
+			return paddedText;
+		}
+
+		private void writeDoc2VecUserDocumentFile()
+		{
+			BufferedWriter bf = null;
+			String doc2vecDirName = "Dataset/424k/";
+			File doc2vecDir = new File(doc2vecDirName);
+			if (!doc2vecDir.exists())
+			{
+				doc2vecDir.mkdirs();
+			}
+
+			try
+			{
+				bf = new BufferedWriter(new FileWriter(doc2vecDirName+ "userdoc.txt"));
+				for (Map.Entry<String,LinkedHashMap<String,Double>> entry : allUserDocuments.entrySet())
+				{
+					LinkedHashMap<String,Double> entry2 = entry.getValue();
+					for (Map.Entry<String,Double> entry3 : entry2.entrySet())
+					{
+						bf.write(entry3.getKey()+ " ");
+					}
+					bf.write(entry.getKey());
+					bf.newLine();
+				}
+				bf.flush();
+			}
+			catch (IOException e)
+			{
+				System.out.println("An error occurred.");
+				e.printStackTrace();
+			}
+			finally
+			{
+				if (bf != null)
+				{
+					try
+					{
+						bf.close();
+					}
+					catch (Exception e)
+					{
+					}
+				}
+			}
+		}
+		
+		private boolean recordTweetBatchFromUserAgent(ACLMessage msg)
+		{
+			Object batchObject;
+			try
+			{
+				batchObject = msg.getContentObject();
+			}
+			catch (UnreadableException e)
+			{
+				e.printStackTrace();
+				return false;
+			}
+			
+			if (!(batchObject instanceof List<?>))
+			{
+				System.out.println(getLocalName()+" received invalid tweet batch payload from "+msg.getSender().getLocalName());
+				return false;
+			}
+			
+				boolean recordedAnyTweet = false;
+				int primaryRecServer = -1;
+				for (Object tweetObject : (List<?>) batchObject)
+				{
+					if (tweetObject instanceof String)
+					{
+						String tweetMessage = (String) tweetObject;
+						if (tweetMessage.startsWith(TWEET_BATCH_PRIMARY_REC_SERVER_PREFIX))
+						{
+							try
+							{
+								primaryRecServer = Integer.parseInt(tweetMessage.substring(TWEET_BATCH_PRIMARY_REC_SERVER_PREFIX.length()).trim());
+							}
+							catch (NumberFormatException e)
+							{
+								primaryRecServer = -1;
+							}
+							continue;
+						}
+						recordedAnyTweet = recordTweetFromUserAgent(tweetMessage, primaryRecServer) || recordedAnyTweet;
+					}
+				}
+			
+			return recordedAnyTweet;
+		}
+		
+			private boolean recordTweetFromUserAgent(String tweetReceived)
+			{
+				return recordTweetFromUserAgent(tweetReceived, -1);
+			}
+
+			private boolean recordTweetFromUserAgent(String tweetReceived, int primaryRecServer)
+			{
+				if (tweetReceived == null)
+				{
+				return false;
+			}
+			
+			String[] tweetFields = splitTweetMessage(tweetReceived);
+			if (tweetFields.length < 5)
+			{
+				System.out.println(getLocalName()+" skipped malformed tweet message: "+tweetReceived);
+				return false;
+			}
+			
+				int totalTweetFromUser;
+				long tweetIdReceived;
+				try
+				{
+					totalTweetFromUser = Integer.parseInt(tweetFields[0]);
+					tweetIdReceived = Long.valueOf(tweetFields[2]);
+				}
+				catch (NumberFormatException e)
+				{
+					System.out.println(getLocalName()+" skipped tweet message with invalid numeric fields: "+tweetReceived);
+					return false;
+				}
+				
+				String tweetUserReceived = tweetFields[1];
+				String tweetFolloweeName = tweetFields[3];
+				String tweetTextReceived = tweetFields[4];
+				
+				tweetCount++;
+				if (tweetCount == 1)
+					firstTweetTime = System.nanoTime();
+				
+				if (tweetUserReceived.equals("sageryereson"))
+					System.out.println("sageryerson: "+tweetReceived);
+				
+				if (!userFollowee.containsKey(tweetUserReceived))
+					userFollowee.put(tweetUserReceived,tweetFolloweeName);
+
+				if (primaryRecServer > 0 && !userPrimaryRecServer.containsKey(tweetUserReceived))
+				{
+					userPrimaryRecServer.put(tweetUserReceived, primaryRecServer);
+				}
+				
+				totalMessageBytes += tweetReceived.length() * 2;
+				
+				if (usersRec.contains(tweetUserReceived))
+				{
+					int userIndex = usersRec.indexOf(tweetUserReceived);
+					usersRecTweetCountsReceived[userIndex]++;
+					
+					if (usersRecTweetCountsReceived[userIndex] == totalTweetFromUser)
+					{
+						ACLMessage msgLastTweetFromRecUser = new ACLMessage( ACLMessage.INFORM );
+						msgLastTweetFromRecUser.addReceiver( new AID(tweetUserReceived+"-UserAgent", AID.ISLOCALNAME) );
+						msgLastTweetFromRecUser.setPerformative( ACLMessage.INFORM );
+						msgLastTweetFromRecUser.setContent("Received Last Tweet");
+						msgLastTweetFromRecUser.setOntology("Last Tweet Received From Rec Agent");
+						send(msgLastTweetFromRecUser);
+					}
+				}
+				
+				if (!userRegisteredInRecAgent.contains(tweetUserReceived))
+				{
+					userRegisteredInRecAgent.add(tweetUserReceived);
+				}
+				
+				tweetIdText.put(tweetIdReceived, tweetTextReceived);
+				tweetIdUser.put(tweetIdReceived, tweetUserReceived);
+				return true;
+		}
+
+		private String[] splitTweetMessage(String tweetReceived)
+		{
+			int firstTab = tweetReceived.indexOf('\t');
+			if (firstTab >= 0)
+			{
+				int secondTab = tweetReceived.indexOf('\t', firstTab + 1);
+				int thirdTab = secondTab >= 0 ? tweetReceived.indexOf('\t', secondTab + 1) : -1;
+				int fourthTab = thirdTab >= 0 ? tweetReceived.indexOf('\t', thirdTab + 1) : -1;
+				if (secondTab >= 0 && thirdTab >= 0 && fourthTab >= 0)
+				{
+					return new String[] {
+						tweetReceived.substring(0, firstTab),
+						tweetReceived.substring(firstTab + 1, secondTab),
+						tweetReceived.substring(secondTab + 1, thirdTab),
+						tweetReceived.substring(thirdTab + 1, fourthTab),
+						tweetReceived.substring(fourthTab + 1)
+					};
+				}
+			}
+			return tweetReceived.split(" ", 5);
+		}
+	
+		private String buildArffClassAttribute(Set<String> classNames)
+		{
+			StringJoiner classJoiner = new StringJoiner(",","{","}");
+			Collection<String> orderedClassNames = classNames;
+			if (algorithmRec == SVM)
+			{
+				orderedClassNames = SvmReproducibility.sortedNonNullValues(classNames);
+			}
+			for (String className: orderedClassNames)
+			{
+				classJoiner.add(formatArffClassValue(className));
+			}
+			return "@attribute result " + classJoiner.toString();
+		}
+
+		private String formatArffClassValue(String className)
+		{
+			if (className == null)
+			{
+				return "?";
+			}
+			return "'" + className
+					.replace("\\", "\\\\")
+					.replace("'", "\\'")
+					.replace("\r", " ")
+					.replace("\n", " ") + "'";
+		}
+
+		private void testNeuralNetwork(NeuralNetwork nnet, DataSet testSet) {
 
 			List<DataSetRow> testSetRows = testSet.getRows();
 			// System.out.println(getLocalName()+" followeeNames: "+Arrays.toString(followeeNames));
@@ -6349,10 +6561,97 @@ public class RecommenderAgent extends Agent
 		
 		// End of code added by Sepide 
 		
-		private void recNeuralNetwork(NeuralNetwork nnet, DataSet recSet) {
-
-			List<DataSetRow> recSetRows = recSet.getRows();
-			System.out.println(getLocalName()+" followeeNames: "+Arrays.toString(followeeNames));
+			private void writeDoc2VecCompatibilityPartFiles(File selectedDatasetFile, String doc2vecDirName, int numFiles, String specificUser) throws IOException
+			{
+				if (selectedDatasetFile == null)
+				{
+					throw new IOException("No selected dataset file is available for Doc2Vec splitting.");
+				}
+				if (numFiles < 1)
+				{
+					numFiles = 1;
+				}
+
+				List<BufferedWriter> writers = new ArrayList<BufferedWriter>();
+				long rowsWritten = 0;
+				long skippedRows = 0;
+				int fileIndex = 0;
+
+				try
+				{
+					for (int i = 0; i < numFiles; i++)
+					{
+						writers.add(new BufferedWriter(new FileWriter(doc2vecDirName + "part" + (i + 1) + "_" + ".txt")));
+					}
+
+					BufferedReader reader = new BufferedReader(new FileReader(selectedDatasetFile));
+					try
+					{
+						String row;
+						while ((row = reader.readLine()) != null)
+						{
+							String[] columns = row.split("\t", 6);
+							if (columns.length < 6)
+							{
+								skippedRows++;
+								continue;
+							}
+
+							String newRow = columns[5] + "\t" + columns[4];
+							if (columns[4].equals(specificUser))
+							{
+								for (BufferedWriter writer : writers)
+								{
+									writer.write(newRow);
+									writer.newLine();
+									rowsWritten++;
+								}
+							}
+							else
+							{
+								BufferedWriter writer = writers.get(fileIndex);
+								writer.write(newRow);
+								writer.newLine();
+								rowsWritten++;
+								fileIndex = (fileIndex + 1) % numFiles;
+							}
+						}
+					}
+					finally
+					{
+						reader.close();
+					}
+				}
+				finally
+				{
+					IOException closeException = null;
+					for (BufferedWriter writer : writers)
+					{
+						try
+						{
+							writer.close();
+						}
+						catch (IOException e)
+						{
+							if (closeException == null)
+							{
+								closeException = e;
+							}
+						}
+					}
+					if (closeException != null)
+					{
+						throw closeException;
+					}
+				}
+
+				System.out.println(getLocalName()+" Doc2Vec compatibility split complete rowsWritten="+rowsWritten+" skippedRows="+skippedRows+" files="+numFiles);
+			}
+			
+			private void recNeuralNetwork(NeuralNetwork nnet, DataSet recSet) {
+
+			List<DataSetRow> recSetRows = recSet.getRows();
+			System.out.println(getLocalName()+" followeeNames: "+Arrays.toString(followeeNames));
 			System.out.println(getLocalName()+ " recSet Size: "+recSetRows.size());
 			
 			allUserScores = new TreeMap<String,TreeMap<String,Double>>();
@@ -6383,11 +6682,90 @@ public class RecommenderAgent extends Agent
 				userScore1 = new TreeMap<String,Double>();
 				
 			}
-
-		}
-		
-		private int findMaxIndex(double[] array)
-		{
+
+		}
+
+		private double calculateSparseCosineScore(LinkedHashMap<String,Double> doc1, LinkedHashMap<String,Double> doc2)
+		{
+			if (doc1 == null || doc2 == null || doc1.isEmpty() || doc2.isEmpty())
+			{
+				return 0.0;
+			}
+
+			Map<String,Double> smallerDoc = doc1;
+			Map<String,Double> largerDoc = doc2;
+			if (doc2.size() < doc1.size())
+			{
+				smallerDoc = doc2;
+				largerDoc = doc1;
+			}
+
+			double dotProduct = 0.0;
+			for (Map.Entry<String,Double> entry : smallerDoc.entrySet())
+			{
+				Double largerValue = largerDoc.get(entry.getKey());
+				if (largerValue != null)
+				{
+					dotProduct += entry.getValue() * largerValue;
+				}
+			}
+			return dotProduct;
+		}
+
+		private TreeMap<String,Double> keepTopCosSimScores(TreeMap<String,Double> scores, int limit)
+		{
+			if (scores == null || limit <= 0 || scores.size() <= limit)
+			{
+				return scores;
+			}
+
+			ArrayList<Map.Entry<String,Double>> sortedScores = new ArrayList<Map.Entry<String,Double>>(scores.entrySet());
+			Collections.sort(sortedScores, new Comparator<Map.Entry<String,Double>>() {
+				public int compare(Map.Entry<String,Double> first, Map.Entry<String,Double> second)
+				{
+					int scoreCompare = Double.compare(second.getValue(), first.getValue());
+					if (scoreCompare != 0)
+					{
+						return scoreCompare;
+					}
+					return first.getKey().compareTo(second.getKey());
+				}
+			});
+
+			TreeMap<String,Double> limitedScores = new TreeMap<String,Double>();
+			for (int i = 0; i < sortedScores.size() && i < limit; i++)
+			{
+				Map.Entry<String,Double> entry = sortedScores.get(i);
+				limitedScores.put(entry.getKey(), entry.getValue());
+			}
+			return limitedScores;
+		}
+
+		private int getCosSimLocalResultLimit()
+		{
+			int requestedRecommendations = 0;
+			if (myGui != null && myGui.recommendationField != null)
+			{
+				try
+				{
+					requestedRecommendations = Integer.parseInt(myGui.recommendationField.getText().trim());
+				}
+				catch (Exception e)
+				{
+					requestedRecommendations = 0;
+				}
+			}
+
+			int bufferedLimit = requestedRecommendations * COS_SIM_LOCAL_TOP_MULTIPLIER;
+			if (bufferedLimit < COS_SIM_LOCAL_TOP_MIN_RESULTS)
+			{
+				bufferedLimit = COS_SIM_LOCAL_TOP_MIN_RESULTS;
+			}
+			return bufferedLimit;
+		}
+		
+		private int findMaxIndex(double[] array)
+		{
 			int maxIndex = 0;
 			double maxValue = array[0];
 			for (int i = 0; i < array.length; i++)
@@ -6481,4 +6859,4 @@ public class RecommenderAgent extends Agent
 		}
 	}
 
-}
\ No newline at end of file
+}
diff --git BEFORE/TwitterGatherDataFollowers/userRyersonU/StarterAgent.java AFTER/TwitterGatherDataFollowers/userRyersonU/StarterAgent.java
index e31c953..31a6613 100644
--- BEFORE/TwitterGatherDataFollowers/userRyersonU/StarterAgent.java
+++ AFTER/TwitterGatherDataFollowers/userRyersonU/StarterAgent.java
@@ -125,8 +125,9 @@ public class StarterAgent extends Agent
 	private int numberofusers=0;
 	private int numberofusers_counter=0;
 	private int queryUserCounter=0;
-	private int numberofusermessagesfromserver=0;
-	private int userCounter=0;
+	private int numberofusermessagesfromserver=0;
+	private int userCounter=0;
+	private int nextQueryUserIndex=0;
 
 
 	private int tfidfservercount=0;
@@ -157,15 +158,123 @@ public class StarterAgent extends Agent
 	private int[] messagePassingCosts; //Message passing cost bytes for each node
 	private int totalMessagePassingCost; //Total message passing cost of parallel nodes
 
-	private long kmeansMessageTime; //Message passing time for k-means results
-	private long beginKmeansMergeTime;
-	private long endKmeansMergeTime;
-	
-	protected void setup() 
-	{
-
-
-		getContentManager().registerLanguage(new SLCodec());
+	private long kmeansMessageTime; //Message passing time for k-means results
+	private long beginKmeansMergeTime;
+	private long endKmeansMergeTime;
+	private long stageSimulationStartTime;
+	private long stageStartSimBroadcastEndTime;
+	private long stageAllTweetingCompletedTime;
+	private long stageAllTextProcessingCompletedTime;
+	private long stageAlgorithmStartTime;
+	private long stageAllAlgorithmsReportedTime;
+	private long stageMergeCompletedTime;
+	private long stageQueryStartTime;
+	private long stageQueryCompletedTime;
+	
+	private void logStageDuration(String label, long startTime, long endTime)
+	{
+		if (startTime <= 0 || endTime <= 0)
+		{
+			return;
+		}
+		String line = "[StageTimer] " + label + ": " + (endTime - startTime) + " ms";
+		System.out.println(line);
+		if (myGui != null)
+		{
+			myGui.appendResult(line);
+		}
+	}
+	
+	private void logStagePoint(String label)
+	{
+		String line = "[StageTimer] " + label + " at " + LocalDateTime.now();
+		System.out.println(line);
+		if (myGui != null)
+		{
+			myGui.appendResult(line);
+		}
+	}
+
+	private boolean sendNextQueryUserRequest()
+	{
+		if (usersRec == null || nextQueryUserIndex >= usersRec.size())
+		{
+			return false;
+		}
+
+		String queryUserName = usersRec.get(nextQueryUserIndex);
+		String suffixAgentName = "-UserAgent";
+		for (int i = 0; i < allUserAgentsList.size(); i++)
+		{
+			if (allUserAgentsList.get(i).getLocalName().equals(queryUserName+suffixAgentName))
+			{
+				ACLMessage msg2 = new ACLMessage( ACLMessage.REQUEST);
+				msg2.setContent("requestedBy");
+				msg2.setOntology("Start Querying");
+				msg2.addReceiver(allUserAgentsList.get(i));
+				nextQueryUserIndex++;
+				System.out.println(getLocalName()+" "+queryUserName+suffixAgentName+" queryMessageIndex: "+nextQueryUserIndex+" of "+usersRec.size());
+				send(msg2);
+				return true;
+			}
+		}
+
+		System.out.println("ERROR: QUERY USER " + queryUserName + " NOT FOUND IN USER AGENT LIST");
+		myGui.appendResult("ERROR: QUERY USER " + queryUserName + " NOT FOUND IN USER AGENT LIST");
+		nextQueryUserIndex++;
+		return sendNextQueryUserRequest();
+	}
+
+	private ArrayList<String> filterUsersRecToAvailableUserAgents(ArrayList<String> requestedUsers)
+	{
+		ArrayList<String> availableUsers = new ArrayList<String>();
+		if (requestedUsers == null)
+		{
+			return availableUsers;
+		}
+
+		for (String requestedUser : requestedUsers)
+		{
+			if (isUserAgentAvailable(requestedUser))
+			{
+				availableUsers.add(requestedUser);
+			}
+			else
+			{
+				String line = "Skipping query for " + requestedUser + " because no active user agent exists.";
+				System.out.println(line);
+				if (myGui != null)
+				{
+					myGui.appendResult(line);
+				}
+			}
+		}
+		return availableUsers;
+	}
+
+	private boolean isUserAgentAvailable(String userName)
+	{
+		if (userName == null)
+		{
+			return false;
+		}
+		String suffixAgentName = "-UserAgent";
+		for (int i = 0; i < allUserAgentsList.size(); i++)
+		{
+			if (allUserAgentsList.get(i).getLocalName().equals(userName + suffixAgentName))
+			{
+				return true;
+			}
+		}
+		return false;
+	}
+	
+	protected void setup() 
+	{
+		long starterSetupStartTime = System.currentTimeMillis();
+
+
+		getContentManager().registerLanguage(new SLCodec());
 		getContentManager().registerOntology(JADEManagementOntology.getInstance());
 
 		Object[] args 				= getArguments();
@@ -180,9 +289,10 @@ public class StarterAgent extends Agent
 		numNodes = (Integer) args[12];
 
 
-		final int numberofuserparticipated = numberofusers;
-		
-		//@Jason checking numberofuserparticipated
+		final int numberofuserparticipated = numberofusers;
+		logStagePoint("STARTER setup started nodes="+numNodes+" expectedUsers="+numberofuserparticipated);
+		
+		//@Jason checking numberofuserparticipated
 		System.out.println("numberofuserparticipated: "+numberofuserparticipated);
 
 		try {
@@ -228,12 +338,13 @@ public class StarterAgent extends Agent
 			controllerAID = result[0].getName();
 		}
 
-		catch (FIPAException fe) {
-			fe.printStackTrace();
-		}
-
-
-
+		catch (FIPAException fe) {
+			fe.printStackTrace();
+		}
+		logStageDuration("STARTER setup DF discovery and agent list collection", starterSetupStartTime, System.currentTimeMillis());
+
+
+
 		//Set userAgentDFTemplate to look in DF for user agents
 		DFAgentDescription userAgentDFTemplate = new DFAgentDescription();
 		ServiceDescription sd = new ServiceDescription();
@@ -264,16 +375,26 @@ public class StarterAgent extends Agent
 			msg2.addReceiver(allUserAgentsList.get(i));  
 		}	  	
 
-		//@Jason added simulation start message and time
-		System.out.println("Simulation has started...");
-		final long startSimTime = System.currentTimeMillis();
-		System.out.println("The start time: "+startSimTime);
-		System.out.println("The readable start time: "+LocalDateTime.now());
-
-		msg2.setConversationId(conversationIDInitial);
-		msg2.setOntology("Start SIM");
-		send(msg2);
-		System.out.println(getLocalName()+": Tweets have started...");
+		//@Jason added simulation start message and time
+		System.out.println("Simulation has started...");
+		final long startSimTime = System.currentTimeMillis();
+		stageSimulationStartTime = startSimTime;
+		long startSimBroadcastStartTime = System.currentTimeMillis();
+		System.out.println("The start time: "+startSimTime);
+		System.out.println("The readable start time: "+LocalDateTime.now());
+
+		msg2.setConversationId(conversationIDInitial);
+		msg2.setOntology("Start SIM");
+		send(msg2);
+		stageStartSimBroadcastEndTime = System.currentTimeMillis();
+		logStageDuration("START_SIM broadcast to MobileAgents", startSimBroadcastStartTime, stageStartSimBroadcastEndTime);
+		if (isMobileAgentTweetDelayBypassEnabled())
+		{
+			String bypassLine = "[StageTimer] MobileAgent tweet-delay simulation bypass enabled; proceeding after recommender text-processing confirmation.";
+			System.out.println(bypassLine);
+			myGui.appendResult(bypassLine);
+		}
+		System.out.println(getLocalName()+": Tweets have started...");
 
 		myGui.disableList();
 
@@ -289,13 +410,15 @@ public class StarterAgent extends Agent
 			private int messagePassingCostReceived;
 			private boolean messagePassingCompleted = false;
 			
-			protected void onTick() 
-			{
-				ACLMessage msg= myAgent.receive();
-
-				if (messagePassingCostReceived == numNodes && messagePassingTimesReceived == numNodes && !messagePassingCompleted)
-				{
-					maxMessagePassingTime = 0;
+			protected void onTick() 
+			{
+				ACLMessage msg= myAgent.receive();
+				while (msg != null)
+				{
+
+				if (messagePassingCostReceived == numNodes && messagePassingTimesReceived == numNodes && !messagePassingCompleted)
+				{
+					maxMessagePassingTime = 0;
 					totalMessagePassingCost = 0;
 					// for (int i = 0; i < messagePassingTimes.length; i++)
 					for (int i = 0; i < messagePassingCosts.length; i++)
@@ -383,11 +506,13 @@ public class StarterAgent extends Agent
 					//@Jason changed to numberofuserparticipated
 					if(numberofusers_counter == numberofuserparticipated)
 						//if(numberofusers_counter == numberofusers)
-					{
-						numberofusers_counter = 0;
-						//@Jason changed to -1 to reuse counter in ontology: Querying Done from Organizing Agent below
-						alltweetsflag = true;
-						System.out.println(myAgent.getLocalName()+" TWEETING COMPLETED numberofusers: "+numberofusers);
+					{
+						numberofusers_counter = 0;
+						//@Jason changed to -1 to reuse counter in ontology: Querying Done from Organizing Agent below
+						alltweetsflag = true;
+						stageAllTweetingCompletedTime = System.currentTimeMillis();
+						logStageDuration("WAIT all MobileAgents Tweeting Completed", stageStartSimBroadcastEndTime, stageAllTweetingCompletedTime);
+						System.out.println(myAgent.getLocalName()+" TWEETING COMPLETED numberofusers: "+numberofusers);
 
 						// System.out.println("STARTER AGENT BEFORE CALCULATE MESSAGE PASSING COST TIME");
 						//Get the max message passing time from nodes if more than 1 node
@@ -509,9 +634,12 @@ public class StarterAgent extends Agent
 					System.out.println(getLocalName()+" Text Processing Completed: "+numOfRecAgentsCount);
 
 					//All recommender agents ready to cluster since text processing is completed
-					if (numOfRecAgentsCount == alltfidfserviceAgents.length){
-
-						//System.exit(0);
+					if (numOfRecAgentsCount == alltfidfserviceAgents.length){
+						stageAllTextProcessingCompletedTime = System.currentTimeMillis();
+						long textProcessingWaitStartTime = stageAllTweetingCompletedTime > 0 ? stageAllTweetingCompletedTime : stageStartSimBroadcastEndTime;
+						logStageDuration("WAIT all RecommenderAgents Text Processing Complete", textProcessingWaitStartTime, stageAllTextProcessingCompletedTime);
+
+						//System.exit(0);
 						
 						//Send show followers message before clustering
 						ACLMessage showFollowersMsg = new ACLMessage( ACLMessage.REQUEST );
@@ -530,7 +658,7 @@ public class StarterAgent extends Agent
 						send(showFollowersMsg);
 
 						int recUserInList = 0;
-						usersRec = myGui.getUsersRec();
+						usersRec = filterUsersRecToAvailableUserAgents(myGui.getUsersRec());
 
 						for (String recUser : usersRec)
 						{
@@ -546,19 +674,21 @@ public class StarterAgent extends Agent
 
 						}
 
-						if (recUserInList == usersRec.size())
-						{
-							ACLMessage startRecMsg = new ACLMessage(ACLMessage.REQUEST);
-							startRecMsg.setContent("Start Recommend Algorithms");
-							startRecMsg.setOntology("Start Recommend Algorithms");
-
-							for (int i=0; i < alltfidfserviceAgents.length; i++){
-								String recAgentToSend = "Recommender-ServiceAgent"+(i+1);
+						if (recUserInList == usersRec.size())
+						{
+							ACLMessage startRecMsg = new ACLMessage(ACLMessage.REQUEST);
+							startRecMsg.setContent("Start Recommend Algorithms");
+							startRecMsg.setOntology("Start Recommend Algorithms");
+
+							stageAlgorithmStartTime = System.currentTimeMillis();
+							for (int i=0; i < alltfidfserviceAgents.length; i++){
+								String recAgentToSend = "Recommender-ServiceAgent"+(i+1);
 								System.out.println(getLocalName()+ " Start Recommend Algorithm Msg Sent To: "+recAgentToSend);
-								startRecMsg.addReceiver(new AID(recAgentToSend,AID.ISLOCALNAME));
-								send(startRecMsg);
-							}
-						}
+								startRecMsg.addReceiver(new AID(recAgentToSend,AID.ISLOCALNAME));
+								send(startRecMsg);
+							}
+							logStageDuration("START Recommend Algorithms broadcast", stageAlgorithmStartTime, System.currentTimeMillis());
+						}
 						else
 						{
 							for (String recUser : usersRec)
@@ -582,19 +712,24 @@ public class StarterAgent extends Agent
 
 						beginKmeansMergeTime = System.nanoTime();
 					}
-					if(tfidfservercount == alltfidfserviceAgents.length)
-					{
-						tfidfservercount = 0;
-					}
-				}
-				if (msg!=null && msg.getOntology()=="Merge Lists Completed" && msg.getPerformative() == ACLMessage.INFORM)
-				{
-
-					System.out.println(getLocalName()+" received Merge Lists Completed");
-
-					endKmeansMergeTime = System.nanoTime();
-
-					if (alltfidfserviceAgents.length > 1)
+					if(tfidfservercount == alltfidfserviceAgents.length)
+					{
+						stageAllAlgorithmsReportedTime = System.currentTimeMillis();
+						logStageDuration("WAIT all RecommenderAgents Algorithm/TFIDF Done", stageAlgorithmStartTime, stageAllAlgorithmsReportedTime);
+						tfidfservercount = 0;
+					}
+				}
+				if (msg!=null && msg.getOntology()=="Merge Lists Completed" && msg.getPerformative() == ACLMessage.INFORM)
+				{
+
+					System.out.println(getLocalName()+" received Merge Lists Completed");
+
+					endKmeansMergeTime = System.nanoTime();
+					stageMergeCompletedTime = System.currentTimeMillis();
+					logStageDuration("WAIT merge completed after algorithm start", stageAlgorithmStartTime, stageMergeCompletedTime);
+					logStageDuration("WAIT merge completed after all algorithms reported", stageAllAlgorithmsReportedTime, stageMergeCompletedTime);
+
+					if (alltfidfserviceAgents.length > 1)
 					{
 						kmeansMessageTime = endKmeansMergeTime - beginKmeansMergeTime;
 						myGui.addKmeansMergeTime(kmeansMessageTime);
@@ -602,36 +737,27 @@ public class StarterAgent extends Agent
 					}
 
 					myGui.addTiming();
-					usersRec = myGui.getUsersRec();
-
-					ACLMessage msg2 = new ACLMessage( ACLMessage.REQUEST);
-					String result = "requestedBy";					
-					msg2.setContent(result);
-					msg2.setOntology("Start Querying");
-
-					//Send query to only users that are supposed to get recommendations
-
-					int queryMessageCount = 0;
-					for (int i = 0; i < allUserAgentsList.size(); i++){
-
-						String suffixAgentName = "-UserAgent";
-						for (String queryUserName : usersRec)
-						{
-							if (allUserAgentsList.get(i).getLocalName().equals(queryUserName+suffixAgentName))
-							{
-								queryMessageCount++;
-								System.out.println(getLocalName()+" "+queryUserName+suffixAgentName+" queryMessageCount: "+queryMessageCount);
-								msg2.addReceiver(allUserAgentsList.get(i));
-								send(msg2);
-							}
-						}
-						if (queryMessageCount == usersRec.size())
-							break;
-					}
-				}
+						usersRec = filterUsersRecToAvailableUserAgents(myGui.getUsersRec());
+						if (usersRec.size() == 0)
+						{
+							String line = "No active users are available for querying.";
+							System.out.println(line);
+							myGui.appendResult(line);
+							myGui.enableAllButtons();
+							myGui.enableList();
+						}
+						else
+						{
+							stageQueryStartTime = System.currentTimeMillis();
+							queryUserCounter = 0;
+							nextQueryUserIndex = 0;
+							sendNextQueryUserRequest();
+							logStageDuration("START Querying broadcast", stageQueryStartTime, System.currentTimeMillis());
+						}
+						}
 
 				//When user got its recommendation list
-				if (msg!=null && msg.getOntology() == "Querying Done from Organizing Agent" && msg.getPerformative() == ACLMessage.INFORM) 
+				if (msg!=null && "Querying Done from Organizing Agent".equals(msg.getOntology()) && msg.getPerformative() == ACLMessage.INFORM)
 				{
 					queryUserCounter++;
 					numberofusers_counter++;
@@ -655,24 +781,27 @@ public class StarterAgent extends Agent
                     // End of code added by Sepide 					
 					  
 					System.out.println("queryUserCounter: "+queryUserCounter+" usersRec.size(): "+ usersRec.size());
-					if (queryUserCounter == usersRec.size()) 				
-						//if(numberofusers_counter == numberofusers)
-					{
-						numberofusers_counter = 0;
+						if (queryUserCounter == usersRec.size()) 				
+							//if(numberofusers_counter == numberofusers)
+						{
+							numberofusers_counter = 0;
 						queryUserCounter = 0;
 
-						//@Jason timing whole execution
-						final long endSimTime = System.currentTimeMillis();
-						System.out.println("Simulation Completed");
-						System.out.println("Final Total execution time: " + (endSimTime - startSimTime) + "ms" );
-						System.out.println("Simulated ended at "+ LocalDateTime.now());
+						//@Jason timing whole execution
+						final long endSimTime = System.currentTimeMillis();
+						stageQueryCompletedTime = endSimTime;
+						logStageDuration("WAIT querying/recommendation completed", stageQueryStartTime, stageQueryCompletedTime);
+						System.out.println("Simulation Completed");
+						System.out.println("Final Total execution time: " + (endSimTime - startSimTime) + "ms" );
+							logStageDuration("SIMULATION total before graph visualization", stageSimulationStartTime, endSimTime);
+							System.out.println("Simulated ended at "+ LocalDateTime.now());
 						
 						myGui.enableAllButtons();
 						myGui.enableList();
 						//myGui.showMessageBox("finished simulation");    // commented out by Sepide
 						
-						// Code added by Sepide
-                          /*try {
+						// Code added by Sepide
+                          /*try {
 							 Runtime.getRuntime().exec("cmd /c start cmd.exe /K \"gcc -o C:\\Users\\s2baniha\\Desktop\\important-stuff\\TXT2GMLv1.0\\conver C:\\Users\\s2baniha\\Desktop\\important-stuff\\TXT2GMLv1.0\\conversion.c");
                              Runtime.getRuntime().exec("cmd /c start cmd.exe /K \"C:\\Users\\s2baniha\\Desktop\\important-stuff\\TXT2GMLv1.0\\conver C:\\Users\\s2baniha\\Desktop\\important-stuff\\edges-numbers");
 							 //Process p1 = Runtime.getRuntime().exec( "gcc -o C:\\Users\\s2baniha\\Desktop\\important-stuff\\TXT2GMLv1.0 conver C:\\Users\\s2baniha\\Desktop\\important-stuff\\TXT2GMLv1.0 conversion.c" );
@@ -683,79 +812,131 @@ public class StarterAgent extends Agent
                                ex.printStackTrace();
 							   return;
                       
-                         } */
-						
-						ProjectController pc = Lookup.getDefault().lookup(ProjectController.class);
-						pc.newProject();
-						AppearanceController appearanceController = Lookup.getDefault().lookup(AppearanceController.class);
-						AppearanceModel appearanceModel = appearanceController.getModel();
-						ImportController importController = Lookup.getDefault().lookup(ImportController.class);
-						GraphModel graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel();
-						Workspace workspace = pc.getCurrentWorkspace();
-						Container container;
-						
-						String importantStuffDirName = "important-stuff/";
-						File importantStuffDir = new File(importantStuffDirName);
-						if (!importantStuffDir.exists())
-						{
-								importantStuffDir.mkdirs();
-						}
-						
-						try {
-                               //File file = new File(getClass().getResource("C:\\Users\\Sepide\\Desktop\\project2\\94k_after2runSimulation.gml").toURI());
-                               //Process p = Runtime.getRuntime().exec("C:/Users/s2baniha/Desktop/important-stuff/TXT2GMLv1.0/conver C:/Users/s2baniha/Desktop/important-stuff/edges-numbers");  
-							   File file = new File(importantStuffDirName +"edges-numbers.gml");
-							   //System.out.println("the gml file gets inputted in Gephi Software");
-							   container = importController.importFile(file);
-                               container.getLoader().setEdgeDefault(EdgeDirectionDefault.UNDIRECTED);   //Force DIRECTED
-                               container.getLoader().setAllowAutoNode(false);  //Don't create missing nodes
-                              } catch (Exception ex) {
-                               ex.printStackTrace();
-							   return;
-                      
-                        }
-						
-						     //Append imported data to GraphAPI
-                             importController.process(container, new DefaultProcessor(), workspace);
-							 
-					         //See if graph is well imported
+                         } */
+						
+						long graphVisualizationStartTime = System.currentTimeMillis();
+						logStagePoint("GRAPH visualization started");
+						String importantStuffDirName = "important-stuff/";
+						File importantStuffDir = new File(importantStuffDirName);
+						if (!importantStuffDir.exists())
+						{
+								importantStuffDir.mkdirs();
+						}
+
+						File graphFile;
+						long graphBuildStartTime = System.currentTimeMillis();
+						try {
+							GraphGmlBuilder.Result graphBuild = buildCompletedGraphFile(importantStuffDir);
+							graphFile = graphBuild.getOutputFile();
+							System.out.println("Built graph file with " + graphBuild.getNodeCount() + " nodes and "
+									+ graphBuild.getEdgeCount() + " edges: " + graphFile.getPath());
+						}
+						catch (Exception ex) {
+							reportGraphVisualizationFailure("could not build the completed graph file", ex);
+							return;
+						}
+						logStageDuration("GRAPH build validated GML file", graphBuildStartTime, System.currentTimeMillis());
+
+						ProjectController pc;
+						AppearanceController appearanceController;
+						AppearanceModel appearanceModel;
+						ImportController importController;
+						GraphModel graphModel;
+						Workspace workspace;
+						Container container;
+						long graphSetupStartTime = System.currentTimeMillis();
+						try {
+							pc = requireGraphComponent(
+									Lookup.getDefault().lookup(ProjectController.class), "ProjectController");
+							pc.newProject();
+							appearanceController = requireGraphComponent(
+									Lookup.getDefault().lookup(AppearanceController.class), "AppearanceController");
+							appearanceModel = requireGraphComponent(appearanceController.getModel(), "AppearanceModel");
+							importController = requireGraphComponent(
+									Lookup.getDefault().lookup(ImportController.class), "ImportController");
+							GraphController graphController = requireGraphComponent(
+									Lookup.getDefault().lookup(GraphController.class), "GraphController");
+							graphModel = requireGraphComponent(graphController.getGraphModel(), "GraphModel");
+							workspace = requireGraphComponent(pc.getCurrentWorkspace(), "Workspace");
+						}
+						catch (Exception ex) {
+							reportGraphVisualizationFailure("could not initialize Gephi", ex);
+							return;
+						}
+						logStageDuration("GRAPH initialize Gephi controllers/workspace", graphSetupStartTime, System.currentTimeMillis());
+
+						long graphImportStartTime = System.currentTimeMillis();
+						try {
+							System.out.println("Importing graph file: " + graphFile.getPath());
+							container = importController.importFile(graphFile);
+							if (container == null) {
+								throw new IOException("Gephi returned no import container for " + graphFile.getPath());
+							}
+							if (container.getLoader() == null) {
+								throw new IOException("Gephi returned an import container without a loader for "
+										+ graphFile.getPath());
+							}
+							container.getLoader().setEdgeDefault(EdgeDirectionDefault.UNDIRECTED);
+							container.getLoader().setAllowAutoNode(false);
+						}
+						catch (Exception ex) {
+							reportGraphVisualizationFailure("Gephi could not import the completed graph file", ex);
+							return;
+						}
+						logStageDuration("GRAPH import GML file", graphImportStartTime, System.currentTimeMillis());
+						
+						     //Append imported data to GraphAPI
+                             long graphProcessStartTime = System.currentTimeMillis();
+                             importController.process(container, new DefaultProcessor(), workspace);
+							 logStageDuration("GRAPH process imported container", graphProcessStartTime, System.currentTimeMillis());
+							 
+					         //See if graph is well imported
                              //UndirectedGraph graph = graphModel.getUndirectedGraph();
 							 DirectedGraph graph = graphModel.getDirectedGraph();
                              System.out.println("Nodes: " + graph.getNodeCount());
                              System.out.println("Edges: " + graph.getEdgeCount());
-							 
-							 //Run modularity algorithm - community detection
-                             Modularity modularity = new Modularity();
-							 modularity.setResolution(1.0);
-                             modularity.execute(graphModel);
-							 
-							//Partition with 'modularity_class', just created by Modularity algorithm
-                            Column modColumn = graphModel.getNodeTable().getColumn(Modularity.MODULARITY_CLASS);
+							 
+							 //Run modularity algorithm - community detection
+                             long graphModularityStartTime = System.currentTimeMillis();
+                             Modularity modularity = new Modularity();
+							 modularity.setResolution(1.0);
+                             modularity.execute(graphModel);
+							 logStageDuration("GRAPH modularity calculation", graphModularityStartTime, System.currentTimeMillis());
+							 
+							//Partition with 'modularity_class', just created by Modularity algorithm
+                            long graphPartitionStartTime = System.currentTimeMillis();
+                            Column modColumn = graphModel.getNodeTable().getColumn(Modularity.MODULARITY_CLASS);
                             Function func2 = appearanceModel.getNodeFunction(graph, modColumn, PartitionElementColorTransformer.class);
                             Partition partition2 = ((PartitionFunction) func2).getPartition();
                             System.out.println(partition2.size() + " partitions found");
                             Palette palette2 = PaletteManager.getInstance().randomPalette(partition2.size());
-                            partition2.setColors(palette2.getColors());
-                            appearanceController.transform(func2);
-							
-							//Preview 
-							PreviewModel model = Lookup.getDefault().lookup(PreviewController.class).getModel();
+                            partition2.setColors(palette2.getColors());
+                            appearanceController.transform(func2);
+							logStageDuration("GRAPH partition/color transform", graphPartitionStartTime, System.currentTimeMillis());
+							
+							//Preview 
+							long graphPreviewStartTime = System.currentTimeMillis();
+							PreviewModel model = Lookup.getDefault().lookup(PreviewController.class).getModel();
                             model.getProperties().putValue(PreviewProperty.SHOW_NODE_LABELS, Boolean.TRUE);
 							model.getProperties().putValue(PreviewProperty.NODE_LABEL_FONT, model.getProperties().getFontValue(PreviewProperty.NODE_LABEL_FONT).deriveFont(8));
-							model.getProperties().putValue(PreviewProperty.NODE_LABEL_PROPORTIONAL_SIZE, Boolean.FALSE);
-							model.getProperties().putValue(PreviewProperty.NODE_LABEL_OUTLINE_SIZE, 12);
-							
-							//Export
-                            ExportController ec = Lookup.getDefault().lookup(ExportController.class);
-                              try {
-                                ec.exportFile(new File(importantStuffDirName+"partition.pdf"));
+							model.getProperties().putValue(PreviewProperty.NODE_LABEL_PROPORTIONAL_SIZE, Boolean.FALSE);
+							model.getProperties().putValue(PreviewProperty.NODE_LABEL_OUTLINE_SIZE, 12);
+							logStageDuration("GRAPH configure preview", graphPreviewStartTime, System.currentTimeMillis());
+							
+							//Export
+                            long graphPartitionExportStartTime = System.currentTimeMillis();
+                            ExportController ec = Lookup.getDefault().lookup(ExportController.class);
+                              try {
+                                ec.exportFile(new File(importantStuffDirName+"partition.pdf"));
                                 } catch (IOException ex) {
-                                   ex.printStackTrace();
-                                   return;
-                                }
-								
-								//Layout for 1 minute
-								AutoLayout autoLayout = new AutoLayout(1, TimeUnit.MINUTES);
+                                   ex.printStackTrace();
+                                   return;
+                                }
+							  logStageDuration("GRAPH export partition PDF", graphPartitionExportStartTime, System.currentTimeMillis());
+								
+								//Layout for 1 minute
+								long graphLayoutStartTime = System.currentTimeMillis();
+								AutoLayout autoLayout = new AutoLayout(1, TimeUnit.MINUTES);
                                 autoLayout.setGraphModel(graphModel);
 								//YifanHuLayout secondLayout = new YifanHuLayout(null, new StepDisplacement(1f));
 								ForceAtlasLayout firstLayout = new ForceAtlasLayout(null);
@@ -776,32 +957,38 @@ public class StarterAgent extends Agent
 								//AutoLayout.DynamicProperty preventOver = AutoLayout.createDynamicProperty("forceAtlas2.preventOverlap.namePreventOverlap", Boolean.FALSE, 0f);
 								//autoLayout.addLayout(secondLayout, 1f,new AutoLayout.DynamicProperty[]{preventOver});
 								AutoLayout.DynamicProperty adjustBySizeProperty = AutoLayout.createDynamicProperty("ForceAtlas2.adjustSizes.name",Boolean.TRUE,0f);//True for the complete period
-                                AutoLayout.DynamicProperty distrAttraction = AutoLayout.createDynamicProperty("ForceAtlas2.distributedAttraction.name",Boolean.TRUE,0f);//True for the complete period
-                                autoLayout.addLayout(secondLayout, 1.0f, new AutoLayout.DynamicProperty[]{adjustBySizeProperty,distrAttraction});
-								//autoLayout.addLayout(secondLayout, 1.0f);
-								autoLayout.execute();
-								
-								//Export full graph
-								ExportController ec3 = Lookup.getDefault().lookup(ExportController.class);
-                                 try {
-                                       ec3.exportFile(new File(importantStuffDirName+"graph.gexf"));
+								AutoLayout.DynamicProperty distrAttraction = AutoLayout.createDynamicProperty("ForceAtlas2.distributedAttraction.name",Boolean.TRUE,0f);//True for the complete period
+                                autoLayout.addLayout(secondLayout, 1.0f, new AutoLayout.DynamicProperty[]{adjustBySizeProperty,distrAttraction});
+								//autoLayout.addLayout(secondLayout, 1.0f);
+								autoLayout.execute();
+								logStageDuration("GRAPH AutoLayout execute", graphLayoutStartTime, System.currentTimeMillis());
+								
+								//Export full graph
+								long graphGexfExportStartTime = System.currentTimeMillis();
+								ExportController ec3 = Lookup.getDefault().lookup(ExportController.class);
+                                 try {
+                                       ec3.exportFile(new File(importantStuffDirName+"graph.gexf"));
                                    } catch (IOException ex) {
                                        ex.printStackTrace();
-                                     return;
-									}
-								
-								
-								
-								//Export
-                                  ExportController ec2 = Lookup.getDefault().lookup(ExportController.class);
-                                  try {
-                                   ec2.exportFile(new File(importantStuffDirName+"autolayout.pdf"));
-                                    } catch (IOException ex) {
-                                     ex.printStackTrace();
-                                     }
-									 
-									 try {
-										 PDDocument document = PDDocument.load(new File(importantStuffDirName + "autolayout.pdf"));
+                                     return;
+									}
+								logStageDuration("GRAPH export GEXF", graphGexfExportStartTime, System.currentTimeMillis());
+								
+								
+								
+								//Export
+                                  long graphAutolayoutExportStartTime = System.currentTimeMillis();
+                                  ExportController ec2 = Lookup.getDefault().lookup(ExportController.class);
+                                  try {
+                                   ec2.exportFile(new File(importantStuffDirName+"autolayout.pdf"));
+                                    } catch (IOException ex) {
+                                     ex.printStackTrace();
+                                     }
+								  logStageDuration("GRAPH export autolayout PDF", graphAutolayoutExportStartTime, System.currentTimeMillis());
+									 
+									 long graphRenderImageStartTime = System.currentTimeMillis();
+									 try {
+										 PDDocument document = PDDocument.load(new File(importantStuffDirName + "autolayout.pdf"));
 										 PDFRenderer pdfRenderer = new PDFRenderer(document);
 
 										 try {
@@ -826,11 +1013,12 @@ public class StarterAgent extends Agent
 //                                         }
 //                                        pdf.close();
 									 }
-									 catch (Exception e) {
-                                                   System.out.println("ERROR: " + e.getMessage());
-                                            }
-																		
-									 
+									 catch (Exception e) {
+                                                   System.out.println("ERROR: " + e.getMessage());
+                                            }
+									 logStageDuration("GRAPH render layout image", graphRenderImageStartTime, System.currentTimeMillis());
+																		
+									 
 									 /* try {
 										 PDFDocument document = new PDFDocument();
 										 document.load(new File("C:\\Users\\s2baniha\\Desktop\\important-stuff\\autolayout.pdf"));
@@ -854,23 +1042,26 @@ public class StarterAgent extends Agent
                                            System.out.println("ERROR: " + e.getMessage());
                                     }  */
 									
-									 try  
-	                                 {  
-	                                   //constructor of file class having file as argument  
-	                                   File file = new File(importantStuffDirName+"layout.jpg");   
+									 try  
+	                                 {  
+	                                   long graphOpenImageStartTime = System.currentTimeMillis();
+	                                   //constructor of file class having file as argument  
+	                                   File file = new File(importantStuffDirName+"layout.jpg");   
 	                                   if(!Desktop.isDesktopSupported())//check if Desktop is supported by Platform or not  
 	                                     {  
 	                                             System.out.println("not supported");  
 	                                             return;  
 	                                     }  
-	                                  Desktop desktop = Desktop.getDesktop();  
-	                                  if(file.exists())         //checks file exists or not  
-	                                  desktop.open(file);              //opens the specified file  
-	                                   }  
-	                                catch(Exception e)  
-	                                 {  
-	                                       e.printStackTrace();  
-	                                }
+	                                  Desktop desktop = Desktop.getDesktop();  
+	                                  if(file.exists())         //checks file exists or not  
+	                                  desktop.open(file);              //opens the specified file  
+									  logStageDuration("GRAPH open layout image", graphOpenImageStartTime, System.currentTimeMillis());
+	                                   }  
+	                                catch(Exception e)  
+	                                 {  
+	                                       e.printStackTrace();  
+	                                }
+						logStageDuration("GRAPH visualization total", graphVisualizationStartTime, System.currentTimeMillis());
 						
 						// End of code added by Sepide 
 						
@@ -897,23 +1088,90 @@ public class StarterAgent extends Agent
 						
 						//@Jason added exit to save CPU from running forever doing nothing and causing an early death for the CPU's lifespan
 						//System.exit(0);
-
-
-					}
-				}
-				
-				block();
-			}
-		});
-
-
-	}
-
-
-	protected void takeDown() {
-		try {
-			DFService.deregister(this);
-			System.out.println(getLocalName()+" DEREGISTERED WITH THE DF");
+						}
+						else
+						{
+							sendNextQueryUserRequest();
+						}
+					}
+					
+					if (!isMobileAgentTweetDelayBypassEnabled())
+					{
+						break;
+					}
+					msg = myAgent.receive();
+				}
+				
+				block();
+			}
+		});
+
+
+	}
+
+
+	private GraphGmlBuilder.Result buildCompletedGraphFile(File importantStuffDir) throws IOException {
+		if (myGui == null || myGui.fileChooser == null || myGui.fileChooser.getSelectedFile() == null) {
+			throw new IOException("No selected dataset is available for graph generation");
+		}
+
+		File selectedDataset = myGui.fileChooser.getSelectedFile();
+		String graphEdgesBaseName = getDatasetGraphBaseName(selectedDataset.getName());
+		File edgeListFile = new File(importantStuffDir, graphEdgesBaseName + ".txt");
+		File nameNumberFile = new File(importantStuffDir, "name-number-" + selectedDataset.getName());
+		File graphFile = new File(importantStuffDir, graphEdgesBaseName + ".gml");
+		return GraphGmlBuilder.build(edgeListFile, nameNumberFile, graphFile);
+	}
+
+	private <T> T requireGraphComponent(T component, String componentName) {
+		if (component == null) {
+			throw new IllegalStateException("Gephi component is unavailable: " + componentName);
+		}
+		return component;
+	}
+
+	private void reportGraphVisualizationFailure(String reason, Exception exception) {
+		String message = "GRAPH visualization skipped: " + reason + ". "
+				+ (exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
+		System.err.println(message);
+		exception.printStackTrace();
+		if (myGui != null) {
+			myGui.appendResult(message);
+			myGui.showMessageBox("finished simulation");
+		}
+	}
+
+
+	private boolean isMobileAgentTweetDelayBypassEnabled() {
+		return myGui != null && !myGui.isSimulateTweetDelayEnabled();
+	}
+
+
+	private String getGraphEdgesBaseName() {
+		if (myGui != null && myGui.fileChooser != null && myGui.fileChooser.getSelectedFile() != null) {
+			return getDatasetGraphBaseName(myGui.fileChooser.getSelectedFile().getName());
+		}
+		return "edges-numbers";
+	}
+
+	private String getDatasetGraphBaseName(String selectedFileName) {
+		String datasetName = selectedFileName;
+		int extensionIndex = datasetName.lastIndexOf('.');
+		if (extensionIndex > 0) {
+			datasetName = datasetName.substring(0, extensionIndex);
+		}
+		datasetName = datasetName.replaceAll("[^A-Za-z0-9._-]", "_");
+		if (datasetName.length() == 0) {
+			datasetName = "uploaded-dataset";
+		}
+		return "edges-numbers-" + datasetName;
+	}
+
+
+	protected void takeDown() {
+		try {
+			DFService.deregister(this);
+			System.out.println(getLocalName()+" DEREGISTERED WITH THE DF");
 			//doDelete();
 		} catch (FIPAException e) {
 			e.printStackTrace();
diff --git BEFORE/TwitterGatherDataFollowers/userRyersonU/SvmReproducibility.java AFTER/TwitterGatherDataFollowers/userRyersonU/SvmReproducibility.java
new file mode 100644
index 0000000..cf7e178
--- /dev/null
+++ AFTER/TwitterGatherDataFollowers/userRyersonU/SvmReproducibility.java
@@ -0,0 +1,105 @@
+package TwitterGatherDataFollowers.userRyersonU;
+
+import java.util.ArrayList;
+import java.util.Collection;
+import java.util.Collections;
+import java.util.List;
+import java.util.Map;
+import java.util.Random;
+
+import weka.classifiers.functions.SMO;
+
+final class SvmReproducibility {
+	static final long SPLIT_SEED = 1L;
+	static final int MODEL_SEED = 1;
+
+	private SvmReproducibility() {
+	}
+
+	static Split stratifiedSplit(Map<String,? extends List<String>> usersByClass, double testSetPercent) {
+		if (testSetPercent < 0.0 || testSetPercent >= 1.0) {
+			throw new IllegalArgumentException("testSetPercent must be at least 0.0 and less than 1.0");
+		}
+
+		ArrayList<String> trainUsers = new ArrayList<String>();
+		ArrayList<String> testUsers = new ArrayList<String>();
+		if (usersByClass == null || usersByClass.isEmpty()) {
+			return new Split(trainUsers, testUsers);
+		}
+
+		for (String className : sortedNonNullValues(usersByClass.keySet())) {
+			List<String> sourceUsers = usersByClass.get(className);
+			if (sourceUsers == null || sourceUsers.isEmpty()) {
+				continue;
+			}
+
+			ArrayList<String> classUsers = sortedNonNullValues(sourceUsers);
+			Collections.shuffle(classUsers, new Random(seedForClass(className)));
+			int classTestUsers = getStratifiedClassTestCount(classUsers.size(), testSetPercent);
+			int classTrainUsers = classUsers.size() - classTestUsers;
+			trainUsers.addAll(classUsers.subList(0, classTrainUsers));
+			testUsers.addAll(classUsers.subList(classTrainUsers, classUsers.size()));
+		}
+
+		if (trainUsers.isEmpty() && testUsers.size() > 1) {
+			trainUsers.add(testUsers.remove(testUsers.size() - 1));
+		}
+		return new Split(trainUsers, testUsers);
+	}
+
+	static ArrayList<String> sortedNonNullValues(Collection<String> values) {
+		ArrayList<String> sortedValues = new ArrayList<String>();
+		if (values != null) {
+			for (String value : values) {
+				if (value != null) {
+					sortedValues.add(value);
+				}
+			}
+		}
+		Collections.sort(sortedValues);
+		return sortedValues;
+	}
+
+	static void applyModelSeed(SMO svmModel) {
+		if (svmModel == null) {
+			throw new IllegalArgumentException("svmModel must not be null");
+		}
+		svmModel.setRandomSeed(MODEL_SEED);
+	}
+
+	private static int getStratifiedClassTestCount(int classSize, double testSetPercent) {
+		if (classSize <= 1) {
+			return 0;
+		}
+		int classTestUsers = (int)Math.floor(classSize * testSetPercent);
+		if (classTestUsers < 1) {
+			classTestUsers = 1;
+		}
+		if (classTestUsers >= classSize) {
+			classTestUsers = classSize - 1;
+		}
+		return classTestUsers;
+	}
+
+	private static long seedForClass(String className) {
+		return SPLIT_SEED ^ className.hashCode();
+	}
+
+	static final class Split {
+		private final ArrayList<String> trainUsers;
+		private final ArrayList<String> testUsers;
+
+		private Split(ArrayList<String> trainUsers, ArrayList<String> testUsers) {
+			this.trainUsers = trainUsers;
+			this.testUsers = testUsers;
+		}
+
+		ArrayList<String> getTrainUsers() {
+			return new ArrayList<String>(trainUsers);
+		}
+
+		ArrayList<String> getTestUsers() {
+			return new ArrayList<String>(testUsers);
+		}
+	}
+}
diff --git BEFORE/TwitterGatherDataFollowers/userRyersonU/doc2vec_batch.py AFTER/TwitterGatherDataFollowers/userRyersonU/doc2vec_batch.py
new file mode 100644
index 0000000..929d962
--- /dev/null
+++ AFTER/TwitterGatherDataFollowers/userRyersonU/doc2vec_batch.py
@@ -0,0 +1,107 @@
+import base64
+import sys
+import time
+
+from doc2vec_shared import Doc2Vec, ensure_shared_model, score_candidate_shard
+
+
+SCORE_PREFIX = "DOC2VEC_SCORE"
+ERROR_PREFIX = "DOC2VEC_ERROR"
+DONE_PREFIX = "DOC2VEC_DONE"
+
+
+def usage():
+    print(
+        "usage: doc2vec_batch.py <topn> <dataset_file> <node_number> "
+        "<num_nodes> <workers> <users_file>",
+        file=sys.stderr,
+    )
+
+
+def parse_args():
+    if len(sys.argv) != 7:
+        usage()
+        sys.exit(2)
+
+    topn = max(int(sys.argv[1]), 1)
+    dataset_file = sys.argv[2]
+    node_number = max(int(sys.argv[3]), 1)
+    num_nodes = max(int(sys.argv[4]), 1)
+    workers = max(int(sys.argv[5]), 1)
+    users_file = sys.argv[6]
+
+    if node_number > num_nodes:
+        node_number = ((node_number - 1) % num_nodes) + 1
+
+    with open(users_file, "r", encoding="utf-8") as input_file:
+        users = [line.rstrip("\r\n") for line in input_file]
+    users = [user for user in users if user]
+    if not users:
+        raise RuntimeError("No users were supplied for Doc2Vec batch scoring")
+
+    return topn, dataset_file, node_number, num_nodes, workers, users
+
+
+def encode_field(value):
+    return base64.b64encode(str(value).encode("utf-8")).decode("ascii")
+
+
+def emit_score(user, candidate, score):
+    print(
+        "%s\t%s\t%s\t%s"
+        % (SCORE_PREFIX, encode_field(user), encode_field(candidate), repr(float(score)))
+    )
+
+
+def emit_error(user, error):
+    message = str(error).replace("\r", " ").replace("\n", " ")
+    print("%s\t%s\t%s" % (ERROR_PREFIX, encode_field(user), encode_field(message)))
+
+
+def emit_done(user):
+    print("%s\t%s" % (DONE_PREFIX, encode_field(user)))
+
+
+def fallback_candidate_shard(model, user, topn, node_number, num_nodes):
+    query_index = model.dv.key_to_index.get(user)
+    node_offset = node_number - 1
+    candidates = []
+    for index, tag in enumerate(model.dv.index_to_key):
+        if query_index is not None and index == query_index:
+            continue
+        if num_nodes > 1 and (index % num_nodes) != node_offset:
+            continue
+        candidates.append((tag, -1.0))
+        if len(candidates) >= topn:
+            break
+    return candidates
+
+
+def main():
+    start_time = time.time()
+    topn, dataset_file, node_number, num_nodes, workers, users = parse_args()
+    print(
+        "batch model config: users=%s topn=%s node=%s/%s workers=%s"
+        % (len(users), topn, node_number, num_nodes, workers)
+    )
+
+    model_path = ensure_shared_model(dataset_file, workers)
+    model = Doc2Vec.load(model_path)
+
+    for user in users:
+        try:
+            results = score_candidate_shard(model, user, topn, node_number, num_nodes)
+        except Exception as error:
+            emit_error(user, error)
+            results = fallback_candidate_shard(model, user, topn, node_number, num_nodes)
+
+        for candidate, score in results:
+            emit_score(user, candidate, score)
+        emit_done(user)
+
+    completion_time_ms = (time.time() - start_time) * 1000.0
+    print("completionTime", completion_time_ms)
+
+
+if __name__ == "__main__":
+    main()
diff --git BEFORE/TwitterGatherDataFollowers/userRyersonU/doc2vec_shared.py AFTER/TwitterGatherDataFollowers/userRyersonU/doc2vec_shared.py
new file mode 100644
index 0000000..d8b63de
--- /dev/null
+++ AFTER/TwitterGatherDataFollowers/userRyersonU/doc2vec_shared.py
@@ -0,0 +1,319 @@
+import csv
+import hashlib
+import json
+import math
+import os
+import pickle
+import sys
+import time
+
+os.environ.setdefault("OPENBLAS_NUM_THREADS", "1")
+os.environ.setdefault("OMP_NUM_THREADS", "1")
+os.environ.setdefault("MKL_NUM_THREADS", "1")
+
+import numpy as np
+import pandas as pd
+from gensim.models import Doc2Vec
+from gensim.models.doc2vec import TaggedDocument
+from gensim.parsing.preprocessing import STOPWORDS as gensim_stop_words
+from gensim.utils import simple_preprocess
+from nltk.stem.lancaster import LancasterStemmer
+from sklearn.feature_extraction import text
+
+
+MODEL_DIR = "Stored_Doc2VecModel"
+CACHE_DIR = os.path.join(MODEL_DIR, "shared_cache")
+DATASET_CACHE_DIR = os.path.join("Dataset", "424k")
+
+MODEL_PARAMS = {
+    "dm": 0,
+    "dbow_words": 1,
+    "min_count": 4,
+    "negative": 3,
+    "hs": 0,
+    "sample": 1e-4,
+    "window": 5,
+    "vector_size": 300,
+    "epochs": 20,
+}
+
+
+def usage():
+    print(
+        "usage: doc2vec_shared.py <user> <topn> <dataset_file> <node_number> <num_nodes> [workers]",
+        file=sys.stderr,
+    )
+
+
+def parse_args():
+    if len(sys.argv) < 6:
+        usage()
+        sys.exit(2)
+
+    user = str(sys.argv[1])
+    topn = int(sys.argv[2])
+    dataset_file = sys.argv[3]
+    node_number = int(sys.argv[4])
+    num_nodes = int(sys.argv[5])
+    if len(sys.argv) > 6:
+        workers = int(sys.argv[6])
+    else:
+        cpu_count = os.cpu_count() or 4
+        workers = min(max(cpu_count - 1, 1), 12)
+
+    if topn < 1:
+        topn = 1
+    if node_number < 1:
+        node_number = 1
+    if num_nodes < 1:
+        num_nodes = 1
+    if node_number > num_nodes:
+        node_number = ((node_number - 1) % num_nodes) + 1
+    if workers < 1:
+        workers = 1
+
+    return user, topn, dataset_file, node_number, num_nodes, workers
+
+
+def dataset_signature(dataset_file, workers):
+    absolute_path = os.path.abspath(dataset_file)
+    stat_info = os.stat(absolute_path)
+    signature = {
+        "dataset_path": absolute_path,
+        "dataset_size": stat_info.st_size,
+        "dataset_mtime_ns": stat_info.st_mtime_ns,
+        "model_params": MODEL_PARAMS,
+        "workers": workers,
+        "script_version": 1,
+    }
+    encoded = json.dumps(signature, sort_keys=True).encode("utf-8")
+    cache_key = hashlib.sha256(encoded).hexdigest()[:24]
+    return cache_key, signature
+
+
+def read_dataset_rows(dataset_file):
+    rows = []
+    with open(dataset_file, "r", encoding="utf-8", errors="ignore", newline="") as input_file:
+        reader = csv.reader(input_file, delimiter="\t")
+        for fields in reader:
+            if len(fields) >= 6:
+                tweet_text = fields[5]
+                follower = fields[4]
+            elif len(fields) >= 2:
+                tweet_text = fields[0]
+                follower = fields[1]
+            else:
+                continue
+
+            if tweet_text is None or follower is None:
+                continue
+
+            tweet_text = str(tweet_text).strip()
+            follower = str(follower).strip()
+            if tweet_text and follower:
+                rows.append((tweet_text, follower))
+    return rows
+
+
+def make_stop_words():
+    letters = list("abcdefghijklmnopqrstuvwxyz")
+    numbers = list("0123456789")
+    words = [
+        "a",
+        "able",
+        "about",
+        "above",
+        "abst",
+        "accordance",
+        "according",
+        "actually",
+        "added",
+        "affected",
+        "affecting",
+        "another",
+        "anybody",
+        "anyhow",
+        "around",
+        "back",
+        "became",
+        "because",
+        "become",
+        "beginnings",
+        "cant",
+    ]
+    stopwords = gensim_stop_words.union(set(letters)).union(set(numbers)).union(set(words))
+    return text.ENGLISH_STOP_WORDS.union(stopwords)
+
+
+def preprocess_text(tweet_text, stop_words, stemmer):
+    simple = simple_preprocess(tweet_text)
+    return [stemmer.stem(word) for word in simple if word not in stop_words]
+
+
+def build_tagged_documents(rows):
+    stop_words = make_stop_words()
+    stemmer = LancasterStemmer()
+    tagged_docs = []
+    for tweet_text, follower in rows:
+        words = preprocess_text(tweet_text, stop_words, stemmer)
+        tagged_docs.append(TaggedDocument(words=words, tags=[str(follower)]))
+    return tagged_docs
+
+
+def write_dataset_cache(rows, cache_key):
+    os.makedirs(DATASET_CACHE_DIR, exist_ok=True)
+    csv_path = os.path.join(DATASET_CACHE_DIR, "shared_doc2vec_" + cache_key + ".csv")
+    pickle_path = os.path.join(DATASET_CACHE_DIR, "shared_doc2vec_" + cache_key + ".pkl")
+    lookup_path = os.path.join(DATASET_CACHE_DIR, "shared_doc2vec_" + cache_key + "_lookup_dict.pickle")
+
+    df = pd.DataFrame(rows, columns=["Tweets", "Follower"])
+    df.to_csv(csv_path, index=False)
+    df.to_pickle(pickle_path)
+
+    lookup_df = df[["Tweets", "Follower"]].copy()
+    lookup_df["Follower"] = lookup_df["Follower"].astype(str)
+    lookup_df = lookup_df.set_index("Follower")
+    lookup_df = lookup_df.loc[~lookup_df.index.duplicated(keep="first")]
+    with open(lookup_path, "wb") as output_file:
+        pickle.dump(lookup_df.to_dict(orient="index"), output_file, pickle.HIGHEST_PROTOCOL)
+
+
+def valid_cached_model(model_path, meta_path, expected_signature):
+    if not os.path.exists(model_path) or not os.path.exists(meta_path):
+        return False
+    try:
+        with open(meta_path, "r", encoding="utf-8") as meta_file:
+            actual_signature = json.load(meta_file)
+        return actual_signature == expected_signature
+    except Exception:
+        return False
+
+
+def acquire_lock(lock_path):
+    try:
+        fd = os.open(lock_path, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
+        with os.fdopen(fd, "w", encoding="utf-8") as lock_file:
+            lock_file.write(str(os.getpid()))
+        return True
+    except FileExistsError:
+        return False
+
+
+def wait_for_model(model_path, meta_path, expected_signature, lock_path):
+    last_message_time = 0.0
+    while True:
+        if valid_cached_model(model_path, meta_path, expected_signature):
+            return
+
+        if not os.path.exists(lock_path):
+            return
+
+        now = time.time()
+        if now - last_message_time > 30:
+            print("model wait: another Doc2Vec node is training the shared model")
+            last_message_time = now
+        time.sleep(2)
+
+
+def train_shared_model(dataset_file, cache_key, signature, model_path, meta_path, workers):
+    print("model train: building shared Doc2Vec model")
+    rows = read_dataset_rows(dataset_file)
+    if not rows:
+        raise RuntimeError("No usable rows found in dataset: " + dataset_file)
+
+    write_dataset_cache(rows, cache_key)
+    tagged_docs = build_tagged_documents(rows)
+
+    model = Doc2Vec(
+        dm=MODEL_PARAMS["dm"],
+        dbow_words=MODEL_PARAMS["dbow_words"],
+        min_count=MODEL_PARAMS["min_count"],
+        negative=MODEL_PARAMS["negative"],
+        hs=MODEL_PARAMS["hs"],
+        sample=MODEL_PARAMS["sample"],
+        window=MODEL_PARAMS["window"],
+        vector_size=MODEL_PARAMS["vector_size"],
+        workers=workers,
+    )
+    model.build_vocab(tagged_docs, progress_per=100)
+    model.train(tagged_docs, total_examples=model.corpus_count, epochs=MODEL_PARAMS["epochs"])
+    model.save(model_path)
+    with open(meta_path, "w", encoding="utf-8") as meta_file:
+        json.dump(signature, meta_file, sort_keys=True, indent=2)
+    print("model train: shared Doc2Vec model saved")
+
+
+def ensure_shared_model(dataset_file, workers):
+    os.makedirs(CACHE_DIR, exist_ok=True)
+    cache_key, signature = dataset_signature(dataset_file, workers)
+    model_path = os.path.join(CACHE_DIR, "shared_" + cache_key + ".model")
+    meta_path = os.path.join(CACHE_DIR, "shared_" + cache_key + ".json")
+    lock_path = os.path.join(CACHE_DIR, "shared_" + cache_key + ".lock")
+
+    if valid_cached_model(model_path, meta_path, signature):
+        print("model cache: using existing shared Doc2Vec model")
+        return model_path
+
+    if acquire_lock(lock_path):
+        try:
+            if not valid_cached_model(model_path, meta_path, signature):
+                train_shared_model(dataset_file, cache_key, signature, model_path, meta_path, workers)
+        finally:
+            try:
+                os.remove(lock_path)
+            except OSError:
+                pass
+    else:
+        wait_for_model(model_path, meta_path, signature, lock_path)
+        if not valid_cached_model(model_path, meta_path, signature):
+            return ensure_shared_model(dataset_file, workers)
+
+    return model_path
+
+
+def score_candidate_shard(model, user, topn, node_number, num_nodes):
+    if user not in model.dv.key_to_index:
+        raise RuntimeError("User is not present in Doc2Vec model: " + user)
+
+    query_index = model.dv.key_to_index[user]
+    normed_vectors = model.dv.get_normed_vectors()
+    query_vector = normed_vectors[query_index]
+
+    node_offset = node_number - 1
+    scored_candidates = []
+    for index, tag in enumerate(model.dv.index_to_key):
+        if index == query_index:
+            continue
+        if num_nodes > 1 and (index % num_nodes) != node_offset:
+            continue
+
+        score = float(np.dot(normed_vectors[index], query_vector))
+        if math.isnan(score) or math.isinf(score):
+            continue
+        scored_candidates.append((tag, score))
+
+    scored_candidates.sort(key=lambda item: item[1], reverse=True)
+    return scored_candidates[:topn]
+
+
+def main():
+    start_time = time.time()
+    user, topn, dataset_file, node_number, num_nodes, workers = parse_args()
+    print(
+        "model config: user=%s topn=%s node=%s/%s workers=%s"
+        % (user, topn, node_number, num_nodes, workers)
+    )
+
+    model_path = ensure_shared_model(dataset_file, workers)
+    model = Doc2Vec.load(model_path)
+    results = score_candidate_shard(model, user, topn, node_number, num_nodes)
+
+    for candidate, score in results:
+        print("python", (candidate, score))
+
+    completion_time_ms = (time.time() - start_time) * 1000.0
+    print("completionTime", completion_time_ms)
+
+
+if __name__ == "__main__":
+    main()
diff --git BEFORE/build_and_start_modern.sh AFTER/build_and_start_modern.sh
index 7fbb202..0310ffe 100755
--- BEFORE/build_and_start_modern.sh
+++ AFTER/build_and_start_modern.sh
@@ -87,6 +87,9 @@ if ! $PYTHON_CMD -c "import ipython" &> /dev/null; then
     $PYTHON_CMD -m pip install ipython
 fi
 
-javac -nowarn -cp "lib/*" -d classes TwitterGatherDataFollowers/userRyersonU/*.java
+if ! javac -nowarn -cp "lib/*" -d classes TwitterGatherDataFollowers/userRyersonU/*.java; then
+    echo "Java compilation failed. JADE was not started."
+    exit 1
+fi
 
 java $JAVA_OPTS -cp "lib/*:classes" jade.Boot $JADE_OPTS controller:TwitterGatherDataFollowers.userRyersonU.ControllerAgent
diff --git BEFORE/lib/pdfbox-app-2.0.31.jar AFTER/lib/pdfbox-app-2.0.31.jar
new file mode 100644
index 0000000..c74c993
Binary files /dev/null and AFTER/lib/pdfbox-app-2.0.31.jar differ
```
