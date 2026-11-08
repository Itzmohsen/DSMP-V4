# Synthetic Communities Benchmark (1,500 users, 8.6M tweets)

## Purpose

`Synthetic-Communities-1500-Users-8.6M.txt` is a very large artificial
dataset whose ground truth is known by construction:
- **Long runs.** A single run of Similarity or TGF takes more than 5 minutes.
  This leaves time to watch the Node Lab's live view and to pause, crash or
  boost nodes by hand without slowing the emulation.
- **Results that can be checked.** Every user's community and topic mixture
  is recorded, and the main results are verified independently (below).
- **Meaningful difficulty.** The communities are recoverable but not trivially
  separable, and models that use timing as well as text do better.

## Structure

| | |
|---|---|
| Rows | 8,557,653 tweets in the standard six-column DSMP format: followee account, tweet id, date, user id, user name, text |
| Users | 1,500, each following exactly one account |
| Accounts (communities) | 12 fictional accounts |
| Tweets per user | 947 to 9,000 (median 5,518) |
| Words | 81,298,905 (4 to about 20 per tweet); 29,320 distinct words, each used by at least two users |
| Dates | 2019-01-01 to 2024-12-31, inside the simulator's default window |
| Size | 1,294,765,078 bytes |
| SHA-256 | `14018bff7b0418d93f31206807227b5220ebb257d65ba4c8b63400d8358df013` |

| Account | Users | Tweets |
|---|---:|---:|
| AstroNightly | 214 | 1,192,108 |
| TrailRunnersClub | 193 | 1,098,272 |
| CodeCraftDevs | 172 | 970,539 |
| BakeHouseDaily | 141 | 780,202 |
| PixelArcadeHQ | 133 | 772,323 |
| GreenThumbGarden | 130 | 762,476 |
| CityCycleHub | 113 | 630,929 |
| JazzCellarLive | 103 | 614,248 |
| MarketPulseFin | 94 | 548,725 |
| BookNookReads | 89 | 505,086 |
| PawsRescueNet | 60 | 344,628 |
| SaltwaterSurfCo | 58 | 338,117 |

Rows are ordered by account, then user, newest tweet first. All accounts and
people are fictional.

Every row is valid input for V4:
- Every tweet id is unique.
- Every word is lowercase letters only and is not a stop word.
- Every tweet has at least four words.

V4's text processing therefore keeps every tweet and every word exactly as
written, so every algorithm sees exactly this text.

## How it is generated

`build_synthetic_communities.py` (in this folder) writes the dataset
deterministically: the same seed gives the same bytes. It takes about
30 seconds.

```bash
python3 Dataset/Benchmarks/build_synthetic_communities.py --name Synthetic-Communities-1500-Users-8.6M
```

The 1.29 GB data file is not stored in the repository (it is listed in
`.gitignore`). Generate it with this command; it needs Python 3 with numpy.
Its SHA-256 must equal the one above and in the manifest:

```bash
shasum -a 256 Dataset/Benchmarks/Synthetic-Communities-1500-Users-8.6M.txt
```

It also writes:
- `…-ground-truth.csv`: every user's account, topic weights and second interest.
- `…-manifest.json`: parameters, counts, the checks below and the SHA-256.

The generative model:

1. **Topics.**
   - Each account has its own hand-written lexicon of 87 to 152 real topical
     words, for example telescope, nebula and perseids for AstroNightly, or
     sourdough, levain and croissant for BakeHouseDaily.
   - Five shared-interest topics (outdoors, fitness, tech, culture, home life)
     each link three to five accounts, so communities are related to
     different degrees.
   - Background words come from the project's own Twitter word list
     (`words_100k.txt`) at its observed frequencies, names and handles
     included.
   - Within a topic, word frequencies follow a Zipf law.
2. **Each user's mixture.**
   - Their own account's topic, at a small weight (0.8% to about 4%).
   - The shared interests of their account (4–12%).
   - For 75% of users, a second account's topic (1–10%). This is sometimes
     stronger than their own.
   - The rest is background.
   - 15% are silent followers: they follow an account but almost never tweet
     about its topic. On real Twitter, following an account says little about
     what a person tweets.
3. **Time.**
   - Each account has a seasonal peak, typical hours and weekdays, and ten
     dated events. In an event month, its followers mention the event's
     words.
   - Each user's own daily and weekly rhythm is 65% personal and 35% their
     account's.
   - Each user is active for a lognormal span of 6 to 72 months.
4. **Volume.** Tweets per user are lognormal (median 5,500), clipped to 9,000.

## What is known to be correct

