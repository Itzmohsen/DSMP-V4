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
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

final class RecommendationPostProcessor
{
	private static final int LEGACY_RESULT_LIMIT = 5;
	private static final Object MAPPING_LOCK = new Object();
	private static final Object EDGE_WRITE_LOCK = new Object();
	private static final Map<String, MappingCacheEntry> MAPPING_CACHE =
			new HashMap<String, MappingCacheEntry>();

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

		List<String> graphNodeNames = request.datasetContext.getGraphNodeNames();
		if (request.selectedDataset != null
				&& (!unresolvedIdentities.isEmpty() || graphNodeNames.isEmpty()))
		{
			DatasetFallback fallback = scanDatasetOnce(
					request.selectedDataset,
					unresolvedIdentities,
					graphNodeNames.isEmpty());
			firstColumnByIdentity.putAll(fallback.firstColumnByIdentity);
			if (graphNodeNames.isEmpty())
			{
				graphNodeNames = fallback.graphNodeNames;
			}
		}

		writeLegacyOutputs(
				request,
				legacyCandidates,
				firstColumnByIdentity);

		int selectedRecommendationIndex = BatchRecommendationSelector.selectIndex(
				recommendationWeights,
				request.adjustDoc2VecFirstWeight,
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

		boolean edgesWritten = false;
		if (request.selectedDataset != null
				&& request.graphEdgesBaseName != null
				&& selectedRecommendation != null)
		{
			File mappingFile = new File(
					request.importantStuffDirectory,
					"name-number-" + request.selectedDataset.getName());
			Map<String, String> nodeNumbers =
					ensureAndLoadMapping(mappingFile, graphNodeNames);
			edgesWritten = appendEdges(
					new File(
							request.importantStuffDirectory,
							request.graphEdgesBaseName + ".txt"),
					nodeNumbers.get(request.userName),
					nodeNumbers.get(request.originalFollowee),
					nodeNumbers.get(selectedRecommendation));
		}

		return new Result(
				selectedRecommendationIndex,
				selectedRecommendation,
				edgesWritten);
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

	private static DatasetFallback scanDatasetOnce(
			File selectedDataset,
			Set<String> identities,
			boolean collectGraphNodes) throws IOException
	{
		if (!selectedDataset.isFile())
		{
			throw new IOException(
					"Selected dataset is unavailable: " + selectedDataset.getPath());
		}

		Map<String, String> matches = new LinkedHashMap<String, String>();
		LinkedHashSet<String> graphNodes = new LinkedHashSet<String>();
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(
						new FileInputStream(selectedDataset),
						StandardCharsets.UTF_8),
				128 * 1024))
		{
			String line;
			boolean firstLine = true;
			while ((line = reader.readLine()) != null)
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
				if (collectGraphNodes)
				{
					graphNodes.add(fields[0]);
					graphNodes.add(fields[4]);
				}
				if (matches.size() < identities.size())
				{
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
		}
		return new DatasetFallback(
				matches,
				new ArrayList<String>(graphNodes));
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

	private static Map<String, String> ensureAndLoadMapping(
			File mappingFile,
			List<String> graphNodeNames) throws IOException
	{
		synchronized (MAPPING_LOCK)
		{
			if (!mappingFile.isFile())
			{
				if (graphNodeNames == null || graphNodeNames.isEmpty())
				{
					throw new IOException(
							"Cannot create graph node mapping without dataset node names");
				}
				writeMappingAtomically(mappingFile, graphNodeNames);
			}

			String cacheKey = mappingFile.getCanonicalPath();
			MappingCacheEntry cached = MAPPING_CACHE.get(cacheKey);
			if (cached != null && cached.matches(mappingFile))
			{
				return cached.nodeNumbers;
			}

			Map<String, String> nodeNumbers = loadMapping(mappingFile);
			Map<String, String> immutable =
					Collections.unmodifiableMap(nodeNumbers);
			MAPPING_CACHE.put(
					cacheKey,
					new MappingCacheEntry(
							mappingFile.lastModified(),
							mappingFile.length(),
							immutable));
			return immutable;
		}
	}

	private static void writeMappingAtomically(
			File mappingFile,
			List<String> graphNodeNames) throws IOException
	{
		ensureDirectory(mappingFile.getParentFile());
		File temporaryFile = File.createTempFile(
				mappingFile.getName() + ".",
				".tmp",
				mappingFile.getParentFile());
		boolean moved = false;
		try
		{
			try (BufferedWriter writer = newUtf8Writer(temporaryFile, false))
			{
				LinkedHashSet<String> uniqueNames =
						new LinkedHashSet<String>(graphNodeNames);
				int nodeNumber = 1;
				for (String nodeName : uniqueNames)
				{
					if (nodeName == null || nodeName.length() == 0)
					{
						continue;
					}
					writer.write(nodeName);
					writer.write('\t');
					writer.write(Integer.toString(nodeNumber++));
					writer.newLine();
				}
			}
			try
			{
				Files.move(
						temporaryFile.toPath(),
						mappingFile.toPath(),
						StandardCopyOption.ATOMIC_MOVE);
			}
			catch (AtomicMoveNotSupportedException e)
			{
				Files.move(
						temporaryFile.toPath(),
						mappingFile.toPath());
			}
			moved = true;
		}
		finally
		{
			if (!moved)
			{
				temporaryFile.delete();
			}
		}
	}

	private static Map<String, String> loadMapping(File mappingFile)
			throws IOException
	{
		Map<String, String> nodeNumbers = new LinkedHashMap<String, String>();
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(
						new FileInputStream(mappingFile),
						StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				String[] fields = line.split("\\s+(?=\\d+$)", 2);
				if (fields.length != 2)
				{
					throw new IOException(
							"Malformed graph node mapping line: " + line);
				}
				nodeNumbers.put(fields[0], fields[1]);
			}
		}
		return nodeNumbers;
	}

