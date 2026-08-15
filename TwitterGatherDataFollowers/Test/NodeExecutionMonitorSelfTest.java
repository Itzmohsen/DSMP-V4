package TwitterGatherDataFollowers.userRyersonU;

import java.util.concurrent.atomic.AtomicInteger;

public final class NodeExecutionMonitorSelfTest
{
	private NodeExecutionMonitorSelfTest()
	{
	}

	public static void main(String[] args)
	{
		final AtomicInteger notifications = new AtomicInteger();
		NodeExecutionMonitor.Listener listener =
				new NodeExecutionMonitor.Listener()
				{
					public void onSnapshot(NodeExecutionMonitor.Snapshot snapshot)
					{
						notifications.incrementAndGet();
					}
				};
		NodeExecutionMonitor.addListener(listener);

		NodeExecutionMonitor.beginExperiment(3, "Doc2Vec");
		NodeExecutionMonitor.Snapshot snapshot = NodeExecutionMonitor.snapshot();
		assertEquals(3, snapshot.getRequestedNodes(), "requested nodes");
		assertEquals(3, snapshot.getNodes().size(), "provisioned nodes");
		assertEquals("Doc2Vec", snapshot.getAlgorithmName(), "algorithm");

		NodeExecutionMonitor.nodeReady("Recommender-ServiceAgent1", 100);
		NodeExecutionMonitor.nodeReady("Recommender-ServiceAgent2", 80);
		NodeExecutionMonitor.beginNodeRun(
				"Recommender-ServiceAgent1", "run-a", 100);
		NodeExecutionMonitor.beginNodeRun(
				"Recommender-ServiceAgent2", "run-a", 80);
		assertEquals(1, NodeExecutionMonitor.snapshot().getRunSequence(),
				"one shared run");

		NodeExecutionMonitor.updateTweets(
				"Recommender-ServiceAgent1", 50, 100);
		NodeExecutionMonitor.NodeSnapshot first =
				findNode(NodeExecutionMonitor.snapshot(), 1);
			assertEquals(50, first.getProgressPercent(), "input progress");

			NodeExecutionMonitor.waitingForInputCompletion(
					"Recommender-ServiceAgent1", 100, 100);
			first = findNode(NodeExecutionMonitor.snapshot(), 1);
			assertEquals(NodeExecutionMonitor.State.WAITING_FOR_INPUT,
					first.getState(), "input barrier wait state");

			NodeExecutionMonitor.waitingForMemory(
				"Recommender-ServiceAgent1", "SVM training", 512);
		first = findNode(NodeExecutionMonitor.snapshot(), 1);
		assertEquals(NodeExecutionMonitor.State.WAITING_FOR_MEMORY,
				first.getState(), "memory queue state");
		assertEquals(512L, first.getReservationMb(), "memory reservation");

		NodeExecutionMonitor.memoryStageActive(
				"Recommender-ServiceAgent1", "SVM training", 512);
		NodeExecutionMonitor.complete("Recommender-ServiceAgent1");
		assertEquals(NodeExecutionMonitor.State.COMPLETE,
				findNode(NodeExecutionMonitor.snapshot(), 1).getState(),
				"successful terminal state");

		NodeExecutionMonitor.failed(
				"Recommender-ServiceAgent2",
				new IllegalStateException("test failure"));
		NodeExecutionMonitor.publishing("Recommender-ServiceAgent2");
		NodeExecutionMonitor.complete("Recommender-ServiceAgent2");
		assertEquals(NodeExecutionMonitor.State.FAILED,
				findNode(NodeExecutionMonitor.snapshot(), 2).getState(),
				"failure must remain visible");

		NodeExecutionMonitor.beginNodeRun(
				"Recommender-ServiceAgent1", "run-b", 120);
		snapshot = NodeExecutionMonitor.snapshot();
		assertEquals(2, snapshot.getRunSequence(), "next run sequence");
		assertEquals(NodeExecutionMonitor.State.RECEIVING,
				findNode(snapshot, 1).getState(), "new run resets node one");
		assertEquals(NodeExecutionMonitor.State.READY,
				findNode(snapshot, 2).getState(), "new run resets peer failure");

		if (notifications.get() < 10)
		{
			throw new AssertionError(
					"listener received too few telemetry updates: "
							+ notifications.get());
		}

		NodeExecutionMonitor.removeListener(listener);
		NodeExecutionMonitor.reset();
		System.out.println("NodeExecutionMonitorSelfTest passed");
	}

	private static NodeExecutionMonitor.NodeSnapshot findNode(
			NodeExecutionMonitor.Snapshot snapshot, int index)
	{
		for (NodeExecutionMonitor.NodeSnapshot node : snapshot.getNodes())
		{
			if (node.getNodeIndex() == index)
			{
				return node;
			}
		}
		throw new AssertionError("missing node " + index);
	}

	private static void assertEquals(
			Object expected, Object actual, String label)
	{
		if (expected == null ? actual != null : !expected.equals(actual))
		{
			throw new AssertionError(
					label + ": expected " + expected + " but got " + actual);
		}
	}

	private static void assertEquals(int expected, int actual, String label)
	{
		if (expected != actual)
		{
			throw new AssertionError(
					label + ": expected " + expected + " but got " + actual);
		}
	}

	private static void assertEquals(long expected, long actual, String label)
	{
		if (expected != actual)
		{
			throw new AssertionError(
					label + ": expected " + expected + " but got " + actual);
		}
	}
}
