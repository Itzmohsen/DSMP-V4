"""Persistent Doc2Vec scoring worker: one process per recommender-node lane.

The worker prepares the shared model once (training it if no valid cache
exists; concurrent workers wait on the model lock), loads the memory-mapped
vector index once, then serves any number of scoring requests read as JSON
lines from stdin. Each request scores a contiguous shard of the global
candidate index for a list of target users:

    {"id": "u17", "shard": 3, "shards": 16, "topn": 10, "users": ["alice", ...]}

Shard s of S covers index positions [floor((s-1)D/S), floor(sD/S)). Every
candidate lies in exactly one shard, each candidate's score is computed with
the same np.dot on the same stored vectors whichever shard or node scores it,
and each shard returns its top-n by (score descending, name ascending), which
is the order the organizer ranks by. The union of all shards' top-n therefore
contains exactly the global top-n, for any number of shards and any placement.

Output lines (fields base64-encoded where they are names):
    DOC2VEC_READY <candidate count> <cpu seconds spent preparing>
    DOC2VEC_SCORE <user> <candidate> <score>
    DOC2VEC_ERROR <user> <message>
    DOC2VEC_DONE <user>
    DOC2VEC_UNIT_DONE <request id> <cpu seconds for the request>
    DOC2VEC_FATAL <message>
"""
import base64
import json
import math
import sys
import time

from doc2vec_shared import (
    ensure_shared_model,
    load_scoring_index,
    parse_optional_model_params,
)

import numpy as np


def encode_field(value):
    return base64.b64encode(str(value).encode("utf-8")).decode("ascii")


def emit(line):
    sys.stdout.write(line + "\n")
    sys.stdout.flush()


def shard_bounds(shard, shards, count):
    safe_shards = max(1, int(shards))
    safe_shard = min(max(1, int(shard)), safe_shards)
    low = ((safe_shard - 1) * count) // safe_shards
    high = (safe_shard * count) // safe_shards
    return low, high


def score_range(index, user, topn, low, high):
    if user not in index.key_to_index:
        raise RuntimeError("User is not present in Doc2Vec model: " + user)
    query_index = index.key_to_index[user]
    query_vector = index.vectors[query_index]
    tags = index.index_to_key
    scored = []
    for candidate_index in range(low, high):
        if candidate_index == query_index:
            continue
        score = float(np.dot(index.vectors[candidate_index], query_vector))
        if math.isnan(score) or math.isinf(score):
            continue
        scored.append((tags[candidate_index], score))
    scored.sort(key=lambda item: (-item[1], item[0]))
    return scored[: max(1, int(topn))]


def main():
    if len(sys.argv) < 3:
        emit("DOC2VEC_FATAL\t" + encode_field(
            "usage: doc2vec_worker.py <dataset_file> <workers> [model params]"))
        return 2
    dataset_file = sys.argv[1]
    workers = max(1, int(sys.argv[2]))
    parse_optional_model_params(sys.argv, 3)
    started = time.process_time()
    try:
        model_path = ensure_shared_model(dataset_file, workers)
        index = load_scoring_index(model_path)
    except Exception as error:  # noqa: BLE001 - reported to the Java side
        emit("DOC2VEC_FATAL\t" + encode_field("%s: %s" % (type(error).__name__, error)))
        return 1
    count = len(index.index_to_key)
    emit("DOC2VEC_READY\t%d\t%.9f" % (count, time.process_time() - started))

    for raw in sys.stdin:
        raw = raw.strip()
        if not raw:
            continue
        request = json.loads(raw)
        if request.get("op") == "stop":
            break
        request_id = str(request["id"])
        begin = time.process_time()
        low, high = shard_bounds(request["shard"], request["shards"], count)
        topn = int(request["topn"])
        for user in request["users"]:
            try:
                results = score_range(index, user, topn, low, high)
            except Exception as error:  # noqa: BLE001 - per-user error line
                emit("DOC2VEC_ERROR\t%s\t%s" % (encode_field(user), encode_field(error)))
                results = []
            for candidate, score in results:
                emit("DOC2VEC_SCORE\t%s\t%s\t%s"
                     % (encode_field(user), encode_field(candidate), repr(float(score))))
            emit("DOC2VEC_DONE\t%s" % encode_field(user))
        emit("DOC2VEC_UNIT_DONE\t%s\t%.9f" % (request_id, time.process_time() - begin))
    return 0


if __name__ == "__main__":
    sys.exit(main())
