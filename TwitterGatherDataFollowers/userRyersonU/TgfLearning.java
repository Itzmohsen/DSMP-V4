package TwitterGatherDataFollowers.userRyersonU;

import java.util.Arrays;
import java.util.PriorityQueue;

/**
 * The learning machinery of the Temporal Graph Foundation model: sparse
 * k-nearest-neighbour graphs, label propagation (with exact per-user
 * influence), multinomial logistic regression, the non-negative stacked
 * pool, split-conformal prediction sets and calibration metrics.
 *
 * Every routine is deterministic: fixed iteration orders, ties broken by
 * index, no randomness, single-threaded (a node's work is measured on its own
 * thread).
 */
final class TgfLearning
{
	private TgfLearning()
	{
	}

	// ============================================================== graphs

	/**
	 * A user-user graph: each user's top neighbours (directed, by decreasing
	 * cosine) and the symmetrised graph (an edge when either endpoint lists the
	 * other, weight the cosine) with its symmetric normalisation.
	 */
	static final class Graph
	{
		/** Directed lists: neighbours of u by decreasing similarity, ties by index. */
		final int[][] near;
		final float[][] nearWeight;
		/** Symmetrised adjacency, neighbours increasing, normalised weight w / sqrt(d_u d_v). */
		final int[][] adjacent;
		final double[][] normalised;
		final double[][] weight;

		Graph(int[][] near, float[][] nearWeight, int[][] adjacent, double[][] weight, double[][] normalised)
		{
			this.near = near;
			this.nearWeight = nearWeight;
			this.adjacent = adjacent;
			this.weight = weight;
			this.normalised = normalised;
		}

		int size()
		{
			return near.length;
		}

		long edges()
		{
			long total = 0L;
			for (int[] list : adjacent)
			{
				total += list.length;
			}
			return total / 2L;
		}
	}

	/**
	 * The cosine k-nearest-neighbour graph of L2-normalised sparse rows. Each
	 * user keeps its {@code keep} most similar users; the symmetrised graph uses
	 * the first {@code neighbours} of them. Above 2,000 users, features shared
	 * by more than half the users are skipped when accumulating similarities:
	 * their inverse document frequency is below ln 2, so they barely move a
	 * cosine, and they would make the accumulation quadratic.
	 */
	static Graph knn(TgfFeatures.Sparse rows, int neighbours, int keep)
	{
		int n = rows.rows();
		int features = rows.columns;
		int[] df = new int[features];
		for (int p = 0; p < rows.column.length; p++)
		{
			df[rows.column[p]]++;
		}
		int cap = n > 2000 ? n / 2 : Integer.MAX_VALUE;
		int[][] postUser = new int[features][];
		double[][] postValue = new double[features][];
		for (int f = 0; f < features; f++)
		{
			int size = df[f] > cap ? 0 : df[f];
			postUser[f] = new int[size];
			postValue[f] = new double[size];
		}
		int[] fill = new int[features];
		for (int u = 0; u < n; u++)
		{
			for (int p = rows.pointer[u]; p < rows.pointer[u + 1]; p++)
			{
				int f = rows.column[p];
				if (postUser[f].length == 0)
				{
					continue;
				}
				postUser[f][fill[f]] = u;
				postValue[f][fill[f]] = rows.value[p];
				fill[f]++;
			}
		}
		int[][] near = new int[n][];
		float[][] nearWeight = new float[n][];
		double[] accumulator = new double[n];
		int[] touched = new int[n];
		boolean[] seen = new boolean[n];
		for (int u = 0; u < n; u++)
		{
			if ((u & 63) == 0)
			{
				UnitCancellation.check();
			}
			int count = 0;
			for (int p = rows.pointer[u]; p < rows.pointer[u + 1]; p++)
			{
				int f = rows.column[p];
				double w = rows.value[p];
				int[] users = postUser[f];
				double[] values = postValue[f];
				for (int j = 0; j < users.length; j++)
				{
					int v = users[j];
					if (v == u)
					{
						continue;
					}
					if (!seen[v])
					{
						seen[v] = true;
						touched[count++] = v;
					}
					accumulator[v] += w * values[j];
				}
			}
			//Top `keep` by (similarity desc, index asc): a min-heap of the best so far.
			PriorityQueue<long[]> heap = new PriorityQueue<long[]>(keep + 1, new java.util.Comparator<long[]>()
			{
				public int compare(long[] a, long[] b)
				{
					int bySimilarity = Double.compare(Double.longBitsToDouble(a[0]), Double.longBitsToDouble(b[0]));
					return bySimilarity != 0 ? bySimilarity : Long.compare(b[1], a[1]);
				}
			});
			for (int i = 0; i < count; i++)
			{
				int v = touched[i];
				double s = accumulator[v];
				accumulator[v] = 0.0;
				seen[v] = false;
				if (!(s > 0.0))
				{
					continue;
				}
				heap.add(new long[] {Double.doubleToLongBits(s), v});
				if (heap.size() > keep)
				{
					heap.poll();
				}
			}
			int size = heap.size();
			near[u] = new int[size];
			nearWeight[u] = new float[size];
			for (int i = size - 1; i >= 0; i--)
			{
				long[] top = heap.poll();
				near[u][i] = (int)top[1];
				nearWeight[u][i] = (float)Double.longBitsToDouble(top[0]);
			}
		}
		return symmetrise(near, nearWeight, neighbours);
	}

