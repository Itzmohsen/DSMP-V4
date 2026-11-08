# DSMP V4 Node Lab

The Node Lab is DSMP's layer for everything about the recommender's nodes:
their capacity, how work is scheduled onto them, what happens when they are
paused, drained, slowed down or crash, and how a run is measured afterwards.
It turns the node features that V4 added on top of V3 into an instrument for
controlled experiments. Every run sits on one model with stated units and
assumptions. Every run is recorded completely and can be replayed in a
discrete-event simulator. Every comparison comes with its statistics.

This document is the reference for the model, the measures and the tools. The
exactness contract and the per-algorithm constructions in
`DSMP_V4_SCHEDULING_REBUILD.md` still hold unchanged. Its capacity-emulation
model, its separate lab panels and its relative-speed definition are
superseded by what follows.

## 1. Guarantees

- **Exactness.** For a fixed dataset, algorithm, settings and seeds, the
  merged recommendations are bit-for-bit identical for every cluster, policy,
  scenario, live control, crash and retried fault. Scheduling changes only
  where and when work runs. Every run's manifest carries the SHA-256 of its
  merged recommendations, so two runs can be compared from their records
  alone. The Results page and the study reports check it for you.
- **One time scale.** Every duration is in model milliseconds, and node speeds
  are absolute (reference cores), so runs on different clusters are directly
  comparable.
- **Reproducibility.** Every random draw is seeded: transient faults, crash
  and repair times, cost noise in the simulator, and the run order of studies.
  Live interventions can be scripted as scenarios. A run's record holds
  everything its replay needs.
- **Honest analysis.** Efficiency is measured against a lower bound that no
  policy can beat (section 7.2), proven on random instances and checked on
  every run. Each run is replayed in the simulator, and the replay error is
  reported as the run's model check. Conditions under which the emulation is
  not faithful (an oversubscribed host, a node faster than the time dilation)
  are flagged in the record.

## 2. Using the Node Lab

The Node Lab is one entry in the navigation rail. It is laid out as a guided
flow:

| Page | What it is for |
|---|---|
| Overview | Where you are: a checklist for the next run (dataset, algorithm and targets, cluster, plan) with buttons to each step, the latest run's makespan, efficiency, model error and exactness, and one-click start. |
| 1 Cluster | Design the nodes: presets, saved clusters, per-node speed, lanes, queue, link latency and bandwidth, memory, slowdown, faults and crash process, plus the cluster-wide settings (coordinator speed, time dilation, detection timeout, uptime and repair distributions, seed). A topology view and live insights (throughput, time dilation, expected host load) update as you edit. |
| 2 Plan | Choose the execution mode and the scheduling policy, with an instant simulated preview of every policy on this cluster. Optionally attach a scenario from the library or write one in the editor (validated as you type). |
| 3 Live | Watch the running system (milestones, topology with node states, phase progress against its lower bound, Gantt chart, event feed) and steer it: pause, take out, drain, crash and repair nodes, change their capacity, switch the policy, redirect work. |
| 4 Results | Every recorded run: measures, stage bar, timeline, phase table with lower bounds and the model check, counterfactual policy comparison, node table, events, and the exactness verdict against all other runs of the same algorithm and dataset. |
| What-If | Compare every policy in the simulator on synthetic work or on a recorded run's measured work, on any cluster, with replications, confidence intervals and paired tests. |
| Studies | Design, run and analyze replicated factorial experiments (section 9), or open past studies. |

A study can also run without the window:

```bash
python build.py study Studies/my-study.study
```

This compiles if needed, runs every slot of the study hidden, writes the study
to `Results/Studies/`, logs to `logs/study-*.log` and exits non-zero when a
run failed or the exactness check did not hold.

## 3. The node model

### 3.1 Nodes

