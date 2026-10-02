# DSMP Distributed Recommender Simulator

This repository contains the Java/JADE DSMP simulator, the modern Swing GUI, the user-generation studio, the Node Lab (cluster design, scheduling, live node control, run records and replicated studies, with results identical on any node split), and the dataset import lab.

## Fast Start

Clone the repository, open a terminal in the project folder, and run one command.
The first run creates local build/runtime directories, installs the pinned Python
runtime into `.venv`, compiles the Java source for Java 17, and opens the GUI.

```bash
git -c filter.lfs.smudge= -c filter.lfs.process= -c filter.lfs.required=false clone --depth 1 https://github.com/Itzmohsen/DSMP-V4.git
cd DSMP-V4
```

This fast-start clone includes everything required to open and run DSMP while
leaving optional benchmark datasets and cached models as small Git LFS pointer
files. It also avoids downloading the repository's large development history.
Researchers who need those optional artifacts can fetch them later as described
under **Required Software For A Source Clone**.

### macOS / Linux

```bash
./start_modern.sh
```

You can also run:

```bash
./run.sh
```

### Windows

Double-click:

```text
start_modern.bat
```

or run:

```bat
run.bat
```

The launcher prepares the local Python environment, checks every bundled Java
jar, compiles Java when needed, chooses a safe JADE port, runs the Doc2Vec
runtime preflight, writes a startup log, and opens the GUI. It treats a Java
process that exits before the GUI becomes visible as a startup failure, even if
old JADE code returns exit status zero.

## Required Software For A Source Clone

A source clone needs two host runtimes. Project libraries and build steps are
handled by the launcher, but Git cannot include one executable runtime that runs
unchanged on Windows, macOS, and Linux.

- Python 3.11 or 3.12 (64-bit CPython)
- Java 17 or newer **JDK** (not a JRE-only install)
- Git, if cloning from GitHub
- Internet access on the first launch, for pinned Python wheels

The pinned wheels cover Windows x64, Linux x64/ARM64 with a glibc 2.17+
(`manylinux2014`) baseline, and Intel or Apple Silicon macOS. The Apple Silicon
scientific stack requires macOS 12 or newer. A graphical desktop session is
required because DSMP is a Swing application.

Java 17 is the bytecode target even when a newer JDK performs the compile, so a
clone built with Java 21 can still run on Java 17. Maven is not required for
normal startup. The simulator uses the checked-in `lib/*.jar` runtime set so
students do not have to reconstruct JADE, Weka, Neuroph, Twitter4J, MySQL, or
other classpath dependencies manually.

Git LFS is optional for opening the core application. Some very large benchmark
datasets and cached models use LFS; users who need every research artifact
should install Git LFS and run:

```bash
git lfs install
git lfs pull
```

The optional live Twitter crawler does not contain API secrets. Configure it
only when needed with `DSMP_TWITTER_CONSUMER_KEY`,
`DSMP_TWITTER_CONSUMER_SECRET`, `DSMP_TWITTER_ACCESS_TOKEN`, and
`DSMP_TWITTER_ACCESS_TOKEN_SECRET`. These variables are not required to open or
run the simulator with the bundled text datasets. Credentials found in an older
revision should be revoked because removing them from the current source does
not remove them from Git history.

The optional MySQL input mode reads `DSMP_DB_HOST`, `DSMP_DB_PORT`,
`DSMP_DB_NAME`, `DSMP_DB_USER`, and `DSMP_DB_PASSWORD`. MySQL is not contacted
during normal startup; the default and supported first-run source is a bundled
text dataset.

## What The Launcher Does

The bootstrap script is `build.py`.

Useful commands:

```bash
python build.py launch
python build.py doctor
python build.py compile
python build.py rebuild
python build.py test
python build.py run
python build.py study FILE
python build.py info
python build.py clean
```

- `launch` is the recommended command. It prepares everything and opens the GUI.
- `test` compiles the Java self-test suite and runs it. Also runs in CI.
- `doctor` checks and prepares the local runtime without opening the GUI.
- `compile` compiles the Java sources if needed.
- `rebuild` deletes compiled classes and compiles again.
- `run` starts the GUI only after preflight passes.
- `study FILE` runs a Node Lab study without the window and writes it to `Results/Studies/` (see below).
- `info` prints detected paths and versions.
- `clean` removes generated Java classes, the Python virtual environment, and runtime caches.

