package TwitterGatherDataFollowers.userRyersonU;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Small, process-safe result returned by an isolated exact-SVM worker.
 */
final class ExactSvmWorkerResult implements Serializable
{
	private static final long serialVersionUID = 1L;

	private final ArrayList<String> classNames;
	private final ArrayList<String> predictedClasses;
	private final ArrayList<double[]> distributions;
	private final double testAccuracyPercent;
	private final String confusionMatrix;

	ExactSvmWorkerResult(
			List<String> classNames,
			List<String> predictedClasses,
			List<double[]> distributions,
			double testAccuracyPercent,
			String confusionMatrix)
	{
		this.classNames = new ArrayList<String>(classNames);
		this.predictedClasses = new ArrayList<String>(predictedClasses);
		this.distributions = new ArrayList<double[]>();
		for (double[] distribution : distributions)
		{
			this.distributions.add(distribution == null
					? null : Arrays.copyOf(distribution, distribution.length));
		}
		this.testAccuracyPercent = testAccuracyPercent;
		this.confusionMatrix = confusionMatrix;
	}

	List<String> getClassNames()
	{
		return Collections.unmodifiableList(classNames);
	}

	List<String> getPredictedClasses()
	{
		return Collections.unmodifiableList(predictedClasses);
	}

	List<double[]> getDistributions()
	{
		ArrayList<double[]> copy = new ArrayList<double[]>();
		for (double[] distribution : distributions)
		{
			copy.add(distribution == null
					? null : Arrays.copyOf(distribution, distribution.length));
		}
		return copy;
	}

	double getTestAccuracyPercent()
	{
		return testAccuracyPercent;
	}

	String getConfusionMatrix()
	{
		return confusionMatrix;
	}
}