	/** The undirected graph of each user's first {@code neighbours} neighbours (weight: the larger cosine). */
	static Graph symmetrise(int[][] near, float[][] nearWeight, int neighbours)
	{
		int n = near.length;
		java.util.TreeMap<Integer,Double>[] edges = newEdgeMaps(n);
		for (int u = 0; u < n; u++)
		{
			for (int j = 0; j < Math.min(neighbours, near[u].length); j++)
			{
				int v = near[u][j];
				double w = nearWeight[u][j];
				put(edges[u], v, w);
				put(edges[v], u, w);
			}
		}
		int[][] adjacent = new int[n][];
		double[][] weight = new double[n][];
		double[] degree = new double[n];
		for (int u = 0; u < n; u++)
		{
			adjacent[u] = new int[edges[u].size()];
			weight[u] = new double[edges[u].size()];
			int i = 0;
			for (java.util.Map.Entry<Integer,Double> entry : edges[u].entrySet())
			{
				adjacent[u][i] = entry.getKey().intValue();
				weight[u][i] = entry.getValue().doubleValue();
				degree[u] += weight[u][i];
				i++;
			}
		}
		double[][] normalised = new double[n][];
		for (int u = 0; u < n; u++)
		{
			normalised[u] = new double[adjacent[u].length];
			for (int i = 0; i < adjacent[u].length; i++)
			{
				int v = adjacent[u][i];
				double d = degree[u] * degree[v];
				normalised[u][i] = d > 0.0 ? weight[u][i] / Math.sqrt(d) : 0.0;
			}
		}
		return new Graph(near, nearWeight, adjacent, weight, normalised);
	}

	@SuppressWarnings("unchecked")
	private static java.util.TreeMap<Integer,Double>[] newEdgeMaps(int n)
	{
		java.util.TreeMap<Integer,Double>[] maps = new java.util.TreeMap[n];
		for (int u = 0; u < n; u++)
		{
			maps[u] = new java.util.TreeMap<Integer,Double>();
		}
		return maps;
	}

	private static void put(java.util.TreeMap<Integer,Double> map, int key, double value)
	{
		Double old = map.get(Integer.valueOf(key));
		if (old == null || value > old.doubleValue())
		{
			map.put(Integer.valueOf(key), Double.valueOf(value));
		}
	}

	// ======================================================== propagation

