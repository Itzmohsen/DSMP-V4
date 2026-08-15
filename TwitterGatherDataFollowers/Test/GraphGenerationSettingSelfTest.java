package TwitterGatherDataFollowers.userRyersonU;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

public final class GraphGenerationSettingSelfTest
{
	private GraphGenerationSettingSelfTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		RecommendationExperimentConfig legacyConfig =
				RecommendationExperimentConfig.legacyDefault(
						RecommendationModeSupport.SVM,
						ControllerAgent.FROM_TEXT);
		assertTrue(legacyConfig.isGraphGenerationEnabled(),
				"Legacy experiments must continue generating graphs");

		RecommendationExperimentConfig checkedConfig =
				new RecommendationExperimentConfig(
						RecommendationTargetMode.SIMILAR_USERS,
						RecommendationModeSupport.DOC2VEC,
						ControllerAgent.FROM_TEXT,
						Arrays.asList("alice", "bob"),
						true);
		assertTrue(checkedConfig.isGraphGenerationEnabled(),
				"Checked graph setting was not preserved");

		RecommendationExperimentConfig uncheckedConfig =
				new RecommendationExperimentConfig(
						RecommendationTargetMode.FOLLOWEE_ACCOUNTS,
						RecommendationModeSupport.SVM,
						ControllerAgent.FROM_TEXT,
						Arrays.asList("alice"),
						false);
		assertTrue(!uncheckedConfig.isGraphGenerationEnabled(),
				"Unchecked graph setting was not preserved");

		RecommendationExperimentConfig serializedConfig = roundTrip(uncheckedConfig);
		assertTrue(!serializedConfig.isGraphGenerationEnabled(),
				"Unchecked graph setting did not survive agent serialization");

		File datasetFile = File.createTempFile("experiment-dataset-snapshot", ".txt");
		Files.write(datasetFile.toPath(), "dataset".getBytes(StandardCharsets.UTF_8));
		RecommendationExperimentConfig datasetConfig =
				new RecommendationExperimentConfig(
						RecommendationTargetMode.SIMILAR_USERS,
						RecommendationModeSupport.DOC2VEC,
						ControllerAgent.FROM_TEXT,
						Arrays.asList("alice"),
						false,
						datasetFile);
		RecommendationExperimentConfig serializedDatasetConfig =
				roundTrip(datasetConfig);
		assertTrue(datasetFile.getAbsoluteFile().equals(
						serializedDatasetConfig.getDatasetFile()),
				"Dataset snapshot path did not survive agent serialization");
		assertTrue(serializedDatasetConfig.validateDatasetSnapshot() == null,
				"Unchanged dataset snapshot was rejected");
		Files.write(datasetFile.toPath(), "changed dataset".getBytes(StandardCharsets.UTF_8));
		assertTrue(serializedDatasetConfig.validateDatasetSnapshot() != null,
				"Changed dataset snapshot was not detected");
		datasetFile.delete();

		RecommendationExperimentConfig convertedUserConfig =
				new RecommendationExperimentConfig(
						RecommendationTargetMode.SIMILAR_USERS,
						RecommendationModeSupport.SVM,
						ControllerAgent.FROM_TEXT,
						Arrays.asList("alice"),
						true);
		assertTrue("_similar-users".equals(convertedUserConfig.getConvertedModeFileSuffix()),
				"Converted user graph suffix is inconsistent");

		RecommendationExperimentConfig convertedFolloweeConfig =
				new RecommendationExperimentConfig(
						RecommendationTargetMode.FOLLOWEE_ACCOUNTS,
						RecommendationModeSupport.DOC2VEC,
						ControllerAgent.FROM_TEXT,
						Arrays.asList("alice"),
						true);
		assertTrue("_followee-accounts".equals(convertedFolloweeConfig.getConvertedModeFileSuffix()),
				"Converted followee graph suffix is inconsistent");

		Object[] runArgs = new Object[14];
		runArgs[13] = Boolean.FALSE;
		assertTrue(!StarterAgent.resolveGraphGenerationEnabled(runArgs, checkedConfig),
				"Run-time unchecked graph setting did not override initialization");
		runArgs[13] = Boolean.TRUE;
		assertTrue(StarterAgent.resolveGraphGenerationEnabled(runArgs, uncheckedConfig),
				"Run-time checked graph setting did not override initialization");
		assertTrue(!StarterAgent.resolveGraphGenerationEnabled(new Object[13], uncheckedConfig),
				"Legacy run did not retain its initialization graph setting");

		System.out.println("Graph generation setting self-test passed");
	}

	private static RecommendationExperimentConfig roundTrip(
			RecommendationExperimentConfig config) throws Exception
	{
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		ObjectOutputStream output = new ObjectOutputStream(bytes);
		output.writeObject(config);
		output.close();

		ObjectInputStream input = new ObjectInputStream(
				new ByteArrayInputStream(bytes.toByteArray()));
		RecommendationExperimentConfig copy =
				(RecommendationExperimentConfig) input.readObject();
		input.close();
		return copy;
	}

	private static void assertTrue(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
