package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The statistics of node-lab studies, implemented from first principles so
 * every number in a report can be checked:
 *
 * <ul>
 * <li>descriptive statistics of one cell (mean, standard deviation, median,
 *     quartiles, coefficient of variation);</li>
 * <li>Student-t confidence intervals for a mean (the t quantile comes from the
 *     regularized incomplete beta function, not a table);</li>
 * <li>paired comparisons of a policy with a baseline over repetitions that
 *     share their random seeds (common random numbers): mean difference with
 *     its t interval, relative change, effect size d_z, and an exact
 *     sign-flip permutation p-value (Monte Carlo above 20 pairs);</li>
 * <li>Holm's step-down adjustment of a family of p-values.</li>
 * </ul>
 */
final class StudyStatistics
{
	private StudyStatistics()
	{
	}

	/** Descriptive statistics of a sample (NaN values are ignored). */
	static final class Summary
	{
		final int n;
		final double mean;
		final double sd;
		final double median;
		final double q1;
		final double q3;
		final double min;
		final double max;
		final double ciLow;
		final double ciHigh;

		private Summary(int n, double mean, double sd, double median, double q1, double q3,
				double min, double max, double ciLow, double ciHigh)
		{
			this.n = n;
			this.mean = mean;
			this.sd = sd;
			this.median = median;
			this.q1 = q1;
			this.q3 = q3;
			this.min = min;
			this.max = max;
			this.ciLow = ciLow;
			this.ciHigh = ciHigh;
		}

		/** Standard error of the mean. */
		double se()
		{
			return n < 2 ? Double.NaN : sd / Math.sqrt(n);
		}

		/** Coefficient of variation sd / mean. */
		double cv()
		{
			return mean == 0.0 ? Double.NaN : sd / Math.abs(mean);
		}

		/** Half-width of the 95% interval relative to the mean. */
		double relativePrecision()
		{
			return mean == 0.0 || Double.isNaN(ciHigh) ? Double.NaN : (ciHigh - mean) / Math.abs(mean);
		}
	}

	/** A paired comparison of a treatment with a baseline. */
	static final class Comparison
	{
		final int n;
		final double baselineMean;
		final double treatmentMean;
		final double meanDifference;
		final double ciLow;
		final double ciHigh;
		final double relativeChange;
		final double effectSize;
		final double pValue;
		double adjustedPValue = Double.NaN;

		private Comparison(int n, double baselineMean, double treatmentMean, double meanDifference,
				double ciLow, double ciHigh, double relativeChange, double effectSize, double pValue)
		{
			this.n = n;
			this.baselineMean = baselineMean;
			this.treatmentMean = treatmentMean;
			this.meanDifference = meanDifference;
			this.ciLow = ciLow;
			this.ciHigh = ciHigh;
			this.relativeChange = relativeChange;
			this.effectSize = effectSize;
			this.pValue = pValue;
		}

		boolean isSignificant(double alpha)
		{
			double p = Double.isNaN(adjustedPValue) ? pValue : adjustedPValue;
			return !Double.isNaN(p) && p < alpha;
		}
	}

	static Summary summarize(double[] values, double confidence)
	{
		double[] clean = finite(values);
		int n = clean.length;
		if (n == 0)
		{
			return new Summary(0, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
					Double.NaN, Double.NaN, Double.NaN, Double.NaN);
		}
		double mean = mean(clean);
		double sd = n < 2 ? Double.NaN : Math.sqrt(sumSquares(clean, mean) / (n - 1));
		double[] sorted = clean.clone();
		Arrays.sort(sorted);
		double half = n < 2 ? Double.NaN : tQuantile(0.5 + confidence / 2.0, n - 1) * sd / Math.sqrt(n);
		return new Summary(n, mean, sd, quantile(sorted, 0.5), quantile(sorted, 0.25),
				quantile(sorted, 0.75), sorted[0], sorted[n - 1], mean - half, mean + half);
	}

