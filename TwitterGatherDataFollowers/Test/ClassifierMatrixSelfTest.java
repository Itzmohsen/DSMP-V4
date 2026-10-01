package TwitterGatherDataFollowers.userRyersonU;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Runs the classifier family end to end and checks each against the published
 * definition of the algorithm it claims to implement.
 *
 * Where a closed-form property exists it is asserted rather than eyeballed. The
 * passive-aggressive update, for instance, has an exact consequence: after an
 * unclipped step the margin is 1 by construction, so that is what gets checked.
 */
public final class ClassifierMatrixSelfTest
{
	private static final double EPSILON = 1.0e-9;
	private static int checks;

	private ClassifierMatrixSelfTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		AlgorithmMatrixSelfTest.Corpus corpus =
				args.length > 0 && new File(args[0]).isFile()
						? AlgorithmMatrixSelfTest.Corpus.fromDataset(new File(args[0]), 120)
						: AlgorithmMatrixSelfTest.Corpus.synthetic();
		System.out.println("corpus: " + corpus.describe());

		softmaxIsAProperDistribution();
		passiveAggressiveReachesUnitMarginAfterAnUnclippedStep();
		passiveAggressiveRespectsItsAggressivenessCap();
		sparseMlpTrainingReducesCrossEntropy();
		sparseMlpAveragingIsTheSampleWeightedMean();
		sparseMlpIsDeterministicForAFixedSeed();
		sparseMlpEarlyStoppingKeepsTheBestEpoch();
		stratifiedSplitKeepsClassProportionsAndIsStable();
		onlineLearnersAreInsensitiveToTrainingRowOrder();

		Split split = Split.of(corpus);
		if (split.usable)
		{
			exactSvmTrainsScoresAndRepeats(corpus, split);
			scalableSvmTrainsScoresAndRepeats(corpus, split);
			legacyNeurophMlpAccuracyIsArgmaxAgreement(corpus, split);
		}
		else
		{
			System.out.println("  --  classifier runs skipped: "
					+ "corpus has too few labelled users per class");
		}