**An oracle that knows the generator.** It classifies each user by the
likelihood of their words under each account's average word distribution, and
recovers the followed account of **90.5%** of users. The remainder are mostly
the silent followers, whose text is genuinely ambiguous. No text-only method
should beat this. The largest account holds 14.3% of users.

**Similarity, recomputed independently.**
- `verify_similarity_record.py` recomputes Similarity's scoring rule in Python
  from the raw file: TF-IDF tf × log2(N/df), L2-normalised, cosine, exact ties
  by name.
- On the recorded 40-target run below, **all 40 delivered lists (100 users
  each) are identical** to the recomputation.
- Every score ratio agrees to 5 × 10⁻⁹, which is the record's six-decimal
  rounding.

```bash
python3 Dataset/Benchmarks/verify_similarity_record.py \
    Dataset/Benchmarks/Synthetic-Communities-1500-Users-8.6M.txt \
    Results/Runs/<your Similarity run's folder>
```

Over all 1,500 users as targets, the recomputation gives Similarity P@10
0.718 and top-1 91.8%, against a chance P@10 of 0.095.

## Running it

0. **Generate the file** once, as above, if `Dataset/Benchmarks` does not
   have it yet.
1. **Java heap: 24,576 MB.** In the Simulator, open Run › Memory › Maximum heap
   (MB), enter `24576` and click Apply; the simulator restarts.
   - TGF's peak live heap on this dataset was 10.5 GB.
   - The simulator's own minimum for a file this size (8 GB) is not enough.
   - A 64 GB machine is comfortable.
2. **Load it.** Data › Choose file › Dataset › open the `Benchmarks` folder ›
   `Synthetic-Communities-1500-Users-8.6M.txt` › Open. Loading users takes
   about 47 seconds.
3. **Choose targets.** Stratified sample, 40 targets (seed 20261001), k = 10.
4. **Run.** Initialize, then Run simulation.

Disk space:
- Each run writes the normalised corpus once under `.runtime/run-corpus`
  (about 1.3 GB). It is content-addressed, so later runs reuse it.
- Each run record is small.

TGF, K-Means, K-Means Euclidean and MLP create one agent per user, so their
Initialize takes longer than for the streaming algorithms. Similarity, Common
Neighbors, Doc2Vec and the SVMs stream input through the corpus agent.

## Measured in the application

One node, Hardware mode, 40 stratified targets, k = 10, 24 GB heap; times
measured on this machine (16 cores).

| Algorithm | Load users | Initialize | Run simulation | Total | P@10 [95% CI] | Top-1 | vs chance and MostPop |
|---|---:|---:|---:|---:|---|---:|---|
| Similarity | 47 s | 2 min 25 s | 2 min 15 s | **5 min 27 s** | 0.693 [0.601, 0.784] | 87.5% | p < 0.0001, p < 0.0001 |
| Temporal Graph Foundation | 46 s | 2 min 27 s | 4 min 29 s | **7 min 42 s** | 0.950 [0.879, 1.000] | 95.0% | p < 0.0001, p < 0.0001 |
| K-Means | 48 s | 2 min 8 s | 2 min 19 s | **5 min 15 s** | 0.693 [0.601, 0.784] | 87.5% | p < 0.0001, p < 0.0001 |
| Common-Neighbors | 48 s | 2 min 29 s | 3 min 10 s | **6 min 27 s** | 0.500 [0.402, 0.599] | 70.0% | p < 0.0001, p < 0.0001 |

Chance P@10 is 0.096 and MostPop's is 0.150.
- Similarity reaches about 7 times chance.
- TGF, which also uses each user's timing, reaches 95%. That is above the
  text-only oracle's 90.5%, because the followed account also shapes when a
  user tweets.
- K-Means recommends a target's cluster mates by the same cosine as
  Similarity. Its clusters contain each target's nearest neighbours, so 37 of
  its 40 lists equal Similarity's; the other three score the same in total.
- Common Neighbors counts shared words without TF-IDF weighting, so the many
  background words dilute its lists. It is the weakest of the four, as
  expected.

Peak live heap:
- 10.5 GB in the Similarity-then-TGF session.
- 7.4 GB in the K-Means-then-Common-Neighbors session.
- No full garbage collections in either.

Not measured on this dataset: K-Means Euclidean, SVM, Scalable Linear SVM,
MLP and Doc2Vec. Expect much longer runs from the exact SVM (one model per
target), MLP (a 29,320-input network) and Doc2Vec (training on 81 million
words).

Every run's recommendations are identical on any number of nodes. Running the
same experiment on several emulated nodes in the Node Lab changes only the
timing; the results hash in Node Lab › Results confirms it.