	/**
	 * Compares paired samples (pair i shares its seeds): treatment[i] -
	 * baseline[i]. Pairs with a NaN on either side are dropped.
	 */
	static Comparison paired(double[] baseline, double[] treatment, double confidence, long seed)
	{
		ArrayList<double[]> pairs = new ArrayList<double[]>();
		for (int index = 0; index < Math.min(baseline.length, treatment.length); index++)
		{
			if (isFinite(baseline[index]) && isFinite(treatment[index]))
			{
				pairs.add(new double[] {baseline[index], treatment[index]});
			}
		}
		int n = pairs.size();
		if (n == 0)
		{
			return new Comparison(0, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
					Double.NaN, Double.NaN, Double.NaN);
		}
		double[] differences = new double[n];
		double[] base = new double[n];
		double[] treated = new double[n];
		for (int index = 0; index < n; index++)
		{
			base[index] = pairs.get(index)[0];
			treated[index] = pairs.get(index)[1];
			differences[index] = treated[index] - base[index];
		}
		double baselineMean = mean(base);
		double treatmentMean = mean(treated);
		double meanDifference = mean(differences);
		double sd = n < 2 ? Double.NaN : Math.sqrt(sumSquares(differences, meanDifference) / (n - 1));
		double half = n < 2 ? Double.NaN : tQuantile(0.5 + confidence / 2.0, n - 1) * sd / Math.sqrt(n);
		double relative = baselineMean == 0.0 ? Double.NaN : treatmentMean / baselineMean - 1.0;
		double effect = sd > 0.0 ? meanDifference / sd : (meanDifference == 0.0 ? 0.0 : Double.NaN);
		return new Comparison(n, baselineMean, treatmentMean, meanDifference, meanDifference - half,
				meanDifference + half, relative, effect, signFlipPValue(differences, seed));
	}

	/**
	 * Two-sided sign-flip permutation test of mean difference zero: under the
	 * null each difference is equally likely to have either sign. Exact
	 * (all 2^n sign patterns) for n up to 20, else 20000 seeded Monte Carlo
	 * patterns (with the +1 correction).
	 */
	static double signFlipPValue(double[] differences, long seed)
	{
		int n = differences.length;
		if (n == 0)
		{
			return Double.NaN;
		}
		double observed = Math.abs(sum(differences));
		double tolerance = 1e-12 * Math.max(1.0, observed);
		if (n <= 20)
		{
			long patterns = 1L << n;
			long extreme = 0L;
			for (long pattern = 0L; pattern < patterns; pattern++)
			{
				double total = 0.0;
				for (int index = 0; index < n; index++)
				{
					total += ((pattern >>> index) & 1L) == 0L ? differences[index] : -differences[index];
				}
				if (Math.abs(total) >= observed - tolerance)
				{
					extreme++;
				}
			}
			return extreme / (double)patterns;
		}
		FailureSchedule.SplitMix random = new FailureSchedule.SplitMix(seed);
		int draws = 20000;
		int extreme = 0;
		for (int draw = 0; draw < draws; draw++)
		{
			double total = 0.0;
			for (int index = 0; index < n; index++)
			{
				total += (random.nextLong() & 1L) == 0L ? differences[index] : -differences[index];
			}
			if (Math.abs(total) >= observed - tolerance)
			{
				extreme++;
			}
		}
		return (extreme + 1.0) / (draws + 1.0);
	}

	/** Holm's step-down adjustment; sets {@code adjustedPValue} of every comparison. */
	static void holm(List<Comparison> family)
	{
		ArrayList<Comparison> tested = new ArrayList<Comparison>();
		for (Comparison comparison : family)
		{
			if (!Double.isNaN(comparison.pValue))
			{
				tested.add(comparison);
			}
		}
		Collections.sort(tested, new Comparator<Comparison>()
		{
			public int compare(Comparison first, Comparison second)
			{
				return Double.compare(first.pValue, second.pValue);
			}
		});
		int m = tested.size();
		double running = 0.0;
		for (int index = 0; index < m; index++)
		{
			double adjusted = Math.min(1.0, (m - index) * tested.get(index).pValue);
			running = Math.max(running, adjusted);
			tested.get(index).adjustedPValue = running;
		}
	}

	// ------------------------------------------------------------ distributions

	/** The p-quantile of Student's t with {@code df} degrees of freedom (bisection on the CDF). */
	static double tQuantile(double p, double df)
	{
		if (Double.isNaN(p) || df <= 0.0)
		{
			return Double.NaN;
		}
		if (p == 0.5)
		{
			return 0.0;
		}
		if (p < 0.5)
		{
			return -tQuantile(1.0 - p, df);
		}
		double low = 0.0;
		double high = 1.0;
		while (tCdf(high, df) < p && high < 1.0e8)
		{
			high *= 2.0;
		}
		for (int iteration = 0; iteration < 200; iteration++)
		{
			double middle = (low + high) / 2.0;
			if (tCdf(middle, df) < p)
			{
				low = middle;
			}
			else
			{
				high = middle;
			}
			if (high - low < 1e-12 * Math.max(1.0, high))
			{
				break;
			}
		}
		return (low + high) / 2.0;
	}

