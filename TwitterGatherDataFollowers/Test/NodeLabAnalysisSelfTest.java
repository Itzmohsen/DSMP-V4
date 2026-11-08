package TwitterGatherDataFollowers.userRyersonU;

import java.io.File;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Node Lab's analysis layer: statistics against textbook values, the JSON
 * and CSV readers against the writers, the makespan lower bound against
 * hand-computed cases, and a study directory's self-contained design.
 */
public final class NodeLabAnalysisSelfTest
{
	private NodeLabAnalysisSelfTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		checkStatistics();
		checkHolm();
		checkJson();
		checkCsv();
		checkLowerBoundCases();
		checkStudyListLength();
		checkStudyDirectoryIsSelfContained();
		System.out.println("NodeLabAnalysisSelfTest passed");
	}

	/** A design's list length k: its default, its text round trip and its range. */
	private static void checkStudyListLength() throws Exception
	{
		String base = "name: k check\nalgorithms: SIMILARITY\ntargets: 4\npolicies: capacity-weighted\n"
				+ "clusters: preset:1 normal node (reference)\n";
		StudyDesign older = StudyDesign.parse(base, null, 4096);
		require(older.getK() == StudyDesign.DEFAULT_K && StudyDesign.DEFAULT_K == 10,
				"a design without k keeps the list length every study used before (10)");
		StudyDesign three = StudyDesign.parse(base + "k: 3\n", null, 4096);
		require(three.getK() == 3, "k: 3 is read");
		require(three.toText().contains("\nk: 3\n"), "the design's text names its k");
		require(StudyDesign.parse(three.toText(), null, 4096).getK() == 3, "k survives the text round trip");
		for (String outside : new String[] {"2", "101"})
		{
			boolean refused = false;
			try
			{
				StudyDesign.parse(base + "k: " + outside + "\n", null, 4096);
			}
			catch (java.io.IOException expected)
			{
				refused = expected.getMessage().contains("k must be between 3 and 100");
			}
			require(refused, "k = " + outside + " is refused (the Targets page's range is 3 to 100)");
		}
	}

	private static void checkStatistics()
	{
		require(close(StudyStatistics.tQuantile(0.975, 1.0), 12.7062, 1e-3), "t(0.975, 1)");
		require(close(StudyStatistics.tQuantile(0.975, 9.0), 2.2622, 1e-3), "t(0.975, 9)");
		require(close(StudyStatistics.tQuantile(0.975, 30.0), 2.0423, 1e-3), "t(0.975, 30)");
		require(close(StudyStatistics.regularizedBeta(0.5, 2.0, 2.0), 0.5, 1e-9), "I_0.5(2,2) = 0.5");
		require(close(StudyStatistics.regularizedBeta(0.25, 1.0, 1.0), 0.25, 1e-9), "I_x(1,1) = x");
		double[] values = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
		StudyStatistics.Summary summary = StudyStatistics.summarize(values, 0.95);
		require(summary.n == 10 && close(summary.mean, 5.5, 1e-12), "mean of 1..10");
		require(close(summary.sd, 3.02765, 1e-4), "sd of 1..10");
		require(close(summary.ciLow, 3.3341, 1e-3) && close(summary.ciHigh, 7.6659, 1e-3),
				"95% CI of 1..10: " + summary.ciLow + " .. " + summary.ciHigh);
		require(close(summary.median, 5.5, 1e-12), "median of 1..10");
		double[] baseline = {10, 12, 11, 13, 12, 11, 10, 12};
		StudyStatistics.Comparison same = StudyStatistics.paired(baseline, baseline.clone(), 0.95, 1L);
		require(close(same.meanDifference, 0.0, 1e-12) && close(same.pValue, 1.0, 1e-12),
				"identical samples: no difference, p = 1 (got " + same.pValue + ")");
		double[] faster = new double[baseline.length];
		for (int index = 0; index < baseline.length; index++)
		{
			faster[index] = baseline[index] - 2.0 - 0.1 * index;
		}
		StudyStatistics.Comparison shift = StudyStatistics.paired(baseline, faster, 0.95, 1L);
		//All 8 differences negative: exactly 2 of the 2^8 sign patterns are as extreme.
		require(close(shift.pValue, 2.0 / 256.0, 1e-12), "exact sign-flip p for 8 same-sign pairs: " + shift.pValue);
		require(shift.ciHigh < 0.0 && shift.meanDifference < 0.0, "a consistent shift has a CI below zero");
		require(shift.relativeChange < 0.0, "relative change of a faster treatment is negative");
	}

	private static void checkHolm() throws Exception
	{
		Constructor<StudyStatistics.Comparison> constructor = StudyStatistics.Comparison.class.getDeclaredConstructor(
				int.class, double.class, double.class, double.class, double.class, double.class, double.class,
				double.class, double.class);
		constructor.setAccessible(true);
		double[] raw = {0.01, 0.04, 0.03};
		ArrayList<StudyStatistics.Comparison> family = new ArrayList<StudyStatistics.Comparison>();
		for (double p : raw)
		{
			family.add(constructor.newInstance(Integer.valueOf(5), Double.valueOf(1), Double.valueOf(1),
					Double.valueOf(0), Double.valueOf(0), Double.valueOf(0), Double.valueOf(0), Double.valueOf(0),
					Double.valueOf(p)));
		}
		StudyStatistics.holm(family);
		require(close(family.get(0).adjustedPValue, 0.03, 1e-12), "Holm 0.01 of 3 -> 0.03");
		require(close(family.get(2).adjustedPValue, 0.06, 1e-12), "Holm 0.03 (2nd) -> 0.06");
		require(close(family.get(1).adjustedPValue, 0.06, 1e-12), "Holm 0.04 (3rd) -> max(0.04, 0.06) = 0.06");
	}

	private static void checkJson()
	{
		LinkedHashMap<String,Object> value = new LinkedHashMap<String,Object>();
		value.put("text", "quote \" backslash \\ newline\n tab\t unicode é ✓");
		value.put("number", Double.valueOf(-12.5e-3));
		value.put("whole", Integer.valueOf(42));
		value.put("flag", Boolean.TRUE);
		value.put("nothing", null);
		value.put("list", new ArrayList<Object>(Arrays.asList(Double.valueOf(1), "two", Boolean.FALSE)));
		LinkedHashMap<String,Object> nested = new LinkedHashMap<String,Object>();
		nested.put("mode", "Emulated cluster");
		value.put("scheduling", nested);
		Object parsed = JsonText.parse(JsonText.of(value));
		require(JsonText.string(parsed, "text").equals(value.get("text")), "strings round-trip");
		require(close(JsonText.number(parsed, "number"), -12.5e-3, 1e-15), "numbers round-trip");
		require(JsonText.string(parsed, "whole").equals("42"), "whole numbers print without a fraction");
		require(Boolean.TRUE.equals(JsonText.path(parsed, "flag")), "booleans round-trip");
		require(JsonText.path(parsed, "nothing") == null, "null round-trips");
		require(((List<?>)JsonText.path(parsed, "list")).size() == 3, "lists round-trip");
		require(JsonText.string(parsed, "scheduling.mode").equals("Emulated cluster"), "dotted paths");
		require(JsonText.path(parsed, "missing.deeper") == null, "absent paths are null");
		boolean rejected = false;
		try
		{
			JsonText.parse("{\"a\": 1,}");
		}
		catch (IllegalArgumentException expected)
		{
			rejected = true;
		}
		require(rejected, "malformed JSON is rejected");
	}

	private static void checkCsv() throws Exception
	{
		String text = JsonText.csvLine("plain", "with, comma", "with \"quotes\"", "multi\nline", Double.valueOf(1.5),
				Double.valueOf(Double.NaN), null) + JsonText.csvLine("second", "", "x", "y", Double.valueOf(2), "", "z");
		List<List<String>> rows = JsonText.parseCsv(text);
		require(rows.size() == 2, "two rows, the newline inside quotes included: " + rows.size());
		require(rows.get(0).get(1).equals("with, comma"), "commas inside quotes");
		require(rows.get(0).get(2).equals("with \"quotes\""), "doubled quotes");
		require(rows.get(0).get(3).equals("multi\nline"), "newlines inside quotes");
		require(rows.get(0).get(4).equals("1.500000"), "doubles are written with six decimals");
		require(rows.get(0).get(5).equals("") && rows.get(0).get(6).equals(""), "NaN and null are empty");
		require(rows.get(1).size() == 7 && rows.get(1).get(1).equals(""), "empty fields are kept");
		File file = File.createTempFile("nodelab", ".csv");
		try
		{
			Files.write(file.toPath(), ("a,b\n" + text.replace("\n", "\r\n").replace("\r\r\n", "\r\n")).getBytes("UTF-8"));
			List<Map<String,String>> table = JsonText.readCsv(file);
			require(table.size() >= 2 && table.get(0).get("a").equals("plain"), "header maps columns");
		}
		finally
		{
			file.delete();
		}
	}

	private static void checkLowerBoundCases()
	{
		List<NodeSpec> two = Arrays.asList(node(1, 1.0, 1, 0, 0.0), node(2, 1.0, 1, 0, 0.0));
		List<WorkUnitSpec> three = Arrays.asList(unit(1, 10, 0, 0L), unit(2, 10, 0, 0L), unit(3, 10, 0, 0L));
		PlacementPlanner.Bound plain = PlacementPlanner.bound(three, two, null, false);
		require(close(plain.value, 15.0, 1e-9) && "work".equals(plain.kind), "30 work on 2 unit nodes: 15 (" + plain.value
				+ ", " + plain.kind + ")");
		//Node 2 joins at 20: node 1 alone does 20 by then, the last 10 shared: 25.
		PlacementPlanner.Bound late = PlacementPlanner.bound(three, two, new double[] {0.0, 20.0}, false);
		require(close(late.value, 25.0, 1e-9), "a late join delays the work bound to 25: " + late.value);
		//A node that never joined does no work: 30 on node 1.
		PlacementPlanner.Bound absent = PlacementPlanner.bound(three, two, new double[] {0.0, Double.POSITIVE_INFINITY},
				false);
		require(close(absent.value, 30.0, 1e-9), "an absent node contributes nothing: " + absent.value);
		//A unit whose data lives behind a 25 ms link: any node needs >= 25 ms before it can start it.
		List<NodeSpec> linked = Arrays.asList(node(1, 1.0, 1, 0, 0.0), node(2, 1.0, 1, 25, 0.0));
		List<WorkUnitSpec> remote = Arrays.asList(unit(1, 1, 2, 1000L));
		PlacementPlanner.Bound network = PlacementPlanner.bound(remote, linked, null, true);
		require(close(network.value, 26.0, 1e-9) && "unit".equals(network.kind),
				"the unit's descriptor or input crosses the 25 ms link first: " + network.value + " " + network.kind);
		require(close(PlacementPlanner.bound(remote, linked, null, false).value, 1.0, 1e-9),
				"without the network the unit takes 1");
		//Bandwidth: 1000 bytes at 1 Mbit/s (125 bytes/ms) cost 8 ms per hop.
		List<NodeSpec> slow = Arrays.asList(node(1, 1.0, 1, 0, 125.0), node(2, 1.0, 1, 0, 125.0));
		List<WorkUnitSpec> homed = Arrays.asList(unit(1, 1, 2, 1000L));
		double slowBound = PlacementPlanner.bound(homed, slow, null, true).value;
		require(close(slowBound, 256.0 / 125.0 + 1.0, 1e-9), "at home only the descriptor (256 B) crosses the link: "
				+ slowBound);
		//Pinned work: units only node 1 may run.
		List<WorkUnitSpec> pinned = new ArrayList<WorkUnitSpec>();
		for (int id = 1; id <= 4; id++)
		{
			pinned.add(new WorkUnitSpec(id, "p" + id, 10.0, 0, 0, Double.POSITIVE_INFINITY, 1, new int[] {1}));
		}
		PlacementPlanner.Bound pinnedBound = PlacementPlanner.bound(pinned, two, null, false);
		require(close(pinnedBound.value, 40.0, 1e-9) && "pinned".equals(pinnedBound.kind),
				"work only node 1 may run sets the bound: " + pinnedBound.value + " " + pinnedBound.kind);
	}

	private static void checkStudyDirectoryIsSelfContained() throws Exception
	{
		File root = Files.createTempDirectory("nodelab-study").toFile();
		String previous = System.getProperty("dsmp.results.dir");
		System.setProperty("dsmp.results.dir", root.getPath());
		try
		{
			ArrayList<NodeCapacityProfile> profiles = new ArrayList<NodeCapacityProfile>();
			profiles.add(NodeCapacityProfile.rolePreset(1, NodeCapacityProfile.Role.STRONG, 2, 4096).withLink(3, 250.0));
			profiles.add(NodeCapacityProfile.rolePreset(2, NodeCapacityProfile.Role.WEAK, 2, 4096));
			ClusterConfig custom = new ClusterConfig("Current cluster", profiles,
					ClusterSettings.defaults().withSeed(77L));
			Scenario scenario = Scenario.parse("name: My unsaved drill\nat 20% pause node 2\nat 70% resume node 2\n");
			StudyDesign design = new StudyDesign("Round trip", null, Arrays.asList(Integer.valueOf(ControllerAgent.COS_SIM)),
					3, 5, RecommendationSchedulerPlan.Mode.SCORING_SAFE,
					Arrays.asList(SchedulerLabEngine.SchedulingPolicy.ORIGINAL_DSMP,
							SchedulerLabEngine.SchedulingPolicy.WORK_STEALING),
					SchedulerLabEngine.SchedulingPolicy.ORIGINAL_DSMP, Arrays.asList(custom),
					Arrays.asList(Scenario.NONE, scenario), 2, 0, 9L);
			StudyResult result = new StudyResult(design);
			File directory = result.write();
			StudyResult loaded = StudyResult.load(directory, 4096);
			StudyDesign again = loaded.getDesign();
			require(again.cells().size() == design.cells().size(), "same cells after the round trip");
			require(again.getK() == 5 && again.getTargets() == 3, "the study directory keeps its targets and k");
			require(again.getClusters().get(0).getNodeCount() == 2
					&& again.getClusters().get(0).getProfiles().get(0).getNetworkDelayMs() == 3
					&& again.getClusters().get(0).getSettings().getSeed() == 77L,
					"the cluster comes back from the study directory, not from the current simulator");
			require(again.getScenarios().size() == 2 && again.getScenarios().get(1).getSteps().size() == 2
					&& again.getScenarios().get(1).getName().equals("My unsaved drill"),
					"an unsaved scenario comes back from the study directory");
			require(loaded.directory().equals(directory), "a loaded study keeps its directory");
		}
		finally
		{
			if (previous == null)
			{
				System.clearProperty("dsmp.results.dir");
			}
			else
			{
				System.setProperty("dsmp.results.dir", previous);
			}
			delete(root);
		}
	}

	private static NodeSpec node(int index, double speed, int lanes, int delayMs, double bytesPerMs)
	{
		return new NodeSpec(index, "N", speed, 1.0, 1.0, speed, lanes, 1 << 20, lanes, delayMs, bytesPerMs, 0.0, 0.0, 0.0);
	}

	private static WorkUnitSpec unit(int id, double cost, int home, long inputBytes)
	{
		return new WorkUnitSpec(id, "u" + id, cost, 0, 0, Double.POSITIVE_INFINITY, home, null, 0, inputBytes, 0L, 0.0);
	}

	private static void delete(File file)
	{
		File[] children = file.listFiles();
		if (children != null)
		{
			for (File child : children)
			{
				delete(child);
			}
		}
		file.delete();
	}

	private static boolean close(double actual, double expected, double tolerance)
	{
		return Math.abs(actual - expected) <= tolerance;
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new IllegalStateException(message);
		}
	}
}