	private static boolean appendEdges(
			File edgeFile,
			String userNumber,
			String originalFolloweeNumber,
			String selectedFolloweeNumber) throws IOException
	{
		if (userNumber == null
				|| originalFolloweeNumber == null
				|| selectedFolloweeNumber == null)
		{
			return false;
		}

		synchronized (EDGE_WRITE_LOCK)
		{
			try (BufferedWriter writer = newUtf8Writer(edgeFile, true))
			{
				writer.write(userNumber);
				writer.write(' ');
				writer.write(originalFolloweeNumber);
				writer.newLine();
				writer.write(userNumber);
				writer.write(' ');
				writer.write(selectedFolloweeNumber);
				writer.newLine();
			}
		}
		return true;
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
		private final String graphEdgesBaseName;
		private final String userName;
		private final String originalFollowee;
		private final String userScoresText;
		private final LinkedHashMap<String, Double> scores;
		private final int recommendationCount;
		private final boolean adjustDoc2VecFirstWeight;
		private final Random random;
		private final RecommendationDatasetContext datasetContext;

		Request(
				File selectedDataset,
				File importantStuffDirectory,
				String graphEdgesBaseName,
				String userName,
				String originalFollowee,
				String userScoresText,
				LinkedHashMap<String, Double> scores,
				int recommendationCount,
				boolean adjustDoc2VecFirstWeight,
				Random random,
				RecommendationDatasetContext datasetContext)
		{
			this.selectedDataset = selectedDataset;
			this.importantStuffDirectory = importantStuffDirectory;
			this.graphEdgesBaseName = graphEdgesBaseName;
			this.userName = userName;
			this.originalFollowee = originalFollowee;
			this.userScoresText = userScoresText;
			this.scores = scores;
			this.recommendationCount = recommendationCount;
			this.adjustDoc2VecFirstWeight = adjustDoc2VecFirstWeight;
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
		private final boolean edgesWritten;

		private Result(
				int selectedRecommendationIndex,
				String selectedRecommendation,
				boolean edgesWritten)
		{
			this.selectedRecommendationIndex = selectedRecommendationIndex;
			this.selectedRecommendation = selectedRecommendation;
			this.edgesWritten = edgesWritten;
		}

		int getSelectedRecommendationIndex()
		{
			return selectedRecommendationIndex;
		}

		String getSelectedRecommendation()
		{
			return selectedRecommendation;
		}

		boolean wereEdgesWritten()
		{
			return edgesWritten;
		}
	}

	private static final class DatasetFallback
	{
		private final Map<String, String> firstColumnByIdentity;
		private final List<String> graphNodeNames;

		private DatasetFallback(
				Map<String, String> firstColumnByIdentity,
				List<String> graphNodeNames)
		{
			this.firstColumnByIdentity = firstColumnByIdentity;
			this.graphNodeNames = graphNodeNames;
		}
	}

	private static final class MappingCacheEntry
	{
		private final long lastModified;
		private final long length;
		private final Map<String, String> nodeNumbers;

		private MappingCacheEntry(
				long lastModified,
				long length,
				Map<String, String> nodeNumbers)
		{
			this.lastModified = lastModified;
			this.length = length;
			this.nodeNumbers = nodeNumbers;
		}

		private boolean matches(File file)
		{
			return file.lastModified() == lastModified
					&& file.length() == length;
		}
	}
}
