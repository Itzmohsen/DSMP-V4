#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Reliable bootstrap, build, and launcher for the DSMP simulator.

Most students should run one of these:

  macOS/Linux:  ./start_modern.sh
  Windows:      start_modern.bat

Those wrappers call this script's `launch` command. `launch` validates the
vendored Java jars, prepares the Python virtual environment, compiles Java when
needed, chooses a safe JADE port, writes a startup log, and starts the GUI.
"""

import argparse
import datetime
import hashlib
import json
import os
import platform
import re
import shutil
import socket
import subprocess
import sys
import textwrap
import time
import venv
import zipfile


PROJECT_ROOT = os.path.dirname(os.path.abspath(__file__))
VENV_DIR = os.path.join(PROJECT_ROOT, ".venv")
SRC_DIR = os.path.join(PROJECT_ROOT, "TwitterGatherDataFollowers", "userRyersonU")
TEST_SRC_DIR = os.path.join(PROJECT_ROOT, "TwitterGatherDataFollowers", "Test")
LIB_DIR = os.path.join(PROJECT_ROOT, "lib")
CLASSES_DIR = os.path.join(PROJECT_ROOT, "classes")
RUNTIME_DIR = os.path.join(PROJECT_ROOT, ".runtime")
LOG_DIR = os.path.join(PROJECT_ROOT, "logs")
REQUIREMENTS_FILE = os.path.join(PROJECT_ROOT, "requirements.txt")
LOCKED_REQUIREMENTS_FILE = os.path.join(PROJECT_ROOT, "requirements-lock.txt")
DOC2VEC_PREFLIGHT = os.path.join(SRC_DIR, "doc2vec_preflight.py")
RUNTIME_PROPERTIES = os.path.join(PROJECT_ROOT, ".simulator-runtime.properties")
PYTHON_BOOTSTRAP_STAMP = os.path.join(VENV_DIR, ".dsmp-bootstrap.json")
JAVA_BUILD_STAMP = os.path.join(CLASSES_DIR, ".dsmp-build.json")

JAVA_MAIN_CLASS = "jade.Boot"
JAVA_MAIN_AGENT = "controller:TwitterGatherDataFollowers.userRyersonU.ControllerAgent"
JAVA_RELEASE = 17
MIN_JAVA_VERSION = JAVA_RELEASE
MIN_PYTHON_VERSION = (3, 11)
MAX_PYTHON_VERSION = (3, 13)
SUPPORTED_PYTHON_VERSION_STR = "3.11 or 3.12"
DEFAULT_HEAP_MB = 1424
DEFAULT_JADE_PORT = 35240
DEFAULT_DF_MAXRESULT = 100000
RESTART_EXIT_CODE = 42
BOOTSTRAP_FORMAT_VERSION = 2
JAVA_BUILD_FORMAT_VERSION = 1
GUI_READY_MARKER = "ControllerAgentGui visible at"

REQUIRED_JARS = {
    "jade.jar": ["jade/Boot.class"],
    "weka.jar": [
        "weka/classifiers/functions/SMO.class",
        "weka/filters/supervised/instance/ClassBalancer.class",
        "weka/filters/supervised/instance/Resample.class",
        "weka/filters/supervised/instance/SpreadSubsample.class",
    ],
    "gephi-toolkit.jar": ["org/gephi/project/api/ProjectController.class"],
    "neuroph-core-2.94.jar": ["org/neuroph/core/NeuralNetwork.class"],
    "neuroph-contrib-2.94.jar": [],
    "pdfbox-app-2.0.31.jar": ["org/apache/pdfbox/pdmodel/PDDocument.class"],
    "opencsv-5.7.1.jar": ["com/opencsv/CSVReader.class"],
    "mysql-connector-java-5.1.39-bin.jar": ["com/mysql/jdbc/Driver.class"],
    "twitter4j-core-4.0.4.jar": ["twitter4j/Twitter.class"],
    "libsvm.jar": [],
    "LibSVM-1.0.10.jar": [],
    "commons-math3-3.6.1.jar": [],
    "commons-lang3-3.9.jar": [],
}

# These files remain in lib/ for provenance/legacy experiments but must not be
# placed on the production classpath. In particular, slf4j-nop conflicts with
# the Logback binding used by Neuroph and prints a warning on first model use.
CLASSPATH_EXCLUDED_JARS = {
    "commons-lang3-3.9-sources.jar",
    "commons-lang3-3.9-test-sources.jar",
    "commons-lang3-3.9-tests.jar",
    "junit-4.12.jar",
    "slf4j-nop-1.7.6.jar",
}

VERBOSE = False


def pinfo(message):
    print(f"[INFO] {message}")


def pwarn(message):
    print(f"[WARN] {message}")


def perr(message, suggestion=None, exit_code=1):
    print(f"\n[ERROR] {message}", file=sys.stderr)
    if suggestion:
        print(f"        Suggestion: {suggestion}", file=sys.stderr)
    print("\nScript aborted.", file=sys.stderr)
    sys.exit(exit_code)


def pv(message):
    if VERBOSE:
        print(f"  [VERBOSE] {message}")


def is_windows():
    return platform.system() == "Windows"


def run_cmd(cmd, cwd=PROJECT_ROOT, check=True, capture_output=True, text=True,
            env=None, error_msg_on_fail="Command execution failed"):
    cmd_list = [str(part) for part in cmd]
    pv("Running command: " + " ".join(cmd_list))
    stdout_pipe = subprocess.PIPE if capture_output else None
    stderr_pipe = subprocess.PIPE if capture_output else None
    try:
        process = subprocess.run(
            cmd_list,
            cwd=cwd,
            check=False,
            stdout=stdout_pipe,
            stderr=stderr_pipe,
            text=text,
            env=env or os.environ,
        )
    except FileNotFoundError:
        perr(f"Command '{cmd_list[0]}' was not found.",
             suggestion=f"Install '{cmd_list[0]}' and make sure it is on PATH.")
    except Exception as error:
        perr(f"Unexpected error while running '{cmd_list[0]}': {error}")

    if check and process.returncode != 0:
        details = f"{error_msg_on_fail} (exit code {process.returncode})"
        details += "\n        Command: " + " ".join(cmd_list)
        if process.stdout:
            details += "\n--- STDOUT ---\n" + process.stdout.strip()
        if process.stderr:
            details += "\n--- STDERR ---\n" + process.stderr.strip()
        perr(details, suggestion="Read the command output above; the bootstrap stopped before starting JADE.")
    return process


def check_tool(command, tool_name, required_for):
    if shutil.which(command):
        pv(f"{tool_name} found at {shutil.which(command)}")
        return True
    perr(f"Required tool '{tool_name}' ('{command}') was not found.",
         suggestion=f"Install {tool_name}; it is required for '{required_for}'.")


def py_exe_path():
    if is_windows():
        return os.path.join(VENV_DIR, "Scripts", "python.exe")
    return os.path.join(VENV_DIR, "bin", "python")


def version_tuple_from_text(text):
    match = re.search(r"(\d+)\.(\d+)(?:\.\d+)?", text or "")
    if not match:
        return None
    return int(match.group(1)), int(match.group(2))


def fetch_py_version(python_exe):
    if not os.path.exists(python_exe) and not shutil.which(python_exe):
        return None, None
    result = run_cmd([python_exe, "--version"], check=False, capture_output=True)
    raw = ((result.stdout or "") + (result.stderr or "")).strip()
    return raw, version_tuple_from_text(raw)


def python_version_supported(version):
    return bool(
        version
        and MIN_PYTHON_VERSION <= tuple(version) < MAX_PYTHON_VERSION
    )


def ensure_supported_script_python():
    current = sys.version_info[:2]
    if not python_version_supported(current):
        perr(
            f"This bootstrap supports Python {SUPPORTED_PYTHON_VERSION_STR}; "
            f"you are running Python {sys.version_info.major}.{sys.version_info.minor}.",
            suggestion="Install Python 3.11 or 3.12 and rerun the platform launcher.",
        )


def fetch_java_version_str():
    result = run_cmd(["java", "-version"], check=False, capture_output=True)
    if result.returncode != 0:
        return None
    output = (result.stderr or "") + (result.stdout or "")
    match = re.search(r'version\s+"([^"]+)"', output)
    return match.group(1) if match else "Unknown"


def fetch_java_major(version_string):
    if not version_string or version_string == "Unknown":
        return None
    parts = version_string.split(".")
    try:
        if parts[0] == "1" and len(parts) > 1:
            return int(parts[1])
        return int(parts[0])
    except ValueError:
        return None


def check_java_runtime():
    check_tool("java", "Java Runtime", "launch")
    version = fetch_java_version_str()
    major = fetch_java_major(version)
    if major and major < MIN_JAVA_VERSION:
        perr(f"Java {version} detected, but Java {MIN_JAVA_VERSION}+ is required.",
             suggestion="Install a current JDK or JRE, preferably Java 17 LTS.")
    if not major:
        pwarn(f"Could not parse Java version '{version}'. Continuing, but Java {MIN_JAVA_VERSION}+ is required.")
    else:
        pinfo(f"Java runtime detected: {version}")
    return major


def check_java_compiler():
    check_tool("javac", "Java Compiler (JDK)", "compile")
    result = run_cmd(["javac", "-version"], check=False, capture_output=True)
    raw = ((result.stdout or "") + (result.stderr or "")).strip()
    version = version_tuple_from_text(raw)
    major = version[0] if version else None
    if result.returncode != 0 or not major:
        perr(
            f"Could not determine the Java compiler version from '{raw}'.",
            suggestion=f"Install a complete Java {JAVA_RELEASE}+ JDK.",
        )
    if major < JAVA_RELEASE:
        perr(
            f"Java compiler {raw} detected, but Java {JAVA_RELEASE}+ is required.",
            suggestion=f"Install a complete Java {JAVA_RELEASE}+ JDK and make sure javac is on PATH.",
        )
    pinfo(f"Java compiler detected: {raw}")
    return major


def is_lfs_pointer(path):
    try:
        with open(path, "rb") as handle:
            prefix = handle.read(200)
        return prefix.startswith(b"version https://git-lfs.github.com/spec/v1")
    except OSError:
        return False


def validate_required_jars():
    if not os.path.isdir(LIB_DIR):
        perr(f"Java dependency folder is missing: {LIB_DIR}",
             suggestion="Make sure the repository was cloned with the lib directory.")

    missing = []
    invalid = []
    lfs_pointers = []
    for jar_name in sorted(os.listdir(LIB_DIR)):
        if not jar_name.endswith(".jar"):
            continue
        jar_path = os.path.join(LIB_DIR, jar_name)
        if is_lfs_pointer(jar_path):
            lfs_pointers.append(jar_name)
            continue
        try:
            with zipfile.ZipFile(jar_path) as jar:
                bad_entry = jar.testzip()
            if bad_entry:
                invalid.append(f"{jar_name} contains a corrupt entry: {bad_entry}")
        except (OSError, zipfile.BadZipFile):
            invalid.append(f"{jar_name} is not a valid jar/zip file")

    for jar_name, required_entries in REQUIRED_JARS.items():
        jar_path = os.path.join(LIB_DIR, jar_name)
        if not os.path.isfile(jar_path):
            missing.append(jar_name)
            continue
        if jar_name in lfs_pointers:
            continue
        if os.path.getsize(jar_path) == 0:
            invalid.append(f"{jar_name} is empty")
            continue
        if required_entries:
            try:
                with zipfile.ZipFile(jar_path) as jar:
                    names = set(jar.namelist())
                for entry in required_entries:
                    if entry not in names:
                        invalid.append(f"{jar_name} is missing {entry}")
            except zipfile.BadZipFile:
                pass

    required_lfs_pointers = sorted(
        set(lfs_pointers).intersection(REQUIRED_JARS.keys())
    )
    if missing or invalid or required_lfs_pointers:
        message = []
        if missing:
            message.append("Missing jars: " + ", ".join(missing))
        if invalid:
            message.append("Invalid jars: " + "; ".join(invalid))
        if required_lfs_pointers:
            message.append(
                "Required jars are unresolved Git LFS pointers: "
                + ", ".join(required_lfs_pointers)
            )
        perr("\n".join(message),
             suggestion=(
                 "Use a complete repository clone. If this is an older revision, "
                 "install Git LFS and run 'git lfs pull'. Do not run Maven to "
                 "replace the vendored jars."
             ))

    pinfo(f"Vendored Java libraries verified in {LIB_DIR}")


def ensure_runtime_dirs():
    for path in [RUNTIME_DIR, LOG_DIR, CLASSES_DIR]:
        os.makedirs(path, exist_ok=True)
    try:
        probe = os.path.join(RUNTIME_DIR, ".write-test")
        with open(probe, "w", encoding="utf-8") as handle:
            handle.write("ok")
        os.unlink(probe)
    except Exception as error:
        perr(f"The project runtime directory is not writable: {RUNTIME_DIR}",
             suggestion=f"Move the project to a writable folder. Details: {error}")


def ensure_venv():
    pinfo(f"Checking Python virtual environment ({SUPPORTED_PYTHON_VERSION_STR})...")
    venv_python = py_exe_path()
    if not os.path.isdir(VENV_DIR) or not os.path.exists(venv_python):
        pinfo("Creating local Python virtual environment...")
        try:
            venv.create(VENV_DIR, with_pip=True)
        except Exception as error:
            perr(f"Could not create Python virtual environment: {error}",
                 suggestion=(
                     "Install a complete Python 3.11 or 3.12 distribution "
                     "with the venv module enabled."
                 ))

    version_text, version = fetch_py_version(venv_python)
    if not python_version_supported(version):
        perr(f"The virtual environment Python is unsupported: {version_text}",
             suggestion=(
                 "Delete .venv and rerun the platform launcher with Python "
                 f"{SUPPORTED_PYTHON_VERSION_STR}."
             ))
    pinfo(f"Virtual environment Python: {version_text}")
    return venv_python


def requirements_path():
    return LOCKED_REQUIREMENTS_FILE if os.path.exists(LOCKED_REQUIREMENTS_FILE) else REQUIREMENTS_FILE


def sha256_file(path):
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def read_json_file(path):
    try:
        with open(path, "r", encoding="utf-8") as handle:
            value = json.load(handle)
        return value if isinstance(value, dict) else None
    except (OSError, ValueError):
        return None


def write_json_file(path, value):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    temporary = path + ".tmp"
    with open(temporary, "w", encoding="utf-8") as handle:
        json.dump(value, handle, sort_keys=True, indent=2)
        handle.write("\n")
    os.replace(temporary, path)


def python_bootstrap_fingerprint(python_exe, req):
    version_text, _ = fetch_py_version(python_exe)
    return {
        "format": BOOTSTRAP_FORMAT_VERSION,
        "python": version_text,
        "requirements": os.path.basename(req),
        "requirements_sha256": sha256_file(req),
        "platform": platform.system(),
        "machine": platform.machine(),
    }


def locked_python_packages_present(python_exe, req):
    check_script = r"""
