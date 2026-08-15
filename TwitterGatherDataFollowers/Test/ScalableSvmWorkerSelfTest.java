package TwitterGatherDataFollowers.userRyersonU;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.Arrays;

public final class ScalableSvmWorkerSelfTest
{
	private ScalableSvmWorkerSelfTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		supportsDeterministicMulticlassScoringInAnIsolatedWorker();
		handlesADeclaredClassWithNoTrainingRows();
		handlesHighCardinalitySparseInputWithoutDenseExpansion();
		isRegisteredAsASeparateClassifier();
		System.out.println("ScalableSvmWorkerSelfTest passed");
	}

	private static void supportsDeterministicMulticlassScoringInAnIsolatedWorker()
			throws Exception
	{
		File directory = Files.createTempDirectory(
				"scalable-svm-worker-test-").toFile();
		File train = new File(directory, "train.arff");
		File test = new File(directory, "test.arff");
		File recommend = new File(directory, "recommend.arff");
		try
		{
			String header = "@attribute x numeric\n"
					+ "@attribute y numeric\n"
					+ "@attribute result {'A','B','C'}\n";
			write(train,
					"@relation train\n" + header + "@data\n"
					+ "0.0,0.0,'A'\n0.1,0.0,'A'\n"
					+ "1.0,0.0,'B'\n0.9,0.1,'B'\n"
					+ "0.0,1.0,'C'\n0.1,0.9,'C'\n");
			write(test,
					"@relation test\n" + header + "@data\n"
					+ "0.05,0.0,'A'\n0.95,0.05,'B'\n0.05,0.95,'C'\n");
			write(recommend,
					"@relation recommend\n" + header + "@data\n"
					+ "0.02,0.0,'A'\n0.98,0.02,'B'\n0.02,0.98,'C'\n");

			ExactSvmWorkerResult first = ScalableSvmWorkerRunner.run(
					"ScalableSelfTest",
					"multiclass",
					null,
					train,
					test,
					recommend,
					2,
					6,
					3);
			ExactSvmWorkerResult second = ScalableSvmWorkerRunner.run(
					"ScalableSelfTest",
					"multiclass repeat",
					null,
					train,
					test,
					recommend,
					2,
					6,
					3);
			require(first.getPredictedClasses().equals(second.getPredictedClasses()),
					"scalable SVM predictions changed between identical runs");
			for (int index = 0; index < first.getDistributions().size(); index++)
			{
				require(Arrays.equals(
						first.getDistributions().get(index),
						second.getDistributions().get(index)),
						"scalable SVM scores changed between identical runs");
				requireDistribution(first.getDistributions().get(index));
			}
			require(first.getPredictedClasses().equals(
					Arrays.asList("A", "B", "C")),
					"scalable SVM did not learn the simple multiclass problem: "
					+ first.getPredictedClasses());
		}
		finally
		{
			delete(directory);
		}
	}

	private static void isRegisteredAsASeparateClassifier()
	{
		require(RecommendationModeSupport.isClassifierAlgorithm(
				RecommendationModeSupport.SCALABLE_SVM),
				"scalable SVM is not registered as a classifier");
		require(!RecommendationModeSupport.algorithmName(
				RecommendationModeSupport.SCALABLE_SVM).equals(
						RecommendationModeSupport.algorithmName(
								RecommendationModeSupport.SVM)),
				"scalable SVM is not visibly separate from exact SVM");
	}

	private static void handlesADeclaredClassWithNoTrainingRows()
			throws Exception
	{
		File directory = Files.createTempDirectory(
				"scalable-svm-empty-class-test-").toFile();
		File train = new File(directory, "train.arff");
		File test = new File(directory, "test.arff");
		File recommend = new File(directory, "recommend.arff");
		try
		{
			String header = "@attribute x numeric\n"
					+ "@attribute result {'A','B','EMPTY'}\n";
			write(train,
					"@relation train\n" + header + "@data\n"
					+ "0.0,'A'\n0.1,'A'\n0.9,'B'\n1.0,'B'\n");
			write(test,
					"@relation test\n" + header + "@data\n0.05,'A'\n");
			write(recommend,
					"@relation recommend\n" + header + "@data\n0.95,'B'\n");
			ExactSvmWorkerResult result = ScalableSvmWorkerRunner.run(
					"ScalableSelfTest",
					"empty declared class",
					null,
					train,
					test,
					recommend,
					1,
					4,
					3);
			require(result.getDistributions().get(0)[2] == 0.0,
					"a declared class with no training rows received a score");
		}
		finally
		{
			delete(directory);
		}
	}

	private static void handlesHighCardinalitySparseInputWithoutDenseExpansion()
			throws Exception
	{
		File directory = Files.createTempDirectory(
				"scalable-svm-high-cardinality-test-").toFile();
		File train = new File(directory, "train.arff");
		File test = new File(directory, "test.arff");
		File recommend = new File(directory, "recommend.arff");
		try
		{
			int classes = 600;
			int features = 2400;
			writeSparseDataset(train, "train", classes, features, 2);
			writeSparseDataset(test, "test", classes, features, 1);
			writeSparseDataset(recommend, "recommend", classes, features, 1);
			ExactSvmWorkerResult result = ScalableSvmWorkerRunner.run(
					"ScalableSelfTest",
					"high-cardinality sparse",
					null,
					train,
					test,
					recommend,
					features,
					classes * 2,
					classes);
			require(result.getPredictedClasses().size() == classes,
					"high-cardinality scoring lost recommendation rows");
			for (double[] distribution : result.getDistributions())
			{
				requireDistribution(distribution);
			}
		}
		finally
		{
			delete(directory);
		}
	}

	private static void writeSparseDataset(
			File file,
			String relation,
			int classes,
			int features,
			int rowsPerClass) throws Exception
	{
		StringBuilder content = new StringBuilder();
		content.append("@relation ").append(relation).append('\n');
		for (int feature = 0; feature < features; feature++)
		{
			content.append("@attribute word").append(feature)
					.append(" numeric\n");
		}
		content.append("@attribute result {");
		for (int classIndex = 0; classIndex < classes; classIndex++)
		{
			if (classIndex > 0)
			{
				content.append(',');
			}
			content.append("'C").append(classIndex).append("'");
		}
		content.append("}\n@data\n");
		for (int classIndex = 0; classIndex < classes; classIndex++)
		{
			for (int row = 0; row < rowsPerClass; row++)
			{
				int secondFeature = classes + classIndex;
				content.append('{').append(classIndex).append(" 1.0,")
						.append(secondFeature).append(" 0.5,")
						.append(features).append(" 'C")
						.append(classIndex).append("'}\n");
			}
		}
		write(file, content.toString());
	}

	private static void write(File file, String content) throws Exception
	{
		try (FileWriter writer = new FileWriter(file))
		{
			writer.write(content);
		}
	}

	private static void delete(File file) throws Exception
	{
		if (file == null || !file.exists())
		{
			return;
		}
		if (file.isDirectory())
		{
			File[] children = file.listFiles();
			if (children != null)
			{
				for (File child : children)
				{
					delete(child);
				}
			}
		}
		Files.deleteIfExists(file.toPath());
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}

	private static void requireDistribution(double[] distribution)
	{
		double total = 0.0;
		for (double score : distribution)
		{
			require(Double.isFinite(score) && score >= 0.0,
					"distribution contains an invalid score");
			total += score;
		}
		require(Math.abs(total - 1.0) < 1.0e-9,
				"distribution is not normalized: " + total);
	}
}
