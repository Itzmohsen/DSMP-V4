package TwitterGatherDataFollowers.userRyersonU;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;

public final class ScalableSvmWorkerMain
{
	private ScalableSvmWorkerMain()
	{
	}

	public static void main(String[] args)
	{
		if (args.length != 4)
		{
			System.err.println(
					"Usage: ScalableSvmWorkerMain <train.arff> <test.arff> "
					+ "<recommend.arff> <result.bin>");
			System.exit(2);
		}
		try
		{
			ExactSvmWorkerResult result = ScalableSvmModelSupport.trainAndScore(
					new File(args[0]),
					new File(args[1]),
					new File(args[2]));
			try (ObjectOutputStream output = new ObjectOutputStream(
					new FileOutputStream(args[3])))
			{
				output.writeObject(result);
			}
		}
		catch (Throwable failure)
		{
			System.err.println(
					"Scalable SVM worker failed: "
					+ failure.getClass().getSimpleName()
					+ ": " + failure.getMessage());
			failure.printStackTrace(System.err);
			System.exit(1);
		}
	}
}
