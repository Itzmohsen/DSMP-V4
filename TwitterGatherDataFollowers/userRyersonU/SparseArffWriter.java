package TwitterGatherDataFollowers.userRyersonU;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

final class SparseArffWriter
{
	private SparseArffWriter()
	{
	}

	static void writeHeader(
			BufferedWriter writer,
			String relationName,
			Collection<String> orderedTerms,
			Collection<String> orderedClassNames) throws IOException
	{
		writer.write("@relation " + relationName);
		writer.newLine();
		writer.newLine();

		int attributeIndex = 0;
		for (String ignored : orderedTerms)
		{
			writer.write("@attribute word" + attributeIndex + " numeric");
			writer.newLine();
			attributeIndex++;
		}

		writer.write("@attribute result {");
		boolean first = true;
		for (String className : orderedClassNames)
		{
			if (!first)
			{
				writer.write(",");
			}
			writer.write(formatNominalValue(className));
			first = false;
		}
		writer.write("}");
		writer.newLine();
		writer.newLine();
		writer.write("@data");
		writer.newLine();
	}

	static void writeRow(
			BufferedWriter writer,
			Map<String,Double> values,
			Collection<String> orderedTerms,
			String className) throws IOException
	{
		writer.write("{");
		boolean first = true;
		int attributeIndex = 0;
		Set<String> presentTerms = values.keySet();
		for (String term : orderedTerms)
		{
			if (presentTerms.contains(term))
			{
				Double value = values.get(term);
				if (value != null && value.doubleValue() != 0.0)
				{
					if (!first)
					{
						writer.write(",");
					}
					writer.write(Integer.toString(attributeIndex));
					writer.write(" ");
					writer.write(Double.toString(value.doubleValue()));
					first = false;
				}
			}
			attributeIndex++;
		}

		if (!first)
		{
			writer.write(",");
		}
		writer.write(Integer.toString(attributeIndex));
		writer.write(" ");
		writer.write(formatNominalValue(className));
		writer.write("}");
		writer.newLine();
	}

	static String formatNominalValue(String value)
	{
		if (value == null)
		{
			return "?";
		}
		return "'" + value
				.replace("\\", "\\\\")
				.replace("'", "\\'")
				.replace("\r", " ")
				.replace("\n", " ") + "'";
	}
}
