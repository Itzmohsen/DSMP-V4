# DSMP Distributed Recommender Simulator

This repository contains the Java/JADE DSMP simulator, the modern Swing GUI, the user-generation studio, the exact multi-node lab, and the dataset import lab.

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
students do not have to reconstruct PDFBox, JADE, Weka, Gephi, Neuroph,
Twitter4J, MySQL, or other classpath dependencies manually.

Git LFS is optional for opening the core application. The required Gephi jar is
stored as an ordinary Git file. Some very large benchmark datasets and cached
models still use LFS; users who need every research artifact should install Git
LFS and run:

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
python build.py run
python build.py info
python build.py clean
```

- `launch` is the recommended command. It prepares everything and opens the GUI.
- `doctor` checks and prepares the local runtime without opening the GUI.
- `compile` compiles the Java sources if needed.
- `rebuild` deletes compiled classes and compiles again.
- `run` starts the GUI only after preflight passes.
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

## GitHub Verification

The repository includes a GitHub Actions workflow:

```text
.github/workflows/bootstrap-smoke.yml
```

It verifies both supported Python versions, the platform launcher, fresh-clone
bootstrap, the exact Python lock, repeat/offline-ready bootstrap behavior, and
Java 17 compilation on:

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
