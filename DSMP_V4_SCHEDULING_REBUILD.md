# DSMP V4 Scheduling, Capacity Emulation and Exactness

This document describes the rebuilt scheduling layer of DSMP V4: node capacity
profiles, scheduling policies, live workload controls, and the changes that make
every recommendation algorithm exact regardless of how the work is split across
nodes. It states the guarantees, the models behind them, the decisions that
changed algorithm outputs, and how each guarantee was verified.

## 1. The contract

For a fixed dataset, algorithm, parameter settings and seeds, the final merged
recommendations (every target's ranked candidates and their scores) are
**bit-for-bit identical** for:

- any number of recommender nodes;
- any placement of users onto nodes (original DSMP list scheduling or any policy);
- any node capacity profile (speed, lanes, memory, queue, link delay, failure rate);
- any scheduling policy, including switching policy mid-run;
- any live operator action (pause, resume, unavailable, drain, redirect,
  capacity scale, action window) at any moment;
- any pattern of injected or real unit failures that the retry budget absorbs.

Scheduling changes *where and when* work runs and therefore *how long* a run
takes; it never changes *what* is computed. This makes V4 a controlled
experiment: differences between two runs of the same experiment are caused by
the scheduling configuration alone.

## 2. Architecture

| Component | Role |
|---|---|
| `SchedulingRunContext` | Everything scheduling-related about one run, frozen at initialization: plan mode, policy, node profiles; live controls; open work phases; collectives; input routing; per-node emulators. |
| `RecommendationSchedulerRegistry` | Holds the plan for the next run and freezes it into a context (`beginRun`). |
| `SchedulingRuntime` | Registry of run contexts by run id (the agents' conversation id). |
| `NodeSpec` | One node's emulated capacity derived from its profile. |
| `WorkUnitSpec` | One unit of work: id, key, estimated cost, memory, priority, deadline, home node, allowed nodes, failure budget. |
| `PlacementPlanner` | Static placement of units (all static policies and the original DSMP rule). |
| `WorkDispatcher` | The live scheduler of one phase: unit states, reservations, exactly-once commit tokens, retries, drains, redirects, policy switches. |
| `WorkPhase` | Thread-safe blocking façade over a dispatcher for node lanes; stall detection; run abort. |
| `NodeWorkRunner` | Runs one node's lanes over a phase: claim, transfer, execute, dilate, commit. |
| `NodeEmulator` | Makes a node's profile physically true in a live run (speed, link delay). |
| `RunCollectives` | All-gather / all-reduce / barrier among the run's nodes, combining contributions once in node order. |
| `ExactSum` | Exact, order-independent summation of doubles (correctly rounded). |
| `SchedulingSimulator` | Discrete-event simulation of a phase for the Scheduler Lab and tests. |
| `GlobalCorpus`, `CandidateShardPlan`, `GlobalTopRows`, `DistributedKMeans` | Building blocks of the exact algorithms (section 5). |

One policy implementation serves the Scheduler Lab, the Dynamic Queue Lab, the
population sweep and live runs: the labs simulate exactly the dispatcher the
live runtime uses.

## 3. Scheduling model

**Units and phases.** Every model stage that can be split is a *phase* of work
units. Each unit is committed exactly once: a claim receives a token, a
completion commits only if the token is still live (a drain or a redirect of a
running unit revokes it and the late completion is discarded as stale), and only
the committing node publishes the unit's result. A unit that fails is retried,
elsewhere when the policy allows, until its failure budget is exhausted; the
phase then fails the run with the reason instead of hanging. A phase whose
remaining units cannot make progress for the stall timeout also fails with a
reason.

**Execution modes.** Static policies plan every unit onto a node when the phase
opens (each node then works through its own queue). Dynamic policies keep a
shared backlog that idle lanes pull from (Queue-length, Deadline/priority,
Memory-aware, Failure-aware), or start from the capacity-weighted plan and let
idle nodes steal (Work stealing).

**Policies.**

| Policy | Rule |
|---|---|
| Static equal split | contiguous blocks of equal size in canonical order |
| Round robin | unit k to node k mod N |
| Weighted round robin | smooth weighted round robin, counts proportional to throughput |
| Least loaded node | Graham list scheduling on assigned cost (ignores speed), 2 - 1/m |
| Fastest node first | earliest free node, ties to the fastest (ignores unit size) |
| Capacity-weighted | LPT-EFT on uniform machines: largest units first, each to its earliest finish |
| Memory-aware safe-wave | largest memory first; a unit starts only where it fits the free budget |
| Queue-length | idle lanes pull; simultaneous requests favour the shortest node queue |
| Work stealing | capacity-weighted plan, idle nodes steal from the node with most remaining work |
| Deadline / priority | highest priority, then earliest deadline; urgent units to the fastest idle node |
| Failure-aware rescheduling | retries move away from failing nodes; placement by reliable throughput t / (1 - p) |

**Unit cost estimates.** Placement uses estimated unit costs in milliseconds:
candidate shards (1 + non-zero terms) x targets x 5e-5 ms; SVM target models
400 ms (worker start) + 1e-3 ms x corpus non-zeros x classes; MLP training the
worst case of the epoch limit at 8e-6 ms per weight update (measured on the
legacy engine); Doc2Vec shards and temporal-graph targets are equal-sized
units. Estimates only steer placement; they never affect results.

**Input delivery.** How input reaches the nodes (per-user agents, or the
corpus streamer for large datasets) depends only on the dataset size and the
algorithm, never on the scheduler plan, so an experiment's original and
scheduled modes differ in scheduling alone.

**Original DSMP (baseline) mode.** Placement of input users is the V3 rule
(each user to the node with the smallest assigned row count, in list order),
units that have a home node may run only there, and nothing is emulated. This
reproduces V3's work distribution while computing the exact results.

## 4. Capacity emulation

All nodes are threads of one JVM, so without emulation every node runs at
hardware speed and capacity profiles would only be labels. In the
capacity-emulated and fault-injection modes the profile is made physically true:

- **Speed.** A node's relative speed is
  `speed = cpu x scale / max over nodes(cpu x scale) / slowdown`. After each
  piece of node work W (thread CPU time, so waiting is never counted) the node
  sleeps `W x (1/speed - 1)`; a node of speed 0.25 takes four times as long.
  Work done in a worker process (Weka SMO, Doc2Vec scoring) is reported by the
  process and dilated the same way. Work spans nest; each nanosecond is dilated
  once, and a node settles its pending work before every synchronization point,
  so a slow node reaches barriers late, as a slow machine would.
- **Lanes.** A node runs as many units at once as its profile has lanes.
- **Memory.** Units may declare memory, and the memory-aware policy admits a
  unit only where it fits the node's free budget. In live runs the input users
  carry their estimated retained memory (placement respects node budgets);
  model-stage units declare none, because their heavy memory lives in worker
  processes that the worker memory coordinator admits against the machine.
- **Queue capacity.** The most units a node may hold claimed at once.
- **Network.** A star network: each node's link has a one-way delay d.
  Messages to the node become ready d after they arrive and are handled in
  arrival order; messages in flight overlap, as on a real link, so latency
  never blocks the node (only its own work does). Acknowledgements leave the
  same way, so the corpus streamer's stop-and-wait batches see a round trip
  of 2d plus processing. Synchronous exchanges (collective upload and download,
  the corpus-wide document-frequency exchange, publication, and moving a unit
  away from its data) wait the delay, because the node needs the reply or the
  data before it can continue.
- **Failures.** In fault-injection mode each unit execution fails with the
  node's failure probability, decided by a seeded hash of (run seed, phase,
  unit, execution, node), so a fault run is reproducible.

Every emulated nanosecond is recorded per node (compute, speed emulation,
network emulation, interactive pacing) and printed with the run.

*Fidelity condition.* The emulation is faithful when the machine has a core for
every busy lane; the timing report says when it does not. Work is measured with
the JVM's thread CPU clock, which on Windows advances in 15.6 ms steps, so there
the slowdown of work shorter than a step is approximate.

## 5. Exactness of each algorithm

Common rules: every user has exactly one **owner** node (the node that received
its tweets, or the controller's planned node for input that carried no owner),
and only the owner contributes the user to any corpus-wide computation. TF-IDF
uses corpus-wide document frequencies (integer counts summed across nodes), so a
user's vector is the same on every node. Everything order-dependent is put in a
canonical order (user name, term, class), and sums that cross nodes are exact
(`ExactSum`). Every node cuts rows at the same requested top-N, agreed once per
run.

| Algorithm | Construction |
|---|---|
| Similarity, Common-Neighbors | Candidates are cut into shards homed on their owners (units of the candidate phase). Each committed shard's scores are cut to its top L; the nodes then exchange rows and each keeps its part of the exact global top L (`GlobalTopRows`). |
| Doc2Vec | The shared model's index is cut into contiguous shards (one per node in the original mode, four per lane otherwise), scored in one persistent worker process per lane; each shard returns its top n by (score, name); global top n as above. |
| K-Means (cosine and Euclidean) | Each node assigns only its own users; seeds are chosen by global arg-max searches, centroids are exact sums combined in node order, convergence is a global vote (`DistributedKMeans`). The clustering is the one-node clustering. |
| Temporal Graph Foundation | Profiles, the temporal dataset scan and the graph context are prepared once from the whole run; each target is a unit scored against every candidate; its row is published by the committing node. |
| Exact SVM (every mode) | One Weka SMO model per target (a unit), trained on the stratified split of every other labelled user of the whole run, corpus-wide vocabulary as attributes and every followee as a class. |
| MLP (legacy and sparse) | One training unit on the controller's central split of the whole run, every target held out; the committing node scores every target and publishes once. |
| Scalable SVM | One shared model for the whole run (its coordinator, unchanged). |

### Decisions that change outputs relative to V3/earlier V4

These are deliberate corrections, not side effects:

1. **Held-out targets in the classifiers.** Earlier versions trained the SVM
   (except the guided mode) and the classic MLP with the target's own followee
   label in the training set, which leaks the answer into the model. Now a
   target is never in its own model's training or test data: SVM uses one
   leave-target-out model per target, MLP holds all targets out of its single
   model. Reported classifier accuracies are therefore honest held-out values.
2. **One model, not an average.** The MLP no longer averages per-node models
   (FedAvg), whose result depended on the partition; the population sweep's SVM
   is leave-target-out as well.
3. **Reproducible legacy MLP.** Neuroph drew initial weights from an unseeded
   generator, so two runs never trained the same network; weights now come from
   a fixed seed, and an epoch limit ends training when the maximum error cannot
   be reached (reported when hit).
4. **Every target scored.** The legacy MLP skipped targets without a followee
   label and then attributed rows to the wrong targets; every target with a
   document is scored now.
5. **Top-L publication for every node count.** Similarity and Common-Neighbors
   publish the exact global top L (L at least the requested top-N) for every
   node count; one node used to publish every candidate. The ranked
   recommendations up to L are unchanged by this.
6. **Canonical order and exact sums.** Where earlier versions summed in arrival
   order, sums are now exact; scores can differ from earlier versions in the
   last bits, and exact ties are broken by name.

## 6. Live workload controls

Controls act on the run's open phases and on input routing, immediately:

- **Pause**: the node claims no new units, holds at its next phase boundary,
  and its not-yet-routed input users wait.
- **Unavailable**: the node takes no new units and no new input users; its data
  stages keep running (it still holds data).
- **Drain**: unavailable, plus its running units are revoked (they restart
  elsewhere; late completions are discarded) and its future input users go to
  another node (the chosen target when eligible).
- **Redirect**: moves not-started units to a node and sends it the next input
  users; nobody else waits.
- **Capacity scale**: changes the node's speed for all future work at once.
- **Policy override**: switches every open phase's policy for units that have
  not started.
- **Action window**: every claim waits a fixed time so controls can be tried;
  runs with an action window are marked as not comparable for timing.

Input is routed in batches of whole users: a user's rows never split across
nodes, and a user's receiver is decided once. Replicated users (targets) go to
every node and wait only while a node is paused.

## 7. Verification

**Unit and property tests** (all in `TwitterGatherDataFollowers/Test`, all
passing): `SchedulingKernelSelfTest` (exactly-once commits, liveness, work
conservation, memory admission, bounds; mutation-tested), `ExactSumSelfTest`
(against BigDecimal), `DistributedKMeansSelfTest` (bitwise equality across node
counts and random partitions, cross-check against the single-node Euclidean
recommender), `ExactModelStagesSelfTest` (global top-L exchange equals one-node
top L for random partitions and ties, shard plans, global corpus invariance,
indexed ARFF rows, emulated-span accounting), `LiveWorkloadAdjustmentSelfTest`,
`LiveWorkloadRuntimeSelfTest`, `NodeCapacitySchedulerSelfTest`,
`NodeCapacityWeightingSelfTest`, `CorpusStreamingAgentSelfTest`,
`SchedulerLabEngineSelfTest`, `DynamicQueueSchedulerEngineSelfTest`, and the
existing suite.

**Live end-to-end check** (`LiveExactnessHarness`): boots the real JADE system
with the window hidden, loads a dataset, and runs each algorithm in:

1. 1 node, original DSMP (reference);
2. 3 nodes, original DSMP;
3. 3 nodes, capacity-weighted, strong / normal / weak profiles;
4. 4 nodes, work stealing, weak / strong / normal / weak;
5. 2 nodes, queue length, fault injection (unreliable node);
6. 3 nodes, queue length, a live operator pausing, redirecting and draining
   nodes during the run.

Every run's final recommendations are compared with the reference bit for bit.
Results are listed in section 9.

Run it from a scratch directory (the simulator writes its reports into the
working directory), with the compiled classes and `lib/*.jar` on the class path:

```
java -Xmx6g -Ddsmp.gui.hidden=true -Drecommender.python.command=.venv/bin/python \
  [--add-opens flags as in build.py] -cp <classes>:<lib jars> \
  TwitterGatherDataFollowers.userRyersonU.LiveExactnessHarness \
  Dataset/Retail.txt SIMILARITY,COMMON_NEIGHBORS,KMEANS,KMEANS_EUCLIDEAN,SVM,MLP,DOC2VEC,TGF 4
```

`-Ddsmp.max.direct.text.user.agents=1` makes supported algorithms deliver input
through the corpus streamer, which is what live input routing acts on.

## 8. Limitations

- Timings are emulated on one machine; see the fidelity condition in section 4.
- K-Means runs as synchronized collective steps rather than queue units, so its
  live controls act at phase boundaries and through node speed, not on units.
- The Scalable SVM keeps its shared coordinator and is not split into units.
- Input routing controls apply to input delivered by the corpus streamer
  (datasets above `dsmp.max.direct.text.user.agents` users); per-user agents
  deliver to their planned node.

## 9. Results

Live end-to-end runs on `Dataset/Retail.txt` (20,456 tweets, 218 users,
4 followees), Java 20, one machine, input streamed
(`-Ddsmp.max.direct.text.user.agents=1`), legacy MLP limited to 300 epochs in
every run (`-Ddsmp.mlp.maxEpochs=300`), SVM and Doc2Vec with 4 targets. Each
cell compares every target's full ranked list and scores with the 1-node
original-DSMP reference, bit for bit.

| Algorithm | 3 nodes original | 3 nodes capacity-weighted | 4 nodes work stealing | 2 nodes fault injection | 3 nodes live operator |
|---|---|---|---|---|---|
| Similarity | identical | identical | identical | identical | identical |
| Common-Neighbors | identical | identical | identical | identical | identical |
| K-Means | identical | identical | identical | identical | identical |
| K-Means Euclidean | identical | identical | identical | identical | identical |
| Temporal Graph Foundation | identical | identical | identical | identical | identical |
| Exact SVM | identical | identical | identical | identical | identical |
| Scalable Linear SVM | identical | identical | identical | identical | identical |
| MLP (legacy engine) | identical | identical | identical | identical | identical |
| Doc2Vec | identical | identical | identical | identical | identical |

45 of 45 comparisons identical. Capacity profiles: strong / normal / weak
(relative speeds 1.0 / 0.5 / 0.25) and an unreliable node (100 ms link,
injected failures). In the live operator run node 2 was paused and resumed,
units and input users were redirected to node 1, node 3 was drained to node 1,
node 1's capacity was halved, the policy switched to work stealing and node 3
returned, all while the run was in flight (actions that arrive after the run
has finished are refused and logged).

The converted target modes use different merge paths (classifier-guided user
evidence; followee projection by maximum), so they were checked the same way
against their own 1-node reference, in the capacity-weighted, fault-injection
and live-operator configurations:

| Target mode | Algorithms | Result |
|---|---|---|
| Similar Users | Exact SVM, Scalable Linear SVM, MLP | identical (9 of 9) |
| Followee Accounts | Similarity, Common-Neighbors, K-Means, Doc2Vec, Temporal Graph Foundation | identical (15 of 15) |

In total 69 of 69 live comparisons were bit-identical.

## 10. Defects fixed along the way

Beyond partition-dependence of results, the audit and the live runs found and
fixed these defects:

| Defect | Effect | Fix |
|---|---|---|
| Three disagreeing policy implementations (labs vs runtime) | Lab predictions did not describe live runs; 11 policy names mapped to 6 behaviours | One planner and one dispatcher for labs, sweep and live runs |
| Capacity profiles had no physical effect in live runs | "Slow" nodes ran at full speed | Live emulation of speed, lanes, memory, queue, link delay, failures |
| Drain turned into a global redirect with no timeout; replicated input blocked by drained nodes | Runs could hang | Drains revoke and rebalance; replicated input goes to every node, held only by pause |
| Input redirects split a user's rows across nodes | Duplicate, partial documents | Batches of whole users, one receiver per user decided once |
| Nodes ended input by expected tweet counts while users could move | Premature text processing | Streamed runs end input on the acknowledged delivery-complete notice |
| Failed shards never retried; one Doc2Vec failure discarded completed shards | Lost or empty results | Per-unit retries with failure budgets, exactly-once commits |
| Dynamic Queue Lab hangs (stranded retries, memory livelock) | Lab never finished | Dispatcher liveness and memory admission, mutation-tested |
| Classifier target label leakage | Inflated accuracy, self-fulfilling predictions | Held-out targets (section 5) |
| Unseeded Neuroph weights, no epoch limit, target row misalignment | Irreproducible MLP, possible endless training, wrong target rows | Seeded weights, epoch limit, explicit target rows |
| Agent threads updated Swing tables directly | Random NullPointerException that failed a run | GUI updates marshalled to the event thread |
| Extra JADE containers always joined port 1099 | Wrong platform when the main container used another port or a second simulator ran | Containers join their own main container |
| "Arm interactive run" silently enabled fault injection | Unexpected unit failures | Arms the capacity-emulated mode |
| Observatory matrix rows ignored the target mode | Wrong mode in automated matrices | Target mode applied from the run spec |
| Superseded runs left lane threads waiting until the stall timeout | Leaked threads after a reset | A new run closes earlier open runs |
| Scheduler plan changed the input delivery mechanism | Original vs scheduled comparisons confounded | Delivery depends only on dataset and algorithm |
