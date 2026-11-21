# Federated MLP

The MLP can train by federated averaging (FedAvg) instead of as one central
model. Federated training is off by default: an MLP run trains centrally and
gives exactly the results it gave before unless a custom parameter profile
selects it. This document covers:

- what the federated mode computes;
- why its results are identical on every cluster;
- how it runs on the nodes and what it costs on the emulated network;
- its settings and how to switch it on;
- what a run records;
- how it was validated;
- its limitations.

## 1. What it is for

V3 trained one MLP per node on that node's users and averaged the weights
once. V4 replaced this with one central model, because the averaged model
depended on how the users were split over the nodes: a different node count
gave different recommendations, so a timing comparison between two clusters
also compared two models.

The federated mode brings distributed training back without that defect. It
gives the scheduler a workload the central MLP cannot: the central MLP is one
work unit, while a federated run is a sequence of rounds, each with one unit
per client, separated by a synchronised aggregation. That is the pattern of
federated learning and of data-parallel training with periodic averaging.

## 2. The algorithm

### 2.1 The federation

Everything up to the model's first weights is the central softmax MLP's:

1. The targets are held out of training. Up to 20% of the labelled users they
   share one model; above that they are split into five folds, and each fold
   is predicted by a model trained without it (`TargetFolds`).
2. For each fold, the labelled users of the controller's training split are
   divided into 85% for fitting and 15% for validation, stratified by class.
   The users of the controller's test split only measure accuracy.
3. The network (tanh hidden layers, softmax output, cross-entropy loss) starts
   from the central engine's seeded weights for that fold.

The fitting users are then dealt to K **clients**. A client is a fixed set of
users. It is decided by the data and the federation's seed alone, never by
the cluster, so the federation is the same on one node and on thirty-two.

- **IID** (default): every class is shuffled by the seed and dealt to the
  clients in turn, continuing the turn from one class to the next. Clients
  differ by at most one user and share every class almost evenly.
- **Label-skewed**: every class is dealt in proportions drawn from a
  symmetric Dirichlet distribution with concentration alpha [3]. A small
  alpha gives each client a few classes and very different sizes; a large one
  approaches the even deal. Clients that receive nobody are dropped.

A fold never has more clients than fitting users.

### 2.2 A round

In round t, for each fold that is still running:

1. **Participants.** All clients train, or a fraction C of them drawn without
   replacement by the seed (at least one).
2. **Local training.** Each participant k copies the fold's global model w
   and runs E epochs of stochastic gradient descent over its own users, one
   user at a time, each epoch in an order drawn from a seed of (fold, round,
   client). The loss is the cross-entropy with L2 and, when mu is positive,
   the FedProx term (mu/2)|w_k - w|^2, which keeps a client near the model it
   started from [2]. As in the central engine, L2 and the proximal term act
   on a first-layer weight only in the steps whose user has that weight's
   feature. The client returns its update, w_k - w.
3. **Aggregation.** The server forms the new global model

   w + sum_k (n_k (w_k - w)) / sum_k n_k

   where n_k is the number of users client k trained on [1]. The sum runs
   over the participants in client order.
4. **Validation.** The server measures the new model's mean cross-entropy on
   the fold's validation users. A model that beats the best so far by more
   than 1e-12 becomes the fold's best model.

A fold stops after `patience` rounds in a row without a new best model, or
after R rounds. Its targets are scored by its best model, as the central
engine scores them by its best epoch. Without validation users the fold runs R
rounds and is scored by its last model.

With one client, every client training and mu = 0, a round is E epochs of
ordinary training, up to rounding in the last place.

### 2.3 Sparse updates

Local training changes a first-layer weight only for a feature that occurs in
one of the client's users. A client therefore copies a first-layer row only
when it first writes it, and its update carries only those rows. Both grow
with the client's own vocabulary, not with the corpus's. A first-layer row no
participant touched is exactly unchanged by a round.

## 3. Why the results are exact

A client's update is a function of four things: the fold's global model, the
client's users, the hyper-parameters and a seed derived from (fold, round,
client). It does not depend on which node computes it, when, or on which
lane. A unit that is retried, stolen, drained or lost in a crash computes the
same update again, and the dispatcher commits it exactly once.