| Parameter | Unit and range | Meaning |
|---|---|---|
| Speed | reference cores, 0.05 to 16 | Reference work W (its duration on a speed-1 node) takes W / speed. A host core is the reference core. |
| Lanes | 1 to 64 | Units the node executes at once (cores). |
| Queue | 1 to 4,096 | Most units the node holds at once, running plus delivered; the coordinator keeps the rest. |
| Memory | 128 to 262,144 MB | Admission budget for units that declare memory (memory-aware policy). |
| Link latency | ms one way, 0 to 600,000 | Latency of the node's link to the coordinator. |
| Bandwidth | Mbit/s per direction, up to 10⁶, 0 = unlimited | Serialization rate of the link. |
| Slowdown | factor, 1 to 50 | Divides the speed (a persistently degraded machine). |
| Faults | percent, 0 to 100 | Probability that one execution of a unit is lost (fault mode only). |
| MTBF, MTTR | seconds, 0 MTBF = never | The node's crash-recovery process (fault mode only). |

A node's effective speed is `speed × capacity scale / slowdown`. The capacity
scale is 1 until a live control or a scenario changes it. Roles are starting
points that fill the parameters:

| Role | Defaults |
|---|---|
| Normal | reference core, one lane, 1 Gbit/s link |
| Strong | double speed, two lanes, double memory, 10 Gbit/s link |
| Weak | half speed, half memory, 25 ms / 100 Mbit/s link |
| Memory-heavy | reference speed, double memory |
| Unreliable | 0.75 speed, 1.5× slowdown, 100 ms link, 5% faults, crashes |

A cluster can be saved as a small text file in `Clusters/` and named by
studies:

```
name: Heterogeneous trio
coordinator-speed: 1
time-dilation: auto
boost-headroom: 1
detection-ms: 500
uptime: exponential
weibull-shape: 1.5
repair: fixed
seed: 1146310992
node 1: role=Strong cpu=2 lanes=2 memory=2730 queue=4 latency=0 bandwidth=10000 slowdown=1 faults=0 mtbf=0 mttr=0
node 2: role=Normal cpu=1 lanes=1 memory=1365 queue=2 latency=0 bandwidth=1000 slowdown=1 faults=0 mtbf=0 mttr=0
```

### 3.2 Model time and time dilation

All nodes are threads of one JVM, so a run emulates the cluster on the host.
Each run has a time dilation factor (TDF). Model time is wall time / TDF.
Node work that uses W ns of host CPU lasts W / s of model time on a node of
speed s, so it must last TDF × W / s of wall time. It already took W, so the
node's emulator sleeps W × (TDF / s − 1). Consequences:

- A node can be faster than a host core, up to the TDF. A node whose speed
  exceeds the TDF runs at the TDF, and the run is flagged (saturation).
- A larger TDF lowers the host load, since each lane of a speed-s node keeps a
  host core busy s / TDF of the time.
- With the TDF set to automatic, a run uses the smallest factor that lets the
  fastest node and the coordinator run at their speed, with the configured
  boost headroom, and that keeps the expected busy cores within the host:

  `TDF = max(1, fastest speed × headroom, Σ lanes × speed / (cores − 2))`,
  rounded up to 0.01.

What is stretched, and what is not:

- Waiting inside a work unit (a worker process starting, a file read) is part
  of the unit. It lasts the same model time on every node, so it is stretched
  by the TDF too.
- Waiting outside units (for other nodes, messages, leases) is not node work
  and is never stretched.
- CPU time reported by worker processes (Weka SMO, Doc2Vec scoring) counts as
  node CPU, capped at the time the lane waited for it.

Precision: sleeps are owed as a debt and paid when the debt reaches 1.5 ms, at
every synchronization point and at the end of every unit. Each payment parks
in shrinking steps and carries its residual into the next one, so timer
granularity never accumulates. Each node keeps a ledger: host compute, speed
emulation, still-owed emulation, network waits, downtime, redone work, and the
mean sleep error. The unemulated hardware mode runs at TDF 1 and only
measures.

### 3.3 Network

The cluster is a star. Every node has one full-duplex link to the coordinator
(the hub), with a latency and a bandwidth in each direction. This is the
alpha-beta (latency-bandwidth) model with FIFO contention on each link:

- A transfer of b bytes asked to start at t starts at max(t, the link's free
  time).
- It occupies the link for b / bandwidth.
- It arrives one latency after it finished sending. Latency overlaps with
  other transfers, as on a pipelined link.
