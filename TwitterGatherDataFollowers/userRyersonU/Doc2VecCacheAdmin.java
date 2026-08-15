package TwitterGatherDataFollowers.userRyersonU;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Doc2VecCacheAdmin
{
	private static final String INFO_PREFIX = "CACHE_INFO";

	private Doc2VecCacheAdmin()
	{
	}

	static CacheInfo inspect(
			String pythonCommand,
			File datasetFile,
			int workers) throws IOException, InterruptedException
	{
		return inspect(
				pythonCommand,
				datasetFile,
				workers,
				AlgorithmParameterSettings.defaults());
	}

	static CacheInfo inspect(
			String pythonCommand,
			File datasetFile,
			int workers,
			AlgorithmParameterSettings settings) throws IOException, InterruptedException
	{
		return run("info", pythonCommand, datasetFile, workers, settings).toCacheInfo();
	}

	static DeleteResult delete(
			String pythonCommand,
			File datasetFile,
			int workers) throws IOException, InterruptedException
	{
		return delete(
				pythonCommand,
				datasetFile,
				workers,
				AlgorithmParameterSettings.defaults());
	}

	static DeleteResult delete(
			String pythonCommand,
			File datasetFile,
			int workers,
			AlgorithmParameterSettings settings) throws IOException, InterruptedException
	{
		RawResult raw = run("delete", pythonCommand, datasetFile, workers, settings);
		return new DeleteResult(
				raw.toCacheInfo(),
				parseLong(raw.fields.get("deleted_files")),
				parseLong(raw.fields.get("deleted_bytes")));
	}

	static int trainingWorkers()
	{
		return Math.min(
				Math.max(Runtime.getRuntime().availableProcessors() - 1, 1),
				12);
	}

	static File scriptFile()
	{
		File script = new File(
				"TwitterGatherDataFollowers/userRyersonU/doc2vec_cache_admin.py");
		if (!script.isFile())
		{
			throw new IllegalStateException(
					"Doc2Vec cache admin script is unavailable: "
							+ script.getAbsolutePath());
		}
		return script;
	}

	private static RawResult run(
			String commandName,
			String pythonCommand,
			File datasetFile,
			int workers,
			AlgorithmParameterSettings settings) throws IOException, InterruptedException
	{
		if (datasetFile == null || !datasetFile.isFile())
		{
			throw new IllegalArgumentException(
					"A readable dataset file is required.");
		}
		List<String> command = new ArrayList<String>();
		command.add(pythonCommand);
		command.add("-u");
		command.add(scriptFile().getPath());
		command.add(commandName);
		command.add(datasetFile.getAbsolutePath());
		command.add(String.valueOf(Math.max(1, workers)));
		command.addAll((settings == null
				? AlgorithmParameterSettings.defaults()
				: settings).toDoc2VecCommandArguments());

		ProcessBuilder processBuilder = new ProcessBuilder(command);
		processBuilder.redirectErrorStream(true);
		Process process = processBuilder.start();
		LinkedHashMap<String,String> fields =
				new LinkedHashMap<String,String>();
		ArrayList<String> diagnostics = new ArrayList<String>();
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				process.getInputStream(), StandardCharsets.UTF_8));
		try
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				if (line.startsWith(INFO_PREFIX + "\t"))
				{
					String[] parts = line.split("\t", 3);
					if (parts.length == 3)
					{
						fields.put(parts[1], parts[2]);
					}
				}
				else if (line.trim().length() > 0)
				{
					diagnostics.add(line.trim());
				}
			}
		}
		finally
		{
			reader.close();
		}
		int exitCode = process.waitFor();
		if (exitCode != 0)
		{
			throw new IOException(
					"Doc2Vec cache " + commandName
							+ " failed with exit code " + exitCode + ". "
							+ joinDiagnostics(diagnostics));
		}
		return new RawResult(fields, diagnostics);
	}

	private static String joinDiagnostics(List<String> diagnostics)
	{
		if (diagnostics == null || diagnostics.isEmpty())
		{
			return "";
		}
		StringBuilder builder = new StringBuilder();
		for (String diagnostic : diagnostics)
		{
			if (builder.length() > 0)
			{
				builder.append(' ');
			}
			builder.append(diagnostic);
			if (builder.length() > 400)
			{
				builder.append(" ...");
				break;
			}
		}
		return builder.toString();
	}

	private static long parseLong(String value)
	{
		if (value == null || value.trim().length() == 0)
		{
			return 0L;
		}
		try
		{
			return Long.parseLong(value.trim());
		}
		catch (NumberFormatException exception)
		{
			return 0L;
		}
	}

	private static int parseInt(String value)
	{
		long parsed = parseLong(value);
		if (parsed > Integer.MAX_VALUE)
		{
			return Integer.MAX_VALUE;
		}
		if (parsed < Integer.MIN_VALUE)
		{
			return Integer.MIN_VALUE;
		}
		return (int)parsed;
	}

	private static boolean parseBoolean(String value)
	{
		return "true".equalsIgnoreCase(value);
	}

	private static final class RawResult
	{
		private final Map<String,String> fields;
		private final List<String> diagnostics;

		private RawResult(
				Map<String,String> fields,
				List<String> diagnostics)
		{
			this.fields = fields;
			this.diagnostics = diagnostics;
		}

		private CacheInfo toCacheInfo()
		{
			return new CacheInfo(
					fields.get("dataset_path"),
					parseLong(fields.get("dataset_size_bytes")),
					parseLong(fields.get("dataset_rows")),
					parseLong(fields.get("dataset_users")),
					parseLong(fields.get("dataset_malformed_rows")),
					parseInt(fields.get("workers")),
					fields.get("cache_key"),
					fields.get("cache_state"),
					parseBoolean(fields.get("cache_valid")),
					fields.get("model_path"),
					fields.get("vectors_path"),
					parseBoolean(fields.get("model_exists")),
					parseBoolean(fields.get("vectors_exists")),
					parseBoolean(fields.get("tags_exists")),
					parseLong(fields.get("tag_count")),
					parseLong(fields.get("vector_rows")),
					parseLong(fields.get("vector_dims")),
					parseLong(fields.get("exact_cache_bytes")),
					parseLong(fields.get("dataset_cache_entries")),
					parseLong(fields.get("dataset_cache_files")),
					parseLong(fields.get("dataset_cache_bytes")),
					diagnostics);
		}
	}

	static final class CacheInfo
	{
		private final String datasetPath;
		private final long datasetSizeBytes;
		private final long datasetRows;
		private final long datasetUsers;
		private final long malformedRows;
		private final int workers;
		private final String cacheKey;
		private final String cacheState;
		private final boolean cacheValid;
		private final String modelPath;
		private final String vectorsPath;
		private final boolean modelExists;
		private final boolean vectorsExists;
		private final boolean tagsExists;
		private final long tagCount;
		private final long vectorRows;
		private final long vectorDims;
		private final long exactCacheBytes;
		private final long datasetCacheEntries;
		private final long datasetCacheFiles;
		private final long datasetCacheBytes;
		private final List<String> diagnostics;

		private CacheInfo(
				String datasetPath,
				long datasetSizeBytes,
				long datasetRows,
				long datasetUsers,
				long malformedRows,
				int workers,
				String cacheKey,
				String cacheState,
				boolean cacheValid,
				String modelPath,
				String vectorsPath,
				boolean modelExists,
				boolean vectorsExists,
				boolean tagsExists,
				long tagCount,
				long vectorRows,
				long vectorDims,
				long exactCacheBytes,
				long datasetCacheEntries,
				long datasetCacheFiles,
				long datasetCacheBytes,
				List<String> diagnostics)
		{
			this.datasetPath = datasetPath;
			this.datasetSizeBytes = datasetSizeBytes;
			this.datasetRows = datasetRows;
			this.datasetUsers = datasetUsers;
			this.malformedRows = malformedRows;
			this.workers = workers;
			this.cacheKey = cacheKey;
			this.cacheState = cacheState;
			this.cacheValid = cacheValid;
			this.modelPath = modelPath;
			this.vectorsPath = vectorsPath;
			this.modelExists = modelExists;
			this.vectorsExists = vectorsExists;
			this.tagsExists = tagsExists;
			this.tagCount = tagCount;
			this.vectorRows = vectorRows;
			this.vectorDims = vectorDims;
			this.exactCacheBytes = exactCacheBytes;
			this.datasetCacheEntries = datasetCacheEntries;
			this.datasetCacheFiles = datasetCacheFiles;
			this.datasetCacheBytes = datasetCacheBytes;
			this.diagnostics = diagnostics;
		}

		boolean isWarm()
		{
			return cacheValid;
		}

			boolean hasAnyDatasetCache()
			{
				return datasetCacheFiles > 0L;
			}

			boolean hasExactCache()
			{
				return exactCacheBytes > 0L
						|| modelExists || vectorsExists || tagsExists;
			}

		String getDatasetPath()
		{
			return datasetPath;
		}

		long getDatasetSizeBytes()
		{
			return datasetSizeBytes;
		}

		long getDatasetRows()
		{
			return datasetRows;
		}

		long getDatasetUsers()
		{
			return datasetUsers;
		}

		long getMalformedRows()
		{
			return malformedRows;
		}

		int getWorkers()
		{
			return workers;
		}

		String getCacheKey()
		{
			return cacheKey;
		}

		String getCacheState()
		{
			return cacheState;
		}

		String getModelPath()
		{
			return modelPath;
		}

		String getVectorsPath()
		{
			return vectorsPath;
		}

		boolean isModelExists()
		{
			return modelExists;
		}

		boolean isVectorsExists()
		{
			return vectorsExists;
		}

		boolean isTagsExists()
		{
			return tagsExists;
		}

		long getTagCount()
		{
			return tagCount;
		}

		long getVectorRows()
		{
			return vectorRows;
		}

		long getVectorDims()
		{
			return vectorDims;
		}

		long getExactCacheBytes()
		{
			return exactCacheBytes;
		}

		long getDatasetCacheEntries()
		{
			return datasetCacheEntries;
		}

		long getDatasetCacheFiles()
		{
			return datasetCacheFiles;
		}

		long getDatasetCacheBytes()
		{
			return datasetCacheBytes;
		}

		List<String> getDiagnostics()
		{
			return diagnostics;
		}
	}

	static final class DeleteResult
	{
		private final CacheInfo cacheInfoAfterDelete;
		private final long deletedFiles;
		private final long deletedBytes;

		private DeleteResult(
				CacheInfo cacheInfoAfterDelete,
				long deletedFiles,
				long deletedBytes)
		{
			this.cacheInfoAfterDelete = cacheInfoAfterDelete;
			this.deletedFiles = deletedFiles;
			this.deletedBytes = deletedBytes;
		}

		CacheInfo getCacheInfoAfterDelete()
		{
			return cacheInfoAfterDelete;
		}

		long getDeletedFiles()
		{
			return deletedFiles;
		}

		long getDeletedBytes()
		{
			return deletedBytes;
		}
	}
}
