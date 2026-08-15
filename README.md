# DSMP Distributed Recommender Simulator

This repository contains the Java/JADE DSMP simulator, the modern Swing GUI, the user-generation studio, the exact multi-node lab, and the dataset import lab.

## Fast Start

Clone the repository, open a terminal in the project folder, and run one command.

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

The launcher prepares the local Python environment, checks the bundled Java jars, compiles Java when needed, chooses a safe JADE port, runs the Doc2Vec runtime preflight, writes a startup log, and opens the GUI.

## Required Software For A Source Clone

A source clone still needs a few developer tools because the Java source has to be compiled locally.

- Python 3.9 or newer
- Java JDK 8 or newer, preferably Java 17 LTS
- Git, if cloning from GitHub

Maven is no longer required for normal startup. The simulator uses the checked-in `lib/*.jar` runtime set so students do not have to reconstruct PDFBox, JADE, Weka, Gephi, Neuroph, Twitter4J, MySQL, or other classpath dependencies manually.

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

## Runtime Files

The launcher creates local generated folders:

- `.venv/` for Python packages
- `.runtime/` for Matplotlib and NLTK runtime cache
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

Python dependencies are bounded in:

```text
requirements-lock.txt
```

The Doc2Vec preflight runs before JADE starts. If Gensim/Numpy/Scipy compatibility is broken, the simulator stops early with a clear error instead of opening a GUI that later fails.

## GitHub Verification

The repository includes a GitHub Actions workflow:

```text
.github/workflows/bootstrap-smoke.yml
```

It verifies fresh-clone bootstrap and Java compilation on:

- Windows
- macOS
- Ubuntu Linux

## Packaging Recommendation

For non-developer users, the most reliable distribution is still a GitHub Release built with `jpackage` for each operating system. A packaged release can include a known Java runtime, so users do not need to install a JDK.

For a source clone, the current launcher is the supported path.

## Troubleshooting

If startup fails:

1. Run `python build.py doctor`.
2. Check `logs/startup-*.log`.
3. Make sure Python 3.9+ and Java JDK 8+ are on PATH.
4. If Python packages look corrupted, run `python build.py clean-python`, then `python build.py launch`.
5. If Java classes look stale, run `python build.py rebuild`, then `python build.py launch`.

Do not use old hand-written classpath commands. Use the launcher so the same checks run on every operating system.
