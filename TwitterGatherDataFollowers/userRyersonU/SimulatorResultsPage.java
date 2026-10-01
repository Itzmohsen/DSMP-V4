package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * Step 5: how good the last run's lists are, measured against the dataset's
 * ground truth with intervals, chance and most-popular baselines, a paired
 * test, the top-1 label view and every target's own numbers.
 */
final class SimulatorResultsPage extends SimulatorPanel.Page
{
	private static final long serialVersionUID = 1L;
	private static final int CONFUSION_CLASSES = 10;
	private static final int TARGET_ROWS = 250;

	private final ControllerAgentGui gui;
	private final NodeLabUi.Page page = new NodeLabUi.Page(1360);
	private RecommendationQualityEvaluator.Report shown;
	private boolean shownEmpty;
	/** The run record card's body; filled in once the run's record has been written. */
	private final JPanel recordBody = new JPanel(new BorderLayout());
	private File recordFolder;
	private boolean recordFound;
	private int refreshes;

	SimulatorResultsPage(SimulatorPanel simulator)
	{
		this.gui = simulator.gui();
		add(NodeLabUi.scroll(page), BorderLayout.CENTER);
		refresh();
	}

	void refresh()
	{
		RecommendationQualityEvaluator.Report report = gui.getLatestQualityReport();
		if (report == null)
		{
			if (!shownEmpty)
			{
				shownEmpty = true;
				shown = null;
				page.clear();
				NodeLabUi.Card empty = new NodeLabUi.Card(null, null);
				empty.content(NodeLabUi.emptyState(NodeLabUi.Glyph.CHART, "No results yet",
						"Run a simulation: once every target has its list, its quality appears here.", null));
				page.section(empty);
				page.revalidate();
				page.repaint();
			}
			return;
		}
		if (report == shown)
		{
			if (!recordFound && ++refreshes % 4 == 0)
			{
				updateRecord(report);
			}
			return;
		}
		shown = report;
		shownEmpty = false;
		page.clear();
		build(report);
		page.revalidate();
		page.repaint();
	}

