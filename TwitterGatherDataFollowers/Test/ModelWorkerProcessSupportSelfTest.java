package TwitterGatherDataFollowers.userRyersonU;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class ModelWorkerProcessSupportSelfTest
{
	private ModelWorkerProcessSupportSelfTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		if (args.length == 1 && "child".equals(args[0]))
		{
			System.out.println("worker-output-ok");
			return;
		}
		if (args.length == 1 && "sleep".equals(args[0]))
		{
			Thread.sleep(10000L);
			return;
		}

		ModelWorkerProcessSupport.Result success =
				ModelWorkerProcessSupport.run(command("child"), 10L);
		require(success.getExitCode() == 0 && !success.isTimedOut(),
				"successful worker was not reported correctly");
		require(success.getOutput().contains("worker-output-ok"),
				"worker output was not drained");

		ModelWorkerProcessSupport.Result timeout =
				ModelWorkerProcessSupport.run(command("sleep"), 1L);
		require(timeout.isTimedOut(),
				"hung worker was not contained by the timeout");
		System.out.println("ModelWorkerProcessSupportSelfTest passed");
	}

	private static List<String> command(String argument)
	{
		ArrayList<String> command = new ArrayList<String>();
		command.add(System.getProperty("java.home") + File.separator
				+ "bin" + File.separator + "java");
		command.add("-cp");
		command.add(System.getProperty("java.class.path"));
		command.add(ModelWorkerProcessSupportSelfTest.class.getName());
		command.add(argument);
		return command;
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