The server adds the updates in client order, so the average does not depend
on the order they arrive in or on which node held them. It refuses a round
with a missing or doubled update. Every decision the nodes must agree on (who
trains next, which folds go on) is part of the aggregation's result, which
every node receives as the same object.

Before the first round every node submits a fingerprint of its inputs
(targets, splits, classes, vocabulary, every user's label and document, and
the settings). The federation is planned only if all fingerprints are equal.
Whether the run trains centrally or by federated averaging is itself decided
once per run, by the first node that reaches the MLP, so the nodes cannot
split between the two.

`FederatedMlp.runSequential` runs the whole federation in one thread. Every
scheduled run must reproduce its models and scores bit for bit; section 7
lists the checks.

The kernel sums a user's features in ascending feature order. With the same
order, the same seed and the same starting weights it reproduces the central
engine's local training bit for bit, which is also checked.

## 4. How it runs on the cluster

`FederatedMlpStage` runs the federation on the nodes:

| Step | What happens | Scheduled as |
|---|---|---|
| Corpus | The nodes combine their TF-IDF vectors into the run's corpus, as for the central MLP. | Collective `mlp-training/corpus` |
| Plan | Every node submits its input fingerprint; the coordinator checks them and plans the federation once. | Collective `mlp-federated/plan` |
| Round t | One work unit per participating client, placed and moved by the run's policy like any other unit. | Phase `mlp-federated-r001`, `-r002`, ... |
| Aggregation t | Every node uploads the updates of the clients it trained; the coordinator averages and validates; every node downloads the new global models. | Collective `mlp-federated-r001/aggregate`, ... |
| Scoring | One unit measures test accuracy and scores every target. The node that commits it publishes the scores once. | Phase `mlp-federated-scoring` |

With five folds a round holds the clients of every running fold, up to 5 K
units.

**Units.** A client unit has no home node: after the corpus step every node
holds every user's vector. Its estimated cost grows with its users, their
features and the network's size (constants fitted to the kernel, within about
30%), so cost-aware policies see that label-skewed clients differ in size.
The scoring unit is homed on node 1, as the central MLP's unit is.

**Network.** A round costs what it would cost a real federation. Each update
is charged at its wire size (its touched first-layer rows, the upper layers
and the biases, 8 bytes per value) on its node's uplink. Each new global model
is charged in full on every node's downlink.

**Memory.** Each node takes one lease from the model memory gate for the
whole stage. Its reservation covers the node's share of the plan's rows, of
every fold's models and of a round's updates, and the clients its lanes are
training. The nodes meet in a collective after every round, so they are
admitted together (see `DSMP_V4_NODE_LAB.md`, Platform fixes).

**Controls, faults and crashes** act on a round's phase as on any unit phase.
A node that is down holds up the aggregation until it is repaired, as it holds
up every collective.

## 5. Settings

Open **Simulator › 2 Model › Edit parameters** with MLP selected, switch on
the custom profile and set **Training** to **Federated averaging**. The
federation's controls are below it. Federated training needs the softmax
engine; the Neuroph engine trains centrally.

| Setting | Property | Default | Range |
|---|---|---|---|
| Training | `dsmp.mlp.training` | `central` | `central`, `federated` |
| Clients per fold | `dsmp.mlp.federated.clients` | 8 | 1 to 256 |
| Rounds | `dsmp.mlp.federated.rounds` | 40 | 1 to 1000 |
| Local epochs | `dsmp.mlp.federated.localEpochs` | 5 | 1 to 100 |
| Clients per round | `dsmp.mlp.federated.clientFraction` | 1 | 0.01 to 1 |
| Client data | `dsmp.mlp.federated.partition` | `iid` | `iid`, `dirichlet` |
| Dirichlet alpha | `dsmp.mlp.federated.dirichletAlpha` | 0.5 | 0.01 to 1000 |
| Proximal term mu | `dsmp.mlp.fedProxMu` | 0 | 0 to 10 |
| Patience (rounds) | `dsmp.mlp.federated.patience` | 5 | 0 to 1000; 0 runs every round |
| Federation seed | `dsmp.mlp.federated.seed` | 1 | 0 or more |

