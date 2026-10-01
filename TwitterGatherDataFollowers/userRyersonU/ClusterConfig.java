package TwitterGatherDataFollowers.userRyersonU;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A named cluster: its nodes' capacity profiles and its cluster-wide settings,
 * savable as a small text file so experiments can name the exact cluster they
 * ran on.
 *
 * <pre>
 * name: Heterogeneous trio
 * coordinator-speed: 1
 * time-dilation: auto
 * boost-headroom: 1
 * detection-ms: 500
 * uptime: exponential
 * weibull-shape: 1.5
 * repair: fixed
 * seed: 1146310992
 * node 1: role=Strong cpu=2 lanes=2 memory=2730 queue=4 latency=0 bandwidth=10000 slowdown=1 faults=0 mtbf=0 mttr=0
 * node 2: role=Normal cpu=1 lanes=1 memory=1365 queue=2 latency=0 bandwidth=1000 slowdown=1 faults=0 mtbf=0 mttr=0
 * </pre>
 *
 * Bandwidth is in Mbit/s (0 unlimited), latency in ms, MTBF/MTTR in seconds,
 * faults in percent; slowdown 1 means none.
 */
final class ClusterConfig
{
	private final String name;
	private final List<NodeCapacityProfile> profiles;
	private final ClusterSettings settings;

	ClusterConfig(String name, List<NodeCapacityProfile> profiles, ClusterSettings settings)
	{
		this.name = name == null || name.trim().length() == 0 ? "Cluster" : name.trim();
		ArrayList<NodeCapacityProfile> copy = new ArrayList<NodeCapacityProfile>();
		if (profiles != null)
		{
			for (NodeCapacityProfile profile : profiles)
			{
				if (profile != null)
				{
					copy.add(profile);
				}
			}
		}
		Collections.sort(copy, new java.util.Comparator<NodeCapacityProfile>()
		{
			public int compare(NodeCapacityProfile first, NodeCapacityProfile second)
			{
				return Integer.compare(first.getNodeIndex(), second.getNodeIndex());
			}
		});
		this.profiles = Collections.unmodifiableList(copy);
		this.settings = settings == null ? ClusterSettings.defaults() : settings;
	}

	/** The cluster the simulator is configured with now. */
	static ClusterConfig current(String name)
	{
		return new ClusterConfig(name, NodeCapacityRegistry.snapshot(), NodeCapacityRegistry.clusterSettings());
	}

	/** A cluster of {@code roles} (node i has roles[i - 1]) with default settings. */
	static ClusterConfig ofRoles(String name, int heapMb, NodeCapacityProfile.Role... roles)
	{
		ArrayList<NodeCapacityProfile> profiles = new ArrayList<NodeCapacityProfile>();
		for (int node = 1; node <= roles.length; node++)
		{
			profiles.add(NodeCapacityProfile.rolePreset(node, roles[node - 1], roles.length, heapMb));
		}
		return new ClusterConfig(name, profiles, ClusterSettings.defaults());
	}

	/** Built-in clusters for studies: homogeneous and heterogeneous sets of each size. */
	static List<ClusterConfig> presets(int heapMb)
	{
		NodeCapacityProfile.Role normal = NodeCapacityProfile.Role.NORMAL;
		NodeCapacityProfile.Role strong = NodeCapacityProfile.Role.STRONG;
		NodeCapacityProfile.Role weak = NodeCapacityProfile.Role.WEAK;
		NodeCapacityProfile.Role memory = NodeCapacityProfile.Role.MEMORY_HEAVY;
		NodeCapacityProfile.Role unreliable = NodeCapacityProfile.Role.UNRELIABLE;
		ArrayList<ClusterConfig> presets = new ArrayList<ClusterConfig>();
		presets.add(ofRoles("1 normal node (reference)", heapMb, normal));
		presets.add(ofRoles("3 normal nodes", heapMb, normal, normal, normal));
		presets.add(ofRoles("3 nodes: strong, normal, weak", heapMb, strong, normal, weak));
		presets.add(ofRoles("4 normal nodes", heapMb, normal, normal, normal, normal));
		presets.add(ofRoles("4 nodes: strong, normal, weak, weak", heapMb, strong, normal, weak, weak));
		presets.add(ofRoles("4 nodes: one unreliable", heapMb, normal, normal, normal, unreliable));
		presets.add(ofRoles("6 nodes: mixed", heapMb, strong, normal, normal, weak, memory, weak));
		presets.add(ofRoles("8 normal nodes", heapMb, normal, normal, normal, normal, normal, normal,
				normal, normal));
		return presets;
	}

	String getName()
	{
		return name;
	}

	List<NodeCapacityProfile> getProfiles()
	{
		return profiles;
	}

	ClusterSettings getSettings()
	{
		return settings;
	}

