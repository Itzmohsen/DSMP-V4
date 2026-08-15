# Extreme 15-Minute-Plus Journal Benchmark

## Purpose

`Extreme-15-Minute-Plus-Journal-245k.txt` is a deliberately extreme benchmark
for long-running parallel demonstrations. It is separate from the safer
universal benchmark because it trades convenience for sustained workload.

It preserves valid recommendation signal while adding a very large shared
vocabulary workload. Shared load terms occur for every author, so Java TF-IDF
assigns them zero information weight. They therefore increase parsing, feature
construction, sparse traversal, model construction, and training work without
changing which journals and authors are meaningfully related.

## Exact Structure

- 245,760 valid six-column rows
- 62,914,560 text tokens
- 480 authors/users
- 16 balanced journals/followees
- 30 authors per journal
- 512 rows per author
- 256 lowercase alphabetic terms per row
- 262,144 global cleaned vocabulary terms
- 107,520 unique cleaned terms per author
- Every node from 1 through 8 receives authors from every journal

## Recommended Runtime Setup

- Java heap: **16,384 MB**
- Recommendation mode: `Algorithm Default`
- Graph output: disabled while measuring recommendation work
- Compare exactly 1 node and exactly 8 nodes
- Keep the selected target count and all options identical

For the clearest parallel demonstration, use SVM, select the first author,
enter `480` Batch Users, then compare 1 node against 8 nodes. Selecting a later
author produces a smaller batch because batching continues from the selected
row to the end of the user list.

This workload is intentionally large. Use `Reset Experiment` between runs and
stop immediately if a node reports failure.

## Per-Algorithm Verdict

| Algorithm | Verdict for this extreme corpus |
| --- | --- |
| Similarity | Valid and should produce meaningful similar-author results, but its normal single-target path will probably still finish in less than 15 minutes. It has too little inherent work to guarantee a 15-minute run. |
| K-Means | Valid under a 16 GB heap. The full active cosine-plus-Euclidean pairwise core completed, but its measured one-node time was only about 5.3 minutes. A full clustering run will be longer, but 15 minutes is not guaranteed. |
| K-Means Euclidean | The corpus is structurally valid and the distance core handles it under 16 GB. However, the current application assigns points to the greatest Euclidean distance instead of the closest centroid, so its results follow the current program but are not standard Euclidean K-Means. |
| SVM | Valid. Select the first user and set Batch Users to 480. Exact SVM retrains per requested target; the measured one-target rate projects to about 34.5 minutes on one node. |
| MLP | Structurally valid, but operationally unusable for normal testing. Constructing and training the real 262,144-input Neuroph shape on only one representative row exceeded 15 minutes and was intentionally stopped. A complete run would be much longer. |
| Doc2Vec | Valid. A cold-cache run is projected far beyond 15 minutes because it trains for 20 epochs over more than 62 million text tokens. Nodes do not divide the shared training phase. |
| Common Neighbors | Does not work. The current application marks it unavailable and has no active recommendation implementation. |
| Scalable Linear SVM | Not the purpose of this corpus. Use the separate Scalable SVM benchmark instead. |

## Honest Multi-Node Expectations

- The clearest normal-GUI multi-node demonstrations are K-Means, K-Means
  Euclidean, and a large exact-SVM batch.
- MLP trains local models concurrently, but model averaging remains a shared
  phase.
- Similarity divides candidate scoring but normally has too little total work
  to remain busy for 15 minutes.
- Doc2Vec is intentionally long-running, but its one shared model trains once.
  Additional nodes mainly divide the much smaller scoring phase.

No dataset can make every algorithm scale equally without changing those
algorithms.

## Generate And Validate

```bash
javac -nowarn -cp "lib/*:classes" -d classes \
  TwitterGatherDataFollowers/Test/ExtremeHeavyDatasetTool.java
java -Xmx4096m -cp "lib/*:classes" \
  TwitterGatherDataFollowers.userRyersonU.ExtremeHeavyDatasetTool generate
```

The validator checks every row, every user block, every token, every class
balance, exact vocabulary dimensions, eight-node class coverage, and the
semantic overlap hierarchy.

## Measured Validation

- File size: `841,757,616` bytes
- SHA-256:
  `eae30af8f422ad3e54bd64bef95ec7aad43b8d9c40de6ef8677b6ca7323e364b`
- Malformed rows: zero
- Full Similarity population pipeline, 8 targets:
  one worker scoring `2,822 ms`, eight workers scoring `566 ms`, identical
  results. The full file was ingested successfully, but Similarity is not a
  15-minute algorithm.
- Exact calibrated SVM, one target:
  one-worker scoring `4,317 ms`, repeated verification `4,488 ms`, identical
  results. At the measured one-target rate, a 480-target one-node batch is
  approximately `34.5 minutes` before accounting for batch overhead.
- Active K-Means cosine-plus-Euclidean pairwise core, all 480 users:
  one worker `315,671 ms`, eight workers `53,823 ms`, identical results,
  `5.86x` speedup. This proves the full sparse document shape is usable under
  16 GB, but the measured core itself is below 15 minutes.
- Extreme MLP shape:
  the real 262,144-input model with one representative row exceeded
  `15 minutes` and was stopped. Do not use this dataset for a practical MLP
  experiment.
- Cold Doc2Vec projection from the measured universal benchmark:
  approximately `120.6 minutes`. This projection scales the measured
  282.7-second cold run by exact input-token count. It is not presented as an
  exact guarantee because training speed is machine-dependent.
- The active Doc2Vec parser successfully read all `245,760` rows, and there is
  currently no matching warm model cache. The first GUI Doc2Vec run will
  therefore be a cold training run.

Run the default fast compatibility probe with:

```bash
javac -nowarn -cp "lib/*:classes" -d classes \
  TwitterGatherDataFollowers/Test/ExtremeHeavyAlgorithmSmokeTest.java
java -Xmx4096m -cp "lib/*:classes" \
  TwitterGatherDataFollowers.userRyersonU.ExtremeHeavyAlgorithmSmokeTest
```

The intentionally long MLP construction probe is available by adding
`--mlp-probe`, but repeating it is not recommended.
