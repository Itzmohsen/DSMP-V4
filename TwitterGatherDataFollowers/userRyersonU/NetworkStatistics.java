package TwitterGatherDataFollowers.userRyersonU;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.distribution.TDistribution;

/**
 * The statistics of the run graph, each with its null model.
 *
 * <ul>
 * <li>attribute assortativity of a directed graph (Newman 2003, "Mixing
 * patterns in networks") with a jackknife standard error;</li>
 * <li>agreement of two partitions: mutual information, NMI, the adjusted
 * mutual information of Vinh, Epps and Bailey (2010) with its exact
 * hypergeometric expectation, and the adjusted Rand index of Hubert and
 * Arabie (1985), matching scikit-learn's definitions;</li>
 * <li>exact tails of a sum of independent hypergeometric or Bernoulli
 * variables (random recommendation and random adoption), with a normal
 * approximation only when the exact distribution would be too large;</li>
 * <li>Gini coefficient and Lorenz curve, normalised entropy, skewness and the
 * Spearman rank correlation with its t-test.</li>
 * </ul>
 */
final class NetworkStatistics
{
	/** Beyond this many outcomes a sum's distribution is approximated by a normal one. */
	static final int EXACT_SUPPORT_LIMIT = 40000;

	private NetworkStatistics()
	{
	}

	// ------------------------------------------------------------ assortativity

	/** An assortativity coefficient with its jackknife standard error. */
	static final class Assortativity
	{
		final double r;
		final double standardError;
		final int edges;

		Assortativity(double r, double standardError, int edges)
		{
			this.r = r;
			this.standardError = standardError;
			this.edges = edges;
		}

		/** Two-sided p-value of r against 0 under a normal approximation (NaN without an error). */
		double pValue()
		{
			if (!(standardError > 0.0) || Double.isNaN(r))
			{
				return Double.NaN;
			}
			return 2.0 * new NormalDistribution(null, 0.0, 1.0).cumulativeProbability(-Math.abs(r / standardError));
		}
	}

	/**
	 * Newman's assortativity of directed edges by category:
	 * r = (sum_i e_ii - sum_i a_i b_i) / (1 - sum_i a_i b_i), with e the
	 * fraction of edges from category i to j and a, b its margins. Edges with
	 * a negative category are skipped. The standard error is the jackknife
	 * over edges, ((M - 1) / M) sum (r_(e) - mean)^2, each leave-one-out value
	 * updated in constant time.
	 */
	static Assortativity assortativity(int[] sourceCategory, int[] targetCategory, int categories)
	{
		return assortativity(sourceCategory, targetCategory, null, categories);
	}

	/**
	 * The weighted assortativity: e_ij is the fraction of edge weight from
	 * category i to j (all weights 1 when {@code weights} is null). The
	 * jackknife leaves out one edge, and its weight, at a time.
	 */
	static Assortativity assortativity(int[] sourceCategory, int[] targetCategory, double[] weights, int categories)
	{
		double[] rows = new double[categories];
		double[] columns = new double[categories];
		double trace = 0.0;
		double total = 0.0;
		int m = 0;
		for (int edge = 0; edge < sourceCategory.length; edge++)
		{
			int a = sourceCategory[edge];
			int b = targetCategory[edge];
			double w = weights == null ? 1.0 : weights[edge];
			if (a < 0 || b < 0 || !(w > 0.0))
			{
				continue;
			}
			rows[a] += w;
			columns[b] += w;
			trace += a == b ? w : 0.0;
			total += w;
			m++;
		}
		if (m < 2)
		{
			return new Assortativity(Double.NaN, Double.NaN, m);
		}
		double margins = 0.0;
		for (int category = 0; category < categories; category++)
		{
			margins += rows[category] * columns[category];
		}
		double r = coefficient(trace, margins, total);
		double[] leaveOut = new double[m];
		int index = 0;
		double sum = 0.0;
		for (int edge = 0; edge < sourceCategory.length; edge++)
		{
			int a = sourceCategory[edge];
			int b = targetCategory[edge];
			double w = weights == null ? 1.0 : weights[edge];
			if (a < 0 || b < 0 || !(w > 0.0))
			{
				continue;
			}
			double reduced;
			if (a == b)
			{
				reduced = margins - rows[a] * columns[a] + (rows[a] - w) * (columns[a] - w);
			}
			else
			{
				reduced = margins - rows[a] * columns[a] - rows[b] * columns[b]
						+ (rows[a] - w) * columns[a] + rows[b] * (columns[b] - w);
			}
			leaveOut[index] = coefficient(trace - (a == b ? w : 0.0), reduced, total - w);
			sum += leaveOut[index];
			index++;
		}
		double mean = sum / m;
		double squares = 0.0;
		for (double value : leaveOut)
		{
			squares += (value - mean) * (value - mean);
		}
		double error = Math.sqrt((m - 1.0) / m * squares);
		return new Assortativity(r, Double.isNaN(error) ? Double.NaN : error, m);
	}

