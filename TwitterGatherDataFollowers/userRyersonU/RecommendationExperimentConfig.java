package TwitterGatherDataFollowers.userRyersonU;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class RecommendationExperimentConfig implements Serializable
{
	private static final long serialVersionUID = 1L;

	private final RecommendationTargetMode targetMode;
	private final int algorithm;
	private final int sourceType;
	private final ArrayList<String> targetUsers;
	private final boolean graphGenerationEnabled;
	private final String datasetPath;
	private final long datasetSize;
	private final long datasetLastModified;
	/** The run's canonical normalised corpus (see RunCorpus), or null. */
	private final String runCorpusPath;

	RecommendationExperimentConfig(
			RecommendationTargetMode targetMode,
			int algorithm,
			int sourceType,
			List<String> targetUsers)
	{
		this(targetMode, algorithm, sourceType, targetUsers, true, null);
	}

	RecommendationExperimentConfig(
			RecommendationTargetMode targetMode,
			int algorithm,
			int sourceType,
			List<String> targetUsers,
			boolean graphGenerationEnabled)
	{
		this(targetMode, algorithm, sourceType, targetUsers,
				graphGenerationEnabled, null);
	}

	RecommendationExperimentConfig(
			RecommendationTargetMode targetMode,
			int algorithm,
			int sourceType,
			List<String> targetUsers,
			boolean graphGenerationEnabled,
			File datasetFile)
	{
		this.targetMode = targetMode == null
				? RecommendationTargetMode.ALGORITHM_DEFAULT : targetMode;
		this.algorithm = algorithm;
		this.sourceType = sourceType;
		this.graphGenerationEnabled = graphGenerationEnabled;
		File absoluteDataset = datasetFile == null
				? null : datasetFile.getAbsoluteFile();
		this.datasetPath = absoluteDataset == null
				? null : absoluteDataset.getPath();
		this.datasetSize = absoluteDataset != null && absoluteDataset.isFile()
				? absoluteDataset.length() : -1L;
		this.datasetLastModified = absoluteDataset != null && absoluteDataset.isFile()
				? absoluteDataset.lastModified() : -1L;
		this.runCorpusPath = null;
		this.targetUsers = new ArrayList<String>();
		if (targetUsers != null)
		{
			for (String targetUser : targetUsers)
			{
				if (targetUser != null && targetUser.length() > 0 && !this.targetUsers.contains(targetUser))
				{
					this.targetUsers.add(targetUser);
				}
			}
		}
	}

	private RecommendationExperimentConfig(RecommendationExperimentConfig base, File runCorpus)
	{
		this.targetMode = base.targetMode;
		this.algorithm = base.algorithm;
		this.sourceType = base.sourceType;
		this.graphGenerationEnabled = base.graphGenerationEnabled;
		this.datasetPath = base.datasetPath;
		this.datasetSize = base.datasetSize;
		this.datasetLastModified = base.datasetLastModified;
		this.targetUsers = new ArrayList<String>(base.targetUsers);
		this.runCorpusPath = runCorpus == null ? null : runCorpus.getAbsolutePath();
	}

	/** The same configuration reading its text from {@code runCorpus}. */
	RecommendationExperimentConfig withRunCorpus(File runCorpus)
	{
		return new RecommendationExperimentConfig(this, runCorpus);
	}

	/** The run's canonical normalised corpus, or null when none was written. */
	File getRunCorpusFile()
	{
		return runCorpusPath == null ? null : new File(runCorpusPath);
	}

	static RecommendationExperimentConfig legacyDefault(int algorithm, int sourceType)
	{
		return new RecommendationExperimentConfig(
				RecommendationTargetMode.ALGORITHM_DEFAULT,
				algorithm,
				sourceType,
				Collections.<String>emptyList());
	}

	RecommendationTargetMode getTargetMode()
	{
		return targetMode;
	}

	int getAlgorithm()
	{
		return algorithm;
	}

	int getSourceType()
	{
		return sourceType;
	}

	List<String> getTargetUsers()
	{
		return Collections.unmodifiableList(targetUsers);
	}

	boolean isGraphGenerationEnabled()
	{
		return graphGenerationEnabled;
	}

	File getDatasetFile()
	{
		return datasetPath == null || datasetPath.length() == 0
				? null : new File(datasetPath);
	}

	String validateDatasetSnapshot()
	{
		File datasetFile = getDatasetFile();
		if (datasetFile == null)
		{
			return "No text dataset was captured for this experiment.";
		}
		if (!datasetFile.isFile())
		{
			return "The experiment dataset is no longer available: "
					+ datasetFile.getAbsolutePath();
		}
		if (datasetSize >= 0L && datasetFile.length() != datasetSize)
		{
			return "The experiment dataset changed size after initialization: "
					+ datasetFile.getAbsolutePath();
		}
		if (datasetLastModified >= 0L
				&& datasetFile.lastModified() != datasetLastModified)
		{
			return "The experiment dataset changed after initialization: "
					+ datasetFile.getAbsolutePath();
		}
		return null;
	}

	RecommendationCandidateType getCandidateType()
	{
		return RecommendationModeSupport.resolveCandidateType(targetMode, algorithm);
	}

	boolean usesConvertedPipeline()
	{
		return RecommendationModeSupport.requiresConvertedPipeline(targetMode, algorithm);
	}

	String getConvertedModeFileSuffix()
	{
		if (!usesConvertedPipeline())
		{
			return "";
		}
		return getCandidateType() == RecommendationCandidateType.USER
				? "_similar-users" : "_followee-accounts";
	}

	String getModeLabel()
	{
		if (targetMode == RecommendationTargetMode.ALGORITHM_DEFAULT)
		{
			RecommendationCandidateType nativeType = RecommendationModeSupport.nativeCandidateType(algorithm);
			return nativeType == null ? targetMode.toString() : targetMode + " (" + nativeType + ")";
		}
		return targetMode.toString();
	}
}
