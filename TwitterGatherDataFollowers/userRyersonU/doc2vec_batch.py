import base64
import sys
import time

from doc2vec_shared import (
    ensure_shared_model,
    load_scoring_index,
    parse_optional_model_params,
    report_progress,
    score_candidate_shard,
)


SCORE_PREFIX = "DOC2VEC_SCORE"
ERROR_PREFIX = "DOC2VEC_ERROR"
DONE_PREFIX = "DOC2VEC_DONE"


def usage():
    print(
        "usage: doc2vec_batch.py <topn> <dataset_file> <node_number> "
        "<num_nodes> <workers> <users_file> "
        "[dm dbow_words min_count negative hs sample window vector_size epochs]",
        file=sys.stderr,
    )


def parse_args():
    if len(sys.argv) not in (7, 16):
        usage()
        sys.exit(2)

    topn = max(int(sys.argv[1]), 1)
    dataset_file = sys.argv[2]
    node_number = max(int(sys.argv[3]), 1)
    num_nodes = max(int(sys.argv[4]), 1)
    workers = max(int(sys.argv[5]), 1)
    users_file = sys.argv[6]
    parse_optional_model_params(sys.argv, 7)

    if node_number > num_nodes:
        node_number = ((node_number - 1) % num_nodes) + 1

    with open(users_file, "r", encoding="utf-8") as input_file:
        users = [line.rstrip("\r\n") for line in input_file]
    users = [user for user in users if user]
    if not users:
        raise RuntimeError("No users were supplied for Doc2Vec batch scoring")

    return topn, dataset_file, node_number, num_nodes, workers, users


def encode_field(value):
    return base64.b64encode(str(value).encode("utf-8")).decode("ascii")


def emit_score(user, candidate, score):
    print(
        "%s\t%s\t%s\t%s"
        % (SCORE_PREFIX, encode_field(user), encode_field(candidate), repr(float(score)))
    )


def emit_error(user, error):
    message = str(error).replace("\r", " ").replace("\n", " ")
    print("%s\t%s\t%s" % (ERROR_PREFIX, encode_field(user), encode_field(message)))


def emit_done(user):
    print("%s\t%s" % (DONE_PREFIX, encode_field(user)))


def main():
    start_time = time.time()
    topn, dataset_file, node_number, num_nodes, workers, users = parse_args()
    print(
        "batch model config: users=%s topn=%s node=%s/%s workers=%s"
        % (len(users), topn, node_number, num_nodes, workers)
    )

    model_path = ensure_shared_model(dataset_file, workers)
    index = load_scoring_index(model_path)
    report_progress(
        "SCORING",
        "Scoring %s recommendation target%s"
        % (len(users), "" if len(users) == 1 else "s"),
    )

    for user in users:
        try:
            results = score_candidate_shard(index, user, topn, node_number, num_nodes)
        except Exception as error:
            emit_error(user, error)
            results = []

        for candidate, score in results:
            emit_score(user, candidate, score)
        emit_done(user)

    completion_time_ms = (time.time() - start_time) * 1000.0
    report_progress("COMPLETE", "Doc2Vec scoring completed")
    print("completionTime", completion_time_ms)


if __name__ == "__main__":
    main()