	private static double coefficient(double trace, double margins, double total)
	{
		if (!(total > 0.0))
		{
			return Double.NaN;
		}
		double expected = margins / (total * total);
		double observed = trace / total;
		return expected >= 1.0 - 1e-15 ? Double.NaN : (observed - expected) / (1.0 - expected);
	}

	// ------------------------------------------------------------ partitions

	/** The agreement of two partitions of the same items. */
	static final class Agreement
	{
		final double mutualInformation;
		final double nmi;
		final double ami;
		final double ari;
		final int items;

		Agreement(double mutualInformation, double nmi, double ami, double ari, int items)
		{
			this.mutualInformation = mutualInformation;
			this.nmi = nmi;
			this.ami = ami;
			this.ari = ari;
			this.items = items;
		}
	}

	/**
	 * Agreement of partitions {@code a} and {@code b} (labels of the same
	 * items; items with a negative label in either are left out), as
	 * scikit-learn defines NMI and AMI (arithmetic-mean normaliser) and ARI.
	 */
	static Agreement agreement(int[] a, int[] b)
	{
		int[] left = relabel(a, b);
		int[] right = relabel(b, a);
		int n = left.length;
		if (n == 0)
		{
			return new Agreement(Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0);
		}
		int rows = NetworkAlgorithms.groupCount(left);
		int columns = NetworkAlgorithms.groupCount(right);
		HashMap<Long,Integer> cells = new HashMap<Long,Integer>();
		long[] rowSums = new long[rows];
		long[] columnSums = new long[columns];
		for (int item = 0; item < n; item++)
		{
			Long key = Long.valueOf((long)left[item] * columns + right[item]);
			Integer old = cells.get(key);
			cells.put(key, Integer.valueOf(old == null ? 1 : old.intValue() + 1));
			rowSums[left[item]]++;
			columnSums[right[item]]++;
		}
		double mi = 0.0;
		for (Map.Entry<Long,Integer> cell : cells.entrySet())
		{
			int row = (int)(cell.getKey().longValue() / columns);
			int column = (int)(cell.getKey().longValue() % columns);
			double count = cell.getValue().intValue();
			mi += count / n * Math.log(count * n / ((double)rowSums[row] * columnSums[column]));
		}
		mi = Math.max(0.0, mi);
		double ari = adjustedRand(cells, rowSums, columnSums, n);
		if ((rows == 1 && columns == 1) || (rows == 0 && columns == 0))
		{
			return new Agreement(mi, 1.0, 1.0, ari, n);
		}
		double hLeft = entropy(rowSums, n);
		double hRight = entropy(columnSums, n);
		double normaliser = (hLeft + hRight) / 2.0;
		double nmi = mi == 0.0 ? 0.0 : mi / normaliser;
		double emi = expectedMutualInformation(rowSums, columnSums, n);
		double denominator = normaliser - emi;
		if (denominator < 0.0)
		{
			denominator = Math.min(denominator, -Math.ulp(1.0));
		}
		else
		{
			denominator = Math.max(denominator, Math.ulp(1.0));
		}
		return new Agreement(mi, nmi, (mi - emi) / denominator, ari, n);
	}

	/** {@code labels} restricted to the items labelled in both arrays. */
	private static int[] relabel(int[] labels, int[] other)
	{
		int count = 0;
		for (int item = 0; item < labels.length; item++)
		{
			count += labels[item] >= 0 && other[item] >= 0 ? 1 : 0;
		}
		int[] kept = new int[count];
		HashMap<Integer,Integer> names = new HashMap<Integer,Integer>();
		int cursor = 0;
		for (int item = 0; item < labels.length; item++)
		{
			if (labels[item] >= 0 && other[item] >= 0)
			{
				Integer name = names.get(Integer.valueOf(labels[item]));
				if (name == null)
				{
					name = Integer.valueOf(names.size());
					names.put(Integer.valueOf(labels[item]), name);
				}
				kept[cursor++] = name.intValue();
			}
		}
		return kept;
	}

	private static double entropy(long[] sums, int n)
	{
		double h = 0.0;
		for (long sum : sums)
		{
			if (sum > 0)
			{
				double p = sum / (double)n;
				h -= p * Math.log(p);
			}
		}
		return h;
	}

