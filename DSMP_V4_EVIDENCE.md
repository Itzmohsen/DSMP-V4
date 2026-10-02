# Evidence Studio

Evidence Studio explains why each recommendation was made, for every
algorithm. It works on the runs of the current session as their results
arrive, and on any recorded run. This document covers:

- what the previous version did;
- the reference models and when they are exact;
- the measures;
- what the studio shows;
- how the engine was verified.

## 1. Why it was rebuilt

The previous engine did not explain the models:

- **Its own text processing.** It re-read the raw dataset with its own
  tokenizer and a short stop-word list, not the text the run actually used.
- **Raw word counts.** It ranked "shared terms" by raw counts, with no inverse
  document frequency. Words every user writes therefore led the explanation.
- **Arbitrary quotes.** It quoted a user's first six rows in file order, not
  the rows that hold the evidence.
- **Invented counterfactuals.** Its "counterfactuals" were arithmetic on those
  counts, and labelled "model was not rerun".
- **Stale method notes.** Its description of TGF still named retired signals,
  and its score bar showed a "visual scale" percentage for any number.

## 2. The run's text

Every run now writes its canonical corpus (`RunCorpus`):

- the tweets the run used, normalised exactly as the recommender nodes
  normalise them;
- content-addressed under `.runtime/run-corpus`, so an unchanged corpus is
  written once;
- named in the run record's manifest (`corpus`).

Evidence Studio recomputes the recommender agents' TF-IDF from it:

- the weight is the raw term frequency times log2(N / df), where N is the
  number of users with a document and df the number of users who write the
  term;
- each user's weights are L2-normalised.

The cosine of two such rows is exactly the Similarity algorithm's score.

## 3. Reference models

A recommendation is explained against a reference model chosen by the
algorithm and the candidate type:

| Algorithm | Users | Accounts |
|---|---|---|
| Similarity | TF-IDF cosine: **exact** | the best follower's cosine: **exact** (the run projects user scores onto accounts by the maximum) |
| Common Neighbors | the run's link-prediction variant: **exact** | the best follower's link score: **exact** |
| K-Means, K-Means Euclidean, Doc2Vec | TF-IDF cosine: fidelity measured | the best follower's cosine: fidelity measured |
| SVM, Scalable SVM, MLP | TF-IDF cosine: fidelity measured | cosine to the TF-IDF centroid of the account's followers: fidelity measured |
| Temporal Graph Foundation | TF-IDF cosine, plus TGF's own exact explanation | centroid cosine, plus TGF's own exact explanation |

**Exact** means the reference is the algorithm's own scoring rule. The studio
shows that it reproduces the delivered scores: their ratios to the first
score, since delivered scores may be rescaled so the first is 100.

**Fidelity measured** means the reference is shared vocabulary rather than the
algorithm. The studio then measures how much of the algorithm's list that
vocabulary reproduces, and says so.

## 4. Measures

**Decomposition.** Every reference score is a sum over the words the two users
share, so each word's share is exact.

| Reference | A shared word t adds |
|---|---|
| Cosine | a_t b_t |
| Common neighbours | 1, plus its share of the Adamic–Adar tie-break, (1/ln d_t)/(1 + AA) |
| Adamic–Adar | 1/ln d_t |
| Resource allocation | 1/d_t |
| Jaccard | 1/\|union\| |
| Centroid | a_t c_t / \|\|c\|\| |

Here d_t = max(2, df_t) is the term's degree in the user-term graph.

**Counterfactuals.** Removing a word removes it from the target's profile,
while the corpus statistics stay fixed (every other user's vector and the
document frequencies). This is the usual perturbation of the instance, not of
the model. Scores are updated exactly through the word's postings. The studio
shows:

- the score and rank after removing each of the strongest words, and who
  takes the recommendation's place;
- the fewest words, removed in order of contribution, that cost the
  recommendation its reference rank;
- the fewest words that push it out of the delivered list's length.

The search covers up to 300 words. The last two items are the minimal
counterfactual explanations of PRINCE (Ghazimatin et al. 2020), computed on
the reference.

**Contrast.** This is why the recommendation beat its rival (the next one
delivered, or the first when another is selected). It is the word-by-word
difference of their reference contributions.

**Fidelity.**

- **Rank-biased overlap** (Webber, Moffat and Zobel 2010; extrapolated,
  persistence 0.9). It compares the delivered list with the reference ranking
  at the list's length, with the first ranks weighing most. It is 1 for the
  same list and 0 for disjoint lists.
- **Overlap@k** is the share of the delivered list found in the reference's
  top k.
- **Kendall's tau-b** compares the delivered scores with the reference scores
  of the delivered items.

Measured on the Retail runs of section 8 of `DSMP_V4_EVALUATION.md` (40
targets each, k = 10):

