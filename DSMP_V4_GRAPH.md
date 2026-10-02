# DSMP V4 Graph Insights: the network each run creates

This document describes the run graph: why DSMP builds a graph at all, what
it contains, every measure computed on it and its null model, the Graph
Insights tab that shows it, the files written into each run record, and how
the implementation is verified. The ranking metrics of a run (P@k, NDCG and
the rest) are in `DSMP_V4_EVALUATION.md`; this document is about what the
recommendations do to the social network.

## 1. Why a graph

A recommender does more than rank: when users follow its suggestions, it
rewires the network. The original DSMP added a graph step so a run could
show the network after the simulated users followed a recommendation, with
communities coloured. The research questions behind it are:

- Do recommendations stay inside the community a user already belongs to
  (homophily, echo chambers), or do they bridge communities?
- Do the communities the recommendations form match the real ones?
- Who receives the recommender's attention, and how unequally?
- What does one round of adopted follows do to the network's community
  structure?

## 2. What it replaces

The previous pipeline could not answer these questions:

- Each target appended two edges (to its followee and to the one chosen
  recommendation) to one global file per dataset and mode. The file was
  never tied to a run, so different algorithms, settings and repeated runs
  accumulated in it, with duplicates.
- Community detection ran on that graph with every other user as an
  isolated node, so most "communities" were singletons.
- The final stage (on by default) ran Gephi's Louvain with randomisation on,
  while Graph Insights ran it with randomisation off, so the two disagreed.
  It coloured communities with a random palette, exported a partition PDF
  before any layout, ran a fixed one-minute layout that held the run open,
  and opened a PNG saved as `layout.jpg` in an external viewer.
- The Graph Insights tab scored "graph health" from arbitrary penalties. Its
  topology view was decorative (orbits around a core, animated about 40
  times a second) and never drew the graph.

The global edge log, the Gephi stage and the old analysis are gone. Every run
now records its own graph, analysed against chance, in its run record.

## 3. The run graph

One node set, three layers (`RunGraph`):

| Layer | Edges | Meaning |
|---|---|---|
| Follows | user to the account it follows | Observed: the dataset's ground truth |
| Recommendations | target to each of its top-k candidates | What the recommender proposed, with rank and score |
| Adoptions | target to the candidate it follows | The follow simulation's choice |

- **Nodes.** The run's users (every user loaded for the run) and the accounts
  they follow. A handle that is both a user and a followed account is one
  node.
- **Real communities.** Each user belongs to the community of the account it
  follows; an account anchors its own. These communities are the ground truth
  every measure is judged against, as in the evaluation.
- **Weights.** A recommendation edge weighs 1/log2(1 + rank), the DCG
  discount, so the first recommendation counts most. Follows and adoptions
  weigh 1.
- **Adoption.** Each target follows one of its top three recommendations,
  with probability proportional to its score, drawn with a generator seeded
  by the user's name. `BatchRecommendationSelector.adoptedIndex` is the single
  implementation, used by the user agents and by the graph alike, so the
  graph records exactly what the agents chose.
- **Order.** Nodes are ordered by name, so every result depends on the run's
  content only, never on the order agents reported in.

The graph is built when the run's record is written
(`RunRecordWriter`), from what the run was evaluated against and its merged
lists. The data and analysis are always written. The layout and figures are
written when **Generate Final Graph Output** is on (the default). It costs
well under a second for a few hundred nodes.

## 4. Measures and their null models

| Measure | Definition | Null model / test |
|---|---|---|
| Homophily | Share of recommendation edges inside the target's community (for accounts: pointing to the target's own account); unweighted it is the micro-averaged precision@k | Random lists: each target's inside count is hypergeometric; the exact distribution of the sum gives a two-sided p |
| Rank-weighted homophily | The same share of the rank weights | 9,999 random rankings (seeded Monte Carlo; 1,999 above 20,000 list entries) |
| Assortativity r | Newman (2003): (Σ e_ii − Σ a_i b_i) / (1 − Σ a_i b_i) over the rank-weighted recommendation edges | Jackknife standard error over edges; z-test |
| Community recovery | Leiden communities of the recommendation network against the real ones: AMI (Vinh et al. 2010), ARI (Hubert and Arabie 1985), NMI | 999 label permutations |
| Exposure | Each candidate's position-weighted exposure Σ 1/log2(1 + rank) (Singh and Joachims 2018) | Gini and Lorenz curve, coverage, normalised entropy, hubness (skewness; Radovanović et al. 2010), Spearman ρ with community size (t-test) |
| Adoption E–I index | (external − internal) / total adopted follows (Krackhardt and Stern 1988) | Random adoption: inside with each target's share of same-community candidates; Poisson-binomial exact p |
| Before and after | Modularity of the real communities, components, the largest component, Leiden communities and their AMI with the real ones, mean path length and diameter | Observed network vs after adoption |
| Centralities | PageRank (0.85) on all layers and the attention gained over the follows alone; Brandes betweenness (exact to 4,000 nodes, else 400 seeded pivots); k-cores | — |
| Roles | Participation coefficient and within-community degree (Guimerà and Amaral 2005) in Leiden's communities of the whole network | Seven role regions |