- Node-to-node traffic crosses the sender's up link, then the receiver's down
  link (store and forward at the hub).

Everything a distributed deployment would send is charged on these links, and
one model instance per run carries all of it, so all bytes compete:

- unit descriptors (256 B each);
- the input data of a unit that runs away from its home node (home → hub →
  node);
- agent messages (96 B envelope plus content);
- completion notices and pull requests (envelope size);
- collective uploads and downloads (wire sizes defined in `PayloadSize`).

Messages to a node are handled when they have arrived, in arrival order;
messages in flight overlap. In the unemulated mode the network is free.

### 3.4 Coordinator and collectives

Collective steps (all-gather, all-reduce, barrier) work in four steps:

1. Each node uploads its contribution over its link.
2. The coordinator starts combining when the last upload arrives.
3. It combines at the coordinator's speed.
4. Each node downloads its share of the result.

Every node waits for its download, so a slow node, slow link or large
contribution delays everyone, as in a real bulk-synchronous exchange.
Contributions are combined once, in node order, with exact arithmetic. This is
what keeps the distributed algorithms bit-identical.

### 3.5 Faults and crashes (fault mode)

- **Transient faults.** Each execution of a unit fails with its node's
  probability. The outcome is decided by a seeded hash of (run seed, phase,
  unit, execution, node), so a fault run is reproducible. A failed unit is
  retried, elsewhere when the policy allows, until its failure budget is
  exhausted. The phase then fails with the reason instead of hanging.
- **Crash-recovery processes.** Up-times are exponential with mean MTBF
  (memoryless), or Weibull with shape k and mean MTBF (k < 1 for infant
  mortality, k > 1 for wear-out). Repairs last exactly MTTR or are
  exponential with mean MTTR. Draws come from a generator seeded by (seed,
  node), so the live run and the simulator see the same outages.
- **What a crash does.** The node loses its running and delivered units at
  the crash, but the coordinator only notices after the detection timeout
  (500 ms by default) and only then reschedules that node's work. While the
  node is down its data cannot be read, so units homed there wait for its
  repair. A crash stops the node's emulated work at once; its threads block
  until the repair. No thread passes a synchronisation point while its node
  is down, even one with nothing left to pay (a node as fast as the time
  dilation, or a thread whose oversleep left it a credit). A repaired node
  rejoins empty.
- **What a crash loses.** A unit in progress is revoked and restarts wherever
  the scheduler sends it. Node-local work is durable at every
  synchronisation point (a collective, a message to the coordinator, the end
  of a unit). A crash loses only the work since the thread's last durable
  point, which is redone from scratch after the repair. A second crash during
  that redo restarts it rather than adding another redo. The ledger's redo
  time is the redo actually slept, and its slept time still equals what the
  model owed.
- **Progress guarantee.** A node-local step lost to 200 crashes in a row, or
  a unit lost to 200 crashes, takes longer than the time between crashes and
  can never finish. The run fails with that diagnosis instead of running
  forever. A step of duration T on nodes crashing every MTBF needs about
  e^(T/MTBF) attempts, so this happens only when T is several MTBFs. Crash
  losses never count against a unit's failure budget.
- **Real errors.** Unit work is deterministic, so only environment failures
  are retried within the real-failure budget: I/O such as a crashed worker
  process, timeouts, and running out of memory. Any other error fails the
  phase at once with its reason.

## 4. Execution modes and scheduling

| Mode | What runs |
|---|---|
| Hardware (unemulated) | The host's hardware with the original DSMP (V3) placement; profiles are ignored. The exactness reference. |
| Emulated cluster | Every node's speed, lanes, memory, queue, latency and bandwidth emulated on one time scale; the chosen policy places and dispatches the work. |
| Emulated cluster with faults | As emulated, plus seeded transient faults and crash-recovery processes, retried with exactly-once commits. |