| Run | Reference | Mean RBO | Reproduction |
|---|---|---|---|
| Similarity (users / accounts) | exact | 1.000 / 1.000 | relative error ≤ 5 × 10⁻⁸ |
| Common Neighbors (users / accounts) | exact | 1.000 / 1.000 | ≤ 2 × 10⁻⁸ |
| K-Means Euclidean (users) | measured | 0.836 | |
| K-Means (users) | measured | 0.715 | |
| Doc2Vec (users) | measured | 0.217 | |
| TGF (users) | measured | 0.058 | |
| SVM, Scalable SVM, MLP (accounts, 4 accounts) | measured | 0.94–0.95 | |

The reproduction errors are the six-decimal rounding of
`recommendations.csv`.

These numbers are findings in their own right:

- K-Means follows shared vocabulary closely.
- Doc2Vec's paragraph vectors largely do not.
- TGF ranks users by the probability of sharing the target's community, not
  by similarity, so shared vocabulary explains almost none of its user lists.
  For TGF, the studio shows TGF's own exact explanation alongside.

**Ground truth and context.**

- **Relevance** uses the follows recorded in the corpus, which are never shown
  to the algorithms. A user is relevant when it follows the target's account;
  an account is relevant when the target follows it.
- **Relevance by rank** is compared with the chance rate of a random list from
  the target's candidates.
- **The run graph** adds whether the recommendation stays inside the target's
  community, its exposure and PageRank, and the simulated adoption.
- **TGF runs** add TGF's account probabilities, conformal set and evidence by
  learner.

**Tweets.**

- The studio quotes the tweets of both users that hold the decisive words,
  strongest first, with the words highlighted.
- It lists the months in which both users wrote them.
- It reports the cosine of their monthly activity.

## 5. The studio

| Page | Contents |
|---|---|
| Explain | The run's targets and delivered lists. For the selected recommendation: the verdict; each word's exact share; fidelity (RBO, overlap@k, Kendall's tau, and either the reproduction error or the reference rank); the counterfactual table and minimal word sets; the contrast with the rival; the quoted tweets, shared months and activity overlap; the ground truth; the run graph; TGF's view |
| Run | Mean fidelity over the run's targets; relevance by rank against chance; the words that carried the run's first recommendations; every target's RBO and overlap, which link to its explanation |
| Methods | The reference per algorithm, the decomposition, the counterfactuals, the fidelity measures, the ground truth, and references |

The picker lists this session's runs (live, as their results arrive) and
every recorded run with recommendations. A recorded run is rebuilt from its
`recommendations.csv`, its manifest (algorithm, dataset, corpus, link
variant) and its `quality.json`.

## 6. A finding: Common Neighbors' degrees

The first check against recorded runs reproduced Similarity to 5 × 10⁻⁸ but
Common Neighbors only to 3 × 10⁻²: equal shared-word counts had identical
delivered scores. The recommender node released the corpus-wide document
frequencies right after computing TF-IDF, before Common Neighbors scored.
Every term's degree therefore took its floor of 2, so:

- the Adamic–Adar tie-break degenerated into a function of the count;
- equal counts were ordered by name;
- the Adamic–Adar and resource-allocation variants reduced to the shared-word
  count.

The degrees are now kept until scoring. Common Neighbors is reproduced to
2 × 10⁻⁸, and its corrected Retail results are in `DSMP_V4_EVALUATION.md`.

## 7. Verification

- `RecommendationEvidenceEngineSelfTest` checks:
  - the corpus's TF-IDF against an independent computation of the agents'
    formula;
  - that Similarity's delivered scores are reproduced, with RBO 1;
  - that word contributions and shares add up to the score;
  - a removal counterfactual against the cosine recomputed by hand;
  - every Common Neighbors variant against `SparseRecommendationMath`, with
    its decomposition;
  - the account projection (the best follower and its score);
  - the centroid reference;
  - RBO and Kendall's tau-b on known values;
  - the run graph's context;
  - loading a recorded run.
- `RecommendationEvidenceStudioPanelSelfTest` renders every page of a live run
  in both themes.

## 8. References

- Webber, W., Moffat, A. and Zobel, J. (2010). A similarity measure for
  indefinite rankings. ACM TOIS 28(4).
- Ghazimatin, A., Balalau, O., Saha Roy, R. and Weikum, G. (2020). PRINCE:
  provider-side interpretability with counterfactual explanations in
  recommender systems. WSDM.
- Kendall, M. G. (1945). The treatment of ties in ranking problems. Biometrika
  33(3), 239–251.
- Liben-Nowell, D. and Kleinberg, J. (2007). The link-prediction problem for
  social networks. JASIST 58(7), 1019–1031.
- Adamic, L. A. and Adar, E. (2003). Friends and neighbors on the web. Social
  Networks 25(3), 211–230.
- Zhou, T., Lü, L. and Zhang, Y.-C. (2009). Predicting missing links via local
  information. European Physical Journal B 71, 623–630.
- Guidotti, R. et al. (2018). A survey of methods for explaining black box
  models. ACM Computing Surveys 51(5).
- Lundberg, S. M. and Lee, S.-I. (2017). A unified approach to interpreting
  model predictions. NeurIPS.
- Salton, G. and Buckley, C. (1988). Term-weighting approaches in automatic
  text retrieval. Information Processing and Management 24(5), 513–523.
