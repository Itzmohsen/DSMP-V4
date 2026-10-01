import csv
import gc
import glob
import hashlib
import json
import math
import os
import socket
import sys
import time

os.environ.setdefault("OPENBLAS_NUM_THREADS", "1")
os.environ.setdefault("OMP_NUM_THREADS", "1")
os.environ.setdefault("MKL_NUM_THREADS", "1")

import numpy as np

from gensim_runtime_compat import configure_gensim_runtime, runtime_signature


GENSIM_RUNTIME = configure_gensim_runtime()

from gensim.models import Doc2Vec
from gensim.models.doc2vec import TaggedDocument
from gensim.parsing.preprocessing import STOPWORDS as gensim_stop_words
from gensim.utils import simple_preprocess
from nltk.stem.lancaster import LancasterStemmer
from sklearn.feature_extraction import text


MODEL_DIR = "Stored_Doc2VecModel"
CACHE_DIR = os.path.join(MODEL_DIR, "shared_cache")
MODEL_PARAMS = {
    "dm": 0,
    "dbow_words": 1,
    "min_count": 4,
    "negative": 3,
    "hs": 0,
    "sample": 1e-4,
    "window": 5,
    "vector_size": 300,
    "epochs": 20,
    "seed": 1,
}
MODEL_PARAM_ORDER = [
    "dm",
    "dbow_words",
    "min_count",
    "negative",
    "hs",
    "sample",
    "window",
    "vector_size",
    "epochs",
]
# Gensim's asynchronous SGD is only reproducible with a single worker. Training
# stays single-threaded by default so the same dataset and parameters always
# produce the same vectors; set DSMP_DOC2VEC_ALLOW_PARALLEL=1 to trade that
# guarantee for speed.
REPRODUCIBLE_TRAINING = os.environ.get(
    "DSMP_DOC2VEC_ALLOW_PARALLEL", "0").strip() not in ("1", "true", "TRUE", "yes")
SCRIPT_VERSION = 5
LOCK_POLL_SECONDS = 2


def report_progress(event, detail):
    safe_event = str(event).replace("\t", " ").replace("\r", " ").replace("\n", " ")
    safe_detail = str(detail).replace("\t", " ").replace("\r", " ").replace("\n", " ")
    print("DOC2VEC_PROGRESS\t%s\t%s" % (safe_event, safe_detail), flush=True)


def usage():
    print(
        "usage: doc2vec_shared.py <user> <topn> <dataset_file> <node_number> "
        "<num_nodes> [workers] [dm dbow_words min_count negative hs sample window vector_size epochs]",
        file=sys.stderr,
    )


def parse_optional_model_params(arguments, start_index):
    if len(arguments) == start_index:
        return
    if len(arguments) != start_index + len(MODEL_PARAM_ORDER):
        usage()
        sys.exit(2)
    parsed = {}
    try:
        parsed["dm"] = int(arguments[start_index])
        parsed["dbow_words"] = int(arguments[start_index + 1])
        parsed["min_count"] = int(arguments[start_index + 2])
        parsed["negative"] = int(arguments[start_index + 3])
        parsed["hs"] = int(arguments[start_index + 4])
        parsed["sample"] = float(arguments[start_index + 5])
        parsed["window"] = int(arguments[start_index + 6])
        parsed["vector_size"] = int(arguments[start_index + 7])
        parsed["epochs"] = int(arguments[start_index + 8])
    except ValueError:
        usage()
        sys.exit(2)
    validate_model_params(parsed)
    MODEL_PARAMS.update(parsed)


def validate_model_params(params):
    ranges = {
        "dm": (0, 1),
        "dbow_words": (0, 1),
        "min_count": (1, 100),
        "negative": (1, 50),
        "hs": (0, 1),
        "sample": (0.0, 0.1),
        "window": (1, 50),
        "vector_size": (20, 1000),
        "epochs": (1, 200),
        "seed": (0, 2 ** 31 - 1),
    }
    for key, (minimum, maximum) in ranges.items():
        if key not in params:
            # Only validate what the caller actually supplied. "seed" is a fixed
            # model constant, not one of the nine command-line parameters, so it
            # is absent from the parsed dictionary.
            continue
        value = params[key]
        if isinstance(value, float):
            valid = math.isfinite(value) and minimum <= value <= maximum
        else:
            valid = minimum <= int(value) <= maximum
        if not valid:
            raise RuntimeError(
                "Doc2Vec parameter %s must be between %s and %s"
                % (key, minimum, maximum)
            )