One `WorkDispatcher` serves live runs, the simulator, the previews and
What-If, so a policy behaves identically everywhere. A unit that has not
started sits at the coordinator (in a shared backlog or a node's plan) or has
been delivered to a node's local queue. Whenever a node has room, the
coordinator delivers the next unit. Every execution carries a commit token.
An execution that is revoked (by a drain, a crash or its lane withdrawing)
can never commit, and its code stops at its next cancellation check. Three
execution modes:

- **Static.** Every unit is planned onto a node when the phase opens.
- **Pull.** Idle lanes ask the coordinator for the next unit (a request/reply
  round trip). The coordinator prefetches up to the queue capacity, which
  hides that round trip.
- **Steal.** Starts from the capacity-weighted plan. A lane whose node has
  nothing left takes the last unit of the node with the most remaining work.

| Policy | Class | Rule |
|---|---|---|
| Original DSMP (V3) | static baseline | every unit on the node holding its data; units without a home to the least-loaded node |
| Static equal split | static baseline | contiguous equal-size blocks in canonical order |
| Round robin | static | unit k to node k mod N |
| Weighted round robin | static | smooth weighted round robin, counts proportional to throughput |
| Least loaded node | static | least assigned cost (Graham list scheduling); ignores speed |
| Fastest node first | static | the node that frees up first, ties to the fastest; ignores unit size |
| Capacity-weighted | static | largest first, each to its earliest finish time (LPT-EFT), transfers included |
| Memory-aware safe-wave | pull | largest memory first; a unit starts only where it fits the free budget |
| Queue-length | pull | idle lanes pull one queue; simultaneous requests favour the shortest node queue |
| Work stealing | steal | capacity-weighted plan, then idle nodes steal |
| Deadline / priority | pull | highest priority, then earliest deadline; urgent units to the fastest idle node |
| Failure-aware rescheduling | pull | placement by reliable throughput s / (1 − p); retries move away from failing nodes |

Every rule is deterministic: units are taken in a fixed order, and every tie
is broken by a total order.

## 5. Live controls and scenarios

| Control | Effect |
|---|---|
| Pause / Resume | The node claims nothing new and holds at its next phase boundary; its unrouted input waits. |
| Take out / Bring back (unavailable / available) | No new units or input for the node; its data stages keep running. |
| Drain [to node M] | Taken out, plus its running units are revoked and restart elsewhere (late completions are discarded) and its future input goes to another node. |
| Redirect K units to node N | Moves not-started units to N. |
| Capacity X% | Scales the node's speed for all future work at once. |
| Policy (or back to the plan's policy) | Switches every open phase for units that have not started. |
| Crash [for t] / Repair | Crashes the node now (fault semantics of 3.5) and repairs it after t or on demand. |
| Action window t | Every claim waits t so controls can be tried by hand; such runs are marked as not comparable for timing. |
| Reset | Clears every live control: nodes active at full capacity, the plan's policy, no redirect, drain or action window. Crashed nodes stay down until repaired. |

An action that cannot apply is refused, and the refusal is recorded with its
reason (for example, draining the last available node).

A **scenario** scripts these controls, so an intervention is exactly
repeatable. It is plain text, one step per line:

```
name: Drain a node mid-phase
description: node 3 leaves when the candidate phase is 40% done
at 1.5s pause node 2
at 3s resume node 2
at 40% of candidates drain node 3 to node 1
when svm opens + 200ms crash node 2 for 2s
at 50% of input unavailable node 1
at 2s capacity node 1 = 50%
at 2s policy work stealing
at 3s redirect 5 units to node 1
at 7s reset
```

Scenario syntax:

- Triggers: model time since the run started (`at <time>`), a phase's
  committed fraction (`at <p>% [of <phase>]`; without a phase, the unit phase
  running at that moment), delivered input (`at <p>% of input`), and a phase
  opening (`when <phase> opens [+ <time>]`).
- Phases: `candidates`, `doc2vec`, `tgf`, `svm`, `mlp`, `population`, or a
  phase key.
- Built-in library: Pause and resume, Drain mid-phase, Drain and return, Crash
  and recover, Rolling slowdown, Switch to work stealing, Input-time pause.
- Saving: the Plan page saves scenarios to `Scenarios/`.

The same scenario drives live runs (`RunTimeline`, which fires progress
triggers within microseconds of the commit that reaches them) and the
simulator.

## 6. Run records

Every run writes `Results/Runs/<yyyyMMdd-HHmmss>_<algorithm>_<N>n_<policy>_<id>/`
and appends a line to `Results/Runs/runs-index.csv`. All times are model
milliseconds.

| File | Contents |
|---|---|
| `manifest.json` | the run's settings (algorithm, dataset, targets, ...), dataset and results SHA-256, scheduling (mode, base and final policy, nodes, TDF, cluster settings, fault seed, detection timeout, coordinator speed), every node's profile, the scenario's text and fingerprint, the environment (code commit, Java, OS, cores, heap), the summary measures and warnings |
| `metrics.json` | the measures of section 7 |
| `units.csv` | every execution of every unit: phase, unit, key, execution, node, lane, home node, source (local queue, hub, stolen), estimated cost, assigned, ready, start and end time, duration, host CPU, worker CPU, host wall time, moved bytes, outcome |
| `phases.csv` | every phase: policy, mode, units, executions, commits, open and completion times, makespan, lower bound and its kind, efficiency, imbalance, utilization, wasted fraction, mean ready delay, mean unit time, unit cost CV, stolen, off-home, moved bytes, faults, revocations, errors, status |
| `phase-units.csv`, `phase-nodes.csv`, `phase-joins.csv` | each phase's unit specs, node specs at its opening, and node join times (what a replay needs) |
| `nodes.csv` | per node: busy and wasted time, commits, crashes, bytes up and down, and the emulation ledger |
| `stages.csv`, `milestones.csv` | each node's pipeline stages and the run's milestones |
| `collectives.csv` | every collective step: bytes, first arrival, reduction start and end, reduction CPU, last delivery |
| `events.csv`, `controls.csv` | operator actions, scenario steps, faults, placement decisions; every control as the replay re-applies it |
| `replay.csv` | each phase replayed with its live policy: predicted vs live makespan, relative error |
| `counterfactuals.csv` | the main phase replayed under every policy |
| `recommendations.csv` | the merged ranked lists: target, its followee, rank, candidate, its followee, score, relevant |
| `quality.json`, `quality-targets.csv` | the run's ranking quality (`DSMP_V4_EVALUATION.md`): every metric with its interval, chance and MostPop baselines and tests; each target's own figures |

`runs-index.csv` also carries the run's headline quality (k, scored targets,
hit rate, P@k, R@k, NDCG@k, MRR, MAP@k, NDCG chance level, lift and p-value,
top-1 accuracy). An index written by an older version is migrated in place.

Every notification of a run (initialized, finished, failed) carries the
run's id, and the window ignores those of a superseded run. A run that is
still winding down can therefore never fail, finish or redefine the next run.

`RunRecordReader` loads any past run back, so the Results page, What-If and
replays work on old runs exactly as on a run that just finished.

## 7. Measures

### 7.1 Definitions

- **Makespan**: Start SIM to the last answered target. Milestones split it
  into the stages **input** (to text processing complete), **model** (model
  start to every node reported), **merge**, and **delivery**.
- **Phase makespan**: a work phase's opening to its last commit.
- **Efficiency**: lower bound / phase makespan. 1 is a schedule no policy
  could beat.
- **Imbalance**: the busiest node's busy time / the mean node busy time.
- **Utilization**: busy lane time / (lanes × phase makespan).
- **Wasted fraction**: lane time of executions that did not commit (faults,
  revocations, errors) / all lane time.
- **Mean ready delay**: assignment to start, which is where descriptor and
  data transfers show.
- **Unit cost CV**: dispersion of the measured unit costs.
- **Model error**: (replayed − live) / live phase makespan (section 8).
- **Host utilization, mean sleep error**: fidelity indicators of the
  emulation.

### 7.2 The lower bound

For units with reference cost c_u and speed-independent time f_u, on nodes
with speed s_n and lanes L_n, let node n be able to compute from a_n. This is
the later of its join time (+∞ if it took no part) and, with a network, the
arrival of a first unit descriptor over its link. The bound is the largest of
four terms, and the record says which one set it:

| Kind | Bound |
|---|---|
| W (work) | earliest T with Σ_n s_n L_n (T − a_n)⁺ ≥ Σ_u c_u |
| L (lanes) | earliest T with Σ_n L_n (T − a_n)⁺ ≥ Σ_u c_u / max_n s_n + Σ_u f_u |
| U (unit) | max over units of the earliest finish on its best allowed node: a_n, then (away from home) its input travelling home → hub → node, then c_u / s_n + f_u |
| P (pinned) | max over nodes of a_n + (lane time of the units only n may run) / L_n |

Contention, faults, crashes and slowing controls only lengthen a schedule, so
they are left out. For a live phase the bound uses three inputs:

- each unit's measured reference work: host CPU plus worker CPU, with a host
  core as the reference core, and its speed-independent remainder;
- each node's recorded join time;
- each node's fastest speed during the phase (capacity boosts included, capped
  at the TDF).