Hidden layers, hidden neurons, the learning rate and L2 are the MLP's own
settings and apply to both trainings. The softmax epoch limit applies to
central training only.

A session without a window (a study run by `python build.py study`, a test
harness) has no dialog. It takes its first settings from system properties
when `dsmp.algorithm.custom=true` is among them:

```bash
JAVA_TOOL_OPTIONS="-Ddsmp.algorithm.custom=true -Ddsmp.mlp.training=federated \
  -Ddsmp.mlp.federated.clients=8" python build.py study Studies/my-study.study
```

An ordinary launch sets none of these properties and starts with the default
profile.

A study uses the settings the Simulator has when it starts, for this as for
every other algorithm parameter.

## 6. What a run records

- `manifest.json` lists the federation's settings among the run's properties
  (`mlpTraining`, `mlpFederatedClients`, ...). A central run has none of them.
- `federated-mlp/report.json` holds the settings, every fold (clients and
  their sizes, rounds, best round, the SHA-256 of its final model) and every
  round.
- `federated-mlp/rounds.csv` has one line per fold and round: clients and
  users that trained, bytes uploaded, the size of the new global model,
  validation loss and accuracy, whether the round was a new best, and the
  largest change any client made to a weight.
- `phases.csv`, `units.csv` and `collectives.csv` hold every round's phase,
  client unit and aggregation, as for any run.

Two runs with equal model fingerprints trained bit-identical models.

**Reading the Node Lab's measures.** A federated run has many unit phases.
The "main phase" measures (makespan, efficiency, imbalance) describe one
round, the one with the most units. The whole training is the **model stage**
(`stage_model_ms`), which includes every round and every aggregation; use it,
or the run's makespan, to compare policies in a study.

**Scenarios.** The phase alias `mlp` names the central MLP's phase. A
federated run never opens that phase, so a step that names it does not fire
there. A step for a federated run names a round by its key (`at 40% of
mlp-federated-r003 crash node 2`) or names no phase (`at 40% pause node 1`
acts on the phase running at that moment). A scenario used for both trainings
should name no phase.

## 7. Validation

Self-tests (run by `python build.py test`):

