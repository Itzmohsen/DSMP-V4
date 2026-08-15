# Universal Heavy Benchmark Dataset

## Purpose

`Universal-Heavy-All-Working-Algorithms-76k.txt` is a deterministic,
journal-style benchmark for the current simulator's six working,
non-scalable algorithms:

- Similarity
- K-Means
- K-Means Euclidean
- SVM
- MLP
- Doc2Vec

It intentionally does **not** target Scalable Linear SVM. Common Neighbors is
also excluded because the current application marks it unavailable and has no
active recommendation implementation. No valid dataset can make an unavailable
algorithm produce recommendations.

The corpus is designed to be heavy enough to expose parallel work without
crossing the application's fragile shared limits. In particular, its 480 users
stay below the direct text-agent ceiling used by MLP, while its 24,576-term
cleaned vocabulary, 76,800 rows, and balanced labels create substantial model
work.

## Exact Structure

- 76,800 valid six-column dataset rows
- 480 authors/users
- 16 balanced journals/followees
- 30 authors per journal
- 160 title/post rows per author
- 32 lowercase alphabetic terms per row
- 24,576 global cleaned vocabulary terms
- 3,712 unique cleaned terms per author
- Every requested node from 1 through 8 receives authors from every journal

The semantic structure has three deliberately measurable levels:

1. Authors in the same journal share the most vocabulary.
2. Authors in related journal groups share less vocabulary.
3. Authors in unrelated groups share the least vocabulary.

This gives every working algorithm real signal instead of merely forcing it to
process random noise.

## Recommended Setup

1. Load `Dataset/Benchmarks/Universal-Heavy-All-Working-Algorithms-76k.txt`.
2. Use `Algorithm Default` recommendation mode.
3. Set Java heap to 4096 MB and apply/restart if needed.
4. Disable final graph output while measuring recommendation time.
5. Compare exactly 1 recommender node with exactly 8 recommender nodes.
6. Use the same selected target and settings for both runs.

Start with one target and three recommendations. Do not compare a warm-cache run
against a cold-cache run.

## What To Expect

| Algorithm | Valid? | Expected workload and node effect |
| --- | --- | --- |
| Similarity | Yes | Produces meaningful similar-author results. It only scores one target against candidates, so it may still finish quickly and show a modest node benefit. |
| K-Means | Yes | Heavy sparse clustering and candidate scoring. This is one of the clearest multi-node demonstrations. |
| K-Means Euclidean | Yes | Heavy sparse clustering and candidate scoring. This is one of the clearest multi-node demonstrations. |
| SVM | Yes | Balanced 16-class exact calibrated SMO workload. Isolated workers keep failures contained; multiple nodes divide the training population. |
| MLP | Yes | Large dense input layer with balanced classes. Multiple nodes train local models concurrently and the existing organizer averages them. |
| Doc2Vec | Yes | Cold model training is deliberately heavy. The shared model trains once, so nodes mainly accelerate candidate scoring rather than the dominant training phase. |

This is one universal corpus, so the algorithms will not all take exactly the
same time or show the same speedup. Similarity has much less inherent work than
K-Means, exact SVM, or MLP. Doc2Vec deliberately shares one trained model across
nodes. Those are algorithm properties, not dataset defects.

## Fair Doc2Vec Timing

Doc2Vec caches the shared trained model. A first run may spend minutes training;
a later run can reuse the cache and be much faster.

- To compare node scoring fairly, build the model once and use that same cache
  for both the 1-node and 8-node runs.
- To compare cold end-to-end time, remove the matching shared Doc2Vec cache
  before **each** run.
- Increasing nodes does not parallelize the one shared training job.

Using a larger Doc2Vec batch makes its distributed candidate-scoring phase more
visible, but changes the amount of requested work and must be held constant
between comparisons.

## Reproducibility And Validation

Regenerate and validate the corpus with:

```bash
javac -nowarn -cp "lib/*:classes" -d classes \
  TwitterGatherDataFollowers/Test/UniversalHeavyDatasetTool.java
java -cp "lib/*:classes" \
  TwitterGatherDataFollowers.userRyersonU.UniversalHeavyDatasetTool generate
```

Validation rejects malformed rows, tokens that would disappear during text
cleaning, unbalanced classes, incorrect document sizes, missing node/class
coverage, and a broken semantic-overlap hierarchy.

Run the cross-algorithm compatibility and pairwise parallel checks with:

```bash
javac -nowarn -cp "lib/*:classes" -d classes \
  TwitterGatherDataFollowers/Test/UniversalHeavyAlgorithmSmokeTest.java \
  TwitterGatherDataFollowers/Test/UniversalHeavyParallelCoreBenchmark.java
java -Xmx4096m -cp "lib/*:classes" \
  TwitterGatherDataFollowers.userRyersonU.UniversalHeavyAlgorithmSmokeTest
java -Xmx4096m -cp "lib/*:classes" \
  TwitterGatherDataFollowers.userRyersonU.UniversalHeavyParallelCoreBenchmark
```

The compatibility smoke test constructs and exercises both active K-Means
distance functions and the real Neuroph MLP shape of 24,576 inputs, 10 hidden
neurons, and 16 outputs.

## Measured Validation On The Development Machine

These measurements are machine-specific and are validation evidence, not
promised timings:

- File size: 38,455,645 bytes
- Malformed rows: zero
- SHA-256:
  `f61a3b8ba6f9708aab8810d7806e1eb3118d5cf1fd1761013f2b2f3db45d99a0`
- Same-journal / related-group / unrelated sampled term overlap:
  `3397 / 1536 / 1296`
- Similarity exact-population verification, 32 targets:
  one worker `598 ms`, eight workers `72 ms`, identical results, `8.31x`
- Exact calibrated SVM verification, 8 targets:
  one worker `16,888 ms`, eight workers `2,946 ms`, identical results, `5.73x`
- Active cosine plus Euclidean pairwise primitives, all 480 users:
  one worker `14,399 ms`, eight workers `1,617 ms`, identical results, `8.90x`
- Doc2Vec cold shared-model training: about `283 seconds`
- Doc2Vec warm scoring, 32 targets:
  one process `1,080 ms`, eight processes `1,201 ms`, identical results

The Doc2Vec measurement demonstrates the expected architectural limit:
training is the expensive shared phase, and extra nodes do not divide it.

## Safety Boundaries

- Keep users at 480. Raising the count above 512 can make MLP initialization
  create too many direct JADE user agents.
- Keep the Java heap at 4096 MB or higher for the intended benchmark.
- Begin with graph output disabled; graph construction measures a different
  phase than recommendation computation.
- Stop a run if the GUI reports a failed node or incomplete recommendations.
- The file is deliberately demanding. Other machines may need fewer concurrent
  nodes even when the logical dataset remains valid.
