package TwitterGatherDataFollowers.userRyersonU;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

import org.gephi.graph.api.Column;
import org.gephi.graph.api.Graph;
import org.gephi.graph.api.GraphController;
import org.gephi.graph.api.GraphModel;
import org.gephi.graph.api.Node;
import org.gephi.io.importer.api.Container;
import org.gephi.io.importer.api.EdgeDirectionDefault;
import org.gephi.io.importer.api.ImportController;
import org.gephi.io.processor.plugin.DefaultProcessor;
import org.gephi.project.api.ProjectController;
import org.gephi.project.api.Workspace;
import org.gephi.statistics.plugin.Modularity;
import org.openide.util.Lookup;

/**
 * Runs the same Gephi modularity path used by final graph visualization.
 *
 * This keeps Graph Insights numerically aligned with the Gephi Statistics panel
 * without changing recommendation scoring or the persisted DSMP edge files.
 */
final class GraphModularityAnalyzer
{
	private static final double GEPHI_RESOLUTION = 1.0;

	private GraphModularityAnalyzer()
	{
	}

	static Result analyze(
			File mappingFile,
			File edgeFile,
			Map<Integer, String> namesById) throws Exception
	{
		if (mappingFile == null || edgeFile == null)
		{
			throw new IllegalArgumentException("Graph modularity needs both map and edge files.");
		}

		File tempGraph = Files.createTempFile(
				"dsmp-graph-insights-", ".gml").toFile();
		ProjectController projectController = null;
		boolean projectCreated = false;
		try
		{
			GraphGmlBuilder.Result graphBuild =
					GraphGmlBuilder.build(edgeFile, mappingFile, tempGraph);

			projectController = require(
					Lookup.getDefault().lookup(ProjectController.class),
					"ProjectController");
			projectController.newProject();
			projectCreated = true;

			ImportController importController = require(
					Lookup.getDefault().lookup(ImportController.class),
					"ImportController");
			GraphController graphController = require(
					Lookup.getDefault().lookup(GraphController.class),
					"GraphController");
			Workspace workspace = require(
					projectController.getCurrentWorkspace(),
					"Workspace");

			Container container = importController.importFile(tempGraph);
			if (container == null || container.getLoader() == null)
			{
				throw new IOException("Gephi returned an empty GML import container.");
			}
			container.getLoader().setEdgeDefault(EdgeDirectionDefault.UNDIRECTED);
			container.getLoader().setAllowAutoNode(false);
			importController.process(container, new DefaultProcessor(), workspace);

			GraphModel graphModel = require(
					graphController.getGraphModel(), "GraphModel");
			Modularity modularity = new Modularity();
			modularity.setResolution(GEPHI_RESOLUTION);
			//Deterministic node ordering. With randomisation on, Gephi's Louvain
			//produced a different community assignment and a different modularity
			//value on every run over identical input.
			modularity.setRandom(false);
			modularity.setUseWeight(true);
			modularity.execute(graphModel);

			Column modularityColumn =
					graphModel.getNodeTable().getColumn(Modularity.MODULARITY_CLASS);
			if (modularityColumn == null)
			{
				throw new IOException("Gephi did not produce a modularity_class column.");
			}

			Graph graph = graphModel.getGraph();
			Map<Integer, Integer> sizesByClass = new LinkedHashMap<Integer, Integer>();
			Map<Integer, String> labelsByClass = new LinkedHashMap<Integer, String>();
			Map<Integer, Integer> communityByNodeId =
					new LinkedHashMap<Integer, Integer>();
			for (Node node : graph.getNodes())
			{
				Integer modularityClass =
						toInteger(node.getAttribute(modularityColumn));
				if (modularityClass == null)
				{
					continue;
				}
				Integer current = sizesByClass.get(modularityClass);
				sizesByClass.put(
						modularityClass,
						current == null ? 1 : current.intValue() + 1);
				if (!labelsByClass.containsKey(modularityClass))
				{
					labelsByClass.put(
							modularityClass,
							communityName(modularityClass, node, namesById));
				}
				Integer nodeId = toInteger(node.getId());
				if (nodeId != null)
				{
					communityByNodeId.put(nodeId, modularityClass);
				}
			}

			return new Result(
					sizesByClass,
					labelsByClass,
					communityByNodeId,
					modularity.getModularity(),
					graphBuild.getNodeCount(),
					graphBuild.getEdgeCount());
		}
		finally
		{
			try
			{
				Files.deleteIfExists(tempGraph.toPath());
			}
			catch (IOException ignored)
			{
				// Temporary diagnostics must never block graph analysis completion.
			}
			if (projectCreated && projectController != null)
			{
				try
				{
					projectController.closeCurrentProject();
				}
				catch (RuntimeException ignored)
				{
					// The analysis is read-only; failure to close should not hide results.
				}
			}
		}
	}

	private static String communityName(
			int modularityClass,
			Node node,
			Map<Integer, String> namesById)
	{
		Integer nodeId = toInteger(node.getId());
		String name = nodeId == null || namesById == null
				? null : namesById.get(nodeId);
		if (name == null || name.trim().length() == 0)
		{
			name = nodeId == null ? "node " + node.getId() : "node " + nodeId;
		}
		return "Gephi community " + modularityClass + " near " + name;
	}

	private static Integer toInteger(Object value)
	{
		if (value == null)
		{
			return null;
		}
		if (value instanceof Number)
		{
			return Integer.valueOf(((Number)value).intValue());
		}
		try
		{
			return Integer.valueOf(String.valueOf(value).trim());
		}
		catch (NumberFormatException ex)
		{
			return null;
		}
	}

	private static <T> T require(T value, String name) throws IOException
	{
		if (value == null)
		{
			throw new IOException("Gephi " + name + " is unavailable.");
		}
		return value;
	}

	static final class Result
	{
		final Map<Integer, Integer> communitySizes;
		final Map<Integer, String> communityNames;
		final Map<Integer, Integer> communityByNodeId;
		final double modularity;
		final int importedNodeCount;
		final long importedEdgeCount;

		private Result(
				Map<Integer, Integer> communitySizes,
				Map<Integer, String> communityNames,
				Map<Integer, Integer> communityByNodeId,
				double modularity,
				int importedNodeCount,
				long importedEdgeCount)
		{
			this.communitySizes =
					new LinkedHashMap<Integer, Integer>(communitySizes);
			this.communityNames =
					new LinkedHashMap<Integer, String>(communityNames);
			this.communityByNodeId =
					new LinkedHashMap<Integer, Integer>(communityByNodeId);
			this.modularity = modularity;
			this.importedNodeCount = importedNodeCount;
			this.importedEdgeCount = importedEdgeCount;
		}
	}
}
