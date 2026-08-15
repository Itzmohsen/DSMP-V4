package TwitterGatherDataFollowers.userRyersonU;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class RuntimeRelaunchHelper
{
	private RuntimeRelaunchHelper()
	{
	}

	public static void main(String[] args) throws Exception
	{
		if (args.length < 2)
		{
			throw new IllegalArgumentException(
					"Expected parent process id and relaunch command.");
		}
		long parentPid = Long.parseLong(args[0]);
		waitForParent(parentPid);
		Thread.sleep(500L);

		List<String> command = new ArrayList<String>();
		for (int index = 1; index < args.length; index++)
		{
			command.add(args[index]);
		}
		new ProcessBuilder(command)
				.directory(new File(System.getProperty("user.dir")))
				.inheritIO()
				.start();
	}

	private static void waitForParent(long parentPid) throws InterruptedException
	{
		while (isProcessAlive(parentPid))
		{
			Thread.sleep(200L);
		}
	}

	private static boolean isProcessAlive(long processId)
	{
		try
		{
			Class<?> processHandleClass =
					Class.forName("java.lang.ProcessHandle");
			Method ofMethod =
					processHandleClass.getMethod("of", long.class);
			Object optionalValue = ofMethod.invoke(null, processId);
			if (optionalValue instanceof Optional)
			{
				Optional<?> process = (Optional<?>)optionalValue;
				if (!process.isPresent())
				{
					return false;
				}
				Method isAliveMethod =
						processHandleClass.getMethod("isAlive");
				return ((Boolean)isAliveMethod.invoke(
						process.get())).booleanValue();
			}
		}
		catch (ClassNotFoundException ignored)
		{
			return isProcessAliveLegacy(processId);
		}
		catch (Exception ignored)
		{
			return isProcessAliveLegacy(processId);
		}
		return false;
	}

	private static boolean isProcessAliveLegacy(long processId)
	{
		boolean windows = System.getProperty("os.name", "")
				.toLowerCase().contains("win");
		try
		{
			if (!windows)
			{
				return new ProcessBuilder(
						"/bin/kill",
						"-0",
						Long.toString(processId))
						.start()
						.waitFor() == 0;
			}

			Process process = new ProcessBuilder(
					"cmd",
					"/c",
					"tasklist",
					"/FI",
					"PID eq " + processId,
					"/NH")
					.redirectErrorStream(true)
					.start();
			String processIdText = Long.toString(processId);
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(process.getInputStream())))
			{
				String line;
				while ((line = reader.readLine()) != null)
				{
					if (line.contains(processIdText))
					{
						return true;
					}
				}
			}
			process.waitFor();
		}
		catch (Exception ignored)
		{
			return false;
		}
		return false;
	}
}