- `FederatedMlpSelfTest`, without a cluster:
  - the kernel's prediction, loss and local training equal the central
    engine's bit for bit (one to three hidden layers, with and without L2 and
    the proximal term);
  - the average equals its definition bit for bit, including rows no client
    touched;
  - local training leaves the global model unchanged;
  - both partitions deal every user exactly once, with no empty client;
  - the plan's folds, splits and first weights are the central engine's;
  - the federation is deterministic and independent of the order clients
    train and report in and of which node owns which user;
  - it learns a separable fixture, scores every target in five folds, stops
    early at the right round and keeps the best model;
  - the input fingerprint changes with every input (a setting, a target, one
    value of one document) and with nothing else (the order of the targets,
    of a document's terms or of the nodes that own the users);
  - each run keeps its own report until its record is written, and
    `report.json` and `rounds.csv` hold the run.
- `FederatedMlpStageSelfTest`, on clusters. Three federations (one model; five
  folds; sampled, label-skewed clients with a proximal term and early
  stopping) each run on:
  - one and three unemulated nodes;
  - two equal nodes with static equal split;
  - strong, normal and weak nodes with capacity weighting (two lanes on the
    strong node);
  - four nodes with work stealing;
  - eight nodes with the queue-length policy (more nodes than clients);
  - the unreliable preset;
  - two nodes that lose 35% of their executions and crash about once a second;
  - a scripted crash, drain, policy switch and pause;
  - all twelve policies, for the first federation.

  Every run must end in the reference's state and scores bit for bit, commit
  exactly the federation's client units and open one phase per round. Nodes
  given different inputs must stop at the plan.
- `AlgorithmParameterSettingsSelfTest`: federated training is off by default
  and ignored by the default profile; its values are validated and survive the
  property round trip.

### 7.1 Live runs

`LiveExactnessHarness` runs the whole application without its window and
compares every configuration's recommendations with the one-node run's. Its
nine configurations are: one node; three nodes in the original DSMP mode; three
emulated nodes (strong, normal, weak) with capacity weighting; four with work
stealing; two with the queue-length policy and injected faults; three under
live operator controls; three with the original DSMP placement on the emulated
cluster; three with a scripted crash, drain and boost; four with the
failure-aware policy, crash processes and faults.

| Dataset | Targets | Federation | Configurations | Recommendations |
|---|---:|---|---:|---|
| Retail | 40 (one model) | defaults | 9 | identical |
| Retail | 218 (five folds) | defaults | 9 | identical |
| Retail | 40 | label-skewed (alpha 0.3), 3 of 6 clients per round, mu 0.01, 6 rounds without early stopping, 2 hidden layers of 16 | 9 | identical |
| Retail | 40, Similar Users mode | defaults | 6 | identical |
| Reduced_94k | 40 (five folds), 1,424 MB heap | defaults | 4 | identical |
| Retail | 40 | 300 rounds without early stopping (301 phases, 2,401 units) | 3 | identical |
| Retail | 40 | 10 and 300 rounds on three nodes with static equal split, two of which lose 35% of their executions and crash about once a second | 2 | identical to the runs above |
| Retail, as a study | 218 | defaults; 2 clusters x 5 policies x 3 repetitions and a warm-up | 31 runs | identical |

In the harness's own fault and crash configurations the policies gave the
unreliable node no client, so no client unit was lost there: crashes and live
controls hit the runs (up to 34 crashes, one revoked execution), injected
faults did not. The row with two faulty nodes forces it: its 300-round run
lost 807 executions and had 3 crashes, and committed each of its 2,401 units
once.

The central MLP and six other algorithms (Similarity, Common-Neighbors,
K-Means, K-Means Euclidean, SVM, TGF) were run on the code before and after
the federated mode was added. Their result hashes are equal, and a central
run's record has the same files and manifest fields.

`FederatedMlpStageSelfTest` was also run 30 times, six at once, to look for
races: 30 passes.

### 7.2 Quality

Central and federated training on the same targets, splits and network
(1 hidden layer of 10 neurons, rate 0.1, L2 0.0001; k = 3):

| Dataset, targets | Training | Test accuracy | MRR | Top-1 |
|---|---|---:|---:|---:|
| Retail, 40 | central | 58.00% (29/50) | 0.8333 | 72.5% |
| Retail, 40 | federated | 56.00% (28/50) | 0.8208 | 70.0% |
| Retail, 218 | central | 54.69% (140/256) | 0.7263 | 52.3% |
| Retail, 218 | federated | 59.38% (152/256) | 0.7592 | 57.8% |
| Reduced_94k, 40 | central | 16.67% (8/48) | 0.6479 | 42.5% |
| Reduced_94k, 40 | federated | 22.92% (11/48) | 0.6021 | 35.0% |

Neither training is consistently better. On Retail the federated folds stop
after 9 to 21 rounds, with their best models at rounds 4 to 16. Reduced_94k
has 17 to 19 fitting users per fold, so its clients hold two or three users;
both trainings are near chance on its test users (25% for four classes), and
four of its five federated folds were still improving at the round limit.

### 7.3 Time

Retail, 218 targets (five folds, 21 rounds, 520 client units), as a study with
three repetitions per cell. The table gives the **model stage** in
milliseconds of model time; the central MLP's is given for scale.

| Cluster | Training | Original DSMP | Static equal split | Capacity-weighted | Work stealing | Queue-length |
|---|---|---:|---:|---:|---:|---:|
| Four nodes, one twice as fast; no latency, 1 Gbit/s | central | 48 | 54 | 51 | 77 | 82 |
| | federated | 172 | 182 | 200 | 206 | 201 |
| Strong, normal, weak, weak (weak: half speed, 25 ms, 100 Mbit/s) | central | 203 | 206 | 206 | 206 | 201 |
| | federated | 8,020 | 8,038 | 3,104 | 2,453 | 1,918 |

- Without link latency a round costs about 9 ms and the policies are within
  20% of each other: a client unit is about 0.2 ms of work.
- With slow, latent links the policies differ by a factor of four. A weak
  node's queue holds one unit, so it gets its next unit only after reporting
  the last: a 50 ms round trip on its 25 ms link. The placements that give
  each weak node a quarter of the units (original DSMP, static equal split)
  spend about ten such round trips per round on it while all five folds run.
  Capacity weighting, work stealing and the queue-length policy give the weak
  nodes fewer units.
- No policy goes below about 90 ms per round on that cluster: the aggregation
  waits for every node's upload and sends every node the new models, 0.5 MB
  over the weak nodes' links.

On Reduced_94k (74,623 features) a fold's model is 6 MB, so a five-fold round
sends 30 MB to every node. Forty rounds moved 3.1 to 5.1 GB, and the model
stage took 85 to 96 s of model time on the three emulated clusters tried
(two to four nodes, each cluster with a 100 Mbit/s node) against 2.5 s on one
node; the central MLP took 2.8 to 4.7 s. The 1,424 MB heap was enough.

## 8. Limitations

- **The clients are logical.** The corpus step gives every node every user's
  vector, as the central MLP needs for corpus-wide TF-IDF. The mode reproduces
  the computation and the communication pattern of federated averaging; it
  does not keep any user's data on one node, and it models no privacy
  mechanism (secure aggregation, differential privacy).
- **No client drops out.** A round waits for every participant. Dropping slow
  clients would make the model depend on timing.
- **The batch size is 1**, as in the central softmax engine.
- **Small datasets give small units.** On Retail (218 users, 1,359 features)
  a client's round is about 0.2 ms of work, so a round's time is almost all
  communication and coordination: handing out the units, uploading the
  updates, sending the new models. Policies then differ through the units
  they give to nodes with slow links, not through the work itself
  (section 7.3). More hidden neurons, layers and local epochs, or a larger
  dataset, make the units heavier.
- **Large vocabularies give large models.** Every round sends each running
  fold's whole model to every node. With tens of thousands of features and
  five folds that is tens of megabytes per node and round, and a slow link
  sets the pace (section 7.3). Fewer rounds with more local epochs, a smaller
  network, or targets of at most 20% of the users (one model instead of
  five) reduce it.
- **The phase replay starts with idle links.** The Node Lab's replay of a
  phase does not know that a slow link may still be delivering the new global
  model when the next round opens. On clusters whose links differ it predicts
  such rounds too short (by the time that delivery still needed); the live
  times are right. On rounds of a millisecond or two the replay's relative
  error is also dominated by the host's thread scheduling.
- **Every node receives every running fold's model each round**, whether or
  not it trains a client of that fold, and the whole model rather than the
  rows that changed.

## 9. Files

| File | Contents |
|---|---|
| `FederatedMlp.java` | The algorithm: settings, the plan (folds, clients), a round, the aggregation, scoring, the one-thread reference. |
| `FederatedMlpStage.java` | The federation on the nodes: plan, round phases, aggregation collectives, scoring phase. |
| `FederatedMlpReport.java` | The run record's `federated-mlp` folder. |
| `SparseFederatedMlpModelSupport.java` | The softmax network; `localUpdate` and `plusAverage` are the federated kernel. |
| `AlgorithmParameterSettings.java`, `AlgorithmSettingsDialog.java` | The settings and their dialog rows. |
| `Test/FederatedMlpSelfTest.java`, `Test/FederatedMlpStageSelfTest.java` | The self-tests of section 7. |

## 10. References

1. H. B. McMahan, E. Moore, D. Ramage, S. Hampson and B. Agüera y Arcas,
   "Communication-efficient learning of deep networks from decentralized
   data," AISTATS 2017.
2. T. Li, A. K. Sahu, M. Zaheer, M. Sanjabi, A. Talwalkar and V. Smith,
   "Federated optimization in heterogeneous networks," MLSys 2020.
3. T.-M. H. Hsu, H. Qi and M. Brown, "Measuring the effects of non-identical
   data distribution for federated visual classification," arXiv:1909.06335,
   2019.
4. G. Marsaglia and W. W. Tsang, "A simple method for generating gamma
   variables," ACM Transactions on Mathematical Software 26(3), 2000.
