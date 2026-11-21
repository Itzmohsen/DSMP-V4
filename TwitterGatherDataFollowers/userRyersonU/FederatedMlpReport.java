package TwitterGatherDataFollowers.userRyersonU;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The federated MLP's part of a run record, in the record's
 * {@code federated-mlp} folder:
 *
 * <pre>
 * report.json   the settings, every fold (clients, users, rounds, best round,
 *               the SHA-256 of its final model) and every round
 * rounds.csv    one line per fold and round: clients and users that trained,
 *               bytes uploaded, the size of the new global model, validation
 *               loss and accuracy, whether the round was a new best, and the
 *               largest change any client made to a weight
 * </pre>
 *
 * The model fingerprints make two runs comparable without their scores: equal
 * fingerprints mean bit-identical models.
 *
 * A report holds numbers and names only, never a model, so keeping one until
 * its record is written costs a few kilobytes.
 */
final class FederatedMlpReport
{
	static final String FOLDER = "federated-mlp";
	static final String FILE = "report.json";
	static final String ROUNDS_FILE = "rounds.csv";

	/**
	 * Reports kept at a time. A record is written in the background after its
	 * run, so the next run may finish first; a handful covers that.
	 */
	static final int KEPT_RUNS = 8;

	/** Reports by run, oldest first. */
	private static final LinkedHashMap<String,FederatedMlpReport> REPORTS =
			new LinkedHashMap<String,FederatedMlpReport>();

	private final Map<String,Object> json;
	private final List<FederatedMlp.RoundRecord> rounds;

	private FederatedMlpReport(Map<String,Object> json, List<FederatedMlp.RoundRecord> rounds)
	{
		this.json = json;
		this.rounds = Collections.unmodifiableList(new ArrayList<FederatedMlp.RoundRecord>(rounds));
	}

	/** The report of an outcome: its settings, folds and rounds, without its models. */
	static FederatedMlpReport of(FederatedMlp.Outcome outcome)
	{
		if (outcome == null)
		{
			throw new IllegalArgumentException("A federated report needs an outcome.");
		}
		return new FederatedMlpReport(outcome.toJson(), outcome.state.history());
	}

	/**
	 * Keeps a run's report until its record is written. Called by the node
	 * that scored the targets; a run has one.
	 */
	static synchronized void remember(String runId, FederatedMlp.Outcome outcome)
	{
		if (runId == null || outcome == null)
		{
			return;
		}
		REPORTS.remove(runId);
		REPORTS.put(runId, of(outcome));
		Iterator<String> oldest = REPORTS.keySet().iterator();
		while (REPORTS.size() > KEPT_RUNS && oldest.hasNext())
		{
			oldest.next();
			oldest.remove();
		}
	}

	/** The remembered report of {@code runId}, or null when that run trained no federation. */
	static synchronized FederatedMlpReport of(String runId)
	{
		return runId == null ? null : REPORTS.get(runId);
	}

	static synchronized void clear()
	{
		REPORTS.clear();
	}

	/** The whole run as nested maps and lists: what report.json holds. */
	Map<String,Object> toJson()
	{
		return json;
	}

	/** Every fold's rounds, in the order they were aggregated. */
	List<FederatedMlp.RoundRecord> rounds()
	{
		return rounds;
	}

	/** Writes the report files into a run's directory. */
	void write(File runDirectory) throws IOException
	{
		File folder = new File(runDirectory, FOLDER);
		if (!folder.isDirectory() && !folder.mkdirs())
		{
			throw new IOException("cannot create " + folder.getPath());
		}
		Files.write(new File(folder, FILE).toPath(),
				JsonText.of(json).getBytes(StandardCharsets.UTF_8));
		StringBuilder csv = new StringBuilder();
		csv.append(JsonText.csvLine("round", "fold", "clients", "users", "upload_bytes", "model_bytes",
				"validation_loss", "validation_accuracy", "improved", "largest_update"));
		for (FederatedMlp.RoundRecord record : rounds)
		{
			csv.append(JsonText.csvLine(
					Integer.valueOf(record.round),
					Integer.valueOf(record.fold + 1),
					Integer.valueOf(record.participants),
					Long.valueOf(record.samples),
					Long.valueOf(record.uploadBytes),
					Long.valueOf(record.modelBytes),
					Double.isNaN(record.validationLoss) ? null : Double.valueOf(record.validationLoss),
					Double.isNaN(record.validationAccuracy) ? null : Double.valueOf(record.validationAccuracy),
					Boolean.valueOf(record.improved),
					Double.valueOf(record.largestUpdate)));
		}
		Files.write(new File(folder, ROUNDS_FILE).toPath(), csv.toString().getBytes(StandardCharsets.UTF_8));
	}
}