	/**
	 * Learning with local and global consistency (Zhou et al. 2004): the class
	 * mass F = (1 - alpha)(I - alpha S)^-1 Y, with S the symmetric normalised
	 * adjacency and Y the one-hot labels ({@code label[u]} is the class of a
	 * labelled user, -1 otherwise). I - alpha S is symmetric positive definite
	 * (its condition number is at most (1 + alpha) / (1 - alpha)), so each class
	 * column is solved by conjugate gradients to a relative residual of 1e-10.
	 */
	static double[][] propagate(Graph graph, int[] label, int classes, double alpha)
	{
		int n = graph.size();
		double[][] rhs = new double[n][classes];
		for (int u = 0; u < n; u++)
		{
			if (label[u] >= 0)
			{
				rhs[u][label[u]] = 1.0 - alpha;
			}
		}
		return solveShifted(graph, rhs, classes, alpha);
	}

	/**
	 * Row u of the propagation operator M = (1 - alpha)(I - alpha S)^-1: the
	 * class mass user u receives is exactly sum_v M[u][v] Y[v], so M[u][v] is
	 * labelled user v's influence on u. M is symmetric, so the row is the
	 * solution of (I - alpha S) r = (1 - alpha) e_u.
	 */
	static double[] influence(Graph graph, int user, double alpha)
	{
		int n = graph.size();
		double[][] rhs = new double[n][1];
		rhs[user][0] = 1.0 - alpha;
		double[][] solution = solveShifted(graph, rhs, 1, alpha);
		double[] r = new double[n];
		for (int u = 0; u < n; u++)
		{
			r[u] = solution[u][0];
		}
		return r;
	}

	/** Solves (I - alpha S) X = B column by column with conjugate gradients (all columns in one sweep). */
	private static double[][] solveShifted(Graph graph, double[][] b, int columns, double alpha)
	{
		int n = graph.size();
		double[][] x = new double[n][columns];
		double[][] r = new double[n][columns];
		double[][] p = new double[n][columns];
		double[][] ap = new double[n][columns];
		double[] rr = new double[columns];
		double[] initial = new double[columns];
		for (int u = 0; u < n; u++)
		{
			for (int c = 0; c < columns; c++)
			{
				r[u][c] = b[u][c];
				p[u][c] = b[u][c];
				rr[c] += b[u][c] * b[u][c];
			}
		}
		System.arraycopy(rr, 0, initial, 0, columns);
		boolean[] done = new boolean[columns];
		for (int iteration = 0; iteration < 500; iteration++)
		{
			boolean all = true;
			for (int c = 0; c < columns; c++)
			{
				done[c] = done[c] || rr[c] <= 1e-20 * Math.max(1e-300, initial[c]) || initial[c] == 0.0;
				all &= done[c];
			}
			if (all)
			{
				break;
			}
			if ((iteration & 15) == 0)
			{
				UnitCancellation.check();
			}
			//ap = (I - alpha S) p
			for (int u = 0; u < n; u++)
			{
				double[] row = ap[u];
				double[] own = p[u];
				for (int c = 0; c < columns; c++)
				{
					row[c] = own[c];
				}
				int[] adjacent = graph.adjacent[u];
				double[] s = graph.normalised[u];
				for (int i = 0; i < adjacent.length; i++)
				{
					double weight = alpha * s[i];
					double[] other = p[adjacent[i]];
					for (int c = 0; c < columns; c++)
					{
						row[c] -= weight * other[c];
					}
				}
			}
			double[] pap = new double[columns];
			for (int u = 0; u < n; u++)
			{
				for (int c = 0; c < columns; c++)
				{
					pap[c] += p[u][c] * ap[u][c];
				}
			}
			double[] step = new double[columns];
			double[] next = new double[columns];
			for (int c = 0; c < columns; c++)
			{
				step[c] = done[c] || pap[c] <= 0.0 ? 0.0 : rr[c] / pap[c];
			}
			for (int u = 0; u < n; u++)
			{
				for (int c = 0; c < columns; c++)
				{
					if (step[c] == 0.0)
					{
						continue;
					}
					x[u][c] += step[c] * p[u][c];
					r[u][c] -= step[c] * ap[u][c];
					next[c] += r[u][c] * r[u][c];
				}
			}
			for (int c = 0; c < columns; c++)
			{
				if (step[c] == 0.0)
				{
					done[c] = true;
					continue;
				}
				double beta = next[c] / rr[c];
				rr[c] = next[c];
				for (int u = 0; u < n; u++)
				{
					p[u][c] = r[u][c] + beta * p[u][c];
				}
			}
		}
		return x;
	}