import importlib.metadata
import pathlib
import re
import sys

missing = []
for raw_line in pathlib.Path(sys.argv[1]).read_text(encoding="utf-8").splitlines():
    line = raw_line.strip()
    if not line or line.startswith("#"):
        continue
    match = re.fullmatch(r"([A-Za-z0-9_.-]+)==([^\s]+)", line)
    if not match:
        raise SystemExit("Unsupported non-exact lock entry: " + line)
    name, expected = match.groups()
    try:
        actual = importlib.metadata.version(name)
    except importlib.metadata.PackageNotFoundError:
        missing.append(name + " (missing)")
        continue
    if actual != expected:
        missing.append(name + " (expected " + expected + ", found " + actual + ")")
if missing:
    print("; ".join(missing), file=sys.stderr)
    raise SystemExit(1)
"""
    result = run_cmd(
        [python_exe, "-c", check_script, req],
        check=False,
        capture_output=True,
    )
    if result.returncode != 0:
        pv("Pinned dependency verification: " + (result.stderr or "unknown failure").strip())
        return False
    return True


def install_python_dependencies():
    python_exe = ensure_venv()
    req = requirements_path()
    if not os.path.exists(req):
        perr(f"Python requirements file is missing: {req}")

    fingerprint = python_bootstrap_fingerprint(python_exe, req)
    if (
        read_json_file(PYTHON_BOOTSTRAP_STAMP) == fingerprint
        and locked_python_packages_present(python_exe, req)
    ):
        pinfo("Pinned Python dependencies are already installed.")
        return

    pinfo(f"Installing pinned Python dependencies from {os.path.basename(req)}...")
    pip_env = os.environ.copy()
    pip_env["PIP_CACHE_DIR"] = os.path.join(RUNTIME_DIR, "pip-cache")
    pip_env["PIP_DISABLE_PIP_VERSION_CHECK"] = "1"
    os.makedirs(pip_env["PIP_CACHE_DIR"], exist_ok=True)
    run_cmd([
        python_exe,
        "-m",
        "pip",
        "install",
        "--disable-pip-version-check",
        "--no-input",
        "-r",
        req,
    ], capture_output=False, env=pip_env,
       error_msg_on_fail="Failed to install pinned Python dependencies")
    write_json_file(PYTHON_BOOTSTRAP_STAMP, fingerprint)


def venv_env_vars():
    python_exe = py_exe_path()
    env = os.environ.copy()
    scripts = os.path.dirname(python_exe)
    env["PATH"] = scripts + os.pathsep + env.get("PATH", "")
    env["VIRTUAL_ENV"] = VENV_DIR
    env.pop("PYTHONHOME", None)
    env["PYTHONIOENCODING"] = "utf-8"
    env["MPLCONFIGDIR"] = os.path.join(RUNTIME_DIR, "matplotlib")
    env["NLTK_DATA"] = os.path.join(RUNTIME_DIR, "nltk_data")
    for key in ["MPLCONFIGDIR", "NLTK_DATA"]:
        os.makedirs(env[key], exist_ok=True)
    return env


def run_doc2vec_preflight(env=None):
    python_exe = py_exe_path()
    if not os.path.exists(python_exe):
        perr("Python virtual environment is missing.",
             suggestion="Run launch again; it should create .venv automatically.")
    if not os.path.exists(DOC2VEC_PREFLIGHT):
        perr(f"Doc2Vec preflight script is missing: {DOC2VEC_PREFLIGHT}")
    pinfo("Checking Doc2Vec Python runtime...")
    run_cmd([python_exe, DOC2VEC_PREFLIGHT], env=env or venv_env_vars(),
            error_msg_on_fail="Doc2Vec Python runtime check failed")


def java_source_files(include_tests=False):
    roots = [SRC_DIR]
    if include_tests:
        roots.append(TEST_SRC_DIR)
    files = []
    for root_dir in roots:
        if not os.path.isdir(root_dir):
            continue
        for root, _, names in os.walk(root_dir):
            for name in names:
                if name.endswith(".java"):
                    files.append(os.path.join(root, name))
    return sorted(files)


def classpath_jar_files():
    if not os.path.isdir(LIB_DIR):
        return []
    return [
        os.path.join(LIB_DIR, name)
        for name in sorted(os.listdir(LIB_DIR))
        if name.endswith(".jar") and name not in CLASSPATH_EXCLUDED_JARS
    ]


def java_build_fingerprint():
    inputs = []
    for path in java_source_files(include_tests=False):
        stat = os.stat(path)
        inputs.append([
            os.path.relpath(path, PROJECT_ROOT).replace("\\", "/"),
            stat.st_size,
            stat.st_mtime_ns,
        ])
    for path in classpath_jar_files():
        stat = os.stat(path)
        inputs.append([
            os.path.relpath(path, PROJECT_ROOT).replace("\\", "/"),
            stat.st_size,
            stat.st_mtime_ns,
        ])
    return {
        "format": JAVA_BUILD_FORMAT_VERSION,
        "release": JAVA_RELEASE,
        "inputs": inputs,
    }


def classes_need_compile():
    marker = os.path.join(CLASSES_DIR, "TwitterGatherDataFollowers", "userRyersonU", "ControllerAgent.class")
    if not os.path.exists(marker):
        return True
    return read_json_file(JAVA_BUILD_STAMP) != java_build_fingerprint()


def compile_java(force=False):
    validate_required_jars()
    if not force and not classes_need_compile():
        pinfo("Compiled Java classes are already up to date.")
        return

    check_java_compiler()
    check_java_runtime()
    sources = java_source_files(include_tests=False)
    if not sources:
        perr(f"No Java source files found under {SRC_DIR}")

    if os.path.isdir(CLASSES_DIR):
        shutil.rmtree(CLASSES_DIR)
    os.makedirs(CLASSES_DIR, exist_ok=True)
    classpath = os.pathsep.join([CLASSES_DIR] + classpath_jar_files())

    sources_file = os.path.join(PROJECT_ROOT, "sources.txt")
    classpath_file = os.path.join(PROJECT_ROOT, "classpath.txt")
    try:
        with open(sources_file, "w", encoding="utf-8") as handle:
            for source in sources:
                handle.write('"%s"\n' % source.replace("\\", "/"))
        with open(classpath_file, "w", encoding="utf-8") as handle:
            handle.write("-cp\n")
            handle.write('"%s"\n' % classpath.replace("\\", "/"))

        pinfo(f"Compiling {len(sources)} Java source files...")
        run_cmd([
            "javac",
            "-nowarn",
            "-encoding", "UTF-8",
            "--release", str(JAVA_RELEASE),
            "@classpath.txt",
            "-d", CLASSES_DIR,
            "@sources.txt",
        ], error_msg_on_fail="Java compilation failed")
        write_json_file(JAVA_BUILD_STAMP, java_build_fingerprint())
        pinfo("Java compilation finished.")
    finally:
        for file_name in [sources_file, classpath_file]:
            try:
                if os.path.exists(file_name):
                    os.unlink(file_name)
            except OSError:
                pass


def parse_heap_mb(value):
    if not value:
        return None
    raw = str(value).strip().lower()
    match = re.match(r"^([1-9]\d*)([mg])?$", raw)
    if not match:
        return None
    amount = int(match.group(1))
    unit = match.group(2) or "m"
    return amount * 1024 if unit == "g" else amount


def configured_heap_mb():
    env_heap = parse_heap_mb(os.environ.get("JAVA_HEAP_MAX"))
    if env_heap:
        return env_heap
    if os.path.exists(RUNTIME_PROPERTIES):
        with open(RUNTIME_PROPERTIES, "r", encoding="utf-8") as handle:
            for line in handle:
                match = re.match(r"maxHeapMb=([1-9]\d*)\s*$", line.strip())
                if match:
                    return int(match.group(1))
    return DEFAULT_HEAP_MB


def port_is_free(port):
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as probe:
            probe.bind(("127.0.0.1", port))
        return True
    except PermissionError as error:
        perr(
            f"Local networking is not permitted, so JADE cannot bind port {port}: {error}",
            suggestion="Run the launcher from a normal desktop terminal rather than a restricted sandbox.",
        )
    except OSError:
        return False


def choose_jade_port():
    requested = os.environ.get("JADE_PORT")
    if requested:
        try:
            port = int(requested)
        except ValueError:
            perr("JADE_PORT must be a number.")
        if port < 1 or port > 65535:
            perr("JADE_PORT must be between 1 and 65535.")
        if not port_is_free(port):
            perr(f"Requested JADE_PORT {port} is already in use.",
                 suggestion="Close the other simulator instance or choose a different JADE_PORT.")
        return port

    if port_is_free(DEFAULT_JADE_PORT):
        return DEFAULT_JADE_PORT
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
            sock.bind(("127.0.0.1", 0))
            port = sock.getsockname()[1]
    except OSError as error:
        perr(
            f"Could not reserve a local JADE port: {error}",
            suggestion="Check local firewall/security restrictions and try again.",
        )
    pwarn(f"Default JADE port {DEFAULT_JADE_PORT} is busy. Using free port {port}.")
    return port


def java_runtime_opts(java_major, startup_log):
    heap_mb = configured_heap_mb()
    queue_mb = max(32, min(256, heap_mb // 4))
    queue_bytes = queue_mb * 1024 * 1024
    df_max = os.environ.get("JADE_DF_MAXRESULT", str(DEFAULT_DF_MAXRESULT))
    if not re.match(r"^[1-9]\d*$", df_max):
        perr("JADE_DF_MAXRESULT must be a positive integer.")
    port = choose_jade_port()

    java_opts = [
        "-Xms64m",
        f"-Xmx{heap_mb}m",
        "-XX:-UseGCOverheadLimit",
        "-Djava.awt.headless=false",
        f"-Drecommender.python.command={py_exe_path()}",
        f"-Ddsmp.bootstrap.python={sys.executable}",
        "-Ddsmp.launcher.managed=true",
        f"-Ddsmp.restart.exitCode={RESTART_EXIT_CODE}",
        f"-Ddsmp.startup.log={startup_log}",
    ]
    model_parallelism = os.environ.get("MODEL_PARALLELISM")
    if model_parallelism:
        if not re.match(r"^[1-9]\d*$", model_parallelism):
            perr("MODEL_PARALLELISM must be a positive integer.")
        java_opts.append(f"-Drecommender.model.parallelism={model_parallelism}")

    if java_major and java_major >= 9:
        for module in [
            "java.base/java.lang",
            "java.base/java.io",
            "java.base/java.util",
            "java.base/java.util.concurrent",
            "java.base/sun.nio.ch",
            "java.base/java.net",
        ]:
            java_opts.extend(["--add-opens", f"{module}=ALL-UNNAMED"])

    jade_opts = [
        "-jade_domain_df_maxresult", df_max,
        "-jade_core_messaging_MessageManager_poolsize", "10",
        "-jade_core_messaging_MessageManager_maxqueuesize", str(queue_bytes),
        "-jade_core_messaging_MessageManager_deliverytimethreshold", "10000",
        "-jade_domain_df_autocleanup", "true",
        "-local-port", str(port),
    ]
    pinfo(f"Runtime heap: {heap_mb} MB")
    pinfo(f"JADE port: {port}")
    return java_opts, jade_opts


def runtime_classpath():
    return os.pathsep.join([CLASSES_DIR] + classpath_jar_files())


def startup_log_path():
    os.makedirs(LOG_DIR, exist_ok=True)
    stamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    return os.path.join(LOG_DIR, f"startup-{stamp}.log")


def run_java_process(command, env, log_file):
    pinfo(f"Startup log: {log_file}")
    gui_ready = False
    startup_failure = None
    with open(log_file, "w", encoding="utf-8", errors="replace") as log:
        log.write("DSMP startup command:\n")
        log.write(" ".join(command) + "\n\n")
        log.flush()
        process = subprocess.Popen(
            command,
            cwd=PROJECT_ROOT,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            stdin=None,
            text=True,
            bufsize=1,
            env=env,
        )
        try:
            assert process.stdout is not None
            for line in process.stdout:
                print(line, end="")
                log.write(line)
                log.flush()
                if GUI_READY_MARKER in line:
                    gui_ready = True
                if not gui_ready and (
                    "No ICP active" in line
                    or "ControllerAgent could not create the GUI" in line
                    or "Exception in thread \"main\"" in line
                ):
                    startup_failure = line.strip()
            code = process.wait()
        except KeyboardInterrupt:
            pinfo("Interrupted. Stopping Java process...")
            process.terminate()
            try:
                code = process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                code = process.wait()
        return code, gui_ready, startup_failure


def preflight(require_classes=False):
    ensure_supported_script_python()
    ensure_runtime_dirs()
    java_major = check_java_runtime()
    validate_required_jars()
    python_exe = ensure_venv()
    env = venv_env_vars()
    run_doc2vec_preflight(env)
    if require_classes:
        if not os.path.isdir(CLASSES_DIR) or classes_need_compile():
            perr("Java classes are missing or stale.",
                 suggestion="Run launch, setup, compile, or rebuild before run-only.")
    pinfo("Preflight passed.")
    return java_major, python_exe, env


def install():
    ensure_supported_script_python()
    ensure_runtime_dirs()
    validate_required_jars()
    install_python_dependencies()
    run_doc2vec_preflight()
    pinfo("Install completed.")


def setup():
    install()
    compile_java(force=True)
    pinfo("Setup completed.")


def launch():
    install()
    compile_java(force=False)
    run()


def run():
    java_major, _, env = preflight(require_classes=True)
    restart_count = 0
    while True:
        log_file = startup_log_path()
        java_opts, jade_opts = java_runtime_opts(java_major, log_file)
        command = ["java"] + java_opts + ["-cp", runtime_classpath(), JAVA_MAIN_CLASS] + jade_opts + [JAVA_MAIN_AGENT]
        pinfo("Starting DSMP simulator...")
        code, gui_ready, startup_failure = run_java_process(command, env, log_file)
        if not gui_ready:
            detail = startup_failure or (
                "The Java process exited before the GUI reported that it was visible."
            )
            perr(
                f"DSMP did not finish starting: {detail}",
                suggestion=f"Inspect the startup log: {log_file}",
                exit_code=code if code else 1,
            )
        if code == RESTART_EXIT_CODE:
            restart_count += 1
            pinfo(
                "Simulator requested a managed restart. "
                "Relaunching with the saved runtime settings..."
            )
            time.sleep(0.75)
            continue
        if code == 0:
            if restart_count:
                pinfo(f"DSMP simulator exited normally after {restart_count} managed restart(s).")
            else:
                pinfo("DSMP simulator exited normally.")
        else:
            pwarn(f"DSMP simulator exited with code {code}. See {log_file}")
            sys.exit(code)
        return


def clean_java_output():
    if os.path.exists(CLASSES_DIR):
        shutil.rmtree(CLASSES_DIR)
        pinfo("Removed classes directory.")
    else:
        pinfo("Classes directory already absent.")


def clean_python_env():
    if os.path.exists(VENV_DIR):
        shutil.rmtree(VENV_DIR)
        pinfo("Removed Python virtual environment.")
    else:
        pinfo("Python virtual environment already absent.")


def clean_runtime():
    if os.path.exists(RUNTIME_DIR):
        shutil.rmtree(RUNTIME_DIR)
        pinfo("Removed runtime cache directory.")
    else:
        pinfo("Runtime cache directory already absent.")


def clean_all():
    clean_java_output()
    clean_python_env()
    clean_runtime()


def info():
    print("\n[ Project ]")
    print(f"  Root:              {PROJECT_ROOT}")
    print(f"  Source:            {SRC_DIR}")
    print(f"  Classes:           {CLASSES_DIR}")
    print(f"  Lib:               {LIB_DIR}")
    print(f"  Runtime cache:     {RUNTIME_DIR}")
    print(f"  Startup logs:      {LOG_DIR}")
    print("\n[ Tools ]")
    version = fetch_java_version_str()
    print(f"  Java:              {version or 'not found'}")
    javac = shutil.which("javac")
    print(f"  javac:             {javac or 'not found'}")
    print(f"  Java target:       {JAVA_RELEASE}")
    print(f"  Bootstrap Python:  {platform.python_version()} at {sys.executable}")
    venv_python = py_exe_path()
    venv_version, _ = fetch_py_version(venv_python)
    print(f"  Venv Python:       {venv_version or 'not created'}")
    print("\n[ Runtime ]")
    print(f"  Heap:              {configured_heap_mb()} MB")
    print(f"  JADE default port: {DEFAULT_JADE_PORT}")
    print(f"  Requirements:      {requirements_path()}")
    print("")


def doctor():
    try:
        install()
        check_java_runtime()
        if classes_need_compile():
            check_java_compiler()
            pwarn("Java classes are missing or stale; launch will compile them.")
        else:
            pinfo("Java classes are present and up to date.")
        pinfo("Doctor check passed. This clone is ready to launch.")
    except SystemExit:
        raise
    except Exception as error:
        perr(f"Doctor check failed unexpectedly: {error}")


def parse_args():
    parser = argparse.ArgumentParser(
        description="DSMP reliable build and launch tool",
        formatter_class=argparse.RawTextHelpFormatter,
        epilog=textwrap.dedent("""\
            Common examples:
              python build.py launch      Prepare everything and open the GUI
              python build.py doctor      Check this clone without starting the GUI
              python build.py rebuild     Recompile Java from scratch
        """),
    )
    parser.add_argument(
        "command",
        nargs="?",
        default="launch",
        choices=[
            "launch",
            "install",
            "setup",
            "compile",
            "run",
            "rebuild",
            "doctor",
            "info",
            "clean",
            "clean-java",
            "clean-python",
            "clean-runtime",
            "help",
        ],
    )
    parser.add_argument("-v", "--verbose", action="store_true", help="Show detailed command output.")
    return parser, parser.parse_args()


def main():
    global VERBOSE
    ensure_supported_script_python()
    parser, args = parse_args()
    VERBOSE = args.verbose
    if args.command == "help":
        parser.print_help()
        return

    started = time.time()
    if args.command == "launch":
        launch()
    elif args.command == "install":
        install()
    elif args.command == "setup":
        setup()
    elif args.command == "compile":
        compile_java(force=False)
    elif args.command == "run":
        run()
    elif args.command == "rebuild":
        clean_java_output()
        compile_java(force=True)
    elif args.command == "doctor":
        doctor()
    elif args.command == "info":
        info()
    elif args.command == "clean":
        clean_all()
    elif args.command == "clean-java":
        clean_java_output()
    elif args.command == "clean-python":
        clean_python_env()
    elif args.command == "clean-runtime":
        clean_runtime()
    pinfo(f"Command '{args.command}' finished in {time.time() - started:.1f} seconds.")


if __name__ == "__main__":
    main()
