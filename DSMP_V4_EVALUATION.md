# DSMP V4 Simulator: algorithms and evaluation

This document describes what the Simulator tab runs and how it measures it:
the data a run uses, the nine recommendation algorithms, who they recommend
for, and the evaluation protocol behind every quality figure. It also lists
what the October 2026 audit found and how each finding was fixed.

The node model, the scheduling policies and the timing measurements are in
`DSMP_V4_NODE_LAB.md`. The exactness of distributed runs is covered in
`DSMP_V4_SCHEDULING_REBUILD.md`. The network each run creates (the run graph
behind Graph Insights) is described in `DSMP_V4_GRAPH.md`.

## 1. The Simulator tab

The tab is a guided flow. Each page drives the window's own controls, which
stay the single source of truth for the controller, the help panel and every
automated run (the live harness, studies, the Node Lab launcher).

| Page | What it is for |
|---|---|
| Overview | What the next run will use, the one next step, and the latest run's quality against chance |
| 1 Data | Dataset source and file, experiment label, date window, tweet limit, text processing, loading users (with their community sizes) |
| 2 Model | The nine algorithms (rankers and classifiers), what they recommend (users or followee accounts), parameters, the Doc2Vec cache |
| 3 Targets | Who gets recommendations (selected users, a stratified sample, everyone), the list length k, and the evaluation preview (candidates, relevant items and chance levels) before anything runs |
| 4 Run | Node count and the Node Lab cluster, the Java heap, the three run steps (load users, initialize, run), the live output |
| 5 Results | Six ranking metrics with 95% intervals, chance and MostPop baselines, paired tests, the top-1 view with a confusion matrix, per-target rows, diagnostics, the run record |

The navigation marks each completed step. The status chip at the top right
always shows the simulator's real state.

## 2. Data

**Sources.** A text file (tab-separated: followee, tweet id, date, user id,
user, text), the configured MySQL store, a generated network, or artificial
tweets drawn from the bundled corpus's word statistics.

**Which tweets a run uses.**
- *Date window.* Both boundary days are inside the window. A row whose date
  cannot be read is kept, because the window filters dated rows only.
- *Tweet limit.* Keeps the N newest tweets of the window, by date, with the
  tweet id breaking ties. 0 (or an empty field) keeps them all. The
  database source applies the same limit in SQL.
- *Database queries.* The followee, the dates and screen names are bound
  parameters (`TweetDatabaseQueries`), never spliced into the SQL text.

**Text processing** (`TweetTextNormalizer`, shared by the controller, every
recommender node and the generators):
1. Truncate at `Photo:` / `Photoset:`.
2. Drop retweets (optional) or reduce `RT @` to `RT `.
3. Remove links.
4. Turn every non-letter into a word boundary. Letters of every script are
   kept, after Unicode NFC normalisation (so `café` stays `café`). Digits,
   symbols and punctuation, `#` and `@` among them, separate words.
5. Lower-case in `Locale.ROOT`.
6. Remove stop words (optional).

A tweet needs at least three words to count. The pipeline is idempotent.

The window's old "Remove #'s" option has no effect in this pipeline, because
`#` always separates words. The Simulator tab therefore no longer shows it;
the underlying setting is kept for compatibility.

**The run corpus** (`RunCorpus`). Every tweet the run uses, normalised
exactly as the nodes normalise it. Each line is user, followee, UTC
timestamp and text. Lines are sorted by user, then tweet id, and the file is
named by the SHA-256 of its content. Every run writes it (an unchanged corpus
only once) and its record names it. Doc2Vec's worker and the temporal graph
model read this file, so every algorithm sees the same users, tweets and
words, and Evidence Studio explains any run with the text it read. Doc2Vec no longer stems, uses its own stop words or ignores the date
window and tweet limit.

## 3. Algorithms

