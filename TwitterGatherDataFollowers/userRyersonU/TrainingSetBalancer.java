package TwitterGatherDataFollowers.userRyersonU;

import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.supervised.instance.ClassBalancer;
import weka.filters.supervised.instance.Resample;
import weka.filters.supervised.instance.SpreadSubsample;

/**
 * Optional training-only balancing for exact classifier experiments.
 *
 * The default mode is off so existing simulator runs keep their historical
 * classifier surface unless an experiment explicitly opts in.
 */
final class TrainingSetBalancer
{
	private static final String MODE_PROPERTY =
			"dsmp.training.balance.mode";
	private static final String SEED_PROPERTY =
			"dsmp.training.balance.seed";
	private static final String TARGET_PROPERTY =
			"dsmp.training.balance.target";

	private TrainingSetBalancer()
	{
	}

	static Instances balanceExactSvmTraining(Instances training)
			throws Exception
	{
		String mode = System.getProperty(MODE_PROPERTY, "off").trim()
				.toLowerCase(java.util.Locale.ENGLISH);
		if (mode.length() == 0 || "off".equals(mode)
				|| "none".equals(mode) || "false".equals(mode))
		{
			return training;
		}
		if (training == null || training.numInstances() < 2
				|| training.classIndex() < 0 || training.numClasses() < 2)
		{
			return training;
		}

		Instances source = new Instances(training);
		source.setClassIndex(training.classIndex());
		Instances balanced;
		if ("weka-class-balancer".equals(mode)
				|| "class-balancer".equals(mode)
				|| "classbalancer".equals(mode))
		{
			ClassBalancer filter = new ClassBalancer();
			filter.setInputFormat(source);
			balanced = Filter.useFilter(source, filter);
		}
		else if ("weka-spread".equals(mode)
				|| "spread".equals(mode)
				|| "spreadsubsample".equals(mode)
				|| "weka-spreadsubsample".equals(mode))
		{
			SpreadSubsample filter = new SpreadSubsample();
			filter.setRandomSeed(readInt(SEED_PROPERTY, 1));
			filter.setDistributionSpread(1.0d);
			int target = readInt(TARGET_PROPERTY, 0);
			if (target > 0)
			{
				filter.setMaxCount(target);
			}
			filter.setInputFormat(source);
			balanced = Filter.useFilter(source, filter);
		}
		else if ("weka-resample".equals(mode)
				|| "resample".equals(mode)
				|| "supervised-resample".equals(mode))
		{
			Resample filter = new Resample();
			filter.setRandomSeed(readInt(SEED_PROPERTY, 1));
			filter.setBiasToUniformClass(1.0d);
			int target = readInt(TARGET_PROPERTY, 0);
			if (target > 0 && source.numClasses() > 0)
			{
				double percent = target * source.numClasses() * 100.0d
						/ Math.max(1, source.numInstances());
				filter.setSampleSizePercent(percent);
			}
			else
			{
				filter.setSampleSizePercent(100.0d);
			}
			filter.setInputFormat(source);
			balanced = Filter.useFilter(source, filter);
		}
		else
		{
			throw new IllegalArgumentException(
					"Unsupported exact SVM training balance mode '" + mode
					+ "'. Use off, weka-class-balancer, weka-spread, or weka-resample.");
		}

		if (balanced == null || balanced.numInstances() < 2)
		{
			return training;
		}
		balanced.setClassIndex(training.classIndex());
		return balanced;
	}

	private static int readInt(String property, int fallback)
	{
		try
		{
			return Integer.parseInt(System.getProperty(property,
					Integer.toString(fallback)).trim());
		}
		catch (Exception ignored)
		{
			return fallback;
		}
	}
}