def parse_args():
    if len(sys.argv) < 6:
        usage()
        sys.exit(2)

    user = str(sys.argv[1])
    topn = int(sys.argv[2])
    dataset_file = sys.argv[3]
    node_number = int(sys.argv[4])
    num_nodes = int(sys.argv[5])
    if len(sys.argv) > 6:
        workers = int(sys.argv[6])
        next_index = 7
    else:
        cpu_count = os.cpu_count() or 4
        workers = min(max(cpu_count - 1, 1), 12)
        next_index = 6
    parse_optional_model_params(sys.argv, next_index)

    if topn < 1:
        topn = 1
    if node_number < 1:
        node_number = 1
    if num_nodes < 1:
        num_nodes = 1
    if node_number > num_nodes:
        node_number = ((node_number - 1) % num_nodes) + 1
    if workers < 1:
        workers = 1

    return user, topn, dataset_file, node_number, num_nodes, workers


def effective_workers(workers):
    """Worker count actually used for training.

    Pinned to 1 in reproducible mode so the cache key -- and therefore the model
    -- does not depend on how many cores the machine happens to have.
    """
    return 1 if REPRODUCIBLE_TRAINING else max(1, int(workers))


def dataset_signature(dataset_file, workers):
    absolute_path = os.path.abspath(dataset_file)
    stat_info = os.stat(absolute_path)
    signature = {
        "dataset_path": absolute_path,
        "dataset_size": stat_info.st_size,
        "dataset_mtime_ns": stat_info.st_mtime_ns,
        "model_params": MODEL_PARAMS,
        "workers": effective_workers(workers),
        "reproducible": REPRODUCIBLE_TRAINING,
        "script_version": SCRIPT_VERSION,
        "runtime": runtime_signature(),
    }
    encoded = json.dumps(signature, sort_keys=True).encode("utf-8")
    cache_key = hashlib.sha256(encoded).hexdigest()[:24]
    return cache_key, signature


def is_run_corpus(dataset_file):
    """True for the simulator's canonical run corpus (RunCorpus.java).

    Its rows are user, followee, timestamp, normalised text: the exact tokens
    every other algorithm of the run sees, so they are not re-tokenised here.
    """
    name = os.path.basename(dataset_file)
    return name.startswith("corpus-") and name.endswith(".tsv")


def iter_run_corpus(dataset_file):
    with open(dataset_file, "r", encoding="utf-8", errors="ignore", newline="") as input_file:
        for line in input_file:
            fields = line.rstrip("\n").split("\t", 3)
            if len(fields) == 4 and fields[0] and fields[3]:
                yield fields[3].split(" "), fields[0]


def iter_dataset_rows(dataset_file):
    with open(dataset_file, "r", encoding="utf-8", errors="ignore", newline="") as input_file:
        # Tweets may contain quote characters; they are data, never quoting.
        reader = csv.reader(input_file, delimiter="\t", quoting=csv.QUOTE_NONE)
        for fields in reader:
            if len(fields) >= 6:
                tweet_text = fields[5]
                follower = fields[4]
            elif len(fields) >= 2:
                tweet_text = fields[0]
                follower = fields[1]
            else:
                continue

            if tweet_text is None or follower is None:
                continue

            tweet_text = str(tweet_text).strip()
            follower = str(follower).strip()
            if tweet_text and follower:
                yield tweet_text, follower


def make_stop_words():
    letters = list("abcdefghijklmnopqrstuvwxyz")
    numbers = list("0123456789")
    words = [
        "a",
        "able",
        "about",
        "above",
        "abst",
        "accordance",
        "according",
        "actually",
        "added",
        "affected",
        "affecting",
        "another",
        "anybody",
        "anyhow",
        "around",
        "back",
        "became",
        "because",
        "become",
        "beginnings",
        "cant",
    ]
    stopwords = gensim_stop_words.union(set(letters)).union(set(numbers)).union(set(words))
    return text.ENGLISH_STOP_WORDS.union(stopwords)


