package TwitterGatherDataFollowers.userRyersonU;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Keeps an Exact-SVM node's normalized corpus off-heap until the node receives
 * permission to materialize its feature maps.
 */
final class ExactSvmCorpusSpool implements Closeable
{
	private static final int BUFFER_BYTES = 256 * 1024;
	private static final int MAX_FIELD_BYTES = 16 * 1024 * 1024;

	private final File file;
	private DataOutputStream output;
	private int recordCount;
	private long normalizedTextBytes;
	private boolean writingFinished;
	private boolean closed;

	ExactSvmCorpusSpool() throws IOException
	{
		file = Files.createTempFile("dsmp-exact-svm-corpus-", ".bin").toFile();
		file.deleteOnExit();
		output = new DataOutputStream(new BufferedOutputStream(
				new FileOutputStream(file), BUFFER_BYTES));
	}

	void append(String user, String normalizedText) throws IOException
	{
		if (writingFinished || closed)
		{
			throw new IOException("Exact-SVM corpus spool is not writable.");
		}
		byte[] userBytes = encode(user);
		byte[] textBytes = encode(normalizedText);
		output.writeInt(userBytes.length);
		output.write(userBytes);
		output.writeInt(textBytes.length);
		output.write(textBytes);
		recordCount++;
		normalizedTextBytes += textBytes.length;
	}

	void finishWriting() throws IOException
	{
		if (writingFinished)
		{
			return;
		}
		writingFinished = true;
		if (output != null)
		{
			output.close();
			output = null;
		}
	}

	void replay(RecordConsumer consumer) throws IOException
	{
		finishWriting();
		try (DataInputStream input = new DataInputStream(new BufferedInputStream(
				new FileInputStream(file), BUFFER_BYTES)))
		{
			for (int index = 0; index < recordCount; index++)
			{
				String user = readField(input);
				String normalizedText = readField(input);
				consumer.accept(user, normalizedText);
			}
			if (input.read() >= 0)
			{
				throw new IOException(
						"Exact-SVM corpus spool contains unexpected trailing data.");
			}
		}
		catch (EOFException exception)
		{
			throw new IOException(
					"Exact-SVM corpus spool ended before every record was replayed.",
					exception);
		}
	}

	int size()
	{
		return recordCount;
	}

	long normalizedTextBytes()
	{
		return normalizedTextBytes;
	}

	public void close() throws IOException
	{
		if (closed)
		{
			return;
		}
		closed = true;
		IOException failure = null;
		if (output != null)
		{
			try
			{
				output.close();
			}
			catch (IOException exception)
			{
				failure = exception;
			}
			output = null;
		}
		try
		{
			Files.deleteIfExists(file.toPath());
		}
		catch (IOException exception)
		{
			failure = exception;
		}
		if (failure != null)
		{
			throw failure;
		}
	}

	private static byte[] encode(String value) throws IOException
	{
		byte[] encoded = (value == null ? "" : value)
				.getBytes(StandardCharsets.UTF_8);
		if (encoded.length > MAX_FIELD_BYTES)
		{
			throw new IOException(
					"Exact-SVM corpus field exceeds the supported size: "
					+ encoded.length + " bytes.");
		}
		return encoded;
	}

	private static String readField(DataInputStream input) throws IOException
	{
		int length = input.readInt();
		if (length < 0 || length > MAX_FIELD_BYTES)
		{
			throw new IOException(
					"Invalid Exact-SVM corpus field length: " + length);
		}
		byte[] encoded = new byte[length];
		input.readFully(encoded);
		return new String(encoded, StandardCharsets.UTF_8);
	}

	interface RecordConsumer
	{
		void accept(String user, String normalizedText) throws IOException;
	}
}