	/** The adjusted Rand index from the pair confusion matrix (1 when the partitions agree on every pair). */
	private static double adjustedRand(Map<Long,Integer> cells, long[] rowSums, long[] columnSums, int n)
	{
		double sumCells = 0.0;
		for (Integer count : cells.values())
		{
			sumCells += (double)count.intValue() * count.intValue();
		}
		double sumRows = 0.0;
		for (long sum : rowSums)
		{
			sumRows += (double)sum * sum;
		}
		double sumColumns = 0.0;
		for (long sum : columnSums)
		{
			sumColumns += (double)sum * sum;
		}
		double total = (double)n * n;
		double tp = sumCells - n;
		double fp = sumColumns - sumCells;
		double fn = sumRows - sumCells;
		double tn = total - fp - fn - sumCells;
		if (fn == 0.0 && fp == 0.0)
		{
			return 1.0;
		}
		return 2.0 * (tp * tn - fn * fp) / ((tp + fn) * (fn + tn) + (tp + fp) * (fp + tn));
	}

	/**
	 * The expected mutual information of two random partitions with the
	 * given cluster sizes (the hypergeometric model of Vinh et al. 2010).
	 */
	static double expectedMutualInformation(long[] rowSums, long[] columnSums, int n)
	{
		double[] logFactorial = new double[n + 1];
		for (int value = 2; value <= n; value++)
		{
			logFactorial[value] = logFactorial[value - 1] + Math.log(value);
		}
		double emi = 0.0;
		for (long a : rowSums)
		{
			if (a == 0)
			{
				continue;
			}
			for (long b : columnSums)
			{
				if (b == 0)
				{
					continue;
				}
				long start = Math.max(1, a + b - n);
				long end = Math.min(a, b);
				double constant = logFactorial[(int)a] + logFactorial[(int)b] + logFactorial[(int)(n - a)]
						+ logFactorial[(int)(n - b)] - logFactorial[n];
				for (long nij = start; nij <= end; nij++)
				{
					double term = nij / (double)n * Math.log(n * (double)nij / ((double)a * b));
					double probability = constant - logFactorial[(int)nij] - logFactorial[(int)(a - nij)]
							- logFactorial[(int)(b - nij)] - logFactorial[(int)(n - a - b + nij)];
					emi += term * Math.exp(probability);
				}
			}
		}
		return emi;
	}

	// ------------------------------------------------------------ exact sums

	/** A distribution of counts 0..n, with its mean and variance. */
	static final class CountDistribution
	{
		/** Probabilities of 0, 1, ... (null when approximated). */
		final double[] pmf;
		final double mean;
		final double variance;

		CountDistribution(double[] pmf, double mean, double variance)
		{
			this.pmf = pmf;
			this.mean = mean;
			this.variance = variance;
		}

		boolean exact()
		{
			return pmf != null;
		}

		/** P(X <= x). */
		double lower(long x)
		{
			if (pmf == null)
			{
				return normal(x + 0.5);
			}
			double sum = 0.0;
			for (int value = 0; value <= Math.min(x, pmf.length - 1); value++)
			{
				sum += pmf[value];
			}
			return Math.min(1.0, sum);
		}

		/** P(X >= x). */
		double upper(long x)
		{
			if (pmf == null)
			{
				return 1.0 - normal(x - 0.5);
			}
			double sum = 0.0;
			for (int value = (int)Math.max(0, x); value < pmf.length; value++)
			{
				sum += pmf[value];
			}
			return Math.min(1.0, sum);
		}

		/** Two-sided p-value: twice the smaller tail, at most 1. */
		double twoSided(long x)
		{
			return Math.min(1.0, 2.0 * Math.min(lower(x), upper(x)));
		}

		private double normal(double x)
		{
			if (variance <= 0.0)
			{
				return x >= mean ? 1.0 : 0.0;
			}
			return new NormalDistribution(null, mean, Math.sqrt(variance)).cumulativeProbability(x);
		}
	}

	/**
	 * The distribution of a sum of independent hypergeometric counts: for
	 * each part, the number of successes in {@code draws[i]} draws without
	 * replacement from {@code population[i]} items of which
	 * {@code successes[i]} are successes.
	 */
	static CountDistribution hypergeometricSum(int[] population, int[] successes, int[] draws)
	{
		double mean = 0.0;
		double variance = 0.0;
		long support = 1;
		for (int part = 0; part < population.length; part++)
		{
			int total = population[part];
			int k = Math.min(draws[part], total);
			if (total <= 0 || k <= 0)
			{
				continue;
			}
			double p = successes[part] / (double)total;
			mean += k * p;
			variance += total > 1 ? k * p * (1.0 - p) * (total - k) / (total - 1.0) : 0.0;
			support += Math.min(k, successes[part]);
		}
		if (support > EXACT_SUPPORT_LIMIT)
		{
			return new CountDistribution(null, mean, variance);
		}
		double[] pmf = {1.0};
		for (int part = 0; part < population.length; part++)
		{
			int total = population[part];
			int k = Math.min(draws[part], total);
			if (total <= 0 || k <= 0)
			{
				continue;
			}
			pmf = convolve(pmf, hypergeometric(total, Math.min(successes[part], total), k));
		}
		return new CountDistribution(pmf, mean, variance);
	}