It is therefore valid for every policy. `NodeLabModelSelfTest` checks the
bound on 150 random clusters under every policy (over 1,000 completed phases).
The clusters have real links, data homes, late joins, cost noise, boosts and
drops, faults and crashes. No schedule ever beats the bound, and the bound is
within 10% of the achieved makespan on the tightest phases.

## 8. Replay, model check and counterfactuals

`PhaseReplay` re-runs a recorded phase in the discrete-event simulator. It
uses the phase's units with their data sizes and homes, each unit's measured
work, the node specs at the opening, the recorded join times, the same fault
seed, and every control and crash at its recorded time. Only the scheduler's
decisions and time are simulated.

- **Model check.** Replayed with the live policy, the replay predicts the
  live phase. Its relative error says how far the simulator can be trusted
  for this run. It is shown on the Results page and in every study report.
- **Counterfactuals.** Replayed with every other policy, it answers "what
  would this phase have taken" for exactly the same work. These are the bars
  on the Results page and `counterfactuals.csv`.

## 9. What-If and studies

**What-If** compares policies in the simulator, in seconds, on two kinds of
work:

- **Synthetic work.** N units with a mean cost, a shape, input bytes per unit
  and round-robin data homes. The shapes are equal, uniform on [0.5, 1.5] ×
  mean, heavy-tailed (log-normal, σ = 1) and bimodal (10% of units 10× the
  rest). All are rescaled to the exact mean.
