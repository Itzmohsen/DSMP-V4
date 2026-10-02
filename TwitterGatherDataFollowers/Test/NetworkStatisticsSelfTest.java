package TwitterGatherDataFollowers.userRyersonU;

import java.util.Random;

/**
 * The run graph's statistics against scikit-learn, NetworkX and SciPy values
 * and against brute force: partition agreement (AMI, ARI, NMI), Newman's
 * assortativity and its weighted form, the exact null distributions,
 * inequality and rank correlation.
 */
public final class NetworkStatisticsSelfTest
{
	private NetworkStatisticsSelfTest()
	{
	}

	public static void main(String[] args)
	{
		agreementMatchesScikitLearn();
		assortativityMatchesNetworkX();
		exactDistributionsMatchBruteForce();
		inequalityAndCorrelation();
		System.out.println("NetworkStatisticsSelfTest passed");
	}

	private static void agreementMatchesScikitLearn()
	{
		int[] a = {0, 0, 0, 1, 1, 1, 2, 2, 2, 2, 0, 1};
		int[] b = {0, 0, 1, 1, 1, 2, 2, 2, 0, 0, 0, 1};
		NetworkStatistics.Agreement agreement = NetworkStatistics.agreement(a, b);
		close(agreement.ami, 0.278968861620884, 1e-12, "AMI");
		close(agreement.ari, 0.211604095563140, 1e-12, "ARI");
		close(agreement.nmi, 0.433438077415104, 1e-12, "NMI");
		int[] singletons = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
		NetworkStatistics.Agreement chance = NetworkStatistics.agreement(a, singletons);
		close(chance.ami, 0.0, 1e-12, "AMI against singletons is chance");
		close(chance.ari, 0.0, 1e-15, "ARI against singletons");
		close(chance.nmi, 0.613147192765458, 1e-12, "NMI rewards singletons (why AMI is reported)");
		NetworkStatistics.Agreement same = NetworkStatistics.agreement(a, new int[] {5, 5, 5, 7, 7, 7, 9, 9, 9, 9, 5, 7});
		close(same.ami, 1.0, 1e-12, "relabelled partitions agree");
		close(same.ari, 1.0, 1e-15, "relabelled ARI");
		//Items without a label in either partition are left out.
		NetworkStatistics.Agreement partial = NetworkStatistics.agreement(new int[] {0, 0, -1, 1, 1},
				new int[] {3, 3, 9, -1, 4});
		require(partial.items == 3, "unlabelled items are dropped");
		//Random partitions average to AMI 0 (the point of adjusting for chance).
		Random random = new Random(3L);
		double total = 0.0;
		for (int trial = 0; trial < 200; trial++)
		{
			int[] x = new int[60];
			int[] y = new int[60];
			for (int item = 0; item < 60; item++)
			{
				x[item] = random.nextInt(4);
				y[item] = random.nextInt(6);
			}
			total += NetworkStatistics.agreement(x, y).ami;
		}
		require(Math.abs(total / 200.0) < 0.01, "AMI of random partitions averages 0: " + total / 200.0);
	}

	private static void assortativityMatchesNetworkX()
	{
		//Nodes 0,1 category 0; 2,3 category 1; 4,5 category 2.
		int[] category = {0, 0, 1, 1, 2, 2};
		int[][] edges = {{0, 1}, {1, 0}, {0, 2}, {2, 3}, {3, 2}, {3, 4}, {4, 5}, {5, 4}, {5, 0}, {1, 2}};
		int[] sources = new int[edges.length];
		int[] targets = new int[edges.length];
		for (int edge = 0; edge < edges.length; edge++)
		{
			sources[edge] = category[edges[edge][0]];
			targets[edge] = category[edges[edge][1]];
		}
		NetworkStatistics.Assortativity r = NetworkStatistics.assortativity(sources, targets, 3);
		close(r.r, 0.402985074626866, 1e-12, "assortativity (NetworkX)");
		require(r.standardError > 0.0 && r.edges == 10, "a jackknife error over the 10 edges");
		//The jackknife equals the textbook leave-one-out recomputation.
		double[] leaveOut = new double[edges.length];
		double mean = 0.0;
		for (int skip = 0; skip < edges.length; skip++)
		{
			int[] s = new int[edges.length - 1];
			int[] t = new int[edges.length - 1];
			for (int edge = 0, cursor = 0; edge < edges.length; edge++)
			{
				if (edge != skip)
				{
					s[cursor] = sources[edge];
					t[cursor++] = targets[edge];
				}
			}
			leaveOut[skip] = NetworkStatistics.assortativity(s, t, 3).r;
			mean += leaveOut[skip] / edges.length;
		}
		double squares = 0.0;
		for (double value : leaveOut)
		{
			squares += (value - mean) * (value - mean);
		}
		close(r.standardError, Math.sqrt((edges.length - 1.0) / edges.length * squares), 1e-12, "jackknife");
		//Weights of 1 change nothing; doubling every weight changes nothing; perfect mixing gives 1.
		double[] ones = new double[edges.length];
		java.util.Arrays.fill(ones, 2.0);
		close(NetworkStatistics.assortativity(sources, targets, ones, 3).r, r.r, 1e-12, "uniform weights");
		close(NetworkStatistics.assortativity(new int[] {0, 1, 2, 0}, new int[] {0, 1, 2, 0}, 3).r, 1.0, 1e-15,
				"perfectly assortative");
		//Weighted: e_ij is the share of weight; by hand for two edges inside (weights 3, 1) and one across (2).
		NetworkStatistics.Assortativity weighted = NetworkStatistics.assortativity(new int[] {0, 1, 0},
				new int[] {0, 1, 1}, new double[] {3.0, 1.0, 2.0}, 2);
		double trace = 4.0 / 6.0;
		double margins = (5.0 / 6.0) * (3.0 / 6.0) + (1.0 / 6.0) * (3.0 / 6.0);
		close(weighted.r, (trace - margins) / (1.0 - margins), 1e-15, "weighted assortativity");
	}

