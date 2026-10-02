# Temporal Graph Foundation (TGF)

TGF recommends to a target user the accounts it is most likely to follow and
the users most likely to share its community. A community is the set of users
who follow one account. This document covers:

- why the previous model was replaced;
- the new model;
- how it was validated;
- what the TGF Studio shows;
- the files a run record keeps.

## 1. Why it was rebuilt

The previous TGF scored each (target, candidate) pair. The score was a
logistic fusion of three pairwise signals:

- TF-IDF cosine;
- mass diffusion on the user-term graph, normalised by the target's best
  candidate;
- the cosine of 24-bin activity histograms.

It was trained on balanced pairs of non-target users. It had four problems:

1. **It ignored what the run knows.** The follows of the users who are not
   targets are the run's labels. A pairwise score never asks which account a
   candidate follows, so it could not find the target's community. It could
   only find similar text.
2. **Its signals were weak.** The fusion's held-out pair AUC was about 0.515
   on Retail; 0.5 is chance.
3. **Its explanations described a weak model.** The bootstrap intervals,
   counterfactual probes, timeline and ablation lenses were well formed, but
   they explained a ranking barely better than chance.
4. **Its studio was a fixed "cockpit".** It was 1,420 × 1,320 pixels of
   hand-painted canvases and jargon, with no way to open an earlier run.

| Retail, 40 audit targets, k = 10 | P@10 similar users |
|---|---|
| Chance | 0.359 |
| MostPop | 0.425 |
| Previous TGF | 0.475 (not better than MostPop, p = 0.47) |

## 2. The model

### 2.1 The task

A target's follow is hidden. TGF estimates p(c), the probability that the
target belongs to community c, for every account c the labelled users follow.
Only users who are not targets are labelled.

- **Accounts** are recommended by p(c).
- **A user v** is recommended by the probability of sharing the target's
  community:
  - p(account of v) when v's follow is known;
  - the sum over c of p(c) q_v(c) when v is another target, where q_v is v's
    own estimate.
  - Ties (users of one community) break by TF-IDF cosine.

The target's own follow is read only to grade the result.

### 2.2 Representations

Everything is built once per run from the run's canonical corpus (`RunCorpus`).
That corpus holds the tweets the run used, normalised exactly as the
recommender nodes normalise them.

| View | Definition |
|---|---|
| Text | Sublinear TF-IDF, (1 + ln tf) ln(N / df), L2-normalised, over the terms used by at least two users (at most 40,000, the most widely used first) |
| Activity | Each user's tweets per UTC calendar month over the corpus span, per hour of the day and per weekday, as shares |
| Term at time | The same TF-IDF over (term, month) pairs used by at least two users (at most 200,000). Two users share such a feature when they wrote the same word in the same month |

The term-at-time features are the edges of a user–(term, month) bipartite
graph. Its projection is a temporal co-activity graph: users are linked when
they talk about the same things at the same time.

### 2.3 Six learners

Each learner is a complete classifier of the community, built on one view of
the user.

| Learner | Definition |
|---|---|
| Text model | Multinomial logistic regression on the text view; L2 penalty 1e-4 (intercepts unpenalised); L-BFGS |
| Text graph | Label propagation (Zhou et al. 2004) over the symmetrised cosine 10-nearest-neighbour text graph, alpha 0.9 |
| Term-time graph | The same label propagation over the term-at-time graph |
| Activity model | Multinomial logistic regression on the activity shares; L2 penalty 1e-3 |
| Text neighbours | Similarity-weighted vote of the 10 most similar labelled users by text |
| Activity neighbours | The same vote by monthly activity |

Label propagation solves F = (1 − α)(I − αS)⁻¹ Y. Here S = D^(−1/2) W D^(−1/2)
and Y holds the one-hot labels. I − αS is symmetric positive definite, so each
class column is solved by conjugate gradients to a relative residual of
1e-10.

### 2.4 Cross-fitting and the stack