**Saturated lists.** When k is at least the number of candidates (for example
k = 10 with 4 accounts), every list holds every candidate. Unweighted
homophily and exposure then equal chance by construction, and only the
ranking carries information. The analysis detects this, says so, and leads
with the rank-weighted measures and their permutation test.

**Community detection** is Leiden (Traag, Waltman and van Eck 2019),
maximising modularity at resolution 1. It follows the reference
implementation:
- fast local moving with a queue;
- randomised refinement of well-connected singletons (randomness 0.01);
- aggregation from the refined partition, iterated until stable;
- the best of five seeded starts.

Unlike Louvain, every community it returns is connected.

## 5. The Graph Insights tab

The tab shows the newest run by default. It follows new runs as their
records are written, and the picker opens any earlier run. Runs recorded
before run graphs existed are rebuilt from their `recommendations.csv` and
dataset (the status chip says so).

| Page | What it shows |
|---|---|
| Overview | The headline finding; six measures with their chance baselines; every finding in words with its test; what each layer holds; how the latest runs compare |
| Explorer | The graph, interactive, as described below |
| Communities | AMI, ARI and NMI with the permutation test; detected-against-real heatmap; where recommendations flow between communities (homophily on the diagonal); exposure share against community size; every detected community's purity and conductance |
| Influence | Exposure inequality (Lorenz curve, log-log distribution, Gini, coverage, entropy, hubness, size bias); most recommended, PageRank with attention gained, and brokers; the role map |
| Adoption | E–I index against random adoption; the network before and after; adoption flows; every adopted follow |
| Methods | Every definition, the parameters of this run's analysis, the files in the run record (and a button to write them), references |

The Explorer:
- **Colour by** real community, detected community, node kind, k-core or
  role; **size by** degree, PageRank, betweenness or exposure.
- **Layers and options:** toggle each of the three layers, labels, isolated
  nodes and the legend.
- **Navigation:** search; drag to pan; scroll to zoom; click a node to
  inspect it (its neighbourhood stays bright); double-click to centre it.
- **Inspector:** the node's community, centralities and role, its top-k list
  with each recommendation inside or bridging, and who it was recommended to.
- **Export:** the current view as a PNG or SVG.
- **Layout:** without a saved layout, the canvas lays the graph out itself,
  showing ForceAtlas2 settle.

Clicking a node in the Influence tables or the role map opens it in the
Explorer. The Simulator's Results page summarises the run graph and links to
the tab. Evidence Studio shows each candidate's place in the run graph:
- whether it is inside the target's community or bridges to another;
- its exposure and PageRank rank;
- the target's observed follow and its simulated adoption.

## 6. Files in the run record

Each record's `graph` folder (`RunGraphRecord`):

| File | Contents |
|---|---|
| `graph.json` | The analysis: views, tests, findings, warnings, parameters (`dsmp-run-graph/1`) |
| `nodes.csv` | id, name, kind, community, target, candidate, followers, recommended, adopted, recommendations_out, degree, pagerank, pagerank_observed, attention_gained, betweenness, core, participation, within_module_z, role, detected_community, recommendation_community, x, y |
| `edges.csv` | source, target, layer, rank, score, weight, inside |
| `graph.gexf` | GEXF 1.2 for Gephi, with positions, community colours and sizes |
| `graph.graphml` | GraphML for NetworkX, igraph, Cytoscape and yEd |
| `graph.svg`, `graph.png` | The figure, vector and 3200 × 2000 |

Doubles in the CSV files are written at full round-trip precision.
`runs-index.csv` gains twelve `graph_*` columns: homophily, its chance and p,
assortativity, community AMI, exposure Gini and coverage, adoption E–I, its
chance and p, and modularity before and after.

## 7. Layout

ForceAtlas2 (Jacomy et al. 2014), as in Gephi:
- **LinLog attraction:** its clusters are the modularity communities
  (Noack 2009).
- **Hub dissuasion:** followed accounts sit among their followers.
- **Barnes–Hut repulsion** (θ = 1.2) above 400 nodes.
- **Gephi's adaptive speeds**, then a pass that removes node overlaps at the
  figure's scale.

Starting positions are seeded by node name. The layout runs until it is calm
or reaches an iteration budget that shrinks with the graph. A graph is
therefore always drawn the same way: the record's figure, the Explorer and a
recomputation agree exactly.

## 8. Verification