	/**
	 * Similarity-weighted vote of the first {@code neighbours} labelled users
	 * among each user's nearest, Laplace-smoothed into a distribution.
	 */
	static double[] vote(Graph graph, int user, int[] label, int classes, int neighbours)
	{
		double[] votes = new double[classes];
		double total = 0.0;
		int used = 0;
		for (int i = 0; i < graph.near[user].length && used < neighbours; i++)
		{
			int v = graph.near[user][i];
			if (label[v] < 0)
			{
				continue;
			}
			double w = Math.max(0.0, graph.nearWeight[user][i]);
			votes[label[v]] += w;
			total += w;
			used++;
		}
		for (int c = 0; c < classes; c++)
		{
			votes[c] = (votes[c] + 0.01) / (total + 0.01 * classes);
		}
		return votes;
	}

	// ============================================================ logistic

	/**
	 * Multinomial logistic regression on sparse rows: weights[c][f] and
	 * bias[c], fitted by L-BFGS (memory 10, Armijo backtracking) on the mean
	 * log-loss plus (lambda / 2) ||W||^2 (the bias is not penalised).
	 */
	static final class Logistic
	{
		final double[][] weights;
		final double[] bias;
		final int iterations;
		final boolean converged;

		Logistic(double[][] weights, double[] bias, int iterations, boolean converged)
		{
			this.weights = weights;
			this.bias = bias;
			this.iterations = iterations;
			this.converged = converged;
		}

		double[] logits(TgfFeatures.Sparse rows, int row)
		{
			int classes = bias.length;
			double[] z = bias.clone();
			for (int p = rows.pointer[row]; p < rows.pointer[row + 1]; p++)
			{
				int f = rows.column[p];
				double x = rows.value[p];
				for (int c = 0; c < classes; c++)
				{
					z[c] += weights[c][f] * x;
				}
			}
			return z;
		}
	}

