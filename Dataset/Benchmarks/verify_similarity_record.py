#!/usr/bin/env python3
"""Recomputes the Similarity algorithm's lists from a dataset whose text is
already normalised (such as the Synthetic Communities benchmark) and compares
them with a recorded Similarity run.

Similarity's rule: TF-IDF weights tf * log2(N / df) (N users, df users using
the term), L2-normalised per user, cosine; exact ties ordered by user name.

    python3 Dataset/Benchmarks/verify_similarity_record.py \
        Dataset/Benchmarks/Synthetic-Communities-1500-Users-8.6M.txt \
        Results/Runs/<the Similarity run's folder>

It prints how many delivered lists are identical and the largest difference
between the delivered and recomputed score ratios (the record rounds scores to
six decimals on a 0-100 scale, so about 5e-9 is a perfect match).
"""
import csv, math, sys
import numpy as np
data, record = sys.argv[1], sys.argv[2]
users, index, rows = [], {}, []
vocab = {}
counts = []
with open(data, encoding="utf-8") as f:
    for line in f:
        fields = line.rstrip("\n").split("\t")
        user = fields[4]
        if user not in index:
            index[user] = len(users); users.append(user); counts.append({})
        c = counts[index[user]]
        for w in fields[5].split():
            t = vocab.setdefault(w, len(vocab))
            c[t] = c.get(t, 0) + 1
n = len(users)
df = np.zeros(len(vocab))
for c in counts:
    df[list(c.keys())] += 1
idf = np.log2(n / df)
W = np.zeros((n, len(vocab)))
for u, c in enumerate(counts):
    keys = np.fromiter(c.keys(), dtype=np.int64); vals = np.fromiter(c.values(), dtype=np.float64)
    W[u, keys] = vals * idf[keys]
W /= np.linalg.norm(W, axis=1, keepdims=True)
delivered = {}
with open(record + "/recommendations.csv") as f:
    for r in csv.DictReader(f):
        delivered.setdefault(r["target"], []).append((int(r["rank"]), r["candidate"], float(r["score"])))
names = np.array(users)
order_by_name = np.argsort(np.argsort(names))
same10 = same_all = 0
max_ratio_error = 0.0
for target, items in delivered.items():
    items.sort()
    u = index[target]
    s = W @ W[u]; s[u] = -np.inf
    ranking = np.lexsort((order_by_name, -s))
    mine = [users[v] for v in ranking[:len(items)]]
    theirs = [c for _, c, _ in items]
    same10 += mine[:10] == theirs[:10]
    same_all += mine == theirs
    first = s[ranking[0]]
    for (_, cand, score) in items:
        max_ratio_error = max(max_ratio_error, abs(score / 100.0 - s[index[cand]] / first))
print("targets:", len(delivered), "| identical top-10 lists:", same10, "| identical full lists (", len(items), "each):", same_all,
      "| max score-ratio difference: %.2e" % max_ratio_error)
