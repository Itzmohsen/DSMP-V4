package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A scripted sequence of node adjustments and faults, so that an experiment's
 * live controls are exactly reproducible.
 *
 * A scenario is plain text, one step per line; blank lines and lines starting
 * with # are ignored:
 *
 * <pre>
 * name: Drain a node mid-phase
 * description: node 3 leaves when the candidate phase is 40% done
 * at 1.5s pause node 2
 * at 3s resume node 2
 * at 40% of candidates drain node 3 to node 1
 * when svm opens + 200ms crash node 2 for 2s
 * at 50% of input unavailable node 1
 * at 2s capacity node 1 = 50%
 * at 2s policy work stealing
 * at 3s redirect 5 units to node 1
 * at 6s recover node 2
 * at 7s reset
 * </pre>
 *
 * Triggers: {@code at <time>} (model time since the run started; units ms, s,
 * min, plain numbers are seconds), {@code at <p>% [of <phase>]} (the phase's
 * committed fraction; without a phase, the unit phase running at that moment),
 * {@code at <p>% of input} (delivered input users), and
 * {@code when <phase> opens [+ <time>]}. Phases: candidates, doc2vec, tgf,
 * svm, mlp, population, or a phase key.
 *
 * Actions: pause, resume, unavailable, available (node N); drain node N [to
 * node M]; redirect K units to node N; capacity node N = X% (or a factor);
 * policy &lt;name&gt; (or "policy plan" to return to the plan's policy); crash
 * node N [for &lt;time&gt;]; recover node N; action window &lt;time&gt;; reset.
 *
 * The same scenario drives live runs ({@link RunTimeline}) and the
 * discrete-event simulator ({@link #controlsForPhase}).
 */
final class Scenario
{
	enum TriggerKind
	{
		TIME,
		PROGRESS,
		INPUT_PROGRESS,
		PHASE_OPEN
	}

	enum ActionKind
	{
		PAUSE,
		RESUME,
		UNAVAILABLE,
		AVAILABLE,
		DRAIN,
		REDIRECT,
		CAPACITY,
		POLICY,
		CRASH,
		RECOVER,
		ACTION_WINDOW,
		RESET
	}

	/** When a step fires. */
	static final class Trigger
	{
		private final TriggerKind kind;
		private final double timeMs;
		private final double fraction;
		private final String phase;

		private Trigger(TriggerKind kind, double timeMs, double fraction, String phase)
		{
			this.kind = kind;
			this.timeMs = timeMs;
			this.fraction = fraction;
			this.phase = phase == null ? "" : phase;
		}

		TriggerKind getKind()
		{
			return kind;
		}

		/** Model time for TIME; the delay after the phase opens for PHASE_OPEN. */
		double getTimeMs()
		{
			return timeMs;
		}

		double getFraction()
		{
			return fraction;
		}

		/** Phase key; empty for "the running phase". */
		String getPhase()
		{
			return phase;
		}

		String describe()
		{
			switch (kind)
			{
				case TIME:
					return "at " + formatDuration(timeMs);
				case PROGRESS:
					return "at " + formatPercent(fraction)
							+ (phase.length() == 0 ? "" : " of " + phaseAlias(phase));
				case INPUT_PROGRESS:
					return "at " + formatPercent(fraction) + " of input";
				default:
					return "when " + phaseAlias(phase) + " opens"
							+ (timeMs > 0.0 ? " + " + formatDuration(timeMs) : "");
			}
		}
	}

	/** What a step does. */
	static final class Action
	{
		private final ActionKind kind;
		private final int node;
		private final int target;
		private final int count;
		private final double value;
		private final SchedulerLabEngine.SchedulingPolicy policy;

		private Action(ActionKind kind, int node, int target, int count, double value,
				SchedulerLabEngine.SchedulingPolicy policy)
		{
			this.kind = kind;
			this.node = node;
			this.target = target;
			this.count = count;
			this.value = value;
			this.policy = policy;
		}

		ActionKind getKind()
		{
			return kind;
		}

		int getNode()
		{
			return node;
		}

		/** Preferred receiving node of a drain; 0 for any. */
		int getTarget()
		{
			return target;
		}

		int getCount()
		{
			return count;
		}

		/** Capacity scale, crash outage (ms, NaN until an explicit recover) or window (ms). */
		double getValue()
		{
			return value;
		}

		/** Policy of a POLICY action; null returns to the plan's policy. */
		SchedulerLabEngine.SchedulingPolicy getPolicy()
		{
			return policy;
		}

		String describe()
		{
			switch (kind)
			{
				case PAUSE:
					return "pause node " + node;
				case RESUME:
					return "resume node " + node;
				case UNAVAILABLE:
					return "unavailable node " + node;
				case AVAILABLE:
					return "available node " + node;
				case DRAIN:
					return "drain node " + node + (target > 0 ? " to node " + target : "");
				case REDIRECT:
					return "redirect " + count + " units to node " + node;
				case CAPACITY:
					return "capacity node " + node + " = " + formatPercent(value);
				case POLICY:
					return "policy " + (policy == null ? "plan" : policy.getLabel().toLowerCase(Locale.US));
				case CRASH:
					return "crash node " + node + (Double.isNaN(value) ? "" : " for " + formatDuration(value));
				case RECOVER:
					return "recover node " + node;
				case ACTION_WINDOW:
					return "action window " + formatDuration(value);
				default:
					return "reset";
			}
		}
	}

	static final class Step
	{
		private final Trigger trigger;
		private final Action action;
		private final int line;

		private Step(Trigger trigger, Action action, int line)
		{
			this.trigger = trigger;
			this.action = action;
			this.line = line;
		}

		Trigger getTrigger()
		{
			return trigger;
		}

		Action getAction()
		{
			return action;
		}

		int getLine()
		{
			return line;
		}

		String describe()
		{
			return trigger.describe() + " " + action.describe();
		}
	}

	/** A parse error with its line. */
	static final class ScenarioException extends Exception
	{
		private static final long serialVersionUID = 1L;
		private final int line;

		ScenarioException(int line, String message)
		{
			super("Line " + line + ": " + message);
			this.line = line;
		}

		int getLine()
		{
			return line;
		}
	}

	static final Scenario NONE = new Scenario("No scenario",
			"No scripted adjustments; the run is left alone.", Collections.<Step>emptyList(), "");

	private final String name;
	private final String description;
	private final List<Step> steps;
	private final String source;

	private Scenario(String name, String description, List<Step> steps, String source)
	{
		this.name = name == null || name.trim().length() == 0 ? "Untitled scenario" : name.trim();
		this.description = description == null ? "" : description.trim();
		this.steps = Collections.unmodifiableList(new ArrayList<Step>(steps));
		this.source = source == null ? "" : source;
	}

	String getName()
	{
		return name;
	}

	String getDescription()
	{
		return description;
	}

	List<Step> getSteps()
	{
		return steps;
	}

	boolean isEmpty()
	{
		return steps.isEmpty();
	}

	/** The scenario as text (its source when parsed, else generated). */
	String toText()
	{
		if (source.length() > 0)
		{
			return source;
		}
		StringBuilder builder = new StringBuilder();
		builder.append("name: ").append(name).append('\n');
		if (description.length() > 0)
		{
			builder.append("description: ").append(description).append('\n');
		}
		for (Step step : steps)
		{
			builder.append(step.describe()).append('\n');
		}
		return builder.toString();
	}

	/** A short fingerprint of the steps, for run records. */
	String fingerprint()
	{
		StringBuilder builder = new StringBuilder();
		for (Step step : steps)
		{
			builder.append(step.describe()).append(';');
		}
		return Long.toHexString(WorkDispatcher.mix(builder.toString().hashCode() * 31L + steps.size()));
	}

	/** Largest node number the scenario names (0 when none). */
	int highestNode()
	{
		int highest = 0;
		for (Step step : steps)
		{
			highest = Math.max(highest, Math.max(step.action.node, step.action.target));
		}
		return highest;
	}

	// ------------------------------------------------------------ parsing

	private static final Pattern TIME = Pattern.compile(
			"(?i)([0-9]+(?:\\.[0-9]+)?)\\s*(ms|s|sec|secs|seconds?|min|mins|minutes?)?");
	private static final Pattern AT_TIME = Pattern.compile(
			"(?i)^at\\s+([0-9]+(?:\\.[0-9]+)?\\s*(?:ms|s|sec|secs|seconds?|min|mins|minutes?)?)\\s+(.+)$");
	private static final Pattern AT_PROGRESS = Pattern.compile(
			"(?i)^at\\s+([0-9]+(?:\\.[0-9]+)?)\\s*%\\s*(?:of\\s+([a-z0-9_-]+))?\\s+(.+)$");
	private static final Pattern WHEN_OPENS = Pattern.compile(
			"(?i)^when\\s+([a-z0-9_-]+)\\s+opens(?:\\s*\\+\\s*([0-9]+(?:\\.[0-9]+)?\\s*[a-z]*))?\\s+(.+)$");

	static Scenario parse(String text) throws ScenarioException
	{
		String name = "";
		String description = "";
		ArrayList<Step> steps = new ArrayList<Step>();
		String[] lines = (text == null ? "" : text).split("\\r?\\n");
		for (int index = 0; index < lines.length; index++)
		{
			int lineNumber = index + 1;
			String line = lines[index].trim();
			if (line.length() == 0 || line.startsWith("#"))
			{
				continue;
			}
			String lower = line.toLowerCase(Locale.US);
			if (lower.startsWith("name:"))
			{
				name = line.substring(5).trim();
				continue;
			}
			if (lower.startsWith("description:"))
			{
				description = line.substring(12).trim();
				continue;
			}
			steps.add(parseStep(line, lineNumber));
		}
		return new Scenario(name.length() == 0 ? "Custom scenario" : name, description, steps,
				text == null ? "" : text);
	}

	private static Step parseStep(String line, int lineNumber) throws ScenarioException
	{
		Matcher matcher = AT_PROGRESS.matcher(line);
		if (matcher.matches())
		{
			double percent = Double.parseDouble(matcher.group(1));
			if (percent < 0.0 || percent > 100.0)
			{
				throw new ScenarioException(lineNumber, "a progress trigger must be between 0% and 100%.");
			}
			String phase = matcher.group(2) == null ? "" : matcher.group(2);
			Trigger trigger = "input".equalsIgnoreCase(phase)
					? new Trigger(TriggerKind.INPUT_PROGRESS, 0.0, percent / 100.0, "input")
					: new Trigger(TriggerKind.PROGRESS, 0.0, percent / 100.0, phaseKey(phase));
			return new Step(trigger, parseAction(matcher.group(3), lineNumber), lineNumber);
		}
		matcher = AT_TIME.matcher(line);
		if (matcher.matches())
		{
			Trigger trigger = new Trigger(TriggerKind.TIME, parseDuration(matcher.group(1), lineNumber),
					0.0, "");
			return new Step(trigger, parseAction(matcher.group(2), lineNumber), lineNumber);
		}
		matcher = WHEN_OPENS.matcher(line);
		if (matcher.matches())
		{
			double delay = matcher.group(2) == null ? 0.0 : parseDuration(matcher.group(2), lineNumber);
			Trigger trigger = new Trigger(TriggerKind.PHASE_OPEN, delay, 0.0, phaseKey(matcher.group(1)));
			return new Step(trigger, parseAction(matcher.group(3), lineNumber), lineNumber);
		}
		throw new ScenarioException(lineNumber,
				"expected \"at <time> <action>\", \"at <p>% [of <phase>] <action>\" or \"when <phase> opens [+ <time>] <action>\".");
	}

	private static Action parseAction(String text, int lineNumber) throws ScenarioException
	{
		String action = text.trim().toLowerCase(Locale.US).replaceAll("\\s+", " ");
		Matcher matcher;
		if ((matcher = Pattern.compile("^(pause|resume|unavailable|available|recover) node (\\d+)$")
				.matcher(action)).matches())
		{
			ActionKind kind = ActionKind.valueOf(matcher.group(1).toUpperCase(Locale.US));
			return new Action(kind, node(matcher.group(2), lineNumber), 0, 0, Double.NaN, null);
		}
		if ((matcher = Pattern.compile("^drain node (\\d+)(?: to node (\\d+))?$").matcher(action)).matches())
		{
			return new Action(ActionKind.DRAIN, node(matcher.group(1), lineNumber),
					matcher.group(2) == null ? 0 : node(matcher.group(2), lineNumber), 0, Double.NaN, null);
		}
		if ((matcher = Pattern.compile("^redirect (\\d+) units? to node (\\d+)$").matcher(action)).matches())
		{
			int count = Integer.parseInt(matcher.group(1));
			if (count <= 0)
			{
				throw new ScenarioException(lineNumber, "redirect at least one unit.");
			}
			return new Action(ActionKind.REDIRECT, node(matcher.group(2), lineNumber), 0, count,
					Double.NaN, null);
		}
		if ((matcher = Pattern.compile("^capacity node (\\d+) ?=? ?([0-9]+(?:\\.[0-9]+)?)\\s*(%|x)?$")
				.matcher(action)).matches())
		{
			double raw = Double.parseDouble(matcher.group(2));
			boolean percent = "%".equals(matcher.group(3)) || (matcher.group(3) == null && raw > 8.0);
			double scale = percent ? raw / 100.0 : raw;
			if (scale <= 0.0 || scale > 16.0)
			{
				throw new ScenarioException(lineNumber, "capacity must be above 0% and at most 1600%.");
			}
			return new Action(ActionKind.CAPACITY, node(matcher.group(1), lineNumber), 0, 0, scale, null);
		}
		if ((matcher = Pattern.compile("^policy (.+)$").matcher(action)).matches())
		{
			String name = matcher.group(1).trim();
			if (name.equals("plan") || name.equals("default") || name.equals("original plan"))
			{
				return new Action(ActionKind.POLICY, 0, 0, 0, Double.NaN, null);
			}
			SchedulerLabEngine.SchedulingPolicy policy = policyNamed(name);
			if (policy == null)
			{
				throw new ScenarioException(lineNumber, "unknown policy \"" + name + "\"; use one of "
						+ policyNames() + ".");
			}
			return new Action(ActionKind.POLICY, 0, 0, 0, Double.NaN, policy);
		}
		if ((matcher = Pattern.compile("^crash node (\\d+)(?: for (.+))?$").matcher(action)).matches())
		{
			double outage = matcher.group(2) == null ? Double.NaN
					: parseDuration(matcher.group(2), lineNumber);
			return new Action(ActionKind.CRASH, node(matcher.group(1), lineNumber), 0, 0, outage, null);
		}
		if ((matcher = Pattern.compile("^action window (.+)$").matcher(action)).matches())
		{
			return new Action(ActionKind.ACTION_WINDOW, 0, 0, 0,
					parseDuration(matcher.group(1), lineNumber), null);
		}
		if (action.equals("reset") || action.equals("reset controls"))
		{
			return new Action(ActionKind.RESET, 0, 0, 0, Double.NaN, null);
		}
		throw new ScenarioException(lineNumber, "unknown action \"" + text.trim() + "\".");
	}

	private static int node(String digits, int lineNumber) throws ScenarioException
	{
		int node = Integer.parseInt(digits);
		if (node < 1 || node > 4096)
		{
			throw new ScenarioException(lineNumber, "node numbers start at 1.");
		}
		return node;
	}

	static double parseDuration(String text, int lineNumber) throws ScenarioException
	{
		Matcher matcher = TIME.matcher(text.trim());
		if (!matcher.matches())
		{
			throw new ScenarioException(lineNumber, "\"" + text.trim() + "\" is not a time such as 1.5s or 200ms.");
		}
		double value = Double.parseDouble(matcher.group(1));
		String unit = matcher.group(2) == null ? "s" : matcher.group(2).toLowerCase(Locale.US);
		if (unit.equals("ms"))
		{
			return value;
		}
		if (unit.startsWith("min"))
		{
			return value * 60000.0;
		}
		return value * 1000.0;
	}

	/** Phase key of an alias used in scenarios. */
	static String phaseKey(String alias)
	{
		String name = alias == null ? "" : alias.trim().toLowerCase(Locale.US);
		if (name.length() == 0 || name.equals("any") || name.equals("phase") || name.equals("units"))
		{
			return "";
		}
		if (name.equals("candidates") || name.equals("similarity") || name.equals("common-neighbors"))
		{
			return RecommendationWorkQueueRegistry.CANDIDATES;
		}
		if (name.equals("doc2vec"))
		{
			return RecommendationWorkQueueRegistry.DOC2VEC;
		}
		if (name.equals("svm"))
		{
			return RecommendationWorkQueueRegistry.SVM_TARGETS;
		}
		if (name.equals("mlp"))
		{
			return RecommendationWorkQueueRegistry.MLP_TRAINING;
		}
		if (name.equals("tgf"))
		{
			return "tgf-targets";
		}
		if (name.equals("population"))
		{
			return "population-targets";
		}
		return name;
	}

	static String phaseAlias(String key)
	{
		if (RecommendationWorkQueueRegistry.CANDIDATES.equals(key))
		{
			return "candidates";
		}
		if (RecommendationWorkQueueRegistry.DOC2VEC.equals(key))
		{
			return "doc2vec";
		}
		if (RecommendationWorkQueueRegistry.SVM_TARGETS.equals(key))
		{
			return "svm";
		}
		if (RecommendationWorkQueueRegistry.MLP_TRAINING.equals(key))
		{
			return "mlp";
		}
		if ("tgf-targets".equals(key))
		{
			return "tgf";
		}
		if ("population-targets".equals(key))
		{
			return "population";
		}
		return key == null || key.length() == 0 ? "phase" : key;
	}

	static SchedulerLabEngine.SchedulingPolicy policyNamed(String name)
	{
		String wanted = normalize(name);
		for (SchedulerLabEngine.SchedulingPolicy policy : SchedulerLabEngine.SchedulingPolicy.values())
		{
			if (normalize(policy.getLabel()).equals(wanted) || normalize(policy.name()).equals(wanted))
			{
				return policy;
			}
		}
		for (SchedulerLabEngine.SchedulingPolicy policy : SchedulerLabEngine.SchedulingPolicy.values())
		{
			if (normalize(policy.getLabel()).startsWith(wanted) && wanted.length() >= 4)
			{
				return policy;
			}
		}
		return null;
	}

	private static String normalize(String text)
	{
		return text == null ? "" : text.toLowerCase(Locale.US).replaceAll("\\(.*?\\)", "")
				.replaceAll("[^a-z0-9]", "");
	}

	private static String policyNames()
	{
		StringBuilder builder = new StringBuilder();
		for (SchedulerLabEngine.SchedulingPolicy policy : SchedulerLabEngine.SchedulingPolicy.values())
		{
			if (builder.length() > 0)
			{
				builder.append(", ");
			}
			builder.append(policy.getLabel().toLowerCase(Locale.US));
		}
		return builder.toString();
	}

	static String formatDuration(double milliseconds)
	{
		if (Double.isNaN(milliseconds))
		{
			return "?";
		}
		if (milliseconds < 1000.0 && Math.abs(milliseconds - Math.rint(milliseconds)) < 1e-9)
		{
			return (long)milliseconds + "ms";
		}
		double seconds = milliseconds / 1000.0;
		String text = String.format(Locale.US, "%.3f", seconds).replaceAll("0+$", "").replaceAll("\\.$", "");
		return text + "s";
	}

	static String formatPercent(double fraction)
	{
		String text = String.format(Locale.US, "%.2f", fraction * 100.0)
				.replaceAll("0+$", "").replaceAll("\\.$", "");
		return text + "%";
	}

	// ------------------------------------------------------------ simulation

	/**
	 * The steps that act on one unit phase, as simulator controls. Time
	 * triggers become virtual times (the run's model time, since the simulator
	 * runs the phase at its live opening time {@code phaseOpenMs}); progress
	 * triggers of this phase (or of "the running phase") stay progress
	 * triggers; triggers of other phases and of the input are dropped.
	 *
	 * @param msPerTimeUnit milliseconds per simulator time unit
	 */
	List<SchedulingSimulator.Control> controlsForPhase(String phaseKey, double phaseOpenMs,
			double msPerTimeUnit)
	{
		ArrayList<SchedulingSimulator.Control> controls = new ArrayList<SchedulingSimulator.Control>();
		double unit = msPerTimeUnit <= 0.0 ? 1.0 : msPerTimeUnit;
		for (Step step : steps)
		{
			SchedulingSimulator.Control.Kind kind = simulatorKind(step.action.kind);
			if (kind == null)
			{
				continue;
			}
			Trigger trigger = step.trigger;
			double value = step.action.kind == ActionKind.CRASH
					? (Double.isNaN(step.action.value) ? Double.NaN : step.action.value / unit)
					: step.action.value;
			int node = step.action.node;
			int count = step.action.count;
			if (trigger.kind == TriggerKind.TIME)
			{
				controls.add(new SchedulingSimulator.Control(trigger.timeMs / unit, Double.NaN, kind,
						node, count, step.action.policy, value));
			}
			else if (trigger.kind == TriggerKind.PHASE_OPEN && trigger.phase.equals(phaseKey))
			{
				controls.add(new SchedulingSimulator.Control((phaseOpenMs + trigger.timeMs) / unit,
						Double.NaN, kind, node, count, step.action.policy, value));
			}
			else if (trigger.kind == TriggerKind.PROGRESS
					&& (trigger.phase.length() == 0 || trigger.phase.equals(phaseKey)))
			{
				controls.add(SchedulingSimulator.Control.atProgress(trigger.fraction, kind, node, count,
						step.action.policy, value));
			}
		}
		return controls;
	}

	private static SchedulingSimulator.Control.Kind simulatorKind(ActionKind kind)
	{
		switch (kind)
		{
			case PAUSE:
				return SchedulingSimulator.Control.Kind.PAUSE;
			case RESUME:
				return SchedulingSimulator.Control.Kind.RESUME;
			case UNAVAILABLE:
				return SchedulingSimulator.Control.Kind.UNAVAILABLE;
			case AVAILABLE:
				return SchedulingSimulator.Control.Kind.AVAILABLE;
			case DRAIN:
				return SchedulingSimulator.Control.Kind.DRAIN;
			case REDIRECT:
				return SchedulingSimulator.Control.Kind.REDIRECT;
			case CAPACITY:
				return SchedulingSimulator.Control.Kind.CAPACITY;
			case POLICY:
				return SchedulingSimulator.Control.Kind.SWITCH_POLICY;
			case CRASH:
				return SchedulingSimulator.Control.Kind.CRASH;
			case RECOVER:
				return SchedulingSimulator.Control.Kind.RECOVER;
			default:
				return null;
		}
	}

	// ------------------------------------------------------------ library

	/** Built-in scenarios for common node-adjustment experiments. */
	static List<Scenario> library()
	{
		ArrayList<Scenario> scenarios = new ArrayList<Scenario>();
		scenarios.add(NONE);
		scenarios.add(builtIn(
				"name: Pause and resume\n"
				+ "description: node 2 stops claiming work between 30% and 60% of the running unit phase\n"
				+ "at 30% pause node 2\n"
				+ "at 60% resume node 2\n"));
		scenarios.add(builtIn(
				"name: Drain mid-phase\n"
				+ "description: node 2 leaves when the running unit phase is 40% done; its work moves to the others\n"
				+ "at 40% drain node 2\n"));
		scenarios.add(builtIn(
				"name: Drain and return\n"
				+ "description: node 2 is drained at 25% of the phase and rejoins at 60%\n"
				+ "at 25% drain node 2\n"
				+ "at 60% available node 2\n"));
		scenarios.add(builtIn(
				"name: Crash and recover\n"
				+ "description: node 2 crashes at 30% of the unit phase and is repaired 2 s later\n"
				+ "at 30% crash node 2 for 2s\n"));
		scenarios.add(builtIn(
				"name: Rolling slowdown\n"
				+ "description: node 1 drops to 50% capacity at 20% of the phase and recovers at 70%\n"
				+ "at 20% capacity node 1 = 50%\n"
				+ "at 70% capacity node 1 = 100%\n"));
		scenarios.add(builtIn(
				"name: Switch to work stealing\n"
				+ "description: the remaining work follows work stealing from 30% of the phase on\n"
				+ "at 30% policy work stealing\n"));
		scenarios.add(builtIn(
				"name: Input-time pause\n"
				+ "description: node 2 is paused while half of the input is still being delivered\n"
				+ "at 50% of input pause node 2\n"
				+ "at 75% of input resume node 2\n"));
		return scenarios;
	}

	private static Scenario builtIn(String text)
	{
		try
		{
			return parse(text);
		}
		catch (ScenarioException exception)
		{
			throw new IllegalStateException("Built-in scenario does not parse: " + exception.getMessage());
		}
	}

	/** The built-in scenario of that name, or null. */
	static Scenario builtInNamed(String name)
	{
		for (Scenario scenario : library())
		{
			if (scenario.getName().equalsIgnoreCase(name == null ? "" : name.trim()))
			{
				return scenario;
			}
		}
		return null;
	}
}
