import csv
import glob
import json
import os
import sys

import numpy as np

from doc2vec_shared import (
    CACHE_DIR,
    dataset_signature,
    parse_optional_model_params,
    scoring_cache_paths,
    valid_cached_model,
)


def usage():
    print(
        "usage: doc2vec_cache_admin.py <info|delete> <dataset_file> <workers> "
        "[dm dbow_words min_count negative hs sample window vector_size epochs]",
        file=sys.stderr,
    )


def emit(key, value):
    print("CACHE_INFO\t%s\t%s" % (key, str(value)), flush=True)


def parse_args():
    if len(sys.argv) not in (4, 13):
        usage()
        sys.exit(2)
    command = sys.argv[1].strip().lower()
    if command not in ("info", "delete"):
        usage()
        sys.exit(2)
    dataset_file = os.path.abspath(sys.argv[2])
    workers = max(int(sys.argv[3]), 1)
    parse_optional_model_params(sys.argv, 4)
    return command, dataset_file, workers


def dataset_shape(dataset_file):
    rows = 0
    malformed = 0
    users = set()
    with open(dataset_file, "r", encoding="utf-8", errors="ignore", newline="") as input_file:
        reader = csv.reader(input_file, delimiter="\t")
        for fields in reader:
            if len(fields) >= 6:
                rows += 1
                user = str(fields[4]).strip()
                if user:
                    users.add(user)
            elif len(fields) >= 2:
                rows += 1
                user = str(fields[1]).strip()
                if user:
                    users.add(user)
            else:
                malformed += 1
    return rows, len(users), malformed


def cache_paths(cache_key):
    model_path = os.path.join(CACHE_DIR, "shared_" + cache_key + ".model")
    meta_path = os.path.join(CACHE_DIR, "shared_" + cache_key + ".json")
    vectors_path, tags_path = scoring_cache_paths(model_path)
    paths = [model_path, meta_path, vectors_path, tags_path]
    paths.extend(glob.glob(model_path + ".*"))
    paths.extend(glob.glob(meta_path + ".tmp.*"))
    return sorted(set(paths))


def cache_size(paths):
    total = 0
    for path in paths:
        try:
            if os.path.isfile(path):
                total += os.path.getsize(path)
        except OSError:
            pass
    return total


def related_cache_keys(dataset_file):
    keys = set()
    absolute_path = os.path.abspath(dataset_file)
    if not os.path.isdir(CACHE_DIR):
        return keys
    for meta_path in glob.glob(os.path.join(CACHE_DIR, "shared_*.json")):
        try:
            with open(meta_path, "r", encoding="utf-8") as meta_file:
                metadata = json.load(meta_file)
            if os.path.abspath(str(metadata.get("dataset_path", ""))) == absolute_path:
                name = os.path.basename(meta_path)
                if name.startswith("shared_") and name.endswith(".json"):
                    keys.add(name[len("shared_") : -len(".json")])
        except Exception:
            continue
    return keys


def vector_shape(model_path):
    vectors_path, tags_path = scoring_cache_paths(model_path)
    tag_count = 0
    vector_rows = 0
    vector_dims = 0
    if os.path.isfile(tags_path):
        try:
            with open(tags_path, "r", encoding="utf-8") as tags_file:
                tags = json.load(tags_file)
            if isinstance(tags, list):
                tag_count = len(tags)
        except Exception:
            tag_count = 0
    if os.path.isfile(vectors_path):
        try:
            vectors = np.load(vectors_path, mmap_mode="r")
            if len(vectors.shape) == 2:
                vector_rows = int(vectors.shape[0])
                vector_dims = int(vectors.shape[1])
        except Exception:
            vector_rows = 0
            vector_dims = 0
    return tag_count, vector_rows, vector_dims


def inspect_cache(dataset_file, workers):
    os.makedirs(CACHE_DIR, exist_ok=True)
    cache_key, signature = dataset_signature(dataset_file, workers)
    model_path = os.path.join(CACHE_DIR, "shared_" + cache_key + ".model")
    meta_path = os.path.join(CACHE_DIR, "shared_" + cache_key + ".json")
    vectors_path, tags_path = scoring_cache_paths(model_path)
    exact_paths = cache_paths(cache_key)
    related_keys = related_cache_keys(dataset_file)
    related_keys.add(cache_key)
    related_paths = []
    for related_key in related_keys:
        related_paths.extend(cache_paths(related_key))
    related_paths = sorted(set(related_paths))

    rows, users, malformed = dataset_shape(dataset_file)
    tag_count, vector_rows, vector_dims = vector_shape(model_path)
    valid = valid_cached_model(model_path, meta_path, signature)
    exact_exists = any(os.path.isfile(path) for path in exact_paths)
    related_existing = [path for path in related_paths if os.path.isfile(path)]

    emit("dataset_path", dataset_file)
    emit("dataset_size_bytes", os.path.getsize(dataset_file))
    emit("dataset_rows", rows)
    emit("dataset_users", users)
    emit("dataset_malformed_rows", malformed)
    emit("workers", workers)
    emit("cache_key", cache_key)
    emit("cache_valid", str(valid).lower())
    emit("cache_state", "WARM" if valid else "STALE" if exact_exists else "COLD")
    emit("model_path", model_path)
    emit("meta_path", meta_path)
    emit("vectors_path", vectors_path)
    emit("tags_path", tags_path)
    emit("model_exists", str(os.path.isfile(model_path)).lower())
    emit("vectors_exists", str(os.path.isfile(vectors_path)).lower())
    emit("tags_exists", str(os.path.isfile(tags_path)).lower())
    emit("tag_count", tag_count)
    emit("vector_rows", vector_rows)
    emit("vector_dims", vector_dims)
    emit("exact_cache_bytes", cache_size(exact_paths))
    emit("dataset_cache_entries", len(related_keys))
    emit("dataset_cache_files", len(related_existing))
    emit("dataset_cache_bytes", cache_size(related_paths))


def delete_cache(dataset_file, workers):
    os.makedirs(CACHE_DIR, exist_ok=True)
    cache_key, _ = dataset_signature(dataset_file, workers)
    paths = cache_paths(cache_key)

    deleted_count = 0
    deleted_bytes = 0
    for path in paths:
        try:
            if os.path.isfile(path):
                deleted_bytes += os.path.getsize(path)
                os.remove(path)
                deleted_count += 1
        except FileNotFoundError:
            pass
    emit("deleted_files", deleted_count)
    emit("deleted_bytes", deleted_bytes)
    inspect_cache(dataset_file, workers)


def main():
    command, dataset_file, workers = parse_args()
    if command == "delete":
        delete_cache(dataset_file, workers)
    else:
        inspect_cache(dataset_file, workers)


if __name__ == "__main__":
    main()
