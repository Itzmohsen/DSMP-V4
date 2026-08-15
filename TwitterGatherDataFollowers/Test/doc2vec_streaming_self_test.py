import json
import os
import sys
import tempfile


PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SCRIPT_DIR = os.path.join(
    PROJECT_ROOT, "TwitterGatherDataFollowers", "userRyersonU"
)
sys.path.insert(0, SCRIPT_DIR)

import doc2vec_shared
import gensim_runtime_compat


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def main():
    runtime = gensim_runtime_compat.configure_gensim_runtime()
    require(
        gensim_runtime_compat.active_dot_probe() == -1.0,
        "safe Doc2Vec dot-product kernel was not active",
    )
    require(
        runtime["dot_kernel"] in (
            "gensim-native",
            "direct-float-blas",
            "gensim-no-blas",
        ),
        "unexpected Doc2Vec dot-product kernel",
    )

    with tempfile.TemporaryDirectory(prefix="doc2vec-streaming-test-") as temp_dir:
        dataset_path = os.path.join(temp_dir, "dataset.txt")
        model_path = os.path.join(temp_dir, "model.bin")
        meta_path = os.path.join(temp_dir, "model.json")
        rows = []
        followers = ("Alice", "Bob", "Charlie", "Delta", "Echo")
        for index in range(20):
            follower = followers[index % len(followers)]
            text = "shared repeated words " + follower.lower()
            rows.append(
                "Followee\t%d\t2026-01-01\t1\t%s\t%s\n"
                % (index + 1, follower, text)
            )
        with open(dataset_path, "w", encoding="utf-8") as dataset_file:
            dataset_file.writelines(rows)

        _, dataset_signature = doc2vec_shared.dataset_signature(
            dataset_path, workers=1
        )
        require(
            dataset_signature["script_version"] == 3,
            "unsafe pre-compatibility Doc2Vec caches were not invalidated",
        )
        require(
            dataset_signature["runtime"]["dot_kernel"]
            == runtime["dot_kernel"],
            "Doc2Vec cache signature omitted the active numerical kernel",
        )
        require(
            dataset_signature["runtime"]["scipy_version"]
            == runtime["scipy_version"],
            "Doc2Vec cache signature omitted the SciPy runtime",
        )

        corpus = doc2vec_shared.TaggedDocumentCorpus(dataset_path)
        first_pass = [(document.words, document.tags) for document in corpus]
        second_pass = [(document.words, document.tags) for document in corpus]
        require(first_pass == second_pass, "streaming corpus changed between epochs")
        require(len(first_pass) == 20, "streaming corpus lost rows")

        signature = {"self_test": True}
        doc2vec_shared.train_shared_model(
            dataset_path,
            "self-test",
            signature,
            model_path,
            meta_path,
            workers=1,
        )
        index = doc2vec_shared.load_scoring_index(model_path)
        require(index.vectors.shape[0] == 5, "lightweight cache lost document tags")
        require(set(index.index_to_key) == set(followers), "tag cache is incorrect")
        _, tags_path = doc2vec_shared.scoring_cache_paths(model_path)
        with open(tags_path, "w", encoding="utf-8") as tags_file:
            json.dump(["corrupt"], tags_file)
        repaired_index = doc2vec_shared.load_scoring_index(model_path)
        require(
            repaired_index.index_to_key == index.index_to_key,
            "mismatched scoring cache was not rebuilt from the model",
        )
        index = repaired_index
        scores = doc2vec_shared.score_candidate_shard(
            index, "Alice", 1, 1, 1
        )
        require(scores and scores[0][0] != "Alice", "streamed model scored its target")
        topn = 2
        global_scores = doc2vec_shared.score_candidate_shard(
            index, "Alice", topn, 1, 1
        )
        merged_shards = []
        for node_number in range(1, 3):
            merged_shards.extend(
                doc2vec_shared.score_candidate_shard(
                    index,
                    "Alice",
                    topn,
                    node_number,
                    2,
                )
            )
        merged_shards.sort(key=lambda item: item[1], reverse=True)
        merged_shards = merged_shards[:topn]
        require(
            global_scores == merged_shards,
            "multi-node Doc2Vec shards changed global scores",
        )
        with open(meta_path, "r", encoding="utf-8") as meta_file:
            require(json.load(meta_file) == signature, "model metadata changed")
        require(
            doc2vec_shared.valid_cached_model(
                model_path, meta_path, signature
            ),
            "valid Doc2Vec cache was not reusable",
        )
        require(
            not doc2vec_shared.valid_cached_model(
                model_path, meta_path, {"self_test": False}
            ),
            "mismatched Doc2Vec cache signature was accepted",
        )

        stale_lock_path = os.path.join(temp_dir, "stale.lock")
        with open(stale_lock_path, "w", encoding="utf-8") as stale_lock:
            json.dump({"pid": 99999999}, stale_lock)
        require(
            doc2vec_shared.remove_stale_lock(stale_lock_path),
            "dead Doc2Vec training lock was not removed",
        )
        require(
            not os.path.exists(stale_lock_path),
            "dead Doc2Vec training lock remained on disk",
        )

    print("doc2vec_streaming_self_test passed")


if __name__ == "__main__":
    main()