1. The labelled users are split into 5 folds, stratified by account (3 folds
   above 2,000 labelled users).
2. Every learner scores each fold's users after fitting on the other folds.
   Every labelled user therefore has scores from learners that never saw its
   label.
3. Each learner's centred scores are standardised by their root mean square.
4. The stack is a weighted logarithmic opinion pool:

       log p(c) = b_c + Σ_m w_m s_m(c) − log Z,   w_m ≥ 0

   It is fitted by penalised maximum likelihood, using projected Newton with
   Armijo backtracking. The ridge is 0.01 on w and 0.1 on b.

Non-negative weights follow Breiman's stacked regressions: a learner can only
add evidence, and a learner the others make redundant gets weight zero. The
construction is a Super Learner (van der Laan, Polley and Hubbard 2007). The
final learners are refitted on every label.

The probabilities are calibrated by construction, since the stack is a
maximum-likelihood multinomial model. The validation checks this (section 3).

### 2.5 Conformal prediction sets

1. Nonconformity is 1 − p(real account), on the cross-fitted probabilities of
   the labelled users. These come from the stack refitted without each fold.
2. With n labelled users, the threshold q is the ⌈(n + 1)(1 − α)⌉-th smallest
   score, with α = 0.1.
3. A target's set holds every account c with 1 − p(c) ≤ q, and always the most
   likely one.

This is the least ambiguous set-valued classifier of Sadinle, Lei and
Wasserman (2019). When targets are exchangeable with the labelled users, the
set holds the real account with probability at least 90%. Reported coverage is
cross-conformal: each fold uses the threshold of the other folds.

### 2.6 Exact explanations

Every target's report decomposes its prediction exactly.

- **Learners.** The log-odds of the predicted account c₁ against the runner-up
  c₂ equal (b_c₁ − b_c₂) plus the sum over learners of
  w_m (s_m(c₁) − s_m(c₂)).
- **Words.** Within the text model, term t adds x_t (W[c₁][t] − W[c₂][t]),
  scaled into the final log-odds. With the intercept, the terms sum to the text
  model's contribution.
- **Users.** Label propagation is linear in the labels. The mass of account c
  at the target is the sum, over users v labelled c, of M[target][v], so every
  labelled user's influence is exact. The row of M is one conjugate-gradient
  solve.
- **Months and hours.** These are the activity model's term-by-term
  contributions.
- **Ablations.** Each learner's weight is set to zero, the rest of the stack
  unchanged.
- **Counterfactual.** This is the fewest of the target's words whose removal
  from the text model's input changes the prediction. The vector is
  renormalised and every other learner's evidence held fixed. Words are removed
  in order of their support, up to 25.

The self-test checks every identity above to 1e-8.

### 2.7 Determinism and exactness

Folds use a fixed seed, every loop has a fixed order, ties break by index or
name, and nothing is random or multi-threaded.

- One node fits the model once per run (the `tgf-targets/prepare` collective),
  from the whole corpus.
- Every node scores and explains its targets from that shared state.

The recommendations are therefore identical for every node count, placement,
policy and live control.

Fit times:
- Real-Twitter (699 users, 546k tweets, 40,000 terms): about 13 s.
- Retail: 0.2 s.

## 3. Validation

**Lab protocol.**
- Every user is a held-out target once, using stratified 5-fold
  cross-validation over all users.
- For each fold, the fold's users are the run's targets and only the other
  users' follows are labels.
- The lists are graded by the application's own evaluator,
  `RecommendationQualityEvaluator`.
- The protocol is repeated over 3 seeds.