- **A recorded run's measured work.** On the run's node count this is that
  run's replay. On another node count the measured units move to the new
  nodes, with homes wrapped and placement restrictions dropped.

Conditions you can set:

- the cluster: the current one, a preset, a saved one, or the run's own;
- faults on or off;
- an estimate error: mean-preserving log-normal noise between the costs the
  policy plans with and the true costs;
- a scenario;
- 1 to 200 replications.

Replication r uses the same seeds for every policy (common random numbers).
The output has four parts:

- each policy's mean makespan with a 95% t interval;
- the mean lower bound;
- a paired sign-flip test of every policy against the fastest;
- a Gantt chart of the chosen policy.

**Studies** are replicated live experiments. A design is a full factorial over
algorithms × clusters × policies × scenarios, with R repetitions:

```
name: Policies on a heterogeneous trio
dataset: Dataset/Retail.txt
algorithms: SIMILARITY, KMEANS
targets: 4
k: 10
mode: emulated
policies: original dsmp, capacity-weighted, work stealing, queue-length
baseline: original dsmp
clusters: preset:3 nodes: strong, normal, weak | file:Clusters/slow-links.cluster | current
scenarios: none | builtin:Drain mid-phase | file:Scenarios/crash.scenario
repetitions: 5
warmup: 1
seed: 42
```

Every run of a study sets its own run options; the Simulator's Targets page
does not apply:

- **Targets.** The first `targets` users of the simulator's user list. Every
  cell gets the same set, so comparisons stay paired. This is not a stratified
  sample.
- **List length k.** 3 to 100, the Targets page's range. A design without `k`
  uses 10, which is what every study used before k became a setting. On the
  Studies page it is the "List length k" field.
- **Fixed options.** Tweet delays and graph output are off.

Design principles:

