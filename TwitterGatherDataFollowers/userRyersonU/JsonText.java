package TwitterGatherDataFollowers.userRyersonU;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Minimal JSON and CSV for run records and study reports. Writing: objects
 * from ordered maps and lists, numbers as finite decimals (NaN and infinities
 * become null), strings escaped per RFC 8259, CSV fields quoted per RFC 4180.
 * Reading: a strict RFC 8259 parser (objects become ordered maps, numbers
 * doubles) and an RFC 4180 CSV reader, enough to load run records back.
 */
final class JsonText
{
	private JsonText()
	{
	}

	static String of(Object value)
	{
		StringBuilder builder = new StringBuilder();
		write(builder, value, 0);
		builder.append('\n');
		return builder.toString();
	}

	@SuppressWarnings("unchecked")
	private static void write(StringBuilder out, Object value, int indent)
	{
		if (value == null)
		{
			out.append("null");
		}
		else if (value instanceof String)
		{
			string(out, (String)value);
		}
		else if (value instanceof Boolean)
		{
			out.append(value.toString());
		}
		else if (value instanceof Double || value instanceof Float)
		{
			double number = ((Number)value).doubleValue();
			if (Double.isNaN(number) || Double.isInfinite(number))
			{
				out.append("null");
			}
			else if (number == Math.rint(number) && Math.abs(number) < 1.0e15)
			{
				out.append(String.format(Locale.US, "%.1f", number));
			}
			else
			{
				out.append(String.format(Locale.US, "%.9g", number).trim());
			}
		}
		else if (value instanceof Number)
		{
			out.append(value.toString());
		}
		else if (value instanceof Map)
		{
			Map<Object,Object> map = (Map<Object,Object>)value;
			if (map.isEmpty())
			{
				out.append("{}");
				return;
			}
			out.append("{\n");
			int index = 0;
			for (Map.Entry<Object,Object> entry : map.entrySet())
			{
				pad(out, indent + 1);
				string(out, String.valueOf(entry.getKey()));
				out.append(": ");
				write(out, entry.getValue(), indent + 1);
				out.append(++index < map.size() ? ",\n" : "\n");
			}
			pad(out, indent);
			out.append('}');
		}
		else if (value instanceof List)
		{
			List<Object> list = (List<Object>)value;
			if (list.isEmpty())
			{
				out.append("[]");
				return;
			}
			out.append("[\n");
			for (int index = 0; index < list.size(); index++)
			{
				pad(out, indent + 1);
				write(out, list.get(index), indent + 1);
				out.append(index + 1 < list.size() ? ",\n" : "\n");
			}
			pad(out, indent);
			out.append(']');
		}
		else
		{
			string(out, value.toString());
		}
	}

	private static void pad(StringBuilder out, int indent)
	{
		for (int index = 0; index < indent; index++)
		{
			out.append("  ");
		}
	}

	private static void string(StringBuilder out, String text)
	{
		out.append('"');
		for (int index = 0; index < text.length(); index++)
		{
			char character = text.charAt(index);
			switch (character)
			{
				case '"':
					out.append("\\\"");
					break;
				case '\\':
					out.append("\\\\");
					break;
				case '\n':
					out.append("\\n");
					break;
				case '\r':
					out.append("\\r");
					break;
				case '\t':
					out.append("\\t");
					break;
				default:
					if (character < 0x20)
					{
						out.append(String.format(Locale.US, "\\u%04x", (int)character));
					}
					else
					{
						out.append(character);
					}
					break;
			}
		}
		out.append('"');
	}