| Dataset | Method | Users P@10 | Accounts top-1 | Macro-F1 | Log-loss |
|---|---|---|---|---|---|
| Retail (218 users, 4 accounts) | Previous TGF | 0.44–0.46 | 0.51–0.54 | 0.33–0.36 | – |
| | **New TGF** | **0.62–0.65** | **0.62–0.65** | 0.37–0.39 | 0.97–0.99 |
| | Chance / MostPop | 0.36 / 0.44 | 0.25 / 0.44 | | 1.20 (prior) |
| Real-Twitter (699 users, 10 accounts) | Previous TGF | 0.35–0.36 | 0.41–0.44 | 0.34–0.39 | – |
| | **New TGF** | **0.56–0.57** | **0.56–0.57** | 0.35–0.39 | 1.16–1.17 |
| | Chance / MostPop | 0.26 / 0.43 | 0.10 / 0.43 | | 1.70 (prior) |

Notes on the table:
- Real-Twitter is a local benchmark of real follower data. It is not part of
  the repository.
- On the synthetic Universal benchmark, every method reaches 100%: its
  communities are separable by construction.
- On Real-Twitter, the model's cross-validated probabilities have an expected
  calibration error of 0.03–0.06.
- Its 90% conformal sets cover 89.5–90.7% of held-out users, with 2.7–2.9 of
  the 10 accounts per set.

**What drives it.** On Real-Twitter the stack's weights are:

| Learner | Weight |
|---|---|
| Term-time graph | about 1.6 |
| Text model | about 1.2 |
| Activity model | about 0.3 |
| Text neighbours | about 0.2 |
| Text graph | about 0.2 |
| Activity neighbours | 0 |

The temporal co-activity graph is the strongest complement to the text model.

**In the application.** These are the Retail figures with the audit's 40
targets, one node, k = 10, from the simulator's own reports (as in
`DSMP_V4_EVALUATION.md` section 8):

| | P@10 similar users [95% CI] | vs chance | vs MostPop |
|---|---|---|---|
| Previous TGF | 0.475 [0.398, 0.552] | p < 0.0001 | p = 0.47 |
| **New TGF** | **0.725 [0.580, 0.870]** | p < 0.0001 | **p = 0.0017** |
| Similarity | 0.445 [0.366, 0.524] | p = 0.006 | p = 0.78 |

Followee accounts, same targets: TGF reaches MRR 0.829 [0.738, 0.921] and
top-1 72.5%, against MostPop p = 0.002. The best classifier, MLP, reaches MRR
0.775 and top-1 62.5%.

The same comparison on the Real-Twitter benchmark uses 40 targets stratified
by account (17 TorontoStar, 7 RyersonU, 7 weathernetwork, and 1–2 of each
other account), one node and k = 10:

| Real-Twitter | P@10 similar users [95% CI] | vs chance (0.252) | vs MostPop (0.425) |
|---|---|---|---|
| **New TGF** | **0.575 [0.415, 0.735]** | p < 0.0001 | p = 0.057 |
| Similarity | 0.318 [0.250, 0.385] | p = 0.024 | p = 0.12 |
| K-Means | 0.290 [0.230, 0.350] | p = 0.18 | p = 0.067 |
| Common Neighbors | 0.218 [0.158, 0.277] | p = 0.35 | p = 0.020 (below) |

| Real-Twitter, followee accounts | MRR [95% CI] | Top-1 | vs MostPop (MRR) |
|---|---|---|---|
| **New TGF** | **0.732 [0.626, 0.838]** | **57.5%** | **p = 0.021** |
| Scalable Linear SVM | 0.670 [0.557, 0.783] | 50.0% | p = 0.045 |
| MLP | 0.658 [0.542, 0.774] | 50.0% | p = 0.088 |

Every list TGF delivers for similar users holds the most similar users of the
most likely community. Its precision@k and NDCG@k therefore equal its top-1
accuracy, and so does its MRR. This is the list that maximises the expected
precision.

A ranker that mixes communities can reach a first relevant user earlier when
its first guess is wrong, at the cost of precision. On Real-Twitter,
Similarity's MRR (0.612) is higher than TGF's (0.575) for that reason, while
its P@10 is much lower (0.318 against 0.575).