- **Algorithms against NetworkX, scikit-learn and SciPy:**
  - Planted-partition and random graphs (12 cases): every value agrees to
    about 1e-14. This covers PageRank, betweenness, clustering,
    transitivity, k-cores, components, path lengths, modularity at several
    resolutions, participation and within-community degree, assortativity,
    AMI, ARI, NMI, Spearman, Gini, skewness, and the exact hypergeometric and
    Poisson-binomial distributions.
  - Leiden's partitions are never worse than the best of five Louvain runs,
    and none is disconnected.
- **Real runs:** for 18 Retail run records (nine algorithms, both modes),
  every number in `graph.json` was recomputed independently from
  `nodes.csv` and `edges.csv`. All agree to the precision they are stored
  at. On every run Leiden's modularity is at least NetworkX's best Louvain
  of ten seeds.
- **Self-tests:**
  - `NetworkAlgorithmsSelfTest`: Zachary's karate club against NetworkX.
    Leiden reaches its best known modularity, 0.4198, with connected
    communities.
  - `NetworkStatisticsSelfTest`: scikit-learn, NetworkX and SciPy values, and
    brute-force distributions.
  - `GraphLayoutSelfTest`: determinism, separation of communities with and
    without Barnes–Hut, overlap removal.
  - `RunGraphSelfTest`:
    - adoptions equal the user agents' choices;
    - homophily equals the evaluator's precision with the same chance;
    - the record reads back identically, with well-formed GEXF, GraphML and
      SVG;
    - saturated account lists are detected;
    - older records are rebuilt.
  - `GraphInsightsPanelSelfTest`: every page renders, and the Explorer's own
    layout equals the record's.

## 9. Limitations

- In the bundled datasets every user follows exactly one account, so the
  observed network is a set of stars. Its real-community modularity is the
  maximum possible, and Leiden recovers it exactly; adoptions can only lower
  it.
- The simulation adopts one recommendation per target, once. It does not
  model repeated rounds of recommendation and adoption.
- Modularity has a resolution limit: very small communities can merge in the
  detected partition. The resolution is fixed at 1 and reported.
- A rebuilt older record takes every user with a followee as a candidate,
  which can slightly overstate the run's own candidate set (users without a
  usable document).
- With few targets the tests have little power; the analysis warns below ten.

## 10. References

- Batagelj, V. and Zaversnik, M. (2003). An O(m) algorithm for cores decomposition of networks. arXiv:cs/0310049.
- Brandes, U. (2001). A faster algorithm for betweenness centrality. Journal of Mathematical Sociology 25(2), 163–177.
- Brandes, U. and Pich, C. (2007). Centrality estimation in large networks. International Journal of Bifurcation and Chaos 17(7), 2303–2318.
- Guimerà, R. and Amaral, L. A. N. (2005). Functional cartography of complex metabolic networks. Nature 433, 895–900.
- Hubert, L. and Arabie, P. (1985). Comparing partitions. Journal of Classification 2, 193–218.
- Jacomy, M., Venturini, T., Heymann, S. and Bastian, M. (2014). ForceAtlas2, a continuous graph layout algorithm for handy network visualization designed for the Gephi software. PLoS ONE 9(6), e98679.
- Järvelin, K. and Kekäläinen, J. (2002). Cumulated gain-based evaluation of IR techniques. ACM Transactions on Information Systems 20(4), 422–446.
- Krackhardt, D. and Stern, R. N. (1988). Informal networks and organizational crises: an experimental simulation. Social Psychology Quarterly 51(2), 123–140.
- Newman, M. E. J. (2003). Mixing patterns in networks. Physical Review E 67, 026126.
- Newman, M. E. J. and Girvan, M. (2004). Finding and evaluating community structure in networks. Physical Review E 69, 026113.
- Noack, A. (2009). Modularity clustering is force-directed layout. Physical Review E 79, 026102.
- Page, L., Brin, S., Motwani, R. and Winograd, T. (1999). The PageRank citation ranking: bringing order to the web. Stanford InfoLab.
- Radovanović, M., Nanopoulos, A. and Ivanović, M. (2010). Hubs in space: popular nearest neighbors in high-dimensional data. Journal of Machine Learning Research 11, 2487–2531.
- Singh, A. and Joachims, T. (2018). Fairness of exposure in rankings. Proceedings of KDD 2018, 2219–2228.
- Traag, V. A., Waltman, L. and van Eck, N. J. (2019). From Louvain to Leiden: guaranteeing well-connected communities. Scientific Reports 9, 5233.
- Vinh, N. X., Epps, J. and Bailey, J. (2010). Information theoretic measures for clusterings comparison. Journal of Machine Learning Research 11, 2837–2854.
- Watts, D. J. and Strogatz, S. H. (1998). Collective dynamics of 'small-world' networks. Nature 393, 440–442.
