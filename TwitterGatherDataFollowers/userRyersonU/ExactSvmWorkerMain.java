package TwitterGatherDataFollowers.userRyersonU;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;

/**
 * Child-JVM entry point for the unchanged legacy Weka SMO calculation.
 */
public final class ExactSvmWorkerMain
{
	private ExactSvmWorkerMain()
	{
	}

	public static void main(String[] args)
	{
		if (args.length != 4)
		{
			System.err.println(
					"Usage: ExactSvmWorkerMain <train.arff> <test.arff> "
					+ "<recommend.arff> <result.bin>");
			System.exit(2);
		}

		try
		{
			long cpuStart = NodeEmulator.threadCpuNanos();
			ExactSvmWorkerResult result = ExactSvmModelSupport.trainAndScore(
					new File(args[0]),
					new File(args[1]),
					new File(args[2]));
			//Reported so the parent can emulate this work at its node's speed.
			result.setWorkerCpuNanos(NodeEmulator.isCpuTimeMeasured()
					? Math.max(0L, NodeEmulator.threadCpuNanos() - cpuStart) : -1L);
			try (ObjectOutputStream output = new ObjectOutputStream(
					new FileOutputStream(args[3])))
			{
				output.writeObject(result);
			}
		}
		catch (Throwable failure)
		{
			System.err.println(
					"Exact SVM worker failed: "
					+ failure.getClass().getSimpleName()
					+ ": " + failure.getMessage());
			failure.printStackTrace(System.err);
			System.exit(1);
		}
	}
}