def preprocess_text(tweet_text, stop_words, stemmer):
    simple = simple_preprocess(tweet_text)
    return [stemmer.stem(word) for word in simple if word not in stop_words]


class TaggedDocumentCorpus:
    def __init__(self, dataset_file):
        self.dataset_file = dataset_file
        self.stop_words = make_stop_words()

    def __iter__(self):
        if is_run_corpus(self.dataset_file):
            for words, follower in iter_run_corpus(self.dataset_file):
                yield TaggedDocument(words=words, tags=[str(follower)])
            return
        stemmer = LancasterStemmer()
        for tweet_text, follower in iter_dataset_rows(self.dataset_file):
            words = preprocess_text(tweet_text, self.stop_words, stemmer)
            yield TaggedDocument(words=words, tags=[str(follower)])


def valid_cached_model(model_path, meta_path, expected_signature):
    if not os.path.exists(model_path) or not os.path.exists(meta_path):
        return False
    try:
        with open(meta_path, "r", encoding="utf-8") as meta_file:
            actual_signature = json.load(meta_file)
        return actual_signature == expected_signature
    except Exception:
        return False


def scoring_cache_paths(model_path):
    return model_path + ".dv.npy", model_path + ".tags.json"


def write_scoring_cache(model, model_path):
    vectors_path, tags_path = scoring_cache_paths(model_path)
    process_suffix = ".tmp." + str(os.getpid())
    temporary_vectors_path = vectors_path + process_suffix + ".npy"
    temporary_tags_path = tags_path + process_suffix
    normed_vectors = model.dv.get_normed_vectors()
    validate_finite_array(normed_vectors, "normalized document vectors")
    np.save(temporary_vectors_path, normed_vectors)
    with open(temporary_tags_path, "w", encoding="utf-8") as tags_file:
        json.dump([str(tag) for tag in model.dv.index_to_key], tags_file)
    os.replace(temporary_vectors_path, vectors_path)
    os.replace(temporary_tags_path, tags_path)


def ensure_scoring_cache(model_path):
    vectors_path, tags_path = scoring_cache_paths(model_path)
    if os.path.exists(vectors_path) and os.path.exists(tags_path):
        return
    report_progress("INDEX", "Building the memory-mapped scoring index")
    model = Doc2Vec.load(model_path)
    write_scoring_cache(model, model_path)
    del model
    gc.collect()


def remove_scoring_cache(model_path):
    vectors_path, tags_path = scoring_cache_paths(model_path)
    for cache_path in [vectors_path, tags_path]:
        try:
            os.remove(cache_path)
        except FileNotFoundError:
            pass


class DocVectorIndex:
    def __init__(self, vectors, tags):
        self.vectors = vectors
        self.index_to_key = tags
        self.key_to_index = {tag: index for index, tag in enumerate(tags)}


def read_scoring_index(model_path):
    ensure_scoring_cache(model_path)
    vectors_path, tags_path = scoring_cache_paths(model_path)
    vectors = np.load(vectors_path, mmap_mode="r")
    validate_finite_array(vectors, "cached normalized document vectors")
    if vectors.ndim != 2:
        raise RuntimeError("Doc2Vec scoring cache is not a two-dimensional matrix.")
    with open(tags_path, "r", encoding="utf-8") as tags_file:
        tags = json.load(tags_file)
    if not isinstance(tags, list) or len(tags) != vectors.shape[0]:
        raise RuntimeError(
            "Doc2Vec scoring cache has mismatched vector and tag counts."
        )
    if any(not isinstance(tag, str) for tag in tags):
        raise RuntimeError("Doc2Vec scoring cache contains a non-string document tag.")
    if len(set(tags)) != len(tags):
        raise RuntimeError("Doc2Vec scoring cache contains duplicate document tags.")
    return DocVectorIndex(vectors, tags)


def load_scoring_index(model_path):
    report_progress("INDEX", "Loading the memory-mapped document-vector index")
    try:
        return read_scoring_index(model_path)
    except (OSError, ValueError, json.JSONDecodeError, RuntimeError):
        remove_scoring_cache(model_path)
        return read_scoring_index(model_path)


def process_is_alive(process_id):
    if process_id is None or process_id < 1:
        return False
    try:
        os.kill(process_id, 0)
        return True
    except PermissionError:
        return True
    except ProcessLookupError:
        return False
    except OSError:
        return False