	static Logistic logistic(TgfFeatures.Sparse rows, int[] train, int[] label, int classes, double lambda,
			int maxIterations)
	{
		int features = rows.columns;
		int dim = classes * (features + 1);
		double[] w = new double[dim];
		double[] g = new double[dim];
		int memory = 10;
		double[][] sHistory = new double[memory][];
		double[][] yHistory = new double[memory][];
		double[] rho = new double[memory];
		int stored = 0;
		int head = 0;
		double f = loss(rows, train, label, classes, w, g, lambda);
		int iteration = 0;
		boolean converged = false;
		double[] next = new double[dim];
		double[] nextGradient = new double[dim];
		for (; iteration < maxIterations; iteration++)
		{
			UnitCancellation.check();
			double[] q = g.clone();
			double[] alpha = new double[memory];
			for (int j = 0; j < stored; j++)
			{
				int k = (head - 1 - j + memory) % memory;
				alpha[k] = rho[k] * dot(sHistory[k], q);
				axpy(-alpha[k], yHistory[k], q);
			}
			if (stored > 0)
			{
				int last = (head - 1 + memory) % memory;
				double gamma = dot(sHistory[last], yHistory[last]) / dot(yHistory[last], yHistory[last]);
				scale(q, gamma);
			}
			else
			{
				scale(q, 1.0 / Math.max(1.0, Math.sqrt(dot(g, g))));
			}
			for (int j = stored - 1; j >= 0; j--)
			{
				int k = (head - 1 - j + memory) % memory;
				double beta = rho[k] * dot(yHistory[k], q);
				axpy(alpha[k] - beta, sHistory[k], q);
			}
			double slope = -dot(g, q);
			if (!(slope < 0.0))
			{
				q = g.clone();
				slope = -dot(g, g);
				stored = 0;
			}
			double step = 1.0;
			double nextLoss;
			while (true)
			{
				for (int d = 0; d < dim; d++)
				{
					next[d] = w[d] - step * q[d];
				}
				nextLoss = loss(rows, train, label, classes, next, nextGradient, lambda);
				if (nextLoss <= f + 1e-4 * step * slope || step < 1e-12)
				{
					break;
				}
				step *= 0.5;
			}
			double[] s = new double[dim];
			double[] y = new double[dim];
			for (int d = 0; d < dim; d++)
			{
				s[d] = next[d] - w[d];
				y[d] = nextGradient[d] - g[d];
			}
			double sy = dot(s, y);
			if (sy > 1e-12)
			{
				sHistory[head] = s;
				yHistory[head] = y;
				rho[head] = 1.0 / sy;
				head = (head + 1) % memory;
				stored = Math.min(memory, stored + 1);
			}
			double improvement = f - nextLoss;
			double[] swap = w;
			w = next;
			next = swap;
			swap = g;
			g = nextGradient;
			nextGradient = swap;
			f = nextLoss;
			if (improvement < 1e-9 * Math.max(1.0, Math.abs(f)))
			{
				converged = true;
				iteration++;
				break;
			}
		}
		double[][] weights = new double[classes][features];
		double[] bias = new double[classes];
		for (int c = 0; c < classes; c++)
		{
			int base = c * (features + 1);
			System.arraycopy(w, base, weights[c], 0, features);
			bias[c] = w[base + features];
		}
		return new Logistic(weights, bias, iteration, converged);
	}

	private static double loss(TgfFeatures.Sparse rows, int[] train, int[] label, int classes, double[] w,
			double[] g, double lambda)
	{
		int features = rows.columns;
		Arrays.fill(g, 0.0);
		double total = 0.0;
		double[] z = new double[classes];
		double inverse = 1.0 / Math.max(1, train.length);
		for (int u : train)
		{
			for (int c = 0; c < classes; c++)
			{
				int base = c * (features + 1);
				double s = w[base + features];
				for (int p = rows.pointer[u]; p < rows.pointer[u + 1]; p++)
				{
					s += w[base + rows.column[p]] * rows.value[p];
				}
				z[c] = s;
			}
			double max = Double.NEGATIVE_INFINITY;
			for (double value : z)
			{
				max = Math.max(max, value);
			}
			double sum = 0.0;
			for (int c = 0; c < classes; c++)
			{
				z[c] = Math.exp(z[c] - max);
				sum += z[c];
			}
			int y = label[u];
			total -= Math.log(Math.max(1e-300, z[y] / sum));
			for (int c = 0; c < classes; c++)
			{
				double residual = (z[c] / sum - (c == y ? 1.0 : 0.0)) * inverse;
				if (residual == 0.0)
				{
					continue;
				}
				int base = c * (features + 1);
				for (int p = rows.pointer[u]; p < rows.pointer[u + 1]; p++)
				{
					g[base + rows.column[p]] += residual * rows.value[p];
				}
				g[base + features] += residual;
			}
		}
		total *= inverse;
		for (int c = 0; c < classes; c++)
		{
			int base = c * (features + 1);
			for (int t = 0; t < features; t++)
			{
				double weight = w[base + t];
				total += 0.5 * lambda * weight * weight;
				g[base + t] += lambda * weight;
			}
		}
		return total;
	}

	// ================================================================ pool