	private void build(final RecommendationQualityEvaluator.Report report)
	{
		int k = report.setup.cutoff;
		boolean followeeMode = report.setup.candidateType == RecommendationCandidateType.FOLLOWEE;
		RecommendationQualityEvaluator.MetricSummary ndcg = report.metric("ndcg");
		NodeLabUi.Hero hero = new NodeLabUi.Hero("Results", report.setup.algorithm + " · NDCG@" + k + " "
				+ format(ndcg.mean),
				(followeeMode ? "Followee accounts" : "Similar users") + " for " + report.evaluated()
						+ (report.evaluated() == 1 ? " target" : " targets") + ", k = " + k + ". "
						+ intervalText(ndcg) + "; chance " + format(ndcg.random) + " (" + liftText(ndcg) + ").",
				NebulaDesign.SUCCESS);
		hero.chip(significanceChip(ndcg));
		hero.chip(new NodeLabUi.Chip(report.setup.targetMode, NodeLabUi.Tone.NEUTRAL, false));
		NodeLabUi.Button copy = new NodeLabUi.Button("Copy report", NodeLabUi.ButtonKind.SECONDARY,
				NodeLabUi.Glyph.COPY);
		copy.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
						new StringSelection(report.describe()), null);
			}
		});
		hero.right(NodeLabUi.stack(8, copy));
		page.section(hero);

		JPanel tiles = NodeLabUi.grid(190, 6, 12);
		for (RecommendationQualityEvaluator.MetricSummary metric : report.summaries)
		{
			tiles.add(tile(metric, k));
		}
		page.section(tiles);

		NodeLabUi.Card table = new NodeLabUi.Card("Against chance and popularity",
				"Means over the scored targets with 95% intervals (Wilson for the hit rate, Student-t otherwise)."
						+ " Chance is the exact expectation of a random ranking; MostPop recommends the largest"
						+ " communities first, the strongest non-personalised baseline.");
		table.action(NodeLabUi.help("Reading the tests",
				"For every target, the metric minus the baseline's value for that target is one paired"
						+ " difference; a one-sample t-test over the targets asks whether their mean is zero."
						+ " * p<0.05, ** p<0.01, *** p<0.001; green: the model is better, red: worse."
						+ " Lift is the mean over chance."));
		NodeLabUi.Table metrics = new NodeLabUi.Table(
				NodeLabUi.Table.text("Metric @" + k, 1.2f), NodeLabUi.Table.number("Mean", 0.8f),
				NodeLabUi.Table.number("95% CI", 1.4f), NodeLabUi.Table.number("Chance", 0.8f),
				NodeLabUi.Table.number("MostPop", 0.8f), NodeLabUi.Table.number("Lift", 0.7f),
				NodeLabUi.Table.text("vs chance", 1.3f), NodeLabUi.Table.text("vs MostPop", 1.3f));
		List<String[]> rows = new ArrayList<String[]>();
		List<Color[]> colors = new ArrayList<Color[]>();
		for (RecommendationQualityEvaluator.MetricSummary metric : report.summaries)
		{
			rows.add(new String[] {metric.label, format(metric.mean), interval(metric), format(metric.random),
					format(metric.mostPopular), Double.isNaN(metric.lift()) ? "—"
							: String.format(Locale.US, "%.2f×", metric.lift()),
					RecommendationQualityEvaluator.significance(metric.pValue),
					RecommendationQualityEvaluator.significance(metric.popularPValue)});
			colors.add(new Color[] {null, null, null, null, null, null, tone(metric.pValue, metric.gain),
					tone(metric.popularPValue, metric.popularGain)});
		}
		metrics.setRows(rows, colors);
		table.content(metrics);
		page.section(table);

		JPanel lower = NodeLabUi.grid(460, 2, 16);
		lower.add(labelView(report));
		lower.add(diagnostics(report));
		page.section(lower);
		page.section(perTarget(report));
		page.section(record(report));
	}

	/** Green for significantly better, red for significantly worse, grey otherwise. */
	private static Color tone(double pValue, double gain)
	{
		if (Double.isNaN(pValue))
		{
			return null;
		}
		return pValue < 0.05 && gain > 0 ? NebulaDesign.SUCCESS
				: pValue < 0.05 ? NebulaDesign.DANGER : NebulaDesign.TEXT_TERTIARY;
	}

	private static JComponent tile(RecommendationQualityEvaluator.MetricSummary metric, int k)
	{
		NodeLabUi.Metric tile = new NodeLabUi.Metric(metric.label + "@" + k, format(metric.mean),
				interval(metric) + " · chance " + format(metric.random), metric.pValue < 0.05 && metric.gain > 0
						? NebulaDesign.SUCCESS : NebulaDesign.TEXT_TERTIARY);
		tile.setToolTipText(metric.label + ": " + format(metric.mean) + ", 95% CI " + interval(metric)
				+ ", chance " + format(metric.random) + ", MostPop " + format(metric.mostPopular) + ", "
				+ RecommendationQualityEvaluator.significance(metric.pValue));
		return tile;
	}

	/** The top-1 recommendation read as a prediction of the target's followee. */
	private static JComponent labelView(RecommendationQualityEvaluator.Report report)
	{
		boolean followeeMode = report.setup.candidateType == RecommendationCandidateType.FOLLOWEE;
		NodeLabUi.Card card = new NodeLabUi.Card("Top-1 followee",
				followeeMode ? "The top account as the predicted followee."
						: "The top user's followee as the prediction (a 1-nearest-neighbour classifier).");
		JPanel figures = NodeLabUi.grid(140, 2, 10);
		figures.add(new NodeLabUi.Metric("Accuracy", percent(report.accuracy), "share of targets predicted right",
				NebulaDesign.PURPLE));
		figures.add(new NodeLabUi.Metric("Macro-F1", format(report.macroF1), "every followee weighs the same",
				NebulaDesign.PURPLE));
		//The most frequent followees as rows and columns; the rest are summed into "other".
		final LinkedHashMap<String,Integer> totals = new LinkedHashMap<String,Integer>();
		for (Map.Entry<String,java.util.TreeMap<String,Integer>> line : report.confusion.entrySet())
		{
			int sum = 0;
			for (Integer count : line.getValue().values())
			{
				sum += count.intValue();
			}
			totals.put(line.getKey(), Integer.valueOf(sum));
		}
		List<String> classes = new ArrayList<String>(totals.keySet());
		Collections.sort(classes, new Comparator<String>()
		{
			public int compare(String first, String second)
			{
				int byCount = totals.get(second).compareTo(totals.get(first));
				return byCount != 0 ? byCount : first.compareTo(second);
			}
		});
		if (classes.size() > CONFUSION_CLASSES)
		{
			classes = new ArrayList<String>(classes.subList(0, CONFUSION_CLASSES));
		}
		List<String> columns = new ArrayList<String>(classes);
		TreeSet<String> predicted = new TreeSet<String>();
		for (java.util.TreeMap<String,Integer> line : report.confusion.values())
		{
			predicted.addAll(line.keySet());
		}
		boolean other = report.confusion.size() > classes.size() || !classes.containsAll(predicted);
		if (other)
		{
			columns.add("other");
		}
		NodeLabUi.Table.Column[] headers = new NodeLabUi.Table.Column[columns.size() + 1];
		headers[0] = NodeLabUi.Table.text("actual \\ predicted", 1.6f);
		for (int index = 0; index < columns.size(); index++)
		{
			headers[index + 1] = NodeLabUi.Table.number(shortName(columns.get(index)), 0.8f);
		}
		NodeLabUi.Table matrix = new NodeLabUi.Table(headers);
		List<String[]> rows = new ArrayList<String[]>();
		List<Color[]> colors = new ArrayList<Color[]>();
		for (String actual : classes)
		{
			String[] row = new String[columns.size() + 1];
			Color[] tone = new Color[columns.size() + 1];
			row[0] = actual;
			java.util.TreeMap<String,Integer> line = report.confusion.get(actual);
			int rest = 0;
			for (Map.Entry<String,Integer> cell : line.entrySet())
			{
				if (!classes.contains(cell.getKey()))
				{
					rest += cell.getValue().intValue();
				}
			}
			for (int index = 0; index < columns.size(); index++)
			{
				String column = columns.get(index);
				Integer count = "other".equals(column) && other ? Integer.valueOf(rest) : line.get(column);
				int value = count == null ? 0 : count.intValue();
				row[index + 1] = value == 0 ? "·" : Integer.toString(value);
				tone[index + 1] = value == 0 ? NebulaDesign.TEXT_TERTIARY
						: column.equals(actual) ? NebulaDesign.SUCCESS : NebulaDesign.DANGER;
			}
			rows.add(row);
			colors.add(tone);
		}
		matrix.setRows(rows, colors);
		card.content(NodeLabUi.stack(14, figures, matrix,
				NodeLabUi.note("Rows: the target's real followee; columns: the predicted one; green is right.")));
		return card;
	}

	private static String shortName(String name)
	{
		return name.length() <= 12 ? name : name.substring(0, 11) + "…";
	}

	/** What to keep in mind when reading the figures. */
	private static JComponent diagnostics(RecommendationQualityEvaluator.Report report)
	{
		NodeLabUi.Card card = new NodeLabUi.Card("Reading these numbers", null);
		JPanel tiles = NodeLabUi.grid(140, 2, 10);
		tiles.add(new NodeLabUi.Metric("Relevant per target", format(report.meanRelevant()),
				"recall@" + report.setup.cutoff + " cannot exceed " + format(report.recallBound), NebulaDesign.INFO));
		tiles.add(new NodeLabUi.Metric("Catalogue coverage", percent(report.catalogCoverage),
				"of the candidates appear in some list", NebulaDesign.INFO));
		JPanel notes = NodeLabUi.stack(6);
		List<String> lines = new ArrayList<String>(report.notes());
		if (report.skippedUnlabelled > 0 || report.skippedNoRelevant > 0)
		{
			lines.add("Not scored: " + report.skippedUnlabelled + " target(s) with no known followee, "
					+ report.skippedNoRelevant + " alone in their community.");
		}
		if (lines.isEmpty())
		{
			lines.add("Nothing unusual: every target received a full list and every figure is meaningful.");
		}
		for (String line : lines)
		{
			notes.add(NodeLabUi.prose("• " + line));
		}
		card.content(NodeLabUi.stack(14, tiles, notes));
		return card;
	}

	/** Every scored target's own numbers, the hardest first. */
	private static JComponent perTarget(RecommendationQualityEvaluator.Report report)
	{
		int k = report.setup.cutoff;
		NodeLabUi.Card card = new NodeLabUi.Card("Per target",
				"Each target's list, the hardest first (lowest NDCG). First hit is the rank of the first relevant"
						+ " recommendation within k.");
		NodeLabUi.Table table = new NodeLabUi.Table(
				NodeLabUi.Table.text("Target", 1.6f), NodeLabUi.Table.text("Followee", 1.4f),
				NodeLabUi.Table.number("List", 0.6f), NodeLabUi.Table.number("Relevant", 0.8f),
				NodeLabUi.Table.number("First hit", 0.8f), NodeLabUi.Table.number("P@" + k, 0.8f),
				NodeLabUi.Table.number("NDCG@" + k, 0.9f), NodeLabUi.Table.number("Chance", 0.8f),
				NodeLabUi.Table.text("Top-1 followee", 1.4f));
		List<RecommendationQualityEvaluator.TargetRow> ordered =
				new ArrayList<RecommendationQualityEvaluator.TargetRow>(report.rows);
		Collections.sort(ordered, new Comparator<RecommendationQualityEvaluator.TargetRow>()
		{
			public int compare(RecommendationQualityEvaluator.TargetRow first,
					RecommendationQualityEvaluator.TargetRow second)
			{
				int byScore = Double.compare(first.metrics[3], second.metrics[3]);
				return byScore != 0 ? byScore : first.target.compareTo(second.target);
			}
		});
		List<String[]> rows = new ArrayList<String[]>();
		List<Color[]> colors = new ArrayList<Color[]>();
		for (int index = 0; index < Math.min(TARGET_ROWS, ordered.size()); index++)
		{
			RecommendationQualityEvaluator.TargetRow row = ordered.get(index);
			rows.add(new String[] {row.target, row.followee, Integer.toString(row.listLength),
					Integer.toString(row.relevant), row.firstHit == 0 ? "—" : "#" + row.firstHit,
					format(row.metrics[1]), format(row.metrics[3]), format(row.random[3]), row.predicted});
			colors.add(new Color[] {null, null, null, null, row.firstHit == 0 ? NebulaDesign.DANGER : null, null,
					row.metrics[3] > row.random[3] ? NebulaDesign.SUCCESS : NebulaDesign.DANGER, null,
					row.followee.equals(row.predicted) ? NebulaDesign.SUCCESS : null});
		}
		table.setRows(rows, colors);
		card.content(ordered.size() > TARGET_ROWS ? NodeLabUi.stack(8, table, NodeLabUi.note("The first "
				+ TARGET_ROWS + " of " + ordered.size() + " targets; quality-targets.csv in the run record has"
				+ " them all.")) : table);
		return card;
	}

	/** Where the run's lists and quality files were written. */
	private JComponent record(RecommendationQualityEvaluator.Report report)
	{
		NodeLabUi.Card card = new NodeLabUi.Card("Run record",
				"Every run writes its lists (recommendations.csv), this evaluation (quality.json,"
						+ " quality-targets.csv) and its timing to a folder of its own.");
		NodeLabUi.Button open = new NodeLabUi.Button("Open folder", NodeLabUi.ButtonKind.SECONDARY,
				NodeLabUi.Glyph.FOLDER, true);
		open.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				File folder = recordFolder == null ? RunRecordWriter.runsDirectory() : recordFolder;
				try
				{
					if (java.awt.Desktop.isDesktopSupported())
					{
						java.awt.Desktop.getDesktop().open(folder);
					}
				}
				catch (Exception unavailable)
				{
					System.err.println("Could not open " + folder + ": " + unavailable.getMessage());
				}
			}
		});
		card.action(open);
		recordBody.setOpaque(false);
		recordFound = false;
		recordFolder = null;
		updateRecord(report);
		card.content(recordBody);
		return card;
	}

	private void updateRecord(RecommendationQualityEvaluator.Report report)
	{
		File directory = null;
		for (RunRecordReader.Entry entry : RunRecordReader.index())
		{
			if (entry.getRunId().equals(report.setup.runId))
			{
				directory = entry.directory;
				break;
			}
		}
		recordFound = directory != null && directory.isDirectory();
		recordFolder = recordFound ? directory : null;
		File shownFolder = recordFound ? directory : RunRecordWriter.runsDirectory();
		recordBody.removeAll();
		recordBody.add(NodeLabUi.stack(6, NodeLabUi.mono(shownFolder.getPath()), NodeLabUi.note(recordFound
				? "Run " + report.setup.runId + "."
				: "The run's record is written when the run finishes; the folder above holds every run.")),
				BorderLayout.CENTER);
		recordBody.revalidate();
		recordBody.repaint();
	}

	// ------------------------------------------------------------ formatting (shared with the overview)

	static String format(double value)
	{
		return Double.isNaN(value) ? "—" : String.format(Locale.US, "%.3f", value);
	}

	static String percent(double value)
	{
		return Double.isNaN(value) ? "—" : String.format(Locale.US, "%.1f%%", value * 100.0);
	}

	static String interval(RecommendationQualityEvaluator.MetricSummary metric)
	{
		return Double.isNaN(metric.low) ? "—" : format(metric.low) + "–" + format(metric.high);
	}

	static String intervalText(RecommendationQualityEvaluator.MetricSummary metric)
	{
		return Double.isNaN(metric.low) ? "Too few targets for an interval"
				: "95% CI " + format(metric.low) + "–" + format(metric.high);
	}

	static String liftText(RecommendationQualityEvaluator.MetricSummary metric)
	{
		return Double.isNaN(metric.lift()) ? "no chance baseline"
				: String.format(Locale.US, "%.2f× chance", metric.lift());
	}

	static NodeLabUi.Chip significanceChip(RecommendationQualityEvaluator.MetricSummary metric)
	{
		if (Double.isNaN(metric.pValue))
		{
			return new NodeLabUi.Chip("Not testable", NodeLabUi.Tone.NEUTRAL, true);
		}
		if (metric.pValue < 0.05 && metric.gain > 0)
		{
			return new NodeLabUi.Chip("Better than chance (" + RecommendationQualityEvaluator.significance(
					metric.pValue) + ")", NodeLabUi.Tone.SUCCESS, true);
		}
		if (metric.pValue < 0.05)
		{
			return new NodeLabUi.Chip("Worse than chance (" + RecommendationQualityEvaluator.significance(
					metric.pValue) + ")", NodeLabUi.Tone.DANGER, true);
		}
		return new NodeLabUi.Chip("Not distinguishable from chance (" + RecommendationQualityEvaluator.significance(
				metric.pValue) + ")", NodeLabUi.Tone.WARNING, true);
	}
}
