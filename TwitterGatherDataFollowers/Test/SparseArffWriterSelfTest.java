package TwitterGatherDataFollowers.userRyersonU;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import weka.classifiers.functions.SMO;
import weka.core.Instances;

public final class SparseArffWriterSelfTest
{
	private SparseArffWriterSelfTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		preservesDenseInstanceValuesAndModelScores();
		keepsWideRowsSparse();
		System.out.println("SparseArffWriterSelfTest passed");
	}

	private static void preservesDenseInstanceValuesAndModelScores() throws Exception
	{
		List<String> terms = Arrays.asList("alpha", "beta", "gamma");
		List<String> classes = Arrays.asList("A", "B");
		File sparseFile = File.createTempFile("sparse-arff", ".arff");
		try
		{
			try (BufferedWriter writer = new BufferedWriter(new FileWriter(sparseFile)))
			{
				SparseArffWriter.writeHeader(writer, "sparse", terms, classes);
				SparseArffWriter.writeRow(writer, values(1.0, 0.0, 0.2), terms, "A");
				SparseArffWriter.writeRow(writer, values(0.8, 0.0, 0.1), terms, "A");
				SparseArffWriter.writeRow(writer, values(0.0, 0.9, 0.0), terms, "B");
				SparseArffWriter.writeRow(writer, values(0.0, 1.0, 0.1), terms, "B");
			}

			String denseArff = "@relation dense\n"
					+ "@attribute word0 numeric\n"
					+ "@attribute word1 numeric\n"
					+ "@attribute word2 numeric\n"
					+ "@attribute result {'A','B'}\n"
					+ "@data\n"
					+ "1.0,0.0,0.2,'A'\n"
					+ "0.8,0.0,0.1,'A'\n"
					+ "0.0,0.9,0.0,'B'\n"
					+ "0.0,1.0,0.1,'B'\n";
			Instances dense = new Instances(new StringReader(denseArff));
			Instances sparse = new Instances(
					new StringReader(new String(
							Files.readAllBytes(sparseFile.toPath()),
							StandardCharsets.UTF_8)));
			dense.setClassIndex(dense.numAttributes() - 1);
			sparse.setClassIndex(sparse.numAttributes() - 1);

			for (int row = 0; row < dense.numInstances(); row++)
			{
				for (int column = 0; column < dense.numAttributes(); column++)
				{
					require(dense.instance(row).value(column)
							== sparse.instance(row).value(column),
							"sparse ARFF changed row "+row+" column "+column);
				}
			}

			SMO denseModel = configuredModel();
			SMO sparseModel = configuredModel();
			denseModel.buildClassifier(dense);
			sparseModel.buildClassifier(sparse);
			require(Arrays.equals(
					denseModel.distributionForInstance(dense.instance(0)),
					sparseModel.distributionForInstance(sparse.instance(0))),
					"sparse ARFF changed SVM class scores");
		}
		finally
		{
			sparseFile.delete();
		}
	}

	private static void keepsWideRowsSparse() throws Exception
	{
		File sparseFile = File.createTempFile("wide-sparse-arff", ".arff");
		try
		{
			java.util.ArrayList<String> terms = new java.util.ArrayList<String>();
			for (int i = 0; i < 75000; i++)
			{
				terms.add("term" + i);
			}
			Map<String,Double> values = new LinkedHashMap<String,Double>();
			values.put("term1", 0.25);
			values.put("term74999", 0.75);
			try (BufferedWriter writer = new BufferedWriter(new FileWriter(sparseFile)))
			{
				SparseArffWriter.writeHeader(
						writer, "wide", terms, Arrays.asList("A", "B"));
				SparseArffWriter.writeRow(writer, values, terms, "A");
			}

			List<String> lines = Files.readAllLines(
					sparseFile.toPath(), StandardCharsets.UTF_8);
			String lastLine = lines.get(lines.size() - 1);
			require(lastLine.length() < 80,
					"wide sparse row unexpectedly contains dense zero columns");
			require(lastLine.contains("1 0.25")
					&& lastLine.contains("74999 0.75")
					&& lastLine.contains("75000 'A'"),
					"wide sparse row indexes are incorrect");
		}
		finally
		{
			sparseFile.delete();
		}
	}

	private static Map<String,Double> values(double alpha, double beta, double gamma)
	{
		Map<String,Double> values = new LinkedHashMap<String,Double>();
		values.put("alpha", alpha);
		values.put("beta", beta);
		values.put("gamma", gamma);
		return values;
	}

	private static SMO configuredModel()
	{
		SMO model = new SMO();
		model.setC(0.1);
		model.setBuildCalibrationModels(true);
		SvmReproducibility.applyModelSeed(model);
		return model;
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