def read_lock_owner(lock_path):
    try:
        with open(lock_path, "r", encoding="utf-8") as lock_file:
            raw_value = lock_file.read().strip()
        if not raw_value:
            return None
        try:
            payload = json.loads(raw_value)
            return {
                "pid": int(payload.get("pid", -1)),
                "host": payload.get("host"),
            }
        except (TypeError, ValueError, json.JSONDecodeError):
            return {"pid": int(raw_value), "host": None}
    except (OSError, TypeError, ValueError):
        return None


def remove_stale_lock(lock_path):
    if not os.path.exists(lock_path):
        return False
    owner = read_lock_owner(lock_path)
    if owner is not None and owner.get("host") not in (None, socket.gethostname()):
        return False
    owner_process = None if owner is None else owner.get("pid")
    if process_is_alive(owner_process):
        return False
    try:
        os.remove(lock_path)
        print("model lock: removed stale lock from process %s" % owner_process)
        return True
    except FileNotFoundError:
        return True
    except OSError:
        return False


def acquire_lock(lock_path):
    remove_stale_lock(lock_path)
    try:
        fd = os.open(lock_path, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
        with os.fdopen(fd, "w", encoding="utf-8") as lock_file:
            json.dump(
                {
                    "pid": os.getpid(),
                    "host": socket.gethostname(),
                    "created_at": time.time(),
                },
                lock_file,
                sort_keys=True,
            )
        return True
    except FileExistsError:
        return False


def wait_for_model(model_path, meta_path, expected_signature, lock_path):
    last_message_time = 0.0
    while True:
        if valid_cached_model(model_path, meta_path, expected_signature):
            return

        if not os.path.exists(lock_path) or remove_stale_lock(lock_path):
            return

        now = time.time()
        if now - last_message_time > 30:
            report_progress(
                "WAITING",
                "Another recommender node is training the shared Doc2Vec model",
            )
            print("model wait: another Doc2Vec node is training the shared model")
            last_message_time = now
        time.sleep(LOCK_POLL_SECONDS)


def validate_finite_array(array, label, rows_per_chunk=4096):
    if array is None:
        raise RuntimeError("Doc2Vec produced no " + label + ".")
    if getattr(array, "ndim", 0) == 0:
        if not np.isfinite(array):
            raise RuntimeError("Doc2Vec produced a non-finite value in " + label + ".")
        return
    row_count = array.shape[0]
    for start in range(0, row_count, rows_per_chunk):
        if not np.isfinite(array[start : start + rows_per_chunk]).all():
            raise RuntimeError("Doc2Vec produced non-finite values in " + label + ".")


def validate_trained_model(model):
    if model.corpus_count < 1:
        raise RuntimeError("Doc2Vec trained with an empty corpus.")
    if len(model.dv.index_to_key) < 1:
        raise RuntimeError("Doc2Vec produced no document tags.")
    validate_finite_array(model.dv.vectors, "document vectors")
    validate_finite_array(model.wv.vectors, "word vectors")
    if getattr(model, "syn1neg", None) is not None:
        validate_finite_array(model.syn1neg, "negative-sampling weights")


def remove_incomplete_cache(model_path):
    cache_files = glob.glob(model_path + "*")
    for cache_file in cache_files:
        try:
            os.remove(cache_file)
        except FileNotFoundError:
            pass


def train_shared_model(dataset_file, cache_key, signature, model_path, meta_path, workers):
    report_progress("TRAINING", "Training the shared Doc2Vec model")
    print("model train: building shared Doc2Vec model")
    print(
        "model determinism: seed=%s workers=%s reproducible=%s"
        % (MODEL_PARAMS["seed"], effective_workers(workers), REPRODUCIBLE_TRAINING)
    )
    print(
        "model runtime: gensim=%s numpy=%s dot_kernel=%s patched=%s"
        % (
            GENSIM_RUNTIME["gensim_version"],
            GENSIM_RUNTIME["numpy_version"],
            GENSIM_RUNTIME["dot_kernel"],
            GENSIM_RUNTIME["dot_kernel_patched"],
        )
    )
    tagged_docs = TaggedDocumentCorpus(dataset_file)

    model = Doc2Vec(
        dm=MODEL_PARAMS["dm"],
        dbow_words=MODEL_PARAMS["dbow_words"],
        min_count=MODEL_PARAMS["min_count"],
        negative=MODEL_PARAMS["negative"],
        hs=MODEL_PARAMS["hs"],
        sample=MODEL_PARAMS["sample"],
        window=MODEL_PARAMS["window"],
        vector_size=MODEL_PARAMS["vector_size"],
        seed=MODEL_PARAMS["seed"],
        workers=effective_workers(workers),
    )
    model.build_vocab(tagged_docs, progress_per=100)
    if model.corpus_count < 1:
        raise RuntimeError("No usable rows found in dataset: " + dataset_file)
    model.train(tagged_docs, total_examples=model.corpus_count, epochs=MODEL_PARAMS["epochs"])
    validate_trained_model(model)
    temporary_meta_path = meta_path + ".tmp." + str(os.getpid())
    model.save(model_path)
    write_scoring_cache(model, model_path)
    with open(temporary_meta_path, "w", encoding="utf-8") as meta_file:
        json.dump(signature, meta_file, sort_keys=True, indent=2)
    os.replace(temporary_meta_path, meta_path)
    del model
    gc.collect()
    report_progress("MODEL_READY", "Shared Doc2Vec model trained and cached")
    print("model train: shared Doc2Vec model saved")


def ensure_shared_model(dataset_file, workers):
    os.makedirs(CACHE_DIR, exist_ok=True)
    cache_key, signature = dataset_signature(dataset_file, workers)
    model_path = os.path.join(CACHE_DIR, "shared_" + cache_key + ".model")
    meta_path = os.path.join(CACHE_DIR, "shared_" + cache_key + ".json")
    lock_path = os.path.join(CACHE_DIR, "shared_" + cache_key + ".lock")

    if valid_cached_model(model_path, meta_path, signature):
        report_progress("CACHE", "Loading the existing shared Doc2Vec model")
        print("model cache: using existing shared Doc2Vec model")
        ensure_scoring_cache(model_path)
        return model_path

    if acquire_lock(lock_path):
        try:
            if not valid_cached_model(model_path, meta_path, signature):
                try:
                    train_shared_model(
                        dataset_file,
                        cache_key,
                        signature,
                        model_path,
                        meta_path,
                        workers,
                    )
                except Exception:
                    remove_incomplete_cache(model_path)
                    for metadata_file in [meta_path] + glob.glob(
                        meta_path + ".tmp.*"
                    ):
                        try:
                            os.remove(metadata_file)
                        except FileNotFoundError:
                            pass
                    raise
        finally:
            try:
                os.remove(lock_path)
            except OSError:
                pass
    else:
        wait_for_model(model_path, meta_path, signature, lock_path)
        if not valid_cached_model(model_path, meta_path, signature):
            return ensure_shared_model(dataset_file, workers)

    return model_path


def score_candidate_shard(index, user, topn, node_number, num_nodes):
    if user not in index.key_to_index:
        raise RuntimeError("User is not present in Doc2Vec model: " + user)

    query_index = index.key_to_index[user]
    query_vector = index.vectors[query_index]

    node_offset = node_number - 1
    scored_candidates = []
    for candidate_index, tag in enumerate(index.index_to_key):
        if candidate_index == query_index:
            continue
        if num_nodes > 1 and (candidate_index % num_nodes) != node_offset:
            continue

        score = float(np.dot(index.vectors[candidate_index], query_vector))
        if math.isnan(score) or math.isinf(score):
            continue
        scored_candidates.append((tag, score))

    scored_candidates.sort(key=lambda item: item[1], reverse=True)
    return scored_candidates[:topn]


def main():
    start_time = time.time()
    user, topn, dataset_file, node_number, num_nodes, workers = parse_args()
    print(
        "model config: user=%s topn=%s node=%s/%s workers=%s"
        % (user, topn, node_number, num_nodes, workers)
    )

    model_path = ensure_shared_model(dataset_file, workers)
    index = load_scoring_index(model_path)
    results = score_candidate_shard(index, user, topn, node_number, num_nodes)

    for candidate, score in results:
        print("python", (candidate, score))

    completion_time_ms = (time.time() - start_time) * 1000.0
    print("completionTime", completion_time_ms)


if __name__ == "__main__":
    main()