The first successful Python setup is fingerprinted using the interpreter,
platform, and `requirements-lock.txt` hash. Later launches skip `pip` entirely,
which makes normal startup fast and allows it to work without network access.

## Interface And Theming

The GUI uses the Nebula design system, a single set of tokens shared by every
screen in both day and night mode. Three files own the whole visual language:

- `TwitterGatherDataFollowers/userRyersonU/NebulaDesign.java` — colors, typography,
  radii, spacing, chart palette, and the Swing defaults installed at startup.
- `TwitterGatherDataFollowers/userRyersonU/NebulaWidgets.java` — the navigation
  rail, inner tab strips, scrollbars, and shared card/chip painters.
- `TwitterGatherDataFollowers/userRyersonU/NebulaDialogs.java` — the branded
  message, confirm, and long-text dialogs.

`StudioNightTheme` remains the entry point for switching modes. Panels should ask
`NebulaDesign` for a token rather than constructing a raw `Color`, so a screen
looks correct in both modes without extra code.

Typography ships with the repository in `fonts/` (Inter for the interface,
JetBrains Mono for consoles and numerals). The fonts are registered at startup
and fall back to the platform sans-serif and monospace families when the folder
is unavailable, so the application still runs from a partial clone.

The day/night switch is the pill control in the top-right corner of the window.

## Runtime Files

The launcher creates local generated folders:

- `.venv/` for Python packages
- `.runtime/` for local package/cache data
- `classes/` for compiled Java classes
- `logs/` for startup logs

These are machine-local runtime outputs. They do not need to be edited manually.

## Startup Logs

If the GUI does not appear, check the newest file in:

```text
logs/startup-*.log
```

The log contains the exact Java command and the JADE/application output. This is the first place to check for a missing jar, Python error, JADE port conflict, or Java module-access issue.

## JADE Port Handling

The default JADE port is `35240`. If that port is already used by another simulator run, the launcher automatically chooses a free local port.

To force a specific port:

```bash
JADE_PORT=35241 ./start_modern.sh
```

On Windows:

```bat
set JADE_PORT=35241
start_modern.bat
```

## Heap Setting

The GUI can write `.simulator-runtime.properties` with the desired maximum Java heap. The launcher reads that file automatically.

You can also override it from the terminal:

```bash
JAVA_HEAP_MAX=4096m ./start_modern.sh
```

## Doc2Vec Reliability

Doc2Vec uses the local Python virtual environment. The launcher always passes that exact Python executable into Java through:

```text
-Drecommender.python.command=<project .venv python>
```

That prevents Windows/macOS/Linux from accidentally using a different `python`, `python3`, or global package set.

The complete supported Python dependency graph is pinned exactly in:

```text
requirements-lock.txt
```

The Doc2Vec preflight runs before JADE starts. If Gensim/NumPy/SciPy
compatibility is broken, the simulator stops early with a clear error instead
of opening a GUI that later fails. Old standalone plotting experiments are not
part of the application launch path and are intentionally excluded from the
runtime dependency set.

## Reproducibility

Every stochastic step is seeded, and the seed is printed in the run log.

- **K-Means** uses deterministic farthest-first initialisation. The first
  centroid is chosen by the **Cluster seed** in Advanced Algorithm Settings, so
  the same seed always produces the same clustering. There are no dataset-specific
  seed accounts any more.
- **Text processing** is one shared implementation
  (`TweetTextNormalizer`) used by the controller, every recommender node, the
  user generator, and the population sweep, so every component tokenises
  identically and the vocabularies cannot drift apart.
- **Doc2Vec** trains single-threaded with a fixed seed, so the same dataset and
  parameters always produce the same vectors on any machine. Set
  `DSMP_DOC2VEC_ALLOW_PARALLEL=1` to train in parallel instead, which is faster
  but no longer reproducible.
- **SVM** keeps its existing stratified split seed and SMO model seed.
- **Every simulated draw** (the simulated follow, artificial text, the
  generated dataset, the choice among the top recommendations, the stratified
  target sample) is seeded by names or a fixed seed. It does not depend on
  thread order or the number of nodes.