	/**
	 * The stacked pool: logit_c = b_c + sum_m w_m s_m(c) with w_m >= 0, fitted
	 * by projected Newton with Armijo backtracking on the penalised multinomial
	 * log-likelihood of out-of-fold channel scores (Breiman 1996, stacked
	 * regressions with non-negative weights; a weighted logarithmic opinion
	 * pool). {@code scores[m][i][c]} are standardised channel scores of
	 * calibration user i. Returns {w_1..w_M, b_1..b_C}.
	 */
	static double[] pool(double[][][] scores, int[] y, int classes, double lambdaWeights, double lambdaBias)
	{
		int channels = scores.length;
		int dim = channels + classes;
		double[] theta = new double[dim];
		double[] lambda = new double[dim];
		for (int d = 0; d < dim; d++)
		{
			lambda[d] = d < channels ? lambdaWeights : lambdaBias;
		}
		double objective = poolObjective(scores, y, classes, theta, lambda, null, null);
		for (int iteration = 0; iteration < 200; iteration++)
		{
			double[] gradient = new double[dim];
			double[][] hessian = new double[dim][dim];
			poolObjective(scores, y, classes, theta, lambda, gradient, hessian);
			for (int d = 0; d < channels; d++)
			{
				if (theta[d] <= 0.0 && gradient[d] <= 0.0)
				{
					//Held at the bound: the likelihood would push this weight negative.
					Arrays.fill(hessian[d], 0.0);
					for (int e = 0; e < dim; e++)
					{
						hessian[e][d] = 0.0;
					}
					hessian[d][d] = 1.0;
					gradient[d] = 0.0;
				}
			}
			double[] direction = solve(hessian, gradient);
			double slope = dot(gradient, direction);
			if (!(slope > 0.0))
			{
				direction = gradient.clone();
				slope = dot(gradient, gradient);
			}
			double step = 1.0;
			double[] next = new double[dim];
			double nextObjective;
			while (true)
			{
				for (int d = 0; d < dim; d++)
				{
					next[d] = theta[d] + step * direction[d];
					if (d < channels && next[d] < 0.0)
					{
						next[d] = 0.0;
					}
				}
				nextObjective = poolObjective(scores, y, classes, next, lambda, null, null);
				if (nextObjective >= objective + 1e-4 * step * slope || step < 1e-12)
				{
					break;
				}
				step *= 0.5;
			}
			if (!(nextObjective >= objective))
			{
				break;
			}
			double change = 0.0;
			for (int d = 0; d < dim; d++)
			{
				change = Math.max(change, Math.abs(next[d] - theta[d]));
			}
			double gain = nextObjective - objective;
			theta = next;
			objective = nextObjective;
			if (change < 1e-10 || gain < 1e-12)
			{
				break;
			}
		}
		return theta;
	}

	private static double poolObjective(double[][][] scores, int[] y, int classes, double[] theta, double[] lambda,
			double[] gradient, double[][] hessian)
	{
		int channels = scores.length;
		int dim = theta.length;
		double total = 0.0;
		double[][] phi = new double[classes][dim];
		double[] logits = new double[classes];
		double[] mean = new double[dim];
		double[] p = new double[classes];
		for (int i = 0; i < y.length; i++)
		{
			for (int c = 0; c < classes; c++)
			{
				Arrays.fill(phi[c], 0.0);
				double z = theta[channels + c];
				for (int m = 0; m < channels; m++)
				{
					phi[c][m] = scores[m][i][c];
					z += theta[m] * phi[c][m];
				}
				phi[c][channels + c] = 1.0;
				logits[c] = z;
			}
			double normaliser = logSumExp(logits);
			total += logits[y[i]] - normaliser;
			if (gradient == null)
			{
				continue;
			}
			Arrays.fill(mean, 0.0);
			for (int c = 0; c < classes; c++)
			{
				p[c] = Math.exp(logits[c] - normaliser);
				for (int d = 0; d < dim; d++)
				{
					mean[d] += p[c] * phi[c][d];
				}
			}
			for (int d = 0; d < dim; d++)
			{
				gradient[d] += phi[y[i]][d] - mean[d];
			}
			for (int c = 0; c < classes; c++)
			{
				for (int a = 0; a < dim; a++)
				{
					double da = phi[c][a] - mean[a];
					if (da == 0.0)
					{
						continue;
					}
					double pa = p[c] * da;
					for (int b = 0; b < dim; b++)
					{
						hessian[a][b] += pa * (phi[c][b] - mean[b]);
					}
				}
			}
		}
		for (int d = 0; d < dim; d++)
		{
			total -= 0.5 * lambda[d] * theta[d] * theta[d];
			if (gradient != null)
			{
				gradient[d] -= lambda[d] * theta[d];
				hessian[d][d] += lambda[d];
			}
		}
		return total;
	}