	/** Student's t CDF: 1 - I_{df/(df+t^2)}(df/2, 1/2) / 2 for t &gt;= 0. */
	static double tCdf(double t, double df)
	{
		double x = df / (df + t * t);
		double tail = 0.5 * regularizedBeta(x, df / 2.0, 0.5);
		return t >= 0.0 ? 1.0 - tail : tail;
	}

	/** Regularized incomplete beta I_x(a, b) by the continued fraction (Lentz). */
	static double regularizedBeta(double x, double a, double b)
	{
		if (x <= 0.0)
		{
			return 0.0;
		}
		if (x >= 1.0)
		{
			return 1.0;
		}
		double front = Math.exp(logGamma(a + b) - logGamma(a) - logGamma(b)
				+ a * Math.log(x) + b * Math.log(1.0 - x));
		if (x < (a + 1.0) / (a + b + 2.0))
		{
			return front * betaFraction(x, a, b) / a;
		}
		return 1.0 - front * betaFraction(1.0 - x, b, a) / b;
	}

	private static double betaFraction(double x, double a, double b)
	{
		double tiny = 1e-300;
		double qab = a + b;
		double qap = a + 1.0;
		double qam = a - 1.0;
		double c = 1.0;
		double d = 1.0 - qab * x / qap;
		if (Math.abs(d) < tiny)
		{
			d = tiny;
		}
		d = 1.0 / d;
		double h = d;
		for (int m = 1; m <= 500; m++)
		{
			int m2 = 2 * m;
			double aa = m * (b - m) * x / ((qam + m2) * (a + m2));
			d = 1.0 + aa * d;
			if (Math.abs(d) < tiny)
			{
				d = tiny;
			}
			c = 1.0 + aa / c;
			if (Math.abs(c) < tiny)
			{
				c = tiny;
			}
			d = 1.0 / d;
			h *= d * c;
			aa = -(a + m) * (qab + m) * x / ((a + m2) * (qap + m2));
			d = 1.0 + aa * d;
			if (Math.abs(d) < tiny)
			{
				d = tiny;
			}
			c = 1.0 + aa / c;
			if (Math.abs(c) < tiny)
			{
				c = tiny;
			}
			d = 1.0 / d;
			double delta = d * c;
			h *= delta;
			if (Math.abs(delta - 1.0) < 1e-15)
			{
				break;
			}
		}
		return h;
	}

	/** log Gamma(x) for x &gt; 0 (Lanczos in log form, so large arguments do not overflow). */
	static double logGamma(double x)
	{
		if (x < 0.5)
		{
			return Math.log(Math.PI / Math.abs(Math.sin(Math.PI * x))) - logGamma(1.0 - x);
		}
		double[] g = {
			0.99999999999980993, 676.5203681218851, -1259.1392167224028,
			771.32342877765313, -176.61502916214059, 12.507343278686905,
			-0.13857109526572012, 9.9843695780195716e-6, 1.5056327351493116e-7
		};
		double z = x - 1.0;
		double a = g[0];
		double t = z + 7.5;
		for (int index = 1; index < g.length; index++)
		{
			a += g[index] / (z + index);
		}
		return 0.5 * Math.log(2.0 * Math.PI) + (z + 0.5) * Math.log(t) - t + Math.log(a);
	}

	// ------------------------------------------------------------ helpers

	private static double[] finite(double[] values)
	{
		int count = 0;
		for (double value : values)
		{
			if (isFinite(value))
			{
				count++;
			}
		}
		double[] clean = new double[count];
		int index = 0;
		for (double value : values)
		{
			if (isFinite(value))
			{
				clean[index++] = value;
			}
		}
		return clean;
	}

	private static boolean isFinite(double value)
	{
		return !Double.isNaN(value) && !Double.isInfinite(value);
	}

	private static double sum(double[] values)
	{
		ExactSum total = new ExactSum();
		for (double value : values)
		{
			total.add(value);
		}
		return total.toDouble();
	}

	private static double mean(double[] values)
	{
		return values.length == 0 ? Double.NaN : sum(values) / values.length;
	}

	private static double sumSquares(double[] values, double mean)
	{
		ExactSum total = new ExactSum();
		for (double value : values)
		{
			double deviation = value - mean;
			total.add(deviation * deviation);
		}
		return total.toDouble();
	}

	/** Linear-interpolation quantile (type 7) of a sorted sample. */
	static double quantile(double[] sorted, double p)
	{
		if (sorted.length == 0)
		{
			return Double.NaN;
		}
		double position = p * (sorted.length - 1);
		int lower = (int)Math.floor(position);
		int upper = Math.min(sorted.length - 1, lower + 1);
		double fraction = position - lower;
		return sorted[lower] + fraction * (sorted[upper] - sorted[lower]);
	}
}
