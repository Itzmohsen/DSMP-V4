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

	/**
	 * Same output as {@link #writeRow} for the same terms, but looks each of the
	 * row's terms up in {@code termIndex} (term to attribute index, over
	 * {@code attributeCount} attributes) instead of scanning the whole
	 * vocabulary, so a row costs O(nnz log nnz) instead of O(vocabulary).
	 */
	static void writeIndexedRow(
			BufferedWriter writer,
			Map<String,Double> values,
			Map<String,Integer> termIndex,
			int attributeCount,
			String className) throws IOException
	{
		int[] indexes = new int[values.size()];
		double[] weights = new double[values.size()];
		int count = 0;
		for (Map.Entry<String,Double> entry : values.entrySet())
		{
			Integer index = termIndex.get(entry.getKey());
			Double value = entry.getValue();
			if (index == null || value == null || value.doubleValue() == 0.0)
			{
				continue;
			}
			indexes[count] = index.intValue();
			weights[count] = value.doubleValue();
			count++;
		}
		Integer[] order = new Integer[count];
		for (int position = 0; position < count; position++)
		{
			order[position] = Integer.valueOf(position);
		}
		final int[] sortKeys = indexes;
		java.util.Arrays.sort(order, new java.util.Comparator<Integer>()
		{
			public int compare(Integer first, Integer second)
			{
				return Integer.compare(sortKeys[first.intValue()], sortKeys[second.intValue()]);
			}
		});
		writer.write("{");
		for (int position = 0; position < count; position++)
		{
			int entry = order[position].intValue();
			writer.write(Integer.toString(indexes[entry]));
			writer.write(" ");
			writer.write(Double.toString(weights[entry]));
			writer.write(",");
		}
		writer.write(Integer.toString(attributeCount));
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
