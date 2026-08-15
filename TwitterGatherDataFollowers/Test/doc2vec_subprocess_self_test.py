import base64
import os
import subprocess
import sys
import tempfile


PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SCRIPT_DIR = os.path.join(
    PROJECT_ROOT, "TwitterGatherDataFollowers", "userRyersonU"
)
SHARED_SCRIPT = os.path.join(SCRIPT_DIR, "doc2vec_shared.py")
BATCH_SCRIPT = os.path.join(SCRIPT_DIR, "doc2vec_batch.py")


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def run_command(command, working_directory):
    result = subprocess.run(
        command,
        cwd=working_directory,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )
    require(
        "Exception ignored in:" not in result.stdout,
        "unsafe Gensim numerical exception escaped the compatibility layer",
    )
    require(
        result.returncode == 0,
        "Doc2Vec subprocess failed with code %s:\n%s"
        % (result.returncode, result.stdout),
    )
    return result.stdout


def decode(value):
    return base64.b64decode(value).decode("utf-8")


def parse_batch(output):
    scores = {}
    for line in output.splitlines():
        fields = line.split("\t")
        if len(fields) == 4 and fields[0] == "DOC2VEC_SCORE":
            user = decode(fields[1])
            candidate = decode(fields[2])
            scores.setdefault(user, {})[candidate] = float(fields[3])
    return scores


def parse_single(output):
    scores = {}
    for line in output.splitlines():
        if not line.startswith("python ("):
            continue
        payload = line[len("python (") : -1]
        candidate, score = payload.rsplit(",", 1)
        scores[candidate.strip().strip("'")] = float(score.strip())
    return scores


def main():
    with tempfile.TemporaryDirectory(prefix="doc2vec-subprocess-test-") as temp_dir:
        dataset_path = os.path.join(temp_dir, "dataset.txt")
        users_path = os.path.join(temp_dir, "users.txt")
        users = ("Alice", "Bob", "Charlie", "Delta")
        with open(dataset_path, "w", encoding="utf-8") as dataset_file:
            tweet_id = 1
            for repetition in range(30):
                for user in users:
                    dataset_file.write(
                        "Store\t%d\t2026-01-01\t1\t%s\t"
                        "shared words %s topic%d\n"
                        % (tweet_id, user, user.lower(), repetition % 3)
                    )
                    tweet_id += 1
        with open(users_path, "w", encoding="utf-8") as users_file:
            users_file.write("Alice\nBob\n")

        global_batch = parse_batch(
            run_command(
                [
                    sys.executable,
                    BATCH_SCRIPT,
                    "3",
                    dataset_path,
                    "1",
                    "1",
                    "1",
                    users_path,
                ],
                temp_dir,
            )
        )
        single_scores = parse_single(
            run_command(
                [
                    sys.executable,
                    SHARED_SCRIPT,
                    "Alice",
                    "3",
                    dataset_path,
                    "1",
                    "1",
                    "1",
                ],
                temp_dir,
            )
        )
        require(
            global_batch.get("Alice") == single_scores,
            "single-user and batch Doc2Vec scores diverged",
        )
        require(
            global_batch.get("Alice") != global_batch.get("Bob"),
            "different target users received the same Doc2Vec score map",
        )

        merged_scores = {}
        for node_number in (1, 2):
            node_scores = parse_batch(
                run_command(
                    [
                        sys.executable,
                        BATCH_SCRIPT,
                        "3",
                        dataset_path,
                        str(node_number),
                        "2",
                        "1",
                        users_path,
                    ],
                    temp_dir,
                )
            )
            for candidate, score in node_scores.get("Alice", {}).items():
                merged_scores[candidate] = score
        merged_top = dict(
            sorted(
                merged_scores.items(),
                key=lambda item: item[1],
                reverse=True,
            )[:3]
        )
        require(
            merged_top == global_batch.get("Alice"),
            "multi-node Doc2Vec shards changed the global top results",
        )

    print("doc2vec_subprocess_self_test passed")


if __name__ == "__main__":
    main()
