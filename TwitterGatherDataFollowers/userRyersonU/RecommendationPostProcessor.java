package TwitterGatherDataFollowers.userRyersonU;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * What a user agent does with its merged list: the follow simulation's choice
 * (one of the top recommendations, as {@link BatchRecommendationSelector}
 * draws it) and the legacy text outputs in important-stuff. The run's graph,
 * follows, recommendations and adoptions, is built from the run's lists when
 * its record is written ({@link RunGraph}), not from files written here.
 */
final class RecommendationPostProcessor
{
	private static final int LEGACY_RESULT_LIMIT = 5;

	private RecommendationPostProcessor()
	{
	}

	static Result process(Request request) throws IOException
	{
		if (request == null)
		{
			throw new IllegalArgumentException("request must not be null");
		}
		if (request.scores == null)
		{
			throw new IllegalArgumentException("scores must not be null");
		}
		if (request.random == null)
		{
			throw new IllegalArgumentException("random must not be null");
		}

		ensureDirectory(request.importantStuffDirectory);

		List<String> legacyCandidates = firstKeys(request.scores, LEGACY_RESULT_LIMIT);
		List<String> recommendationNames =
				firstKeys(request.scores, Math.max(0, request.recommendationCount));
		List<Double> recommendationWeights =
				firstValues(request.scores, recommendationNames.size());

		Set<String> unresolvedIdentities = new LinkedHashSet<String>();
		Map<String, String> firstColumnByIdentity = new LinkedHashMap<String, String>();
		for (String candidate : legacyCandidates)
		{
			String resolved = request.datasetContext.resolveFirstColumn(candidate);
			if (resolved == null)
			{
				unresolvedIdentities.add(candidate);
			}
			else
			{
				firstColumnByIdentity.put(candidate, resolved);
			}
		}

		if (request.selectedDataset != null && !unresolvedIdentities.isEmpty())
		{
			firstColumnByIdentity.putAll(scanDatasetOnce(request.selectedDataset, unresolvedIdentities));
		}

		writeLegacyOutputs(
				request,
				legacyCandidates,
				firstColumnByIdentity);

		int selectedRecommendationIndex = BatchRecommendationSelector.selectIndex(
				recommendationWeights,
				request.random);
		String selectedRecommendation = selectedRecommendationIndex >= 0
				? recommendationNames.get(selectedRecommendationIndex)
				: null;

		BatchRecommendationSelector.writeSelectedRecommendation(
				new File(request.importantStuffDirectory, "outputChoice.txt"),
				selectedRecommendation);
		try (BufferedWriter writer = newUtf8Writer(
				new File(request.importantStuffDirectory, "outPutSepFollowee.txt"),
				false))
		{
			BatchRecommendationSelector.writeFolloweeChoices(
					writer,
					Collections.singletonList(request.userName),
					request.originalFollowee,
					legacyCandidates,
					selectedRecommendationIndex);
		}

		return new Result(
				selectedRecommendationIndex,
				selectedRecommendation);
	}

	private static void writeLegacyOutputs(
			Request request,
			List<String> legacyCandidates,
			Map<String, String> firstColumnByIdentity) throws IOException
	{
		try (BufferedWriter writer = newUtf8Writer(
				new File(request.importantStuffDirectory, "outPutSep.txt"),
				false))
		{
			writer.write(request.userScoresText == null ? "" : request.userScoresText);
			writer.newLine();
		}

		try (BufferedWriter resultWriter = newUtf8Writer(
					new File(request.importantStuffDirectory, "outPutSepResult.txt"),
					false);
				BufferedWriter followeeWriter = newUtf8Writer(
					new File(request.importantStuffDirectory, "outPutSepFolloweeResults.txt"),
					true))
		{
			for (String candidate : legacyCandidates)
			{
				resultWriter.write(candidate);
				String firstColumn = firstColumnByIdentity.get(candidate);
				if (firstColumn != null)
				{
					resultWriter.write(" : ");
					resultWriter.write(firstColumn);
					resultWriter.newLine();
					followeeWriter.write(firstColumn);
					followeeWriter.write(" : ");
					followeeWriter.write(request.userName);
					followeeWriter.newLine();
				}
			}
			resultWriter.newLine();
		}

		if (!legacyCandidates.isEmpty()
				&& firstColumnByIdentity.containsKey(legacyCandidates.get(0)))
		{
			try (BufferedWriter writer = newUtf8Writer(
					new File(request.importantStuffDirectory, "test-file-name.txt"),
					true))
			{
				writer.write(legacyCandidates.get(0));
				writer.write(' ');
				writer.write(request.userName);
				writer.newLine();
			}
		}
	}