- **Common random numbers.** Repetition r of every cell uses the same seeds
  (the same faults and crashes), so comparisons are paired.
- **Randomized order.** Within a repetition, cells run in a seeded random
  order, so slow drift of the host cannot masquerade as a policy effect.
- **Warm-up.** Warm-up runs come first and are not analyzed, so JIT
  compilation does not favour later cells.
- **Exactness check.** Every run of an algorithm must produce the same
  recommendations, and the report checks it.

Statistics (`StudyStatistics`, implemented from first principles and
self-tested):

- descriptive summaries;
- Student-t intervals, with the t quantile from the regularized incomplete
  beta function;
- paired differences with their t interval, the relative change and the
  effect size d_z;
- the sign-flip permutation p-value: exact (all 2^n sign patterns) up to 20
  pairs, otherwise 20,000 seeded Monte Carlo patterns with the +1 correction;
- Holm's step-down adjustment over every comparison of a measure.

A study directory (`Results/Studies/<stamp>_<name>/`) contains:

- `design.study`, self-contained with the cluster and scenario files beside
  it, so it re-runs as is;
- `runs.csv`, one line per run with every measure and the run's record
  directory;
- `summary.csv` and `comparisons.csv`;
- `study.json`;
- `report.md`, covering design, validity (completion, exactness, simulator
  fidelity, emulation precision), per-cell intervals and paired, Holm-adjusted
  comparisons for every measure.

## 10. Validation

- **Unit and property tests.** All in `TwitterGatherDataFollowers/Test`:
  - `NodeLabModelSelfTest` covers absolute speeds, boosts, queue capacity under
    latency, pull prefetch, bandwidth contention, data locality, crash
    detection and repair, the bound properties of 7.2, the scenario language,
    failure distributions, mean-preserving noise, progress triggers, the TDF
    rule and wire sizes.
  - `NodeLabAnalysisSelfTest` checks t quantiles, the incomplete beta, the
    exact sign-flip p-value (2/256 for 8 equal-sign pairs), Holm, JSON and CSV
    round trips, each kind of bound on hand-computed cases, and a study round
    trip.
  - `JadeTimerDispatcherSelfTest` covers the platform fix of section 11.
  - The scheduling-kernel and exactness tests of the rebuild document are
    also part of the suite.
- **Live exactness.** `LiveExactnessHarness` runs each algorithm in nine
  configurations against its one-node hardware reference and compares every
  target's ranked list and scores bit for bit. The reference is 1 node,
  original DSMP. The eight configurations compared with it:
  1. 3 nodes, original DSMP;
  2. capacity-weighted on strong/normal/weak;
  3. work stealing on 4 mixed nodes;
  4. queue-length with fault injection;
  5. live operator controls;
  6. original DSMP on the emulated cluster;
  7. a scripted crash, drain and boost;
  8. failure-aware with crash processes and faults.

  Final check on 2026-10-01 used `Dataset/Retail.txt` (218 users, 20,456
  tweets), streamed input (`-Ddsmp.max.direct.text.user.agents=1`) and MLP
  limited to 300 epochs. Every algorithm was compared in every configuration:
  Similarity, Common-Neighbors, K-Means, K-Means Euclidean, Exact SVM,
  Scalable Linear SVM, MLP, Doc2Vec and Temporal Graph Foundation.
  72 of 72 comparisons were bit-identical.

  After the evaluation and crash-model changes of `DSMP_V4_EVALUATION.md`,
  the suites were rerun on 2026-10-01 with four targets per run for every
  algorithm, every result delivered through the structured path:
  - Retail, classifiers converted to similar users: 15 of 15.
  - Retail, rankers converted to followee accounts: 30 of 30.
  - The 480-user Universal benchmark, all nine algorithms in their own mode,
    in configurations 2, 4 and 8: 27 of 27.

  Every comparison was identical to its one-node reference. In the earlier
  run, K-Means with crash processes on 4 nodes never finished. It now takes
  12.5 s, and the runs after it are unaffected.