	int getNodeCount()
	{
		return profiles.size();
	}

	ClusterConfig withSettings(ClusterSettings value)
	{
		return new ClusterConfig(name, profiles, value);
	}

	ClusterConfig withName(String value)
	{
		return new ClusterConfig(value, profiles, settings);
	}

	/** Total throughput of the cluster in reference cores (speed x lanes). */
	double throughput()
	{
		double total = 0.0;
		for (NodeCapacityProfile profile : profiles)
		{
			total += profile.effectiveSpeed() * profile.getMaxConcurrentJobs();
		}
		return total;
	}

	/** One line: nodes and their roles. */
	String describe()
	{
		StringBuilder builder = new StringBuilder();
		LinkedHashMap<String,Integer> roles = new LinkedHashMap<String,Integer>();
		for (NodeCapacityProfile profile : profiles)
		{
			Integer count = roles.get(profile.getRoleLabel());
			roles.put(profile.getRoleLabel(), Integer.valueOf(count == null ? 1 : count.intValue() + 1));
		}
		for (Map.Entry<String,Integer> role : roles.entrySet())
		{
			if (builder.length() > 0)
			{
				builder.append(", ");
			}
			builder.append(role.getValue()).append(' ').append(role.getKey().toLowerCase(Locale.US));
		}
		return String.format(Locale.US, "%d node%s (%s), throughput %.2f", profiles.size(),
				profiles.size() == 1 ? "" : "s", builder, throughput());
	}

	// ------------------------------------------------------------ text

	String toText()
	{
		StringBuilder text = new StringBuilder();
		text.append("name: ").append(name).append('\n');
		text.append("coordinator-speed: ").append(number(settings.getCoordinatorSpeed())).append('\n');
		text.append("time-dilation: ").append(settings.isAutomaticTimeDilation() ? "auto"
				: number(settings.getTimeDilation())).append('\n');
		text.append("boost-headroom: ").append(number(settings.getBoostHeadroom())).append('\n');
		text.append("detection-ms: ").append(settings.getDetectionTimeoutMs()).append('\n');
		text.append("uptime: ").append(settings.getUptimeDistribution().name().toLowerCase(Locale.US)).append('\n');
		text.append("weibull-shape: ").append(number(settings.getWeibullShape())).append('\n');
		text.append("repair: ").append(settings.getRepairDistribution().name().toLowerCase(Locale.US)).append('\n');
		text.append("seed: ").append(settings.getSeed()).append('\n');
		for (NodeCapacityProfile profile : profiles)
		{
			text.append("node ").append(profile.getNodeIndex()).append(": ")
					.append("role=").append(profile.getRole().name())
					.append(" cpu=").append(number(profile.getCpuMultiplier()))
					.append(" lanes=").append(profile.getMaxConcurrentJobs())
					.append(" memory=").append(profile.getMemoryLimitMb())
					.append(" queue=").append(profile.getQueueCapacity())
					.append(" latency=").append(profile.getNetworkDelayMs())
					.append(" bandwidth=").append(number(profile.getBandwidthMbps()))
					.append(" slowdown=").append(number(profile.isSlowdownSimulationEnabled()
							? profile.getSlowdownMultiplier() : 1.0))
					.append(" faults=").append(profile.isFailureSimulationEnabled()
							? profile.getFailureProbabilityPercent() : 0)
					.append(" mtbf=").append(number(profile.getMtbfSeconds()))
					.append(" mttr=").append(number(profile.getMttrSeconds()))
					.append('\n');
		}
		return text.toString();
	}