	/** P(X = x) for x = 0..min(k, K) successes in k draws from N items with K successes. */
	static double[] hypergeometric(int population, int successes, int draws)
	{
		int top = Math.min(draws, successes);
		double[] pmf = new double[top + 1];
		double logTotal = logChoose(population, draws);
		for (int x = 0; x <= top; x++)
		{
			if (draws - x > population - successes)
			{
				continue;
			}
			pmf[x] = Math.exp(logChoose(successes, x) + logChoose(population - successes, draws - x) - logTotal);
		}
		return pmf;
	}

	/** The distribution of the number of successes of independent trials with probabilities {@code p}. */
	static CountDistribution poissonBinomial(double[] p)
	{
		double mean = 0.0;
		double variance = 0.0;
		for (double value : p)
		{
			mean += value;
			variance += value * (1.0 - value);
		}
		if (p.length + 1 > EXACT_SUPPORT_LIMIT)
		{
			return new CountDistribution(null, mean, variance);
		}
		double[] pmf = new double[p.length + 1];
		pmf[0] = 1.0;
		for (int trial = 0; trial < p.length; trial++)
		{
			for (int count = trial + 1; count >= 1; count--)
			{
				pmf[count] = pmf[count] * (1.0 - p[trial]) + pmf[count - 1] * p[trial];
			}
			pmf[0] *= 1.0 - p[trial];
		}
		return new CountDistribution(pmf, mean, variance);
	}

	static double[] convolve(double[] a, double[] b)
	{
		double[] result = new double[a.length + b.length - 1];
		for (int i = 0; i < a.length; i++)
		{
			if (a[i] == 0.0)
			{
				continue;
			}
			for (int j = 0; j < b.length; j++)
			{
				result[i + j] += a[i] * b[j];
			}
		}
		return result;
	}

	private static double logChoose(int n, int k)
	{
		if (k < 0 || k > n)
		{
			return Double.NEGATIVE_INFINITY;
		}
		return logFactorial(n) - logFactorial(k) - logFactorial(n - k);
	}

	private static double[] logFactorials = new double[1];

	private static synchronized double logFactorial(int n)
	{
		if (n >= logFactorials.length)
		{
			int old = logFactorials.length;
			logFactorials = Arrays.copyOf(logFactorials, Math.max(n + 1, old * 2));
			for (int value = Math.max(1, old); value < logFactorials.length; value++)
			{
				logFactorials[value] = logFactorials[value - 1] + Math.log(value);
			}
		}
		return logFactorials[n];
	}

	// ------------------------------------------------------------ inequality and shape

	/** The Gini coefficient of non-negative values (0 equal, towards 1 concentrated; NaN when all are 0). */
	static double gini(double[] values)
	{
		int n = values.length;
		if (n == 0)
		{
			return Double.NaN;
		}
		double[] sorted = Arrays.copyOf(values, n);
		Arrays.sort(sorted);
		double sum = 0.0;
		double weighted = 0.0;
		for (int index = 0; index < n; index++)
		{
			sum += sorted[index];
			weighted += (index + 1.0) * sorted[index];
		}
		if (sum <= 0.0)
		{
			return Double.NaN;
		}
		return 2.0 * weighted / (n * sum) - (n + 1.0) / n;
	}

	/** The Lorenz curve of non-negative values: {population share, value share} points from (0, 0) to (1, 1). */
	static double[][] lorenz(double[] values)
	{
		int n = values.length;
		double[] sorted = Arrays.copyOf(values, n);
		Arrays.sort(sorted);
		double sum = 0.0;
		for (double value : sorted)
		{
			sum += value;
		}
		double[][] points = new double[n + 1][2];
		double running = 0.0;
		for (int index = 0; index < n; index++)
		{
			running += sorted[index];
			points[index + 1][0] = (index + 1.0) / n;
			points[index + 1][1] = sum <= 0.0 ? (index + 1.0) / n : running / sum;
		}
		return points;
	}