- **Endurance and fidelity.** A 42-run study (Similarity, 3 nodes
  strong/normal/weak, original DSMP vs work stealing, 20 repetitions plus 2
  warm-up runs):
  - all 42 runs completed;
  - all were bit-identical;
  - the simulator replay predicted the live phase makespans with a mean
    absolute error of 0.4% (n = 40);
  - the mean emulation sleep error was 0.061 ms per pause.

  The policy effect was large and clean. Work stealing cut the main phase
  from 280.1 ms [279.7, 280.4] to 53.5 ms [53.3, 53.7], and phase efficiency
  rose from 0.094 to 0.482 (exact sign-flip p = 1.9 × 10⁻⁶, Holm-adjusted).

## 11. Platform fixes

Live runs exposed three defects below DSMP, in libraries it ships, plus one
teardown defect in DSMP itself. Each is fixed with a small compatibility class
in `TwitterGatherDataFollowers/compat/`. These classes are compiled before the
jars on the class path and shadow the originals. Each class documents the
defect and keeps everything else as in the original.

| Where | Defect | Effect | Fix |
|---|---|---|---|
| NetBeans lookup `ActiveQueue` (in `gephi-toolkit.jar`) | Its daemon calls `super.remove(0)` on a queue that overrides `remove()` to throw; since JDK 19 that call dispatches to the override | One core spun for the life of the JVM, distorting every timing | Waits with a positive timeout, which never dispatches to the overrides |
| JADE `LADT` (local agent table) | `put` and `remove` changed an unsynchronized hash map outside the lock under which `acquire` and `release` read it | A row lock leaked during agent creation under traffic; later deliveries to that agent blocked (initialization stalls) and killing it hung | Every map access holds the map's lock; the lock order is unchanged |
| JADE `TimerDispatcher` | Timers are kept in a set ordered by expiry millisecond alone, and `remove(t)` deleted whichever timer held t's millisecond, even after t had fired | When a fired timer was removed late, another agent's new timer for the freed millisecond was deleted instead; that agent's ticker never woke and the run never finished (MLP with tweet delays off: 1–2 of 218 user agents froze in 8 of 8 harness runs) | `remove` deletes a timer only if that very timer is pending; with the fix the same configuration passed 3 of 3 harness runs (27 of 27 runs identical) |
| Controller teardown (DSMP) | Re-created agents and containers could collide with late death notices of the previous run's agents | Occasional hangs between runs | A uniquely named work container per initialization; the controller waits until the platform has forgotten the old agents; container kills have a watchdog |

`JadeTimerDispatcherSelfTest` reproduces the timer race deterministically
through JADE's public timer API. It fails against `jade.jar` and passes with
the fix.

## 12. Limitations

- **One host.** The emulation is faithful while the host has a core for every
  busy lane. The automatic TDF keeps the expected load within the host's
  cores, and the record reports host utilization and saturation. On Windows
  the thread CPU clock advances in 15.6 ms steps and a parked thread can wake
  up to a timer period late, so the dilation of shorter work is approximate
  there. Errors are carried into the next payment, so totals stay exact.
- **Network model.** The network is a latency-bandwidth star with FIFO links.
  There is no TCP dynamics, packet loss, switch fabric or topology beyond the
  star.
- **MLP input stage.** MLP's user agents publish one tweet per tick (other
  algorithms send tweets in batches). With tweet delays off the tick is 1 ms,
  and JADE's single timer thread fires at most one timer per millisecond for
  the whole platform. MLP's input stage therefore lasts about 1 ms per tweet
  (about 21 s for `Retail.txt`) whatever the cluster, and in model time it
  shrinks with the TDF. Compare MLP input stages only at equal TDF. The model
  stage is unaffected.
- **K-Means** runs as synchronized collective steps rather than queue units,
  so its live controls act at phase boundaries and through node speed.
- **Scalable SVM** keeps its shared coordinator and is not split into units.
- **Input routing.** Input routing controls act on input delivered by the
  corpus streamer (datasets above `dsmp.max.direct.text.user.agents` users,
  480 by default). Per-user agents deliver to their planned node.
- **Memory.** Memory admission applies to units that declare memory. The
  heavy model memory lives in worker processes, which the worker memory
  coordinator admits against the machine.