- **One corpus.** Doc2Vec and the temporal graph model read the run's
  normalised corpus (`RunCorpus`), so every algorithm sees the same tweets
  and words.
- **The run graph** is deterministic: nodes are ordered by name, Leiden and
  every permutation test use fixed seeds, and the ForceAtlas2 layout starts
  from positions seeded by node name, so a run's communities, tests and
  picture are the same every time.

## Distributed Correctness

For a fixed dataset, algorithm, settings and seeds, the final recommendations
are identical bit for bit for any number of nodes, any node capacity profile,
any scheduling policy, any live workload control and any retried failure; the
scheduler changes only where and when work runs. See
`DSMP_V4_SCHEDULING_REBUILD.md` for the design, the per-algorithm construction
and the verification (including a live end-to-end check,
`LiveExactnessHarness`), and `DSMP_V4_NODE_LAB.md` for the node model and the
analysis tools. Two of the underlying properties:

- **TF-IDF is corpus-wide.** Document frequency and corpus size are summed across
  every node before weighting, so a term carries the same IDF everywhere and the
  scores merged from different nodes live in one vector space. Each node
  contributes only the users it owns, so a recommendation target replicated onto
  every node is still counted as one document. Node count and node-capacity
  settings change run timing, not recommendation scores.
- **Each candidate is scored once.** Recommendation targets are replicated to
  every node so each node can build their document; only the node that owns a
  candidate contributes its score, so replicated targets are not multiplied by
  the node count during the merge.

If a node is lost mid-run, the corpus-wide TF-IDF barrier times out and the run
fails with a clear message rather than silently falling back to per-node weights.

## Node Lab

The Node Lab (one entry in the navigation rail) is where the recommender's
nodes are designed, scheduled, steered and measured:

- **Cluster**: per-node speed (in reference cores), lanes, queue, memory, link
  latency and bandwidth, slowdown, faults and crash-repair processes, with
  presets and saved `Clusters/*.cluster` files.
- **Plan**: execution mode (hardware, emulated cluster, emulated with faults),
  one of twelve scheduling policies with an instant simulated preview, and an
  optional scripted scenario (pause, drain, crash, capacity, policy switch at
  a time or at a phase's progress).
- **Live**: the running system, its phases against their lower bounds, and
  controls to pause, take out, drain, crash or boost nodes.
- **Results**: every run's complete record under `Results/Runs/`, its measures
  (makespan, stages, efficiency against a lower bound no policy can beat,
  utilization, wasted work, bytes moved), a replay of the run in the
  discrete-event simulator as a model check, and what every other policy
  would have taken on the same work.
- **What-If** and **Studies**: policy comparisons in the simulator, and
  replicated factorial experiments with common random numbers, confidence
  intervals and Holm-adjusted paired tests.

Studies also run without the window:

```bash
python build.py study Studies/my-study.study
```

See `DSMP_V4_NODE_LAB.md` for the model, the measures, the record format,
the statistics, the validation and the limitations.

## Simulator

The Simulator tab (the first entry in the navigation rail) runs a
recommendation experiment as a guided flow:

- **Overview**: what the next run will use, the next step, and the latest
  run's quality against chance.
- **1 Data**: the dataset, its date window (both days included), the newest-N
  tweet limit, text processing, and the users with their community sizes.
- **2 Model**: the nine algorithms, grouped as rankers (Similarity,
  Common-Neighbors, K-Means, K-Means Euclidean, Doc2Vec, Temporal Graph
  Foundation) and classifiers (SVM, Scalable Linear SVM, MLP), what they
  recommend, and their parameters.
- **3 Targets**: who gets recommendations (selected users, a seeded
  stratified sample, or everyone) and the list length k. The evaluation
  preview shows the chance level of the headline metrics before anything
  runs.
- **4 Run**: nodes and the Node Lab cluster, the heap, and the three run
  steps with the live output.
- **5 Results**: the run's ranking quality with 95% intervals, chance and
  MostPop baselines, paired tests, the top-1 view with a confusion matrix,
  and every target's numbers.

## Recommendation Quality

Every run is evaluated against the followee each user already has in the
dataset. A recommended user is relevant when it follows the target's own
account; a recommended account is relevant when the target follows it.

The report gives hit rate, P@k, R@k, NDCG@k, MRR and MAP@k:
- every mean has a 95% interval (Wilson for the hit rate);
- every metric is shown next to its exact chance level and a most-popular
  baseline, with a paired test against each;
- diagnostics flag figures that are trivial (for example k covering every
  account).

Classifiers never train on the targets they score. A target that receives no
list scores zero. Each run's record holds its lists and the evaluation
(`recommendations.csv`, `quality.json`, `quality-targets.csv`).

`DSMP_V4_EVALUATION.md` defines the protocol, the algorithms and the metrics,
and lists what the October 2026 audit found and how each finding was fixed.

The per-candidate number shown next to each recommendation is a
**within-list relative rank score**: it is rescaled so the best candidate is
exactly 100. It is not a similarity, a probability or a confidence, and it is
not comparable between targets or between runs.

## Graph Insights

Every run records the network it creates (`RunGraph`). The graph has three
layers:
- the dataset's follows (each user to the account it follows, the ground
  truth);