	/** Shannon entropy of a distribution of counts over its maximum, log(number of bins): 1 when uniform. */
	static double normalisedEntropy(double[] counts)
	{
		double sum = 0.0;
		for (double count : counts)
		{
			sum += count;
		}
		if (counts.length < 2 || sum <= 0.0)
		{
			return Double.NaN;
		}
		double h = 0.0;
		for (double count : counts)
		{
			if (count > 0.0)
			{
				double p = count / sum;
				h -= p * Math.log(p);
			}
		}
		return h / Math.log(counts.length);
	}

	/** The skewness E[(X - mean)^3] / sd^3 (population moments; NaN without spread). */
	static double skewness(double[] values)
	{
		int n = values.length;
		if (n < 3)
		{
			return Double.NaN;
		}
		double mean = 0.0;
		for (double value : values)
		{
			mean += value;
		}
		mean /= n;
		double second = 0.0;
		double third = 0.0;
		for (double value : values)
		{
			double d = value - mean;
			second += d * d;
			third += d * d * d;
		}
		second /= n;
		third /= n;
		return second <= 0.0 ? Double.NaN : third / Math.pow(second, 1.5);
	}

	/** A rank correlation with its two-sided p-value and sample size. */
	static final class Correlation
	{
		final double rho;
		final double pValue;
		final int n;

		Correlation(double rho, double pValue, int n)
		{
			this.rho = rho;
			this.pValue = pValue;
			this.n = n;
		}
	}

	/** Spearman's rho (average ranks for ties) and the t-test p-value with n - 2 degrees of freedom, as SciPy computes. */
	static Correlation spearman(double[] x, double[] y)
	{
		int n = x.length;
		if (n < 3)
		{
			return new Correlation(Double.NaN, Double.NaN, n);
		}
		double rho = pearson(ranks(x), ranks(y));
		if (Double.isNaN(rho))
		{
			return new Correlation(Double.NaN, Double.NaN, n);
		}
		double p;
		if (Math.abs(rho) >= 1.0)
		{
			p = 0.0;
		}
		else
		{
			double t = rho * Math.sqrt((n - 2.0) / (1.0 - rho * rho));
			p = 2.0 * new TDistribution(null, n - 2.0).cumulativeProbability(-Math.abs(t));
		}
		return new Correlation(rho, Math.min(1.0, p), n);
	}

	/**
	 * Average ranks (1-based). Values within a relative 1e-12 of each other
	 * tie: sums of the same weights in another order can differ in their last
	 * bit, and must still rank as equal.
	 */
	static double[] ranks(double[] values)
	{
		int n = values.length;
		Integer[] order = new Integer[n];
		for (int index = 0; index < n; index++)
		{
			order[index] = Integer.valueOf(index);
		}
		final double[] keys = values;
		Arrays.sort(order, new java.util.Comparator<Integer>()
		{
			public int compare(Integer left, Integer right)
			{
				return Double.compare(keys[left.intValue()], keys[right.intValue()]);
			}
		});
		double[] ranks = new double[n];
		int start = 0;
		while (start < n)
		{
			int end = start;
			double first = values[order[start].intValue()];
			while (end + 1 < n && values[order[end + 1].intValue()] - first
					<= 1e-12 * Math.max(1.0, Math.abs(first)))
			{
				end++;
			}
			double rank = (start + end) / 2.0 + 1.0;
			for (int index = start; index <= end; index++)
			{
				ranks[order[index].intValue()] = rank;
			}
			start = end + 1;
		}
		return ranks;
	}

	static double pearson(double[] x, double[] y)
	{
		int n = x.length;
		double meanX = 0.0;
		double meanY = 0.0;
		for (int index = 0; index < n; index++)
		{
			meanX += x[index];
			meanY += y[index];
		}
		meanX /= n;
		meanY /= n;
		double covariance = 0.0;
		double varianceX = 0.0;
		double varianceY = 0.0;
		for (int index = 0; index < n; index++)
		{
			double dx = x[index] - meanX;
			double dy = y[index] - meanY;
			covariance += dx * dy;
			varianceX += dx * dx;
			varianceY += dy * dy;
		}
		if (varianceX <= 0.0 || varianceY <= 0.0)
		{
			return Double.NaN;
		}
		return covariance / Math.sqrt(varianceX * varianceY);
	}

	/** A two-sided p-value as text: "p < 0.001" or "p = 0.023". */
	static String pText(double p)
	{
		if (Double.isNaN(p))
		{
			return "not testable";
		}
		if (p < 0.001)
		{
			return "p < 0.001";
		}
		return String.format(java.util.Locale.US, "p = %.3f", p);
	}
}