| Algorithm | Recommends | Method | Learns from labels? |
|---|---|---|---|
| Similarity | users | TF-IDF vectors with corpus-wide IDF, cosine similarity | no |
| Common-Neighbors | users | Link prediction on the user-term bipartite graph (see below) | no |
| K-Means | users | K-Means on TF-IDF vectors with cosine assignment and farthest-first seeding; the target's cluster mates ranked by cosine | no |
| K-Means Euclidean | users | The same clustering with Euclidean distance | no |
| Doc2Vec | users | Paragraph vectors (Le and Mikolov 2014) trained on the run corpus, single-threaded and seeded; nearest documents by cosine | no |
| Temporal Graph Foundation | users and followee accounts | Cross-fitted Super Learner over text, graph and temporal learners, stacked into calibrated community probabilities (see below) | yes, never the targets |
| SVM | followee accounts | Exact Weka SMO with calibrated probabilities; one model per target, trained on every other labelled user | yes, never the targets |
| Scalable Linear SVM | followee accounts | One-vs-rest linear model learned online with Passive-Aggressive PA-I hinge-loss updates (Crammer et al. 2006) | yes, never the targets |
| MLP | followee accounts | Softmax network trained with cross-entropy and early stopping (default), or the V3 Neuroph network | yes, never the targets |

Every algorithm produces scores where higher is better. The target itself is
never a candidate. A candidate with no positive score carries no evidence,
so it is not recommended: a list can be shorter than k, and evaluation
penalises that (section 5.2).

### Common-Neighbors

A user's neighbours are the terms it uses; a term's degree df is the number
of users who use it (Liben-Nowell and Kleinberg 2007). The score is chosen
under Parameters:
- **Common neighbours** (default): |N(u) ∩ N(v)|. Ties are broken by
  Adamic–Adar through CN + AA/(1 + AA). This keeps the integer order of the
  counts and ranks equal counts by how rare the shared terms are, rather
  than by name. Before this, half of the audit's targets had a tie at the
  top-10 cut, broken alphabetically.
- **Jaccard**: |N(u) ∩ N(v)| / |N(u) ∪ N(v)|.
- **Adamic–Adar** (Adamic and Adar 2003): Σ 1/ln df over shared terms.
- **Resource allocation** (Zhou, Lü and Zhang 2009): Σ 1/df over shared terms.

Until this version, the corpus-wide degrees were released before Common
Neighbors scored, so every degree took its floor of 2. As a result:
- the Adamic–Adar tie-break ordered equal counts by name after all;
- the Adamic–Adar and resource-allocation variants reduced to the
  shared-term count.

Evidence Studio's exactness check found the bug: its reference reproduced the
other algorithms' scores to 1e-8, but not these. The degrees are now kept
until scoring, and the reference reproduces Common Neighbors exactly.

### Temporal Graph Foundation

TGF estimates the probability that the target belongs to each community and
recommends from it. Accounts are ranked by that probability. Users are ranked
by the probability of sharing the target's community: p(account of v) when v's
follow is known, and the sum over c of p(c) q_v(c) for another target.

The probabilities come from six learners, each with its own view of the
run's corpus:
- a text model (logistic regression on sublinear TF-IDF);
- label propagation over the text graph;
- label propagation over the term-time graph, which links users who wrote the
  same words in the same month;
- an activity model;
- two neighbour votes.

Each learner is cross-fitted over stratified folds of the labelled users who
are not targets. A non-negative logarithmic opinion pool stacks them, fitted by
maximum likelihood (a Super Learner). Every prediction carries:
- a 90% split-conformal set;
- an exact explanation: the log-odds split by learner, the words, the
  influential labelled users, the months, the ablations and a minimal word
  counterfactual.

`DSMP_V4_TGF.md` gives the definitions, the validation and the studio.

The previous TGF scored (target, candidate) pairs from three signals and
never used the candidates' known follows. Its fusion's held-out pair AUC was
about 0.515 on Retail. Its successor more than doubles the margin over chance
(section 8).