- each target's top-k recommendations, weighted by rank;
- the follow each simulated user adopts.

The analysis asks whether recommendations stay inside the target's community
or bridge communities, each measured against chance:
- homophily, with an exact test against random lists;
- Newman's assortativity, with a jackknife error;
- community recovery, the AMI and ARI of Leiden communities against the
  real ones;
- the exposure each candidate receives (Gini, coverage, hubness);
- what the adopted follows do to the network, tested against random
  adoption.

The Graph Insights tab shows the newest run and follows new runs as they
finish; any earlier run can be picked. Its pages:
- **Overview:** the findings, with their tests.
- **Explorer:** the graph, interactive.
- **Communities.**
- **Influence:** PageRank, betweenness and the Guimerà-Amaral roles.
- **Adoption:** the network before and after.
- **Methods:** every definition and its reference.

Each run record's `graph` folder holds the analysis (`graph.json`) and the
nodes and edges as CSV. With **Generate Final Graph Output** on (the
default) it also holds the layout, GEXF for Gephi, GraphML for NetworkX,
and the figure as SVG and PNG.

`DSMP_V4_GRAPH.md` describes the graph, the measures, the layout, the files
and their verification against NetworkX, scikit-learn and SciPy.

## Credentials

Twitter and database credentials are read from the environment, never from the
source tree:

```text
DSMP_TWITTER_CONSUMER_KEY        DSMP_TWITTER_ACCESS_TOKEN
DSMP_TWITTER_CONSUMER_SECRET     DSMP_TWITTER_ACCESS_TOKEN_SECRET
DSMP_DB_USER                     DSMP_DB_PASSWORD
```

The normal simulator path reads a dataset file and needs none of them. Only live
Twitter collection does, and it fails with a clear message when they are absent.

## GitHub Verification

The repository includes a GitHub Actions workflow:

```text
.github/workflows/bootstrap-smoke.yml
```

It verifies both supported Python versions, the platform launcher, fresh-clone
bootstrap, the exact Python lock, repeat/offline-ready bootstrap behavior,
Java 17 compilation, and the full Java self-test suite on:

- Windows
- macOS
- Ubuntu Linux

## Packaging Recommendation

For non-developer users, the most reliable distribution is a GitHub Release
built with `jpackage` for each operating system. A packaged release can include
a known Java runtime, so users do not need to install a JDK. Source clones use
the launcher and the two host-runtime prerequisites above.

For a source clone, the current launcher is the supported path.

## Troubleshooting

If startup fails:

1. Run `python build.py doctor`.
2. Check `logs/startup-*.log`.
3. Make sure Python 3.11/3.12 and a Java 17+ JDK are on PATH.
4. If Python packages look corrupted, run `python build.py clean-python`, then `python build.py launch`.
5. If Java classes look stale, run `python build.py rebuild`, then `python build.py launch`.
6. If a large optional dataset is a short text file beginning with `version https://git-lfs`, run `git lfs pull`.

Do not use old hand-written classpath commands. Use the launcher so the same checks run on every operating system.
