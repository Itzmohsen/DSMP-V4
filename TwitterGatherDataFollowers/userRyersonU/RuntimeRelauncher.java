package TwitterGatherDataFollowers.userRyersonU;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

final class RuntimeRelauncher
{
	private static final int DEFAULT_RESTART_EXIT_CODE = 42;

	private RuntimeRelauncher()
	{
	}

	static boolean isManagedByLauncher()
	{
		return Boolean.parseBoolean(
				System.getProperty("dsmp.launcher.managed", "false"));
	}

	static int restartExitCode()
	{
		String value = System.getProperty("dsmp.restart.exitCode");
		if (value == null || value.trim().length() == 0)
		{
			return DEFAULT_RESTART_EXIT_CODE;
		}
		try
		{
			return Integer.parseInt(value.trim());
		}
		catch (NumberFormatException exception)
		{
			return DEFAULT_RESTART_EXIT_CODE;
		}
	}

	static void relaunchWithHeap(int heapMb) throws IOException
	{
		List<String> targetCommand = buildBootstrapCommand();
		if (targetCommand == null)
		{
			targetCommand = buildTargetCommand(
					heapMb,
					ManagementFactory.getRuntimeMXBean().getInputArguments(),
					System.getProperty("java.class.path"),
					System.getProperty("sun.java.command"));
		}
		long parentPid = currentProcessId();
		String javaExecutable = javaExecutable();

		List<String> helperCommand = new ArrayList<String>();
		helperCommand.add(javaExecutable);
		helperCommand.add("-cp");
		helperCommand.add(System.getProperty("java.class.path"));
		helperCommand.add(RuntimeRelaunchHelper.class.getName());
		helperCommand.add(Long.toString(parentPid));
		helperCommand.addAll(targetCommand);

		new ProcessBuilder(helperCommand)
				.directory(new File(System.getProperty("user.dir")))
				.inheritIO()
				.start();
	}

	private static List<String> buildBootstrapCommand()
	{
		File projectRoot = new File(System.getProperty("user.dir"));
		File buildScript = new File(projectRoot, "build.py");
		if (!buildScript.isFile())
		{
			return null;
		}

		String python = System.getProperty("dsmp.bootstrap.python");
		if (python == null || python.trim().length() == 0)
		{
			python = System.getProperty("recommender.python.command");
		}
		if (python == null || python.trim().length() == 0)
		{
			return null;
		}

		List<String> command = new ArrayList<String>();
		command.add(python.trim());
		command.add(buildScript.getAbsolutePath());
		command.add("launch");
		return command;
	}

	static List<String> buildTargetCommand(
			int heapMb,
			List<String> currentJvmArguments,
			String classPath,
			String sunJavaCommand)
	{
		if (sunJavaCommand == null || sunJavaCommand.trim().length() == 0)
		{
			throw new IllegalStateException(
					"The simulator launch command is unavailable.");
		}

		List<String> applicationCommand = tokenizeCommand(sunJavaCommand);
		if (applicationCommand.isEmpty())
		{
			throw new IllegalStateException(
					"The simulator launch command is unavailable.");
		}

		List<String> command = new ArrayList<String>();
		command.add(javaExecutable());
		if (currentJvmArguments != null)
		{
			for (String argument : currentJvmArguments)
			{
				if (!isHeapArgument(argument))
				{
					command.add(argument);
				}
			}
		}
		command.add("-Xms64m");
		command.add("-Xmx" + heapMb + "m");

		String mainEntry = applicationCommand.get(0);
		if (mainEntry.endsWith(".jar"))
		{
			command.add("-jar");
			command.add(mainEntry);
		}
		else
		{
			command.add("-cp");
			command.add(classPath);
			command.add(mainEntry);
		}
		command.addAll(applicationCommand.subList(1, applicationCommand.size()));
		return command;
	}

	static List<String> tokenizeCommand(String command)
	{
		List<String> tokens = new ArrayList<String>();
		StringBuilder token = new StringBuilder();
		boolean quoted = false;
		char quoteCharacter = 0;
		for (int index = 0; index < command.length(); index++)
		{
			char character = command.charAt(index);
			if (quoted)
			{
				if (character == quoteCharacter)
				{
					quoted = false;
				}
				else if (character == '\\' && index + 1 < command.length())
				{
					token.append(command.charAt(++index));
				}
				else
				{
					token.append(character);
				}
			}
			else if (character == '"' || character == '\'')
			{
				quoted = true;
				quoteCharacter = character;
			}
			else if (Character.isWhitespace(character))
			{
				if (token.length() > 0)
				{
					tokens.add(token.toString());
					token.setLength(0);
				}
			}
			else
			{
				token.append(character);
			}
		}
		if (quoted)
		{
			throw new IllegalArgumentException("Unclosed quote in launch command.");
		}
		if (token.length() > 0)
		{
			tokens.add(token.toString());
		}
		return tokens;
	}

	private static boolean isHeapArgument(String argument)
	{
		return argument != null
				&& (argument.startsWith("-Xmx") || argument.startsWith("-Xms"));
	}

	private static long currentProcessId()
	{
		String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
		int separator = runtimeName.indexOf('@');
		if (separator > 0)
		{
			try
			{
				return Long.parseLong(runtimeName.substring(0, separator));
			}
			catch (NumberFormatException ignored)
			{
				// Report a clear error below if this JVM uses an unknown id format.
			}
		}
		throw new IllegalStateException(
				"Could not determine the current Java process id.");
	}

	private static String javaExecutable()
	{
		String executableName = System.getProperty("os.name", "")
				.toLowerCase().contains("win") ? "java.exe" : "java";
		return new File(
				new File(System.getProperty("java.home"), "bin"),
				executableName).getAbsolutePath();
	}
}
