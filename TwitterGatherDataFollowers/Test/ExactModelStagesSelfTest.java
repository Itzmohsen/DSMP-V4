package TwitterGatherDataFollowers.userRyersonU;

import java.io.BufferedWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The building blocks that make the model stages exact regardless of
 * partition: the global top-L exchange, the candidate shard plan, the run's
 * global corpus, the indexed ARFF writer, and nesting of emulated work spans.
 */
public final class ExactModelStagesSelfTest
{
	private ExactModelStagesSelfTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		Random random = new Random(20260929L);
		for (int trial = 0; trial < 200; trial++)
		{
			globalTopRowsEqualSingleNodeTop(random);
		}
		globalTopRowsRejectDoubleScoring();
		for (int trial = 0; trial < 50; trial++)
		{
			candidateShardsCoverEveryCandidateOnce(random);
		}
		candidateShardsRejectSharedOwnership();
		globalCorpusIsPartitionInvariant(random);
		indexedArffRowsMatchScannedRows(random);
		emulatorSpansDilateWorkOnce();
		System.out.println("ExactModelStagesSelfTest passed");
	}

	/** Any partition, any local cut: the kept union is exactly one node's top L. */
	private static void globalTopRowsEqualSingleNodeTop(Random random)
	{
		int candidates = 1 + random.nextInt(60);
		int nodes = 1 + random.nextInt(5);
		int limit = random.nextInt(4) == 0 ? 0 : 1 + random.nextInt(12);
		List<String> targets = Arrays.asList("t1", "t2", "t3");
		TreeMap<String,TreeMap<String,Double>> all = new TreeMap<String,TreeMap<String,Double>>();
		List<TreeMap<String,TreeMap<String,Double>>> byNode = new ArrayList<TreeMap<String,TreeMap<String,Double>>>();
		for (int node = 0; node < nodes; node++)
		{
			TreeMap<String,TreeMap<String,Double>> rows = new TreeMap<String,TreeMap<String,Double>>();
			for (String target : targets)
			{
				rows.put(target, new TreeMap<String,Double>());
			}
			byNode.add(rows);
		}
		for (String target : targets)
		{
			all.put(target, new TreeMap<String,Double>());
			for (int index = 0; index < candidates; index++)
			{
				String candidate = String.format("c%03d", index);
				//Coarse scores so ties, which the name must break, are frequent.
				double score = random.nextInt(8) / 8.0;
				all.get(target).put(candidate, score);
				byNode.get(random.nextInt(nodes)).get(target).put(candidate, score);
			}
		}
		for (TreeMap<String,TreeMap<String,Double>> rows : byNode)
		{
			for (Map.Entry<String,TreeMap<String,Double>> row : rows.entrySet())
			{
				row.setValue(GlobalTopRows.localTop(row.getValue(), limit));
			}
		}
		List<TreeMap<String,TreeMap<String,Double>>> kept = GlobalTopRows.split(byNode, limit);
		for (String target : targets)
		{
			TreeMap<String,Double> union = new TreeMap<String,Double>();
			for (TreeMap<String,TreeMap<String,Double>> rows : kept)
			{
				for (Map.Entry<String,Double> entry : rows.get(target).entrySet())
				{
					require(union.put(entry.getKey(), entry.getValue()) == null,
							"a candidate was kept by two nodes");
				}
			}
			require(union.equals(GlobalTopRows.localTop(all.get(target), limit)),
					"kept rows differ from one node's top " + limit + " for " + target);
		}
	}

	private static void globalTopRowsRejectDoubleScoring()
	{
		TreeMap<String,TreeMap<String,Double>> first = new TreeMap<String,TreeMap<String,Double>>();
		TreeMap<String,TreeMap<String,Double>> second = new TreeMap<String,TreeMap<String,Double>>();
		first.put("t", new TreeMap<String,Double>(Collections.singletonMap("c", 1.0)));
		second.put("t", new TreeMap<String,Double>(Collections.singletonMap("c", 1.0)));
		try
		{
			GlobalTopRows.split(Arrays.asList(first, second), 5);
		}
		catch (IllegalStateException expected)
		{
			return;
		}
		throw new AssertionError("a candidate scored by two nodes was accepted");
	}

	private static void candidateShardsCoverEveryCandidateOnce(Random random)
	{
		int nodes = 1 + random.nextInt(5);
		List<Map<String,Map<String,Double>>> owned = new ArrayList<Map<String,Map<String,Double>>>();
		for (int node = 0; node < nodes; node++)
		{
			owned.add(new LinkedHashMap<String,Map<String,Double>>());
		}
		int users = random.nextInt(80);
		for (int user = 0; user < users; user++)
		{
			Map<String,Double> vector = new LinkedHashMap<String,Double>();
			for (int term = 0; term < 1 + random.nextInt(30); term++)
			{
				vector.put("w" + random.nextInt(50), random.nextDouble());
			}
			owned.get(random.nextInt(nodes)).put("u" + user, vector);
		}
		CandidateShardPlan plan = new CandidateShardPlan(owned, 1 + random.nextInt(10), 1 + random.nextInt(8));
		Set<String> seen = new HashSet<String>();
		for (WorkUnitSpec unit : plan.units())
		{
			List<String> members = plan.candidatesOf(unit.getId());
			require(!members.isEmpty(), "an empty candidate shard was planned");
			ArrayList<String> sorted = new ArrayList<String>(members);
			Collections.sort(sorted);
			require(sorted.equals(members), "a shard's candidates are not in name order");
			for (String candidate : members)
			{
				require(seen.add(candidate), "candidate " + candidate + " is in two shards");
				require(plan.ownerOf(candidate) == unit.getHomeNode(), "a shard is not homed on its owner");
			}
		}
		require(seen.size() == users && plan.candidateCount() == users, "a candidate is in no shard");
	}

	private static void candidateShardsRejectSharedOwnership()
	{
		Map<String,Map<String,Double>> first = new LinkedHashMap<String,Map<String,Double>>();
		Map<String,Map<String,Double>> second = new LinkedHashMap<String,Map<String,Double>>();
		first.put("shared", Collections.singletonMap("w", 1.0));
		second.put("shared", Collections.singletonMap("w", 1.0));
		List<Map<String,Map<String,Double>>> owned = new ArrayList<Map<String,Map<String,Double>>>();
		owned.add(first);
		owned.add(second);
		try
		{
			new CandidateShardPlan(owned, 1, 2);
		}
		catch (IllegalStateException expected)
		{
			return;
		}
		throw new AssertionError("a candidate owned by two nodes was accepted");
	}

	private static void globalCorpusIsPartitionInvariant(Random random)
	{
		Map<String,Map<String,Double>> users = new LinkedHashMap<String,Map<String,Double>>();
		Map<String,String> labels = new LinkedHashMap<String,String>();
		for (int user = 0; user < 40; user++)
		{
			Map<String,Double> vector = new LinkedHashMap<String,Double>();
			vector.put("w" + random.nextInt(9), random.nextDouble());
			users.put("u" + random.nextInt(1000) + "-" + user, vector);
			if (user % 7 != 0)
			{
				labels.put("u" + (user), "f" + (user % 4));
			}
		}
		for (Map.Entry<String,Map<String,Double>> entry : users.entrySet())
		{
			if (random.nextBoolean())
			{
				labels.put(entry.getKey(), "f" + random.nextInt(4));
			}
		}
		GlobalCorpus reference = GlobalCorpus.combine(Collections.singletonList(
				new GlobalCorpus.Contribution(users, labels)));
		for (int nodes = 2; nodes <= 5; nodes++)
		{
			List<Map<String,Map<String,Double>>> parts = new ArrayList<Map<String,Map<String,Double>>>();
			for (int node = 0; node < nodes; node++)
			{
				parts.add(new LinkedHashMap<String,Map<String,Double>>());
			}
			for (Map.Entry<String,Map<String,Double>> entry : users.entrySet())
			{
				parts.get(random.nextInt(nodes)).put(entry.getKey(), entry.getValue());
			}
			List<Object> contributions = new ArrayList<Object>();
			for (Map<String,Map<String,Double>> part : parts)
			{
				contributions.add(new GlobalCorpus.Contribution(part, labels));
			}
			GlobalCorpus combined = GlobalCorpus.combine(contributions);
			require(combined.users().equals(reference.users()), "corpus users depend on the partition");
			require(combined.terms().equals(reference.terms()), "corpus vocabulary depends on the partition");
			require(combined.classes().equals(reference.classes()), "corpus classes depend on the partition");
			require(combined.usersByClass(Collections.singleton(combined.users().get(0)))
							.equals(reference.usersByClass(Collections.singleton(reference.users().get(0)))),
					"held-out class members depend on the partition");
		}
		ArrayList<String> sorted = new ArrayList<String>(reference.users());
		Collections.sort(sorted);
		require(sorted.equals(reference.users()), "corpus users are not in name order");
	}

	private static void indexedArffRowsMatchScannedRows(Random random) throws Exception
	{
		TreeSet<String> terms = new TreeSet<String>();
		for (int term = 0; term < 200; term++)
		{
			terms.add("w" + term);
		}
		Map<String,Integer> index = new LinkedHashMap<String,Integer>();
		for (String term : terms)
		{
			index.put(term, Integer.valueOf(index.size()));
		}
		for (int trial = 0; trial < 100; trial++)
		{
			Map<String,Double> row = new LinkedHashMap<String,Double>();
			for (int entry = 0; entry < random.nextInt(25); entry++)
			{
				double value = random.nextInt(5) == 0 ? 0.0 : random.nextGaussian();
				row.put("w" + random.nextInt(260), value);
			}
			String label = random.nextBoolean() ? "f'" + trial : null;
			StringWriter scanned = new StringWriter();
			StringWriter indexed = new StringWriter();
			BufferedWriter scannedWriter = new BufferedWriter(scanned);
			BufferedWriter indexedWriter = new BufferedWriter(indexed);
			SparseArffWriter.writeRow(scannedWriter, row, terms, label);
			SparseArffWriter.writeIndexedRow(indexedWriter, row, index, terms.size(), label);
			scannedWriter.flush();
			indexedWriter.flush();
			require(scanned.toString().equals(indexed.toString()),
					"indexed ARFF row differs:\n" + scanned + indexed);
		}
	}

	/** Nested spans dilate each nanosecond of work once; checkpoints settle early. */
	private static void emulatorSpansDilateWorkOnce() throws Exception
	{
		ArrayList<NodeCapacityProfile> profiles = new ArrayList<NodeCapacityProfile>();
		profiles.add(NodeCapacityProfile.rolePreset(1, NodeCapacityProfile.Role.NORMAL, 2, 4096));
		profiles.add(NodeCapacityProfile.rolePreset(2, NodeCapacityProfile.Role.WEAK, 2, 4096));
		SchedulingRunContext context = new SchedulingRunContext(
				"emulator-self-test-" + System.nanoTime(),
				SchedulingRunContext.Mode.EMULATED,
				SchedulerLabEngine.SchedulingPolicy.CAPACITY_WEIGHTED,
				profiles, 2, -1, 1L, 3, 60000L, 0);
		NodeEmulator weak = context.emulator(2);
		double speed = context.speedOf(2);
		require(speed < 1.0, "the weak node should be slower than the reference");
		NodeEmulator.Span outer = weak.begin();
		long burned = spin(20);
		NodeEmulator.Span inner = weak.begin();
		long innerBurned = spin(20);
		burned += innerBurned;
		weak.end(inner);
		weak.checkpoint();
		burned += spin(20);
		weak.end(outer);
		weak.checkpoint();
		NodeEmulator.Ledger ledger = weak.getLedger();
		double dilation = context.clock().getDilation();
		double expected = ledger.getComputeNanos() * (dilation / speed - 1.0);
		require(Math.abs(ledger.getOwedNanos() - expected) <= 1.0e-6 * expected + 10.0,
				"owed dilation " + ledger.getOwedNanos() + " is not compute x (TDF/speed - 1) = " + expected);
		//The sleeps themselves hit the owed time to within the carried residual
		//(a fraction of a millisecond) plus how late the platform timer wakes a
		//parked thread, measured here: microseconds on macOS and Linux, up to a
		//15.6 ms timer period on a default Windows.
		long overshoot = parkOvershootNanos();
		require(Math.abs(ledger.getDilationNanos() - ledger.getOwedNanos())
				<= 700000L + 0.01 * expected + 2L * overshoot,
				"slept " + ledger.getDilationNanos() + " ns for " + ledger.getOwedNanos()
				+ " ns owed (timer overshoot " + overshoot + " ns)");
		//Bounds are relative to the CPU time the spins measured, because the thread
		//CPU clock can be coarse (Windows advances it in 15.6 ms steps). Counting the
		//inner span twice would add all of innerBurned; losing a spin would drop it.
		require(ledger.getComputeNanos() >= burned - innerBurned / 2,
				"nested spans lost work: " + ledger.getComputeNanos()
				+ " ns measured, " + burned + " ns burned");
		require(ledger.getComputeNanos() < burned + innerBurned * 3 / 4,
				"nested spans counted work twice: " + ledger.getComputeNanos()
				+ " ns measured, " + burned + " ns burned");
		context.close("self-test finished");
	}

	/** The latest a short park woke up after its request, over a few samples. */
	private static long parkOvershootNanos()
	{
		long worst = 0L;
		for (int sample = 1; sample <= 20; sample++)
		{
			long request = sample * 100000L;
			long start = System.nanoTime();
			java.util.concurrent.locks.LockSupport.parkNanos(request);
			worst = Math.max(worst, System.nanoTime() - start - request);
		}
		return worst;
	}

	/** Burns about {@code millis} of this thread's CPU; returns the CPU time the clock measured. */
	private static long spin(long millis)
	{
		long start = NodeEmulator.threadCpuNanos();
		long end = start + millis * 1000000L;
		long now = start;
		double sink = 0.0;
		while (now < end)
		{
			for (int index = 0; index < 1000; index++)
			{
				sink += Math.sqrt(index + sink);
			}
			now = NodeEmulator.threadCpuNanos();
		}
		if (sink == 42.0)
		{
			System.out.println(sink);
		}
		return now - start;
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