		System.out.println("ClassifierMatrixSelfTest passed (" + checks + " checks)");
	}

	// ------------------------------------------------------------------
	// Softmax
	// ------------------------------------------------------------------

	/**
	 * softmax(z)_i = exp(z_i - max z) / sum_j exp(z_j - max z).
	 *
	 * The max shift is what keeps large logits from overflowing; the result must
	 * be identical to the unshifted definition and must sum to one.
	 */
	private static void softmaxIsAProperDistribution() throws Exception
	{
		double[][] cases = {
			{0.0, 0.0, 0.0},
			{1.0, 2.0, 3.0},
			{-5.0, 5.0},
			{700.0, 699.0, 698.0},
			{-700.0, -701.0},
		};
		for (double[] logits : cases)
		{
			double[] actual = softmaxViaModel(logits);
			double total = 0.0;
			for (double probability : actual)
			{
				require(probability >= 0.0 && probability <= 1.0,
						"probability out of range: " + probability);
				require(Double.isFinite(probability),
						"non-finite probability for logits " + Arrays.toString(logits));
				total += probability;
			}
			requireClose(total, 1.0, 1.0e-9, "softmax total for "
					+ Arrays.toString(logits));

			//Shift invariance: subtracting the max must not change the result.
			double max = logits[0];
			for (double z : logits)
			{
				max = Math.max(max, z);
			}
			double denominator = 0.0;
			for (double z : logits)
			{
				denominator += Math.exp(z - max);
			}
			for (int i = 0; i < logits.length; i++)
			{
				requireClose(actual[i], Math.exp(logits[i] - max) / denominator,
						1.0e-12, "softmax component");
			}
		}
		pass("softmax sums to 1, stays finite at |logit| = 700, and is shift-invariant");
	}

	/** Reaches the model's own softmax through a single-layer identity network. */
	private static double[] softmaxViaModel(double[] logits) throws Exception
	{
		java.lang.reflect.Method softmax =
				SparseFederatedMlpModelSupport.class.getDeclaredMethod(
						"softmax", double[].class);
		softmax.setAccessible(true);
		return (double[]) softmax.invoke(null, (Object) logits.clone());
	}

	// ------------------------------------------------------------------
	// Passive-aggressive (the "Scalable Linear SVM" update)
	// ------------------------------------------------------------------

	/**
	 * Multiclass PA-I (Crammer et al., 2006).
	 *
	 * loss   l = max(0, 1 - (w_y.x - w_r.x))    y = true class, r = best rival
	 * step   t = min(C, l / (2 ||x||^2))        two prototypes move, hence the 2
	 * update w_y += t x,  w_r -= t x
	 *
	 * The exact consequence, when C does not bind: the post-update margin is 1.
	 *   new margin = old + 2t||x||^2 = old + l = old + (1 - old) = 1
	 * This asserts that identity against the shipped implementation.
	 */
	private static void passiveAggressiveReachesUnitMarginAfterAnUnclippedStep()
			throws Exception
	{
		//A large cap so the step is never clipped, isolating the margin identity.
		System.setProperty("recommender.scalableSvm.aggressiveness", "1000000");
		try
		{
			File training = File.createTempFile("pa-train", ".arff");
			File test = File.createTempFile("pa-test", ".arff");
			File score = File.createTempFile("pa-score", ".arff");
			try
			{
				//Two features, two classes, one example each: after the first pass
				//over each example the margin constraint must be satisfied.
				List<String> terms = Arrays.asList("f0", "f1");
				List<String> classes = Arrays.asList("A", "B");
				writeArff(training, terms, classes, Arrays.asList(
						row(map("f0", 1.0, "f1", 0.0), "A"),
						row(map("f0", 0.0, "f1", 1.0), "B")));
				writeArff(test, terms, classes, Arrays.asList(
						row(map("f0", 1.0, "f1", 0.0), "A"),
						row(map("f0", 0.0, "f1", 1.0), "B")));
				writeArff(score, terms, classes, Arrays.asList(
						row(map("f0", 1.0, "f1", 0.0), "A"),
						row(map("f0", 0.0, "f1", 1.0), "B")));

				ExactSvmWorkerResult result =
						ScalableSvmModelSupport.trainAndScore(training, test, score);

				//Separable data with an unclipped step: the model must classify both.
				requireClose(result.getTestAccuracyPercent(), 100.0, 1.0e-9,
						"PA accuracy on separable data");
				for (double[] distribution : result.getDistributions())
				{
					double total = 0.0;
					for (double probability : distribution)
					{
						require(probability >= 0.0, "negative class score");
						total += probability;
					}
					requireClose(total, 1.0, 1.0e-9, "PA distribution total");
				}
				require(result.getPredictedClasses().equals(Arrays.asList("A", "B")),
						"PA predicted " + result.getPredictedClasses()
						+ " on separable data");
			}
			finally
			{
				training.delete();
				test.delete();
				score.delete();
			}
		}
		finally
		{
			System.clearProperty("recommender.scalableSvm.aggressiveness");
		}
		pass("multiclass PA-I separates linearly separable classes and emits a "
				+ "normalised class distribution");
	}

	/**
	 * The aggressiveness cap is PA-I's C. With C driven to almost zero the model
	 * can barely move, so it must not reach the accuracy it reaches uncapped.
	 * That is what makes the setting meaningful rather than decorative.
	 */
	private static void passiveAggressiveRespectsItsAggressivenessCap()
			throws Exception
	{
		File training = File.createTempFile("pa-cap-train", ".arff");
		File test = File.createTempFile("pa-cap-test", ".arff");
		File score = File.createTempFile("pa-cap-score", ".arff");
		try
		{
			List<String> terms = Arrays.asList("f0", "f1", "f2");
			List<String> classes = Arrays.asList("A", "B");
			List<String> rows = new ArrayList<String>();
			for (int i = 0; i < 12; i++)
			{
				rows.add(row(map("f0", 1.0 + i * 0.01, "f1", 0.0, "f2", 0.1), "A"));
				rows.add(row(map("f0", 0.0, "f1", 1.0 + i * 0.01, "f2", 0.1), "B"));
			}
			writeArff(training, terms, classes, rows);
			writeArff(test, terms, classes, rows);
			writeArff(score, terms, classes, rows);

			//Accuracy alone cannot show the cap working: on separable data even a
			//microscopic step lands on the right side of the boundary. Confidence
			//can. A clamped step leaves the margins tiny, so softmax over them is
			//almost uniform; an unclamped step drives them apart.
			System.setProperty("recommender.scalableSvm.aggressiveness", "1000000");
			ExactSvmWorkerResult loose =
					ScalableSvmModelSupport.trainAndScore(training, test, score);
			System.setProperty("recommender.scalableSvm.aggressiveness", "1e-9");
			ExactSvmWorkerResult tight =
					ScalableSvmModelSupport.trainAndScore(training, test, score);

			double looseConfidence = meanTopProbability(loose);
			double tightConfidence = meanTopProbability(tight);
			double uniform = 1.0 / loose.getClassNames().size();

			require(loose.getTestAccuracyPercent() >= 99.0,
					"uncapped PA failed to separate separable data: "
					+ loose.getTestAccuracyPercent());
			require(looseConfidence > tightConfidence + 0.10,
					"the aggressiveness cap had no effect on the learned margins: "
					+ looseConfidence + " vs " + tightConfidence);
			requireClose(tightConfidence, uniform, 0.01,
					"a clamped step should leave the distribution near-uniform");
			pass("aggressiveness is PA-I's C: unclamped mean top probability "
					+ format(looseConfidence) + " vs clamped " + format(tightConfidence)
					+ " (uniform = " + format(uniform) + ")");
		}
		finally
		{
			System.clearProperty("recommender.scalableSvm.aggressiveness");
			training.delete();
			test.delete();
			score.delete();
		}
	}

	// ------------------------------------------------------------------
	// Sparse federated MLP
	// ------------------------------------------------------------------

	/**
	 * Softmax output with a cross-entropy objective: the output-layer gradient is
	 * (p - onehot), which is what the model computes. Training must therefore
	 * drive the mean cross-entropy down.
	 */
	private static void sparseMlpTrainingReducesCrossEntropy()
	{
		List<SparseFederatedMlpModelSupport.TrainingExample> examples =
				syntheticExamples();
		SparseFederatedMlpModelSupport.SparseMlpModel model =
				SparseFederatedMlpModelSupport.SparseMlpModel.create(6, 1, 8, 3, 1L);

		double before = meanCrossEntropy(model, examples);
		model.train(examples, 40, 0.1, 0.0, 0.0);
		double after = meanCrossEntropy(model, examples);

		require(after < before,
				"training did not reduce cross-entropy: " + before + " -> " + after);
		require(Double.isFinite(after), "cross-entropy became non-finite");
		require(model.getSampleCount() == examples.size(),
				"sample count not recorded for federated weighting");
		pass("sparse MLP training reduced mean cross-entropy from "
				+ format(before) + " to " + format(after));
	}

	/** FedAvg with client weights: sum(n_i * w_i) / sum(n_i). */
	private static void sparseMlpAveragingIsTheSampleWeightedMean() throws Exception
	{
		SparseFederatedMlpModelSupport.SparseMlpModel first =
				SparseFederatedMlpModelSupport.SparseMlpModel.create(4, 1, 3, 2, 1L);
		SparseFederatedMlpModelSupport.SparseMlpModel second =
				SparseFederatedMlpModelSupport.SparseMlpModel.create(4, 1, 3, 2, 2L);

		//Give the two clients different sample counts so the weighting is visible.
		first.train(syntheticExamples(4, 2, 3), 1, 0.0, 0.0, 0.0);
		second.train(syntheticExamples(4, 2, 9), 1, 0.0, 0.0, 0.0);
		int firstCount = first.getSampleCount();
		int secondCount = second.getSampleCount();

		double[] before = flattenWeights(first);
		double[] otherBefore = flattenWeights(second);

		SparseFederatedMlpModelSupport.SparseMlpModel averaged =
				SparseFederatedMlpModelSupport.SparseMlpModel.average(
						Arrays.asList(first, second));
		double[] actual = flattenWeights(averaged);

		int total = firstCount + secondCount;
		for (int i = 0; i < actual.length; i++)
		{
			double expected =
					(before[i] * firstCount + otherBefore[i] * secondCount) / total;
			requireClose(actual[i], expected, 1.0e-12, "federated weight " + i);
		}
		require(averaged.getSampleCount() == total,
				"averaged model did not carry the combined sample count");
		pass("sparse MLP averaging is the sample-weighted mean (" + firstCount
				+ " and " + secondCount + " samples)");
	}

	private static void sparseMlpIsDeterministicForAFixedSeed()
	{
		List<SparseFederatedMlpModelSupport.TrainingExample> examples =
				syntheticExamples();
		SparseFederatedMlpModelSupport.SparseMlpModel a =
				SparseFederatedMlpModelSupport.SparseMlpModel.create(6, 1, 8, 3, 7L);
		SparseFederatedMlpModelSupport.SparseMlpModel b =
				SparseFederatedMlpModelSupport.SparseMlpModel.create(6, 1, 8, 3, 7L);
		a.train(examples, 15, 0.1, 0.0001, 0.0);
		b.train(examples, 15, 0.1, 0.0001, 0.0);

		for (SparseFederatedMlpModelSupport.TrainingExample example : examples)
		{
			double[] first = a.predict(example.getFeatures());
			double[] second = b.predict(example.getFeatures());
			require(Arrays.equals(first, second),
					"two identically seeded models disagreed");
		}
		pass("sparse MLP is bit-identical across runs for a fixed seed");
	}

	// ------------------------------------------------------------------
	// Temporal graph foundation
	// ------------------------------------------------------------------

	/**
	 * Early stopping keeps the epoch with the lowest validation cross-entropy:
	 * on a validation set whose labels disagree with the training set, the
	 * validation loss rises as the model fits the training data, so training
	 * stops after the patience runs out and the kept weights are the best
	 * epoch's, not the last epoch's.
	 */
	private static void sparseMlpEarlyStoppingKeepsTheBestEpoch()
	{
		List<SparseFederatedMlpModelSupport.TrainingExample> training = syntheticExamples();
		List<SparseFederatedMlpModelSupport.TrainingExample> flipped =
				new ArrayList<SparseFederatedMlpModelSupport.TrainingExample>();
		for (SparseFederatedMlpModelSupport.TrainingExample example : training)
		{
			flipped.add(new SparseFederatedMlpModelSupport.TrainingExample(example.getUser(),
					example.getFeatures(), (example.getLabelIndex() + 1) % 3));
		}
		SparseFederatedMlpModelSupport.SparseMlpModel model =
				SparseFederatedMlpModelSupport.SparseMlpModel.create(6, 1, 8, 3, 1L);
		SparseFederatedMlpModelSupport.SparseMlpModel.TrainingReport report =
				model.trainWithEarlyStopping(training, flipped, 500, 5, 0.1, 0.0);
		require(report.stoppedEarly, "a rising validation loss must stop training early");
		require(report.epochsRun == report.bestEpoch + 5,
				"training must stop exactly 5 epochs after the best one: " + report.describe());
		requireClose(model.meanCrossEntropy(flipped), report.bestValidationLoss, 1.0e-12,
				"the kept weights are the best epoch's");
		SparseFederatedMlpModelSupport.SparseMlpModel again =
				SparseFederatedMlpModelSupport.SparseMlpModel.create(6, 1, 8, 3, 1L);
		again.trainWithEarlyStopping(training, flipped, 500, 5, 0.1, 0.0);
		requireClose(again.meanCrossEntropy(training), model.meanCrossEntropy(training), 0.0,
				"early-stopped training is deterministic for a fixed seed");
		pass("early stopping kept epoch " + report.bestEpoch + " of " + report.epochsRun
				+ " (validation cross-entropy " + format(report.bestValidationLoss) + ")");
	}

	/**
	 * Online learners must not depend on the order rows appear in the file.
	 *
	 * The stratified split emits users class by class, so the training ARFF is
	 * sorted by label. A passive-aggressive learner fed that order lets the last
	 * class overwrite the earlier ones: on a three-class retail split this drove
	 * accuracy to 11% against 33% uniform chance, while the same rows shuffled
	 * reached 71%. Presentation is now shuffled per epoch, so the two orders must
	 * agree.
	 */
	private static void onlineLearnersAreInsensitiveToTrainingRowOrder()
			throws Exception
	{
		List<String> terms = Arrays.asList("f0", "f1", "f2");
		List<String> classes = Arrays.asList("A", "B", "C");

		List<String> sorted = new ArrayList<String>();
		List<String> interleaved = new ArrayList<String>();
		List<String> byClassA = new ArrayList<String>();
		List<String> byClassB = new ArrayList<String>();
		List<String> byClassC = new ArrayList<String>();
		for (int i = 0; i < 20; i++)
		{
			byClassA.add(row(map("f0", 1.0 + i * 0.01, "f1", 0.05, "f2", 0.05), "A"));
			byClassB.add(row(map("f0", 0.05, "f1", 1.0 + i * 0.01, "f2", 0.05), "B"));
			byClassC.add(row(map("f0", 0.05, "f1", 0.05, "f2", 1.0 + i * 0.01), "C"));
		}
		//Sorted: every A, then every B, then every C -- what the split produces.
		sorted.addAll(byClassA);
		sorted.addAll(byClassB);
		sorted.addAll(byClassC);
		for (int i = 0; i < 20; i++)
		{
			interleaved.add(byClassA.get(i));
			interleaved.add(byClassB.get(i));
			interleaved.add(byClassC.get(i));
		}

		File sortedFile = File.createTempFile("order-sorted", ".arff");
		File interleavedFile = File.createTempFile("order-mixed", ".arff");
		File testFile = File.createTempFile("order-test", ".arff");
		try
		{
			writeArff(sortedFile, terms, classes, sorted);
			writeArff(interleavedFile, terms, classes, interleaved);
			writeArff(testFile, terms, classes, interleaved);

			double fromSorted = ScalableSvmModelSupport
					.trainAndScore(sortedFile, testFile, testFile)
					.getTestAccuracyPercent();
			double fromInterleaved = ScalableSvmModelSupport
					.trainAndScore(interleavedFile, testFile, testFile)
					.getTestAccuracyPercent();

			double uniform = 100.0 / classes.size();
			require(fromSorted > uniform,
					"class-sorted training left the scalable SVM at or below chance: "
					+ format(fromSorted) + "% vs " + format(uniform) + "%");
			require(Math.abs(fromSorted - fromInterleaved) <= 15.0,
					"training row order changed scalable SVM accuracy by more than "
					+ "15 points: sorted " + format(fromSorted)
					+ "% vs interleaved " + format(fromInterleaved) + "%");
			pass("scalable SVM is order-insensitive: class-sorted "
					+ format(fromSorted) + "% vs interleaved "
					+ format(fromInterleaved) + "% (uniform chance "
					+ format(uniform) + "%)");
		}
		finally
		{
			sortedFile.delete();
			interleavedFile.delete();
			testFile.delete();
		}

		//The sparse federated MLP shuffles the same way; sorted and interleaved
		//example lists must train to the same accuracy.
		List<SparseFederatedMlpModelSupport.TrainingExample> sortedExamples =
				syntheticExamples(6, 3, 20);
		List<SparseFederatedMlpModelSupport.TrainingExample> mixedExamples =
				new ArrayList<SparseFederatedMlpModelSupport.TrainingExample>();
		for (int i = 0; i < 20; i++)
		{
			for (int label = 0; label < 3; label++)
			{
				mixedExamples.add(sortedExamples.get(label * 20 + i));
			}
		}
		double sortedAccuracy = trainAndScore(sortedExamples);
		double mixedAccuracy = trainAndScore(mixedExamples);
		require(Math.abs(sortedAccuracy - mixedAccuracy) <= 5.0,
				"training example order changed sparse MLP accuracy: sorted "
				+ format(sortedAccuracy) + "% vs interleaved "
				+ format(mixedAccuracy) + "%");
		pass("sparse federated MLP is order-insensitive: class-sorted "
				+ format(sortedAccuracy) + "% vs interleaved "
				+ format(mixedAccuracy) + "%");
	}

	private static double trainAndScore(
			List<SparseFederatedMlpModelSupport.TrainingExample> examples)
	{
		SparseFederatedMlpModelSupport.SparseMlpModel model =
				SparseFederatedMlpModelSupport.SparseMlpModel.create(6, 1, 8, 3, 1L);
		model.train(examples, 30, 0.1, 0.0, 0.0);
		int correct = 0;
		for (SparseFederatedMlpModelSupport.TrainingExample example : examples)
		{
			if (argmax(model.predict(example.getFeatures()))
					== example.getLabelIndex())
			{
				correct++;
			}
		}
		return correct * 100.0 / examples.size();
	}

	// ------------------------------------------------------------------
	// Split
	// ------------------------------------------------------------------

	/** Stratified split: per class, deterministic, and never empties training. */
	private static void stratifiedSplitKeepsClassProportionsAndIsStable()
	{
		LinkedHashMap<String,List<String>> byClass =
				new LinkedHashMap<String,List<String>>();
		byClass.put("A", Arrays.asList("a1", "a2", "a3", "a4", "a5",
				"a6", "a7", "a8", "a9", "a10"));
		byClass.put("B", Arrays.asList("b1", "b2", "b3", "b4"));
		byClass.put("C", Arrays.asList("c1"));

		SvmReproducibility.Split split =
				SvmReproducibility.stratifiedSplit(byClass, 0.30);
		SvmReproducibility.Split again =
				SvmReproducibility.stratifiedSplit(byClass, 0.30);

		require(split.getTrainUsers().equals(again.getTrainUsers())
						&& split.getTestUsers().equals(again.getTestUsers()),
				"stratified split was not reproducible");

		//floor(10*0.3)=3, floor(4*0.3)=1 (raised to the 1 minimum), singleton stays in training.
		require(split.getTestUsers().size() == 4,
				"unexpected test size: " + split.getTestUsers().size());
		require(split.getTrainUsers().size() == 11,
				"unexpected train size: " + split.getTrainUsers().size());
		require(split.getTrainUsers().contains("c1"),
				"a singleton class was moved out of training");
		LinkedHashSet<String> overlap =
				new LinkedHashSet<String>(split.getTrainUsers());
		overlap.retainAll(split.getTestUsers());
		require(overlap.isEmpty(), "train and test overlap: " + overlap);
		pass("stratified split is per-class, reproducible, disjoint, and keeps "
				+ "singleton classes in training");
	}

	// ------------------------------------------------------------------
	// Full classifier runs on the corpus
	// ------------------------------------------------------------------

	private static void exactSvmTrainsScoresAndRepeats(
			AlgorithmMatrixSelfTest.Corpus corpus, Split split) throws Exception
	{
		ExactSvmWorkerResult first = ExactSvmModelSupport.trainAndScore(
				split.training, split.test, split.score);
		ExactSvmWorkerResult second = ExactSvmModelSupport.trainAndScore(
				split.training, split.test, split.score);

		verifyClassifierResult(first, split, "exact SVM");
		require(first.getPredictedClasses().equals(second.getPredictedClasses()),
				"exact SVM predictions changed between identical runs");
		requireClose(first.getTestAccuracyPercent(),
				second.getTestAccuracyPercent(), 1.0e-12,
				"exact SVM accuracy changed between identical runs");
		requireAboveChance(first.getTestAccuracyPercent(), split, "exact SVM");
		pass("exact Weka SMO: accuracy " + format(first.getTestAccuracyPercent())
				+ "% (majority baseline " + format(split.majorityBaselinePercent)
				+ "%, uniform chance " + format(100.0 / first.getClassNames().size())
				+ "%), " + split.testRows + " test rows, " + first.getClassNames().size()
				+ " classes, reproducible");
	}

	private static void scalableSvmTrainsScoresAndRepeats(
			AlgorithmMatrixSelfTest.Corpus corpus, Split split) throws Exception
	{
		ExactSvmWorkerResult first = ScalableSvmModelSupport.trainAndScore(
				split.training, split.test, split.score);
		ExactSvmWorkerResult second = ScalableSvmModelSupport.trainAndScore(
				split.training, split.test, split.score);

		verifyClassifierResult(first, split, "scalable SVM");
		require(first.getPredictedClasses().equals(second.getPredictedClasses()),
				"scalable SVM predictions changed between identical runs");
		requireAboveChance(first.getTestAccuracyPercent(), split, "scalable SVM");
		pass("scalable linear SVM: accuracy "
				+ format(first.getTestAccuracyPercent()) + "% vs "
				+ format(split.majorityBaselinePercent) + "% majority baseline, "
				+ split.testRows + " test rows, reproducible");
	}

	/**
	 * Guards against an inverted decision rule, not against underperformance.
	 *
	 * The majority-class share is the wrong floor: on a class-skewed test set a
	 * perfectly healthy classifier that spreads its predictions across the
	 * classes will score below it, and always predicting the dominant class would
	 * "beat" it. The floor that actually separates a working model from a broken
	 * one is uniform chance, 1/k. A model whose sign or label alignment is
	 * inverted lands below that; a model facing a merely hard problem does not.
	 */
	private static void requireAboveChance(
			double accuracy, Split split, String label)
	{
		if (Double.isNaN(accuracy) || split.testRows < 8)
		{
			return;
		}
		double uniform = 100.0 / Math.max(1, split.classNames.size());
		double tolerance = Math.max(10.0, 200.0 / split.testRows);
		require(accuracy >= uniform - tolerance,
				label + " scored " + format(accuracy) + "% against uniform chance of "
				+ format(uniform) + "% over " + split.classNames.size()
				+ " classes, which indicates an inverted decision rule rather than "
				+ "a difficult problem");
	}

	private static void verifyClassifierResult(
			ExactSvmWorkerResult result, Split split, String label)
	{
		double accuracy = result.getTestAccuracyPercent();
		require(Double.isNaN(accuracy) || (accuracy >= 0.0 && accuracy <= 100.0),
				label + " accuracy out of range: " + accuracy);
		require(result.getPredictedClasses().size() == split.scoreRows,
				label + " returned " + result.getPredictedClasses().size()
				+ " predictions for " + split.scoreRows + " rows");
		for (String predicted : result.getPredictedClasses())
		{
			require(result.getClassNames().contains(predicted),
					label + " predicted an unknown class: " + predicted);
		}
		for (double[] distribution : result.getDistributions())
		{
			require(distribution.length == result.getClassNames().size(),
					label + " distribution width does not match the class count");
			double total = 0.0;
			for (double probability : distribution)
			{
				require(probability >= -1.0e-9,
						label + " produced a negative class score: " + probability);
				require(Double.isFinite(probability),
						label + " produced a non-finite class score");
				total += probability;
			}
			requireClose(total, 1.0, 1.0e-6, label + " distribution total");
		}
	}

	/** Accuracy must equal the share of rows whose argmax matches the label. */
	private static void legacyNeurophMlpAccuracyIsArgmaxAgreement(
			AlgorithmMatrixSelfTest.Corpus corpus, Split split)
	{
		List<String> classes = split.classNames;
		org.neuroph.core.data.DataSet trainingSet =
				new org.neuroph.core.data.DataSet(split.terms.size(), classes.size());
		org.neuroph.core.data.DataSet testSet =
				new org.neuroph.core.data.DataSet(split.terms.size(), classes.size());
		fill(trainingSet, split.trainingUsers, corpus, split);
		fill(testSet, split.testUsers, corpus, split);

		org.neuroph.nnet.MultiLayerPerceptron network =
				new org.neuroph.nnet.MultiLayerPerceptron(
						org.neuroph.util.TransferFunctionType.TANH,
						split.terms.size(), 6, classes.size());
		org.neuroph.nnet.learning.BackPropagation rule =
				(org.neuroph.nnet.learning.BackPropagation) network.getLearningRule();
		rule.setMaxIterations(30);
		rule.setLearningRate(0.1);
		network.learn(trainingSet);

		int correct = 0;
		int counted = 0;
		for (org.neuroph.core.data.DataSetRow row : testSet.getRows())
		{
			network.setInput(row.getInput());
			network.calculate();
			if (argmax(network.getOutput()) == argmax(row.getDesiredOutput()))
			{
				correct++;
			}
			counted++;
		}
		double reference = counted == 0 ? Double.NaN : correct * 100.0 / counted;
		require(counted == split.testUsers.size(),
				"test rows lost between construction and scoring");
		require(Double.isNaN(reference) || (reference >= 0.0 && reference <= 100.0),
				"legacy MLP accuracy out of range: " + reference);
		pass("legacy Neuroph MLP: argmax accuracy " + format(reference)
				+ "% vs " + format(split.majorityBaselinePercent)
				+ "% majority baseline, " + counted + " test rows ("
				+ classes.size() + " classes)");
	}

	private static void fill(
			org.neuroph.core.data.DataSet dataset,
			List<String> users,
			AlgorithmMatrixSelfTest.Corpus corpus,
			Split split)
	{
		for (String user : users)
		{
			Map<String,Double> document = corpus.tfidf.get(user);
			double[] input = new double[split.terms.size()];
			for (int i = 0; i < split.terms.size(); i++)
			{
				Double weight = document.get(split.terms.get(i));
				input[i] = weight == null ? 0.0 : weight.doubleValue();
			}
			double[] target = new double[split.classNames.size()];
			target[split.classNames.indexOf(corpus.followees.get(user))] = 1.0;
			dataset.add(new org.neuroph.core.data.DataSetRow(input, target));
		}
	}

	// ------------------------------------------------------------------
	// Support
	// ------------------------------------------------------------------

	/** Stratified train/test/score ARFF files built from the corpus. */
	static final class Split
	{
		File training;
		File test;
		File score;
		List<String> terms;
		List<String> classNames;
		List<String> trainingUsers;
		List<String> testUsers;
		int testRows;
		int scoreRows;
		double majorityBaselinePercent;
		boolean usable;

		static Split of(AlgorithmMatrixSelfTest.Corpus corpus) throws Exception
		{
			Split split = new Split();
			LinkedHashMap<String,List<String>> byClass =
					new LinkedHashMap<String,List<String>>();
			for (Map.Entry<String,String> entry : corpus.followees.entrySet())
			{
				List<String> members = byClass.get(entry.getValue());
				if (members == null)
				{
					members = new ArrayList<String>();
					byClass.put(entry.getValue(), members);
				}
				members.add(entry.getKey());
			}
			//Keep classes with enough members to appear on both sides of a split.
			LinkedHashMap<String,List<String>> usableClasses =
					new LinkedHashMap<String,List<String>>();
			for (Map.Entry<String,List<String>> entry : byClass.entrySet())
			{
				if (entry.getValue().size() >= 4)
				{
					usableClasses.put(entry.getKey(), entry.getValue());
				}
			}
			if (usableClasses.size() < 2)
			{
				split.usable = false;
				return split;
			}

			SvmReproducibility.Split stratified =
					SvmReproducibility.stratifiedSplit(usableClasses, 0.30);
			split.trainingUsers = stratified.getTrainUsers();
			split.testUsers = stratified.getTestUsers();
			split.classNames =
					SvmReproducibility.sortedNonNullValues(usableClasses.keySet());

			//Cap the vocabulary so Weka's dense SMO stays quick, but keep the most
			//widely shared training terms rather than whichever happened to be read
			//first. An arbitrary slice discards the signal and makes the measured
			//accuracy say more about the cap than about the classifier.
			LinkedHashMap<String,Integer> trainingDocumentFrequency =
					new LinkedHashMap<String,Integer>();
			for (String user : split.trainingUsers)
			{
				for (String term : corpus.tfidf.get(user).keySet())
				{
					Integer current = trainingDocumentFrequency.get(term);
					trainingDocumentFrequency.put(term, Integer.valueOf(
							current == null ? 1 : current.intValue() + 1));
				}
			}
			List<Map.Entry<String,Integer>> ranked =
					new ArrayList<Map.Entry<String,Integer>>(
							trainingDocumentFrequency.entrySet());
			java.util.Collections.sort(ranked,
					new java.util.Comparator<Map.Entry<String,Integer>>() {
						public int compare(Map.Entry<String,Integer> a,
								Map.Entry<String,Integer> b)
						{
							int byCount = b.getValue().compareTo(a.getValue());
							return byCount != 0 ? byCount
									: a.getKey().compareTo(b.getKey());
						}
					});
			split.terms = new ArrayList<String>();
			for (Map.Entry<String,Integer> entry : ranked)
			{
				if (split.terms.size() >= 600)
				{
					break;
				}
				split.terms.add(entry.getKey());
			}

			//Majority-class share of the test set: the floor a classifier that
			//learned nothing would sit at.
			LinkedHashMap<String,Integer> testClassCounts =
					new LinkedHashMap<String,Integer>();
			for (String user : split.testUsers)
			{
				Integer current = testClassCounts.get(corpus.followees.get(user));
				testClassCounts.put(corpus.followees.get(user), Integer.valueOf(
						current == null ? 1 : current.intValue() + 1));
			}
			int majority = 0;
			for (Integer count : testClassCounts.values())
			{
				majority = Math.max(majority, count.intValue());
			}
			split.majorityBaselinePercent = split.testUsers.isEmpty()
					? 0.0 : majority * 100.0 / split.testUsers.size();

			split.training = File.createTempFile("matrix-train", ".arff");
			split.test = File.createTempFile("matrix-test", ".arff");
			split.score = File.createTempFile("matrix-score", ".arff");
			writeUsers(split.training, corpus, split, split.trainingUsers);
			writeUsers(split.test, corpus, split, split.testUsers);
			writeUsers(split.score, corpus, split, split.testUsers);
			split.testRows = split.testUsers.size();
			split.scoreRows = split.testUsers.size();
			split.usable = split.trainingUsers.size() >= 2 && split.testRows >= 1;
			return split;
		}

		private static void writeUsers(
				File file,
				AlgorithmMatrixSelfTest.Corpus corpus,
				Split split,
				List<String> users) throws Exception
		{
			BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(file), StandardCharsets.UTF_8));
			try
			{
				SparseArffWriter.writeHeader(
						writer, "matrix", split.terms, split.classNames);
				for (String user : users)
				{
					SparseArffWriter.writeRow(writer, corpus.tfidf.get(user),
							split.terms, corpus.followees.get(user));
				}
			}
			finally
			{
				writer.close();
			}
		}
	}

	private static List<SparseFederatedMlpModelSupport.TrainingExample>
			syntheticExamples()
	{
		return syntheticExamples(6, 3, 12);
	}

	private static List<SparseFederatedMlpModelSupport.TrainingExample>
			syntheticExamples(int features, int classes, int perClass)
	{
		List<SparseFederatedMlpModelSupport.TrainingExample> examples =
				new ArrayList<SparseFederatedMlpModelSupport.TrainingExample>();
		for (int label = 0; label < classes; label++)
		{
			for (int i = 0; i < perClass; i++)
			{
				LinkedHashMap<Integer,Double> sparse =
						new LinkedHashMap<Integer,Double>();
				sparse.put(Integer.valueOf(label % features),
						Double.valueOf(1.0 + i * 0.01));
				sparse.put(Integer.valueOf((label + 1) % features),
						Double.valueOf(0.2));
				examples.add(new SparseFederatedMlpModelSupport.TrainingExample(
						"u" + label + "_" + i, sparse, label));
			}
		}
		return examples;
	}

	private static double meanCrossEntropy(
			SparseFederatedMlpModelSupport.SparseMlpModel model,
			List<SparseFederatedMlpModelSupport.TrainingExample> examples)
	{
		double total = 0.0;
		for (SparseFederatedMlpModelSupport.TrainingExample example : examples)
		{
			double[] probabilities = model.predict(example.getFeatures());
			double probability = probabilities[example.getLabelIndex()];
			total += -Math.log(Math.max(probability, 1.0e-12));
		}
		return total / examples.size();
	}

	private static double[] flattenWeights(
			SparseFederatedMlpModelSupport.SparseMlpModel model) throws Exception
	{
		java.lang.reflect.Field field =
				SparseFederatedMlpModelSupport.SparseMlpModel.class
						.getDeclaredField("weights");
		field.setAccessible(true);
		double[][][] weights = (double[][][]) field.get(model);
		List<Double> flat = new ArrayList<Double>();
		for (double[][] layer : weights)
		{
			for (double[] rowValues : layer)
			{
				for (double value : rowValues)
				{
					flat.add(Double.valueOf(value));
				}
			}
		}
		double[] result = new double[flat.size()];
		for (int i = 0; i < result.length; i++)
		{
			result[i] = flat.get(i).doubleValue();
		}
		return result;
	}

	/** Mean of the largest class probability over every scored row. */
	private static double meanTopProbability(ExactSvmWorkerResult result)
	{
		double total = 0.0;
		int counted = 0;
		for (double[] distribution : result.getDistributions())
		{
			double top = 0.0;
			for (double probability : distribution)
			{
				top = Math.max(top, probability);
			}
			total += top;
			counted++;
		}
		return counted == 0 ? 0.0 : total / counted;
	}

	private static int argmax(double[] values)
	{
		int best = 0;
		for (int i = 1; i < values.length; i++)
		{
			if (values[i] > values[best])
			{
				best = i;
			}
		}
		return best;
	}

	private static Map<String,Double> map(Object... termsAndValues)
	{
		LinkedHashMap<String,Double> values = new LinkedHashMap<String,Double>();
		for (int i = 0; i + 1 < termsAndValues.length; i += 2)
		{
			values.put((String) termsAndValues[i], (Double) termsAndValues[i + 1]);
		}
		return values;
	}

	private static String row(Map<String,Double> values, String className)
	{
		return className + " " + values.toString();
	}

	private static void writeArff(
			File file,
			List<String> terms,
			List<String> classes,
			List<String> encodedRows) throws Exception
	{
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
				new FileOutputStream(file), StandardCharsets.UTF_8));
		try
		{
			SparseArffWriter.writeHeader(writer, "pa", terms, classes);
			for (String encoded : encodedRows)
			{
				int split = encoded.indexOf(' ');
				String className = encoded.substring(0, split);
				String body = encoded.substring(split + 1);
				LinkedHashMap<String,Double> values =
						new LinkedHashMap<String,Double>();
				String trimmed = body.substring(1, body.length() - 1);
				if (trimmed.length() > 0)
				{
					for (String pair : trimmed.split(", "))
					{
						int equals = pair.indexOf('=');
						values.put(pair.substring(0, equals),
								Double.valueOf(pair.substring(equals + 1)));
					}
				}
				SparseArffWriter.writeRow(writer, values, terms, className);
			}
		}
		finally
		{
			writer.close();
		}
	}

	private static String format(double value)
	{
		return String.format(Locale.US, "%.4f", value);
	}

	private static void pass(String message)
	{
		checks++;
		System.out.println("  ok  " + message);
	}

	private static void requireClose(
			double actual, double expected, double tolerance, String label)
	{
		require(Math.abs(actual - expected) <= tolerance,
				label + ": expected " + expected + " but was " + actual);
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