	// ======================================================== conformal

	/**
	 * Split-conformal threshold of the least ambiguous set-valued classifier
	 * (Sadinle, Lei and Wasserman 2019): with nonconformity 1 - p(y) on n
	 * calibration users, the ceil((n + 1)(1 - alpha))-th smallest score. A
	 * new user's set {c : 1 - p(c) <= q} then holds its true class with
	 * probability at least 1 - alpha when users are exchangeable.
	 */
	static double conformalThreshold(double[] nonconformity, double alpha)
	{
		int n = nonconformity.length;
		if (n == 0)
		{
			return 1.0;
		}
		double[] sorted = nonconformity.clone();
		Arrays.sort(sorted);
		int rank = (int)Math.ceil((n + 1) * (1.0 - alpha));
		return rank > n ? 1.0 : sorted[Math.max(0, rank - 1)];
	}

	// ============================================================ helpers

	static double[] softmax(double[] logits)
	{
		double normaliser = logSumExp(logits);
		double[] p = new double[logits.length];
		for (int c = 0; c < logits.length; c++)
		{
			p[c] = Math.exp(logits[c] - normaliser);
		}
		return p;
	}

	static double logSumExp(double[] values)
	{
		double max = Double.NEGATIVE_INFINITY;
		for (double value : values)
		{
			max = Math.max(max, value);
		}
		if (max == Double.NEGATIVE_INFINITY)
		{
			return max;
		}
		double sum = 0.0;
		for (double value : values)
		{
			sum += Math.exp(value - max);
		}
		return max + Math.log(sum);
	}

	static double dot(double[] a, double[] b)
	{
		double total = 0.0;
		for (int i = 0; i < a.length; i++)
		{
			total += a[i] * b[i];
		}
		return total;
	}

	static void axpy(double alpha, double[] x, double[] y)
	{
		for (int i = 0; i < x.length; i++)
		{
			y[i] += alpha * x[i];
		}
	}

	static void scale(double[] x, double factor)
	{
		for (int i = 0; i < x.length; i++)
		{
			x[i] *= factor;
		}
	}

	/** Gaussian elimination with partial pivoting (small dense systems). */
	static double[] solve(double[][] matrix, double[] vector)
	{
		int size = vector.length;
		double[][] a = new double[size][size + 1];
		for (int i = 0; i < size; i++)
		{
			System.arraycopy(matrix[i], 0, a[i], 0, size);
			a[i][size] = vector[i];
		}
		for (int column = 0; column < size; column++)
		{
			int pivot = column;
			for (int row = column + 1; row < size; row++)
			{
				if (Math.abs(a[row][column]) > Math.abs(a[pivot][column]))
				{
					pivot = row;
				}
			}
			double[] swap = a[column];
			a[column] = a[pivot];
			a[pivot] = swap;
			double divisor = a[column][column];
			if (Math.abs(divisor) < 1e-15)
			{
				continue;
			}
			for (int row = 0; row < size; row++)
			{
				if (row == column)
				{
					continue;
				}
				double factor = a[row][column] / divisor;
				if (factor == 0.0)
				{
					continue;
				}
				for (int c = column; c <= size; c++)
				{
					a[row][c] -= factor * a[column][c];
				}
			}
		}
		double[] result = new double[size];
		for (int i = 0; i < size; i++)
		{
			result[i] = Math.abs(a[i][i]) < 1e-15 ? 0.0 : a[i][size] / a[i][i];
		}
		return result;
	}
}