	/** The first column (the followed account) of each identity, from one pass over the dataset. */
	private static Map<String, String> scanDatasetOnce(
			File selectedDataset,
			Set<String> identities) throws IOException
	{
		if (!selectedDataset.isFile())
		{
			throw new IOException(
					"Selected dataset is unavailable: " + selectedDataset.getPath());
		}

		Map<String, String> matches = new LinkedHashMap<String, String>();
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(
						new FileInputStream(selectedDataset),
						StandardCharsets.UTF_8),
				128 * 1024))
		{
			String line;
			boolean firstLine = true;
			while ((line = reader.readLine()) != null && matches.size() < identities.size())
			{
				if (firstLine && line.startsWith("\uFEFF"))
				{
					line = line.substring(1);
				}
				firstLine = false;

				String[] fields = line.split("\t", 6);
				if (fields.length < 5)
				{
					continue;
				}
				for (String identity : identities)
				{
					if (!matches.containsKey(identity)
							&& containsField(fields, identity))
					{
						matches.put(identity, fields[0]);
					}
				}
			}
		}
		return matches;
	}

	private static boolean containsField(String[] fields, String identity)
	{
		for (String field : fields)
		{
			if (field.equals(identity))
			{
				return true;
			}
		}
		return false;
	}

	private static List<String> firstKeys(
			LinkedHashMap<String, Double> scores,
			int limit)
	{
		List<String> keys = new ArrayList<String>();
		if (limit <= 0)
		{
			return keys;
		}
		for (String key : scores.keySet())
		{
			if (keys.size() >= limit)
			{
				break;
			}
			keys.add(key);
		}
		return keys;
	}

	private static List<Double> firstValues(
			LinkedHashMap<String, Double> scores,
			int limit)
	{
		List<Double> values = new ArrayList<Double>();
		if (limit <= 0)
		{
			return values;
		}
		for (Double value : scores.values())
		{
			if (values.size() >= limit)
			{
				break;
			}
			values.add(value);
		}
		return values;
	}

	private static BufferedWriter newUtf8Writer(File file, boolean append)
			throws IOException
	{
		File parent = file.getParentFile();
		if (parent != null)
		{
			ensureDirectory(parent);
		}
		return new BufferedWriter(
				new OutputStreamWriter(
						new FileOutputStream(file, append),
						StandardCharsets.UTF_8));
	}

	private static void ensureDirectory(File directory) throws IOException
	{
		if (directory == null)
		{
			return;
		}
		if (!directory.exists() && !directory.mkdirs())
		{
			throw new IOException(
					"Could not create directory: " + directory.getPath());
		}
		if (!directory.isDirectory())
		{
			throw new IOException(
					"Path is not a directory: " + directory.getPath());
		}
	}

	static final class Request
	{
		private final File selectedDataset;
		private final File importantStuffDirectory;
		private final String userName;
		private final String originalFollowee;
		private final String userScoresText;
		private final LinkedHashMap<String, Double> scores;
		private final int recommendationCount;
		private final Random random;
		private final RecommendationDatasetContext datasetContext;

		Request(
				File selectedDataset,
				File importantStuffDirectory,
				String userName,
				String originalFollowee,
				String userScoresText,
				LinkedHashMap<String, Double> scores,
				int recommendationCount,
				Random random,
				RecommendationDatasetContext datasetContext)
		{
			this.selectedDataset = selectedDataset;
			this.importantStuffDirectory = importantStuffDirectory;
			this.userName = userName;
			this.originalFollowee = originalFollowee;
			this.userScoresText = userScoresText;
			this.scores = scores;
			this.recommendationCount = recommendationCount;
			this.random = random;
			this.datasetContext = datasetContext == null
					? RecommendationDatasetContext.empty()
					: datasetContext;
		}
	}

	static final class Result
	{
		private final int selectedRecommendationIndex;
		private final String selectedRecommendation;

		private Result(
				int selectedRecommendationIndex,
				String selectedRecommendation)
		{
			this.selectedRecommendationIndex = selectedRecommendationIndex;
			this.selectedRecommendation = selectedRecommendation;
		}

		int getSelectedRecommendationIndex()
		{
			return selectedRecommendationIndex;
		}

		String getSelectedRecommendation()
		{
			return selectedRecommendation;
		}
	}
}