Hit rate@k, which is 93% by chance at k = 10 on Retail, cannot separate models
(the report says so).

## 4. TGF Studio

The studio follows the newest TGF run: it shows the live run, then its record
once it is written. Any recorded TGF run can be opened from the picker.

| Page | Contents |
|---|---|
| Overview | Held-out accuracy against the largest community and chance; macro-F1, log-loss, calibration error, conformal coverage and fit time; the learners' stack weights and solo accuracy; every target, graded |
| Targets | For each target: account probabilities with the conformal set and the real follow; the exact log-odds waterfall by learner; each learner's own verdict, weight, evidence and ablation; the words behind the text model; the labelled users whose labels reached it through each graph (with their shared words and shared (word, month) pairs); its activity against the two most likely communities; the fewest words that flip the prediction; the recommended users |
| Model | The learners (definition, weight, cross-fitted accuracy and log-loss); a reliability diagram; conformal coverage, set size and threshold; the confusion matrix with each account's precision, recall and F1; the fit |
| Communities | Every community's tweets by month and by hour of the day; its distinctive words from the text model |
| Methods | The algorithm, this run's parameters, the guarantees and the references |

## 5. Files

A TGF run's record holds:

- `tgf/report.json` (format `dsmp-tgf/1`): the model card (data, learners,
  communities, validation, graphs and fit) and every target's report. The
  studio reads only this, whether for a live run or a recorded one.
- `tgf/predictions.csv`: one row per target, with these columns:
  - real and predicted account;
  - probability;
  - runner-up and margin;
  - conformal set and its size;
  - whether the prediction and the set were right;
  - the rank of the real account.

## 6. Verification

- `TemporalGraphFoundationModelSupportSelfTest` checks:
  - the target's own follow is never read;
  - no file outside the run changes the result;
  - two identical runs give identical reports;
  - separable communities are recovered;
  - every explanation identity holds to 1e-8;
  - label propagation satisfies (I − αS)F = (1 − α)Y;
  - every user's influence adds up to the class mass;
  - the logistic regression's penalised gradient vanishes at its optimum;
  - the stack gives a noise channel no weight;
  - the conformal threshold matches its formula;
  - an undated corpus switches the temporal learners off;
  - a raw dataset and its run corpus give the same model;
  - a single account falls back to text similarity;
  - the record round trip keeps the report.
- `TemporalGraphFoundationPanelSelfTest` renders every studio page in both
  themes and opens a target from another page.
- `AlgorithmMatrixSelfTest` checks the scores' range and their
  reproducibility.

## 7. References

- Zhou, D., Bousquet, O., Lal, T. N., Weston, J. and Schölkopf, B. (2004).
  Learning with local and global consistency. NeurIPS 16.
- van der Laan, M. J., Polley, E. C. and Hubbard, A. E. (2007). Super learner.
  Statistical Applications in Genetics and Molecular Biology 6(1).
- Breiman, L. (1996). Stacked regressions. Machine Learning 24(1), 49–64.
- Genest, C. and Zidek, J. V. (1986). Combining probability distributions: a
  critique and an annotated bibliography. Statistical Science 1(1), 114–135.
- Sadinle, M., Lei, J. and Wasserman, L. (2019). Least ambiguous set-valued
  classifiers with bounded error levels. JASA 114(525), 223–234.
- Vovk, V., Gammerman, A. and Shafer, G. (2005). Algorithmic Learning in a
  Random World. Springer.
- Huang, Q., He, H., Singh, A., Lim, S.-N. and Benson, A. R. (2021). Combining
  label propagation and simple models out-performs graph neural networks.
  ICLR.
- Liu, D. C. and Nocedal, J. (1989). On the limited memory BFGS method for
  large scale optimization. Mathematical Programming 45, 503–528.
- Guo, C., Pleiss, G., Sun, Y. and Weinberger, K. Q. (2017). On calibration of
  modern neural networks. ICML.