	/** A CSV field: quoted when it holds a comma, quote or line break. */
	static String csv(Object value)
	{
		if (value == null)
		{
			return "";
		}
		String text;
		if (value instanceof Double)
		{
			double number = ((Double)value).doubleValue();
			text = Double.isNaN(number) || Double.isInfinite(number) ? ""
					: String.format(Locale.US, "%.6f", number);
		}
		else
		{
			text = value.toString();
		}
		if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0
				|| text.indexOf('\r') >= 0)
		{
			return "\"" + text.replace("\"", "\"\"") + "\"";
		}
		return text;
	}

	/** One CSV line of fields. */
	static String csvLine(Object... fields)
	{
		StringBuilder builder = new StringBuilder();
		for (int index = 0; index < fields.length; index++)
		{
			if (index > 0)
			{
				builder.append(',');
			}
			builder.append(csv(fields[index]));
		}
		return builder.append('\n').toString();
	}

	// ------------------------------------------------------------ reading

	/** Parses JSON text: maps (ordered), lists, strings, doubles, booleans and null. */
	static Object parse(String text)
	{
		Parser parser = new Parser(text);
		parser.space();
		Object value = parser.value();
		parser.space();
		if (parser.position < text.length())
		{
			throw parser.error("unexpected text after the value");
		}
		return value;
	}

	static Object read(File file) throws IOException
	{
		try
		{
			return parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
		}
		catch (IllegalArgumentException malformed)
		{
			throw new IOException(file.getName() + ": " + malformed.getMessage());
		}
	}

	/** The member of a parsed object, following a dotted path ("scheduling.mode"); null when absent. */
	@SuppressWarnings("unchecked")
	static Object path(Object root, String dotted)
	{
		Object current = root;
		for (String part : dotted.split("\\."))
		{
			if (!(current instanceof Map))
			{
				return null;
			}
			current = ((Map<String,Object>)current).get(part);
		}
		return current;
	}

	static String string(Object root, String dotted)
	{
		Object value = path(root, dotted);
		if (value == null)
		{
			return "";
		}
		if (value instanceof Double)
		{
			double number = ((Double)value).doubleValue();
			return number == Math.rint(number) && Math.abs(number) < 1.0e15
					? Long.toString((long)number) : Double.toString(number);
		}
		return value.toString();
	}

	static double number(Object root, String dotted)
	{
		Object value = path(root, dotted);
		if (value instanceof Double)
		{
			return ((Double)value).doubleValue();
		}
		if (value instanceof String)
		{
			return parseDouble((String)value);
		}
		return Double.NaN;
	}

	private static final class Parser
	{
		private final String text;
		private int position;

		Parser(String text)
		{
			this.text = text == null ? "" : text;
		}

		IllegalArgumentException error(String message)
		{
			return new IllegalArgumentException("JSON " + message + " at offset " + position);
		}

		void space()
		{
			while (position < text.length())
			{
				char c = text.charAt(position);
				if (c != ' ' && c != '\t' && c != '\n' && c != '\r')
				{
					return;
				}
				position++;
			}
		}

		Object value()
		{
			if (position >= text.length())
			{
				throw error("value expected");
			}
			char c = text.charAt(position);
			if (c == '{')
			{
				return object();
			}
			if (c == '[')
			{
				return array();
			}
			if (c == '"')
			{
				return string();
			}
			if (text.startsWith("true", position))
			{
				position += 4;
				return Boolean.TRUE;
			}
			if (text.startsWith("false", position))
			{
				position += 5;
				return Boolean.FALSE;
			}
			if (text.startsWith("null", position))
			{
				position += 4;
				return null;
			}
			return number();
		}

		Map<String,Object> object()
		{
			LinkedHashMap<String,Object> map = new LinkedHashMap<String,Object>();
			position++;
			space();
			if (position < text.length() && text.charAt(position) == '}')
			{
				position++;
				return map;
			}
			while (true)
			{
				space();
				if (position >= text.length() || text.charAt(position) != '"')
				{
					throw error("member name expected");
				}
				String key = string();
				space();
				expect(':');
				space();
				map.put(key, value());
				space();
				if (position < text.length() && text.charAt(position) == ',')
				{
					position++;
					continue;
				}
				expect('}');
				return map;
			}
		}

		List<Object> array()
		{
			ArrayList<Object> list = new ArrayList<Object>();
			position++;
			space();
			if (position < text.length() && text.charAt(position) == ']')
			{
				position++;
				return list;
			}
			while (true)
			{
				space();
				list.add(value());
				space();
				if (position < text.length() && text.charAt(position) == ',')
				{
					position++;
					continue;
				}
				expect(']');
				return list;
			}
		}

		String string()
		{
			StringBuilder out = new StringBuilder();
			position++;
			while (position < text.length())
			{
				char c = text.charAt(position++);
				if (c == '"')
				{
					return out.toString();
				}
				if (c != '\\')
				{
					out.append(c);
					continue;
				}
				if (position >= text.length())
				{
					break;
				}
				char escape = text.charAt(position++);
				switch (escape)
				{
					case 'b':
						out.append('\b');
						break;
					case 'f':
						out.append('\f');
						break;
					case 'n':
						out.append('\n');
						break;
					case 'r':
						out.append('\r');
						break;
					case 't':
						out.append('\t');
						break;
					case 'u':
						if (position + 4 > text.length())
						{
							throw error("bad unicode escape");
						}
						out.append((char)Integer.parseInt(text.substring(position, position + 4), 16));
						position += 4;
						break;
					default:
						out.append(escape);
						break;
				}
			}
			throw error("unterminated string");
		}

		Double number()
		{
			int start = position;
			while (position < text.length() && "+-0123456789.eE".indexOf(text.charAt(position)) >= 0)
			{
				position++;
			}
			if (start == position)
			{
				throw error("value expected");
			}
			try
			{
				return Double.valueOf(text.substring(start, position));
			}
			catch (NumberFormatException malformed)
			{
				throw error("bad number");
			}
		}

		void expect(char c)
		{
			if (position >= text.length() || text.charAt(position) != c)
			{
				throw error("'" + c + "' expected");
			}
			position++;
		}
	}

	/** Parses RFC 4180 CSV text into rows of fields (quoted fields may hold commas, quotes and newlines). */
	static List<List<String>> parseCsv(String text)
	{
		ArrayList<List<String>> rows = new ArrayList<List<String>>();
		ArrayList<String> row = new ArrayList<String>();
		StringBuilder field = new StringBuilder();
		boolean quoted = false;
		boolean any = false;
		for (int index = 0; index < text.length(); index++)
		{
			char c = text.charAt(index);
			if (quoted)
			{
				if (c == '"')
				{
					if (index + 1 < text.length() && text.charAt(index + 1) == '"')
					{
						field.append('"');
						index++;
					}
					else
					{
						quoted = false;
					}
				}
				else
				{
					field.append(c);
				}
				continue;
			}
			if (c == '"')
			{
				quoted = true;
				any = true;
			}
			else if (c == ',')
			{
				row.add(field.toString());
				field.setLength(0);
				any = true;
			}
			else if (c == '\n' || c == '\r')
			{
				if (c == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n')
				{
					index++;
				}
				if (any || field.length() > 0)
				{
					row.add(field.toString());
					rows.add(row);
				}
				row = new ArrayList<String>();
				field.setLength(0);
				any = false;
			}
			else
			{
				field.append(c);
				any = true;
			}
		}
		if (any || field.length() > 0)
		{
			row.add(field.toString());
			rows.add(row);
		}
		return rows;
	}

	/**
	 * A CSV file with a header line as one map per row (column to field, in
	 * column order); an absent file gives no rows.
	 */
	static List<Map<String,String>> readCsv(File file) throws IOException
	{
		ArrayList<Map<String,String>> table = new ArrayList<Map<String,String>>();
		if (!file.isFile())
		{
			return table;
		}
		List<List<String>> rows = parseCsv(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
		if (rows.isEmpty())
		{
			return table;
		}
		List<String> header = rows.get(0);
		for (int index = 1; index < rows.size(); index++)
		{
			List<String> row = rows.get(index);
			LinkedHashMap<String,String> map = new LinkedHashMap<String,String>();
			for (int column = 0; column < header.size(); column++)
			{
				map.put(header.get(column), column < row.size() ? row.get(column) : "");
			}
			table.add(map);
		}
		return table;
	}

	/** A CSV or JSON number; NaN for empty or malformed text. */
	static double parseDouble(String text)
	{
		if (text == null || text.trim().length() == 0)
		{
			return Double.NaN;
		}
		try
		{
			return Double.parseDouble(text.trim());
		}
		catch (NumberFormatException malformed)
		{
			return Double.NaN;
		}
	}

	static int parseInt(String text, int fallback)
	{
		double value = parseDouble(text);
		return Double.isNaN(value) ? fallback : (int)Math.round(value);
	}

	static long parseLong(String text, long fallback)
	{
		if (text == null || text.trim().length() == 0)
		{
			return fallback;
		}
		try
		{
			return Long.parseLong(text.trim());
		}
		catch (NumberFormatException malformed)
		{
			double value = parseDouble(text);
			return Double.isNaN(value) ? fallback : Math.round(value);
		}
	}
}