### Classifiers: the targets are never trained on

The answer the evaluation scores is the target's followee, so a classifier
must never learn it.
- **SVM** trains one model per target, without that target.
- **Scalable SVM and MLP** use `TargetFolds`. When the targets are at most
  20% of the labelled users, one model is trained with all of them held
  out. Otherwise the targets are split into five folds, stratified by
  followee, and each fold is scored by a model trained on everything else
  (cross-validation). The folds depend only on the target names and labels.

Before a run starts, the window checks that every fold's training set still
has at least two followee classes. A dataset that fails says so at
Initialize, not after the input stage.

**MLP engines.**
- *Softmax (default)*: cross-entropy, Xavier initialisation, seeded
  shuffling. It trains on a stratified 85% of the training users and stops
  when the loss on the other 15% has not improved for 15 epochs (Prechelt
  1998), keeping the best epoch's weights. The epoch limit is a cap (200 by
  default).
- *Neuroph*: the V3 network, with its softmax defect fixed. Each output
  neuron now has its own sigmoid; previously one shared instance mixed every
  prediction with the previous input's values. Outputs are normalised to sum
  to 1.

## 4. Targets

| Mode | Targets |
|---|---|
| Selected users | The users picked in the list (any set; click, shift-click, ctrl/cmd-click) |
| Stratified sample | n users drawn so that every followee community keeps its share (largest-remainder allocation), with a seeded shuffle within each community; the same seed and dataset always give the same targets |
| All users | Every user of the run |

Every algorithm accepts any number of targets. Automated runs (the live
harness, studies, matrices) use the first n users of the list, n being the
run's batch size.

## 5. Evaluation

The evaluation (`RecommendationQualityEvaluator`) runs on the run's merged
lists: the same lists every target receives and the run record stores. It
only observes and never changes a list.

### 5.1 Queries, candidates and relevance

The targets are the queries. For a target t:
- **Similar users**. The candidate set U(t) is every other user of the run
  with a usable document. A candidate is relevant when it follows t's
  followee (same community).
- **Followee accounts**. U(t) is every followee account of the run; only t's
  own followee is relevant.

A target whose followee is unknown, or who is alone in its community,
cannot be scored by any system. It is skipped and counted. A target that
received no list scores zero: abstaining is a miss, never a skip.

### 5.2 Metrics

Binary relevance, cut off at k; m = |R(t)| is the number of relevant
candidates and rel_i marks whether rank i is relevant.

| Metric | Definition |
|---|---|
| Hit rate@k | 1 if any of the first k is relevant |
| P@k | hits / k. A list shorter than k keeps the denominator k |
| R@k | hits / m |
| NDCG@k | Σ rel_i / log2(i+1), divided by the ideal DCG of min(m, k) hits (Järvelin and Kekäläinen 2002) |
| MRR@k | 1 / rank of the first hit within k, else 0 |
| MAP@k | Σ over hits of P@i, divided by min(m, k) |

Earlier versions divided precision by the list length instead of k, and
computed the ideal DCG for the list length. Short lists were inflated: for
example, 100% hit rate in Followee mode and precision above the possible
maximum.

### 5.3 Baselines

- **Chance.** The exact expectation of every metric under a uniformly
  random ranking of U(t), from closed forms (no sampling). With n candidates
  and k' = min(k, n): a position is relevant with probability m/n, and two
  positions together with m(m−1)/(n(n−1)). The first hit is at rank i with
  probability Π_{j<i−1}(n−m−j)/(n−j) · m/(n−i+1). The self-test checks every
  closed form against exhaustive enumeration of all permutations.
- **MostPop.** The non-personalised ranking by community size: the most
  followed accounts first, or the members of the largest communities first.
  Each target's own label is excluded from the counts. This is the strongest
  baseline that ignores the target (Cremonesi, Koren and Turrin 2010).