	static ClusterConfig parse(String text) throws IOException
	{
		String name = "Cluster";
		ClusterSettings settings = ClusterSettings.defaults();
		ArrayList<NodeCapacityProfile> profiles = new ArrayList<NodeCapacityProfile>();
		String[] lines = (text == null ? "" : text).split("\\r?\\n");
		for (int index = 0; index < lines.length; index++)
		{
			String line = lines[index].trim();
			if (line.length() == 0 || line.startsWith("#"))
			{
				continue;
			}
			int colon = line.indexOf(':');
			if (colon < 0)
			{
				throw new IOException("Line " + (index + 1) + ": expected \"key: value\".");
			}
			String key = line.substring(0, colon).trim().toLowerCase(Locale.US);
			String value = line.substring(colon + 1).trim();
			try
			{
				if (key.equals("name"))
				{
					name = value;
				}
				else if (key.equals("coordinator-speed"))
				{
					settings = settings.withCoordinatorSpeed(Double.parseDouble(value));
				}
				else if (key.equals("time-dilation"))
				{
					settings = settings.withTimeDilation(value.equalsIgnoreCase("auto") ? 0.0
							: Double.parseDouble(value));
				}
				else if (key.equals("boost-headroom"))
				{
					settings = settings.withBoostHeadroom(Double.parseDouble(value));
				}
				else if (key.equals("detection-ms"))
				{
					settings = settings.withDetectionTimeoutMs(Integer.parseInt(value));
				}
				else if (key.equals("uptime"))
				{
					settings = settings.withFailureDistributions(
							ClusterSettings.UptimeDistribution.valueOf(value.toUpperCase(Locale.US)),
							settings.getWeibullShape(), settings.getRepairDistribution());
				}
				else if (key.equals("weibull-shape"))
				{
					settings = settings.withFailureDistributions(settings.getUptimeDistribution(),
							Double.parseDouble(value), settings.getRepairDistribution());
				}
				else if (key.equals("repair"))
				{
					settings = settings.withFailureDistributions(settings.getUptimeDistribution(),
							settings.getWeibullShape(),
							ClusterSettings.RepairDistribution.valueOf(value.toUpperCase(Locale.US)));
				}
				else if (key.equals("seed"))
				{
					settings = settings.withSeed(Long.parseLong(value));
				}
				else if (key.startsWith("node "))
				{
					int node = Integer.parseInt(key.substring(5).trim());
					profiles.add(parseNode(node, value));
				}
				else
				{
					throw new IOException("unknown key " + key);
				}
			}
			catch (RuntimeException invalid)
			{
				throw new IOException("Line " + (index + 1) + ": " + invalid.getMessage(), invalid);
			}
		}
		if (profiles.isEmpty())
		{
			throw new IOException("The cluster has no nodes.");
		}
		return new ClusterConfig(name, profiles, settings);
	}

	private static NodeCapacityProfile parseNode(int node, String fields)
	{
		LinkedHashMap<String,String> values = new LinkedHashMap<String,String>();
		for (String field : fields.split("\\s+"))
		{
			int equals = field.indexOf('=');
			if (equals > 0)
			{
				values.put(field.substring(0, equals).toLowerCase(Locale.US), field.substring(equals + 1));
			}
		}
		NodeCapacityProfile.Role role = NodeCapacityProfile.Role.valueOf(
				get(values, "role", "NORMAL").toUpperCase(Locale.US));
		double slowdown = Double.parseDouble(get(values, "slowdown", "1"));
		int faults = Integer.parseInt(get(values, "faults", "0"));
		return new NodeCapacityProfile(
				node,
				role,
				Double.parseDouble(get(values, "cpu", "1")),
				Integer.parseInt(get(values, "memory", "1024")),
				Integer.parseInt(get(values, "lanes", "1")),
				Integer.parseInt(get(values, "latency", "0")),
				Integer.parseInt(get(values, "queue", "2")),
				slowdown > 1.0,
				slowdown,
				faults > 0,
				faults,
				Double.parseDouble(get(values, "bandwidth", "0")),
				Double.parseDouble(get(values, "mtbf", "0")),
				Double.parseDouble(get(values, "mttr", "0")));
	}

	private static String get(Map<String,String> values, String key, String fallback)
	{
		String value = values.get(key);
		return value == null || value.length() == 0 ? fallback : value;
	}

	private static String number(double value)
	{
		if (value == Math.rint(value) && Math.abs(value) < 1.0e15)
		{
			return Long.toString((long)value);
		}
		return String.format(Locale.US, "%.6f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
	}

	void save(File file) throws IOException
	{
		File parent = file.getAbsoluteFile().getParentFile();
		if (parent != null && !parent.isDirectory() && !parent.mkdirs())
		{
			throw new IOException("Cannot create " + parent);
		}
		Files.write(file.toPath(), toText().getBytes(StandardCharsets.UTF_8));
	}

	static ClusterConfig load(File file) throws IOException
	{
		return parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
	}

	/** The folder saved clusters live in. */
	static File directory()
	{
		return new File(System.getProperty("dsmp.clusters.dir", "Clusters"));
	}

	/** Every saved cluster (unreadable files are skipped). */
	static List<ClusterConfig> saved()
	{
		ArrayList<ClusterConfig> clusters = new ArrayList<ClusterConfig>();
		File[] files = directory().listFiles();
		if (files == null)
		{
			return clusters;
		}
		java.util.Arrays.sort(files);
		for (File file : files)
		{
			if (file.getName().endsWith(".cluster"))
			{
				try
				{
					clusters.add(load(file));
				}
				catch (IOException unreadable)
				{
					System.err.println("Skipping cluster file " + file + ": " + unreadable.getMessage());
				}
			}
		}
		return clusters;
	}

	/** Applies this cluster to the simulator: the next run uses it. */
	void apply(int heapMb)
	{
		NodeCapacityRegistry.setProfiles(profiles, heapMb);
		NodeCapacityRegistry.setClusterSettings(settings);
	}
}