	private static void exactDistributionsMatchBruteForce()
	{
		//Hypergeometric parts against enumerating every draw.
		int[] population = {6, 5};
		int[] successes = {2, 3};
		int[] draws = {3, 2};
		NetworkStatistics.CountDistribution sum = NetworkStatistics.hypergeometricSum(population, successes, draws);
		double[] brute = new double[6];
		for (int first = 0; first < 1 << 6; first++)
		{
			if (Integer.bitCount(first) != 3)
			{
				continue;
			}
			for (int second = 0; second < 1 << 5; second++)
			{
				if (Integer.bitCount(second) != 2)
				{
					continue;
				}
				int count = Integer.bitCount(first & 0b11) + Integer.bitCount(second & 0b111);
				brute[count] += 1.0 / (20.0 * 10.0);
			}
		}
		for (int count = 0; count < sum.pmf.length; count++)
		{
			close(sum.pmf[count], brute[count], 1e-15, "hypergeometric sum at " + count);
		}
		close(sum.mean, 3 * 2.0 / 6 + 2 * 3.0 / 5, 1e-15, "hypergeometric mean");
		close(sum.upper(3), brute[3] + brute[4] + brute[5], 1e-15, "upper tail");
		//Poisson-binomial against enumerating every outcome.
		double[] p = {0.1, 0.5, 0.33, 0.9, 0.05};
		NetworkStatistics.CountDistribution pb = NetworkStatistics.poissonBinomial(p);
		double[] exact = new double[6];
		for (int outcome = 0; outcome < 32; outcome++)
		{
			double probability = 1.0;
			for (int trial = 0; trial < 5; trial++)
			{
				probability *= (outcome >> trial & 1) == 1 ? p[trial] : 1.0 - p[trial];
			}
			exact[Integer.bitCount(outcome)] += probability;
		}
		for (int count = 0; count < 6; count++)
		{
			close(pb.pmf[count], exact[count], 1e-15, "Poisson-binomial at " + count);
		}
		close(pb.twoSided(0), Math.min(1.0, 2.0 * exact[0]), 1e-15, "two-sided p at the lower edge");
		//Beyond the exact limit a normal approximation with the same moments is used.
		double[] many = new double[NetworkStatistics.EXACT_SUPPORT_LIMIT];
		java.util.Arrays.fill(many, 0.5);
		NetworkStatistics.CountDistribution normal = NetworkStatistics.poissonBinomial(many);
		require(!normal.exact() && Math.abs(normal.lower((long)normal.mean) - 0.5) < 0.01,
				"the normal approximation is centred");
	}

	private static void inequalityAndCorrelation()
	{
		close(NetworkStatistics.gini(new double[] {1, 1, 1, 1}), 0.0, 1e-15, "equal values");
		close(NetworkStatistics.gini(new double[] {0, 0, 0, 4}), 0.75, 1e-15, "one holds all of four");
		double[][] lorenz = NetworkStatistics.lorenz(new double[] {0, 0, 0, 4});
		close(lorenz[3][1], 0.0, 1e-15, "three quarters hold nothing");
		close(lorenz[4][1], 1.0, 1e-15, "the curve ends at 1");
		close(NetworkStatistics.normalisedEntropy(new double[] {2, 2, 2, 2}), 1.0, 1e-15, "uniform entropy");
		double[] x = {1, 2, 2, 3, 5, 5, 5, 8, 9, 12};
		double[] y = {2, 1, 4, 3, 6, 6, 5, 9, 7, 11};
		NetworkStatistics.Correlation rho = NetworkStatistics.spearman(x, y);
		close(rho.rho, 0.938343116817110, 1e-12, "Spearman (SciPy)");
		close(rho.pValue, 0.000058668302996, 1e-12, "Spearman p (SciPy)");
		close(NetworkStatistics.skewness(y), 0.337371851147220, 1e-12, "skewness (SciPy)");
		//Sums of the same weights in another order tie.
		double a = 1.0 / Math.log(3.0) * Math.log(2.0) + 0.1 + 0.2;
		double b = 0.2 + 0.1 + 1.0 / Math.log(3.0) * Math.log(2.0);
		double[] ranks = NetworkStatistics.ranks(new double[] {a, b, 5.0});
		close(ranks[0], 1.5, 0.0, "near-equal values tie");
		close(ranks[1], 1.5, 0.0, "near-equal values tie");
	}

	static void close(double actual, double expected, double tolerance, String label)
	{
		if (!(Math.abs(actual - expected) <= tolerance))
		{
			throw new AssertionError(label + ": expected " + expected + " but was " + actual);
		}
	}

	static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
