package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Held-out folds of recommendation targets for the classifiers.
 *
 * A classifier must never train on a target it then predicts: the target's
 * followee is exactly what the evaluation scores. When the targets are a small
 * share of the labelled users, one model with every target held out loses
 * little training data. When they are many (up to every user), the targets are
 * split into stratified folds and each fold is predicted by a model trained on
 * everything else (K-fold cross-validation), so every target is still held out
 * of its own model and every labelled user still contributes to the others.
 *
 * The folds depend only on the target names and labels, never on the node
 * count or the order targets were listed in.
 */
final class TargetFolds
{
	/** Targets up to this share of the labelled users share one held-out model. */
	static final double SINGLE_MODEL_SHARE = 0.20;
	/** Folds used when the targets are too many to hold out at once. */
	static final int FOLDS = 5;

	private TargetFolds()
	{
	}

	/**
	 * @param targets         the run's recommendation targets
	 * @param labels          user to followee (targets without a label form their own group)
	 * @param labelledUsers   labelled users available for training, targets included
	 */
	static List<List<String>> of(Collection<String> targets, Map<String,String> labels, int labelledUsers)
	{
		TreeSet<String> sorted = new TreeSet<String>();
		if (targets != null)
		{
			for (String target : targets)
			{
				if (target != null)
				{
					sorted.add(target);
				}
			}
		}
		if (sorted.isEmpty())
		{
			return Collections.emptyList();
		}
		int singleModelLimit = (int)Math.floor(SINGLE_MODEL_SHARE * Math.max(0, labelledUsers));
		if (sorted.size() <= Math.max(1, singleModelLimit))
		{
			return Collections.<List<String>>singletonList(new ArrayList<String>(sorted));
		}
		int folds = Math.min(FOLDS, sorted.size());
		TreeMap<String,List<String>> byLabel = new TreeMap<String,List<String>>();
		for (String target : sorted)
		{
			String label = labels == null ? null : labels.get(target);
			String key = label == null ? "￿" : label;
			List<String> members = byLabel.get(key);
			if (members == null)
			{
				members = new ArrayList<String>();
				byLabel.put(key, members);
			}
			members.add(target);
		}
		List<List<String>> result = new ArrayList<List<String>>();
		for (int fold = 0; fold < folds; fold++)
		{
			result.add(new ArrayList<String>());
		}
		//Deal each class's members to the folds in turn, continuing the turn
		//across classes, so every fold holds a near-proportional share of each.
		int next = 0;
		for (List<String> members : byLabel.values())
		{
			for (String member : members)
			{
				result.get(next % folds).add(member);
				next++;
			}
		}
		for (List<String> fold : result)
		{
			Collections.sort(fold);
		}
		return result;
	}

	/** A one-line description of the protocol for reports. */
	static String describe(List<List<String>> folds, int labelledUsers)
	{
		if (folds.size() <= 1)
		{
			int held = folds.isEmpty() ? 0 : folds.get(0).size();
			return "one model with all " + held + " target(s) held out of "
					+ labelledUsers + " labelled user(s)";
		}
		return folds.size() + "-fold cross-validation over the targets: each fold is predicted"
				+ " by a model trained without it";
	}
}