### 5.4 Statistics

- Means over the scored targets with 95% intervals. The hit rate is a
  proportion of targets, so it uses the Wilson score interval. The other
  metrics use Student-t intervals kept inside [0, 1].
- **Paired tests.** For every target, the metric minus the chance level is
  one paired difference, and so is the metric minus MostPop's value. A
  one-sample t-test over targets gives the two-sided p-value of each.
- **Lift**: the mean over chance.
- **Top-1 view.** The top recommendation is read as a prediction of the
  target's followee. In user mode this is a 1-nearest-neighbour classifier
  (the top user's followee). The report gives accuracy, macro-F1 over every
  actual or predicted followee, and the confusion matrix.

### 5.5 Diagnostics

The report says when a figure must be read with care:
- the whole candidate set fits in k (hit rate and recall are 100% by
  construction);
- a metric is at least 90% by chance alone;
- lists are empty or shorter than k;
- scores tie at the k-th place (decided by name);
- a recommended item lies outside the target's candidate set;
- fewer than ten targets were scored.

It also reports the mean number of relevant items, the largest mean recall
any list could reach (the mean of min(1, k/m)), and the catalogue coverage.

### 5.6 Where the results are

The Results page shows everything above. Each run's record in
`Results/Runs/<run>/` adds three files:
- `recommendations.csv`: target, target followee, rank, candidate,
  candidate followee, score, relevant.
- `quality.json`: every metric's mean, interval, chance, MostPop, lift, gains
  and p-values; the top-1 view with its confusion matrix; the diagnostics.
- `quality-targets.csv`: every target's metrics, with its chance and MostPop
  values.

`Results/Runs/runs-index.csv` gains `quality_k`, `quality_targets`,
`hit_rate`, `precision_at_k`, `recall_at_k`, `ndcg_at_k`, `mrr`, `map_at_k`,
`ndcg_random`, `ndcg_lift`, `ndcg_p_value` and `top1_accuracy`. An index
written with older columns is migrated in place, matching columns by name.

## 6. Reproducibility and robustness

- **One result path.** Every algorithm and mode delivers through the same
  structured merge (`RecommendationScoreMerger`), so every run is evaluated
  and recorded. Each candidate's score comes from exactly one node, so the
  merge is exact for any node split.
- **Seeded draws.** These depend only on names, never on thread order or
  node count (`SimulationSeeds`):
  - the simulated follow (how many tweets the followee sees before
    following), seeded by the pair of users;
  - each user agent's artificial text, seeded by its user name;
  - the generated dataset, from a fixed seed;
  - the simulated choice among the top three recommendations, seeded by the
    user name. The old adjustment that made Doc2Vec's top recommendation
    unchoosable is gone.
  - the stratified target sample, from its seed.
- **Run-scoped notifications.** The window names each run it asks for.
  Every milestone (initialized, finished, failed) carries its run's id, and
  milestones of a superseded run are ignored. A run winding down can no
  longer fail, finish or redefine the next one. This caused the audit's
  "3-node run that ran on 1 node".
- **Crashes.**
  - Node-local work is durable at every synchronisation point (a collective,
    a message to the coordinator, the end of a unit). A crash loses only the
    work since the last durable point, and a second crash during the redo
    restarts it instead of adding another.
  - A crash stops a node's emulated work immediately.
  - No thread passes a synchronisation point while its node is down, even
    with nothing left to pay. Before, a node as fast as the time dilation,
    or a thread whose oversleep had left it a credit, went straight through
    and never redid its lost work.
  - A step or unit lost to 200 crashes in a row cannot finish between the
    node's crashes, so the run fails with that diagnosis instead of running
    forever.
- **Failures.** Unit work is deterministic, so only environment failures
  (I/O such as a crashed worker process, timeouts, running out of memory)
  are retried. Any other error fails the phase at once with its reason.

## 7. Audit findings and their fixes

| # | Finding (audit, 1 Oct 2026) | Fix | Verification |
|---|---|---|---|
| 1 | TGF used the target's own followee (P@10 0.805 as shipped, 0.430 with the label hidden) | Rebuilt as a learned logistic fusion of text, graph and time; no label use | `TemporalGraphFoundationModelSupportSelfTest` (relabelling targets changes nothing) |
| 2 | Scalable SVM trained on the targets it predicted (29/29 vs 5/11) | `TargetFolds`: targets held out (one model or five folds) | Retail: 55% top-1 with targets held out, against 42.5% for the majority class |
| 3 | Default MLP (Neuroph) had a broken softmax | Softmax engine as default (cross-entropy, early stopping); Neuroph fixed | `ClassifierMatrixSelfTest`, live early stopping (best epoch kept) |
| 4 | TGF results changed between identical runs (it read edge files grown by earlier runs) | TGF reads only the run corpus | Self-test with planted edge files |
| 5 | Hang under frequent crashes; it then broke the next runs | Durable points, redo replacement, immediate crash notice, livelock guards, run-scoped notifications | K-Means with crash processes on 4 nodes: 12.5 s, identical to 1 node |
| 6 | No quality report in default mode for 8 of 9 algorithms | One structured result path for every algorithm | Every run writes `quality.json` |
| 7 | One target per run for 6 algorithms; SVM and Doc2Vec took consecutive users | Target modes for every algorithm (selected, stratified sample, all) | `TargetSelectionSelfTest` |
| 8 | Precision and NDCG inflated by short lists; trivial 100% hit rate; wrong recall bound; no chance level | Metric definitions per section 5.2; chance and MostPop baselines; intervals and tests; diagnostics | `RecommendationQualityEvaluatorSelfTest` (enumeration, commons-math) |
| 9 | Zero-score users recommended on the default path | One result path; non-positive scores carry no evidence and are dropped | Lists hold positive scores only |
| — | Common-Neighbors ties broken alphabetically; raw counts favour heavy posters | Adamic–Adar tie-break; Jaccard, Adamic–Adar and resource allocation variants | `EvaluationIntegritySelfTest` |
| — | Doc2Vec preprocessed differently and ignored the window and limit | Run corpus shared by Doc2Vec and TGF | Exactness suites |
| — | TGF's fixed weights unvalidated; popularity correction; line-order clock | Learned weights with held-out AUC; retired signals; dates only | TGF self-test |
| — | Tweet limit kept the highest ids, not the newest; empty field meant 1000 | Newest by date; empty means no limit | Code and `SvmTargetPreflightSelfTest` |
| — | Follow simulation and artificial text unseeded; Doc2Vec −50 hack | `SimulationSeeds`; hack removed | `EvaluationIntegritySelfTest`, `BatchRecommendationSelectorSelfTest` |
| — | Accented and non-Latin letters deleted | Unicode letters kept, NFC, `Locale.ROOT` | `TweetTextNormalizerSelfTest` |
| — | Late failures: two-class checks after the input stage, MLP retried the same error | Early fold-aware check; deterministic errors not retried | `EvaluationIntegritySelfTest` |
| — | Database SQL built by string concatenation | Bound parameters | `EvaluationIntegritySelfTest` (hostile names) |
| — | Common-Neighbors scored with every term degree at 2 (the degrees were released before scoring), so its tie-break and two variants reduced to the shared-term count (found on 2 Oct 2026 by Evidence Studio's exactness check) | Degrees kept until scoring | Evidence Studio reproduces the recorded scores to 1e-8 |
| — | TGF's pairwise fusion ignored the candidates' known follows (held-out pair AUC about 0.515) | Cross-fitted Super Learner over six learners with conformal sets and exact explanations (`DSMP_V4_TGF.md`) | Retail P@10 0.475 to 0.725; `TemporalGraphFoundationModelSupportSelfTest` |
| — | "Scalable Linear SVM" is a passive-aggressive learner | Described as PA-I online hinge-loss learning everywhere | — |

Further fixes found while fixing these:
- **The FROM_DB initialisation** used the MySQL login name where the Twitter
  user was meant, for the replicated-target check and the followee
  argument.
- **The TGF error messages.** Every node reported "no candidates" for
  targets another node had scored. Errors now attach only to targets no node
  scored.
- **The run-quality setup.** It is computed while the run's tweets are still
  in memory (the text branch releases them once the agents hold them).
- **"Remove #'s".** The switch never had an effect; it is documented and no
  longer shown.
- **The Neuroph engine.** It ignored the proximal parameter, which is no
  longer shown.

## 8. Measured quality on Retail

Retail has 218 users in four communities (Wal-Mart 95, Loblaws 87,
Metro 25, Sobeys 11). The figures below are the audit's 40 stratified
targets (17, 16, 5 and 2), one node, default settings, k = 10. They are
taken from the simulator's own reports. An independent re-implementation of
every metric, run on the same lists, agrees to within 5 × 10⁻⁵ (the reports
print four decimals).

**Similar users** (chance P@10 = 0.359, MostPop = 0.425):

| Algorithm | P@10 [95% CI] | NDCG@10 | MRR | vs chance (P@10) | vs MostPop (P@10) | Top-1 |
|---|---|---|---|---|---|---|
| Similarity | 0.445 [0.366, 0.524] | 0.452 | 0.592 | p = 0.006 | p = 0.78 | 40.0% |
| Common-Neighbors | 0.428 [0.359, 0.496] | 0.434 | 0.619 | p = 0.001 | p = 0.97 | 42.5% |
| K-Means | 0.438 [0.373, 0.502] | 0.448 | 0.626 | p = 0.002 | p = 0.87 | 42.5% |
| K-Means Euclidean | 0.435 [0.360, 0.511] | 0.438 | 0.582 | p = 0.012 | p = 0.88 | 37.5% |
| Doc2Vec | 0.385 [0.320, 0.450] | 0.389 | 0.579 | p = 0.28 | p = 0.61 | 40.0% |
| Temporal Graph Foundation | 0.725 [0.580, 0.870] | 0.725 | 0.725 | p < 0.0001 | p = 0.002 | 72.5% |
| SVM (classifier-guided) | 0.500 [0.338, 0.662] | 0.500 | 0.500 | p = 0.088 | p = 0.50 | 50.0% |
| Scalable Linear SVM (classifier-guided) | 0.600 [0.475, 0.725] | 0.606 | 0.698 | p < 0.0001 | p = 0.055 | 62.5% |
| MLP (classifier-guided) | 0.620 [0.468, 0.772] | 0.625 | 0.638 | p = 0.0003 | p = 0.033 | 62.5% |

**Followee accounts.** With four accounts and k = 10, P@10 and the hit
rate are the same for every list, and the report says so. MRR is the
informative metric (chance 0.521, MostPop 0.679; top-1 majority 42.5%):

| Algorithm | MRR [95% CI] | NDCG@10 | Top-1 | vs chance (MRR) | vs MostPop (MRR) |
|---|---|---|---|---|---|
| Similarity | 0.646 [0.549, 0.743] | 0.736 | 40.0% | p = 0.013 | p = 0.57 |
| Common-Neighbors | 0.692 [0.599, 0.785] | 0.771 | 45.0% | p = 0.0006 | p = 0.86 |
| K-Means | 0.671 [0.576, 0.766] | 0.755 | 42.5% | p = 0.003 | p = 0.89 |
| K-Means Euclidean | 0.642 [0.549, 0.735] | 0.733 | 37.5% | p = 0.012 | p = 0.50 |
| Doc2Vec | 0.642 [0.544, 0.740] | 0.732 | 40.0% | p = 0.017 | p = 0.52 |
| Temporal Graph Foundation | 0.829 [0.738, 0.921] | 0.872 | 72.5% | p < 0.0001 | p = 0.002 |
| SVM | 0.721 [0.628, 0.814] | 0.792 | 50.0% | p < 0.0001 | p = 0.49 |
| Scalable Linear SVM | 0.752 [0.652, 0.852] | 0.814 | 60.0% | p < 0.0001 | p = 0.18 |
| MLP | 0.775 [0.679, 0.871] | 0.832 | 62.5% | p < 0.0001 | p = 0.054 |

**Before and after the fixes** (same targets):

| | Audit | Now |
|---|---|---|
| TGF, similar users, P@10 | 0.805 (used the answer) | 0.725 (0.475 with the previous fusion) |
| TGF, followee accounts, top-1 | 0.275 (with the answer hidden) | 72.5% (62.5% with the previous fusion) |
| Scalable SVM, followee accounts, top-1 | 85.0% (trained on the targets) | 60.0% |
| MLP, followee accounts, top-1 | 50.0% (broken softmax) | 62.5% |
| MLP, similar users, P@10 | 0.510 | 0.620 |
| Doc2Vec, similar users, P@10 | 0.427 (its own preprocessing) | 0.385 (the shared corpus) |

On this dataset:
- Every algorithm beats chance.
- The text-similarity rankers do not beat the non-personalised MostPop list.
  Retail's text separates its four retailers only weakly.
- TGF is the best method in both modes and beats MostPop significantly in
  both (p = 0.002 for P@10 and for MRR). Its similar-user lists hold the
  most similar users of the most likely community, so its precision, NDCG
  and MRR all equal its top-1 accuracy.
- The classifiers come next. Only MLP is near a significant margin over
  MostPop (MRR p = 0.054).
- Doc2Vec's change when it moved to the shared corpus is within the
  sampling error (about 1.3 standard errors). The shared corpus makes the
  comparison fair: every algorithm now reads the same words.

The Simulator tab, end to end on its own stratified sample (seed 20261001),
gives Similarity P@10 = 0.455 [0.372, 0.539]. It is the same estimate on a
different sample of 40.

## 9. Limitations

- User names containing `-` are cut at the dash when agent names are turned
  back into user names. This is a property of the agent naming
  (`<user>-UserAgent`).
- The evaluation's candidate set for similar users is the run's users with a
  usable document. Algorithms that cannot score a user (for example one with
  an empty vector) simply never recommend them.
- The Universal benchmark (480 users) is far easier than Retail. Use it for
  load and exactness, not quality comparisons.

## References

- Adamic, L. A., and Adar, E. (2003). Friends and neighbors on the Web.
- Crammer, K., et al. (2006). Online passive-aggressive algorithms. *JMLR*.
- Cremonesi, P., Koren, Y., and Turrin, R. (2010). Performance of recommender algorithms on top-N recommendation tasks. *RecSys*.
- Järvelin, K., and Kekäläinen, J. (2002). Cumulated gain-based evaluation of IR techniques. *ACM TOIS*.
- King, G., and Zeng, L. (2001). Logistic regression in rare events data. *Political Analysis*.
- Le, Q., and Mikolov, T. (2014). Distributed representations of sentences and documents. *ICML*.
- Liben-Nowell, D., and Kleinberg, J. (2007). The link-prediction problem for social networks. *JASIST*.
- Prechelt, L. (1998). Early stopping — but when? In *Neural Networks: Tricks of the Trade*.
- Wilson, E. B. (1927). Probable inference, the law of succession, and statistical inference. *JASA*.
- Zhou, T., Ren, J., Medo, M., and Zhang, Y.-C. (2007). Bipartite network projection and personal recommendation. *Physical Review E*.
- Zhou, T., Lü, L., and Zhang, Y.-C. (2009). Predicting missing links via local information. *European Physical Journal B*.
