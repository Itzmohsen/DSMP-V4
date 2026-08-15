package TwitterGatherDataFollowers.userRyersonU;

import jade.core.AID;
import jade.lang.acl.ACLMessage;

public final class TextBatchBackpressureSelfTest
{
	private TextBatchBackpressureSelfTest()
	{
	}

	public static void main(String[] args)
	{
		usesBoundedProvisioningAtObservedDatasetSize();
		coversEveryRowWithBoundedWindows();
		preservesAclReplyCorrelation();
		System.out.println("TextBatchBackpressureSelfTest passed");
	}

	private static void usesBoundedProvisioningAtObservedDatasetSize()
	{
		require(ControllerAgent.shouldUseBoundedTextAgentProvisioning(480),
				"480-user text datasets should use bounded corpus streaming");
	}

	private static void coversEveryRowWithBoundedWindows()
	{
		int totalRows = 1000000;
		int endExclusive = totalRows;
		int coveredRows = 0;
		int windows = 0;

		while (endExclusive > 0)
		{
			int start = MobileAgent.textBatchStartForEnd(endExclusive);
			int rows = endExclusive - start;
			require(rows > 0, "batch window did not advance");
			require(rows <= MobileAgent.maxTextBatchRows(),
					"batch window exceeded the configured memory bound");
			coveredRows += rows;
			windows++;
			endExclusive = start;
		}

		require(coveredRows == totalRows,
				"batch windows did not cover every dataset row exactly once");
		int expectedWindows =
				(totalRows + MobileAgent.maxTextBatchRows() - 1)
				/ MobileAgent.maxTextBatchRows();
		require(windows == expectedWindows,
				"batch window count was not the expected ceiling division");
	}

	private static void preservesAclReplyCorrelation()
	{
		ACLMessage batch = new ACLMessage(ACLMessage.INFORM);
		batch.setSender(new AID("sender@test-platform", AID.ISGUID));
		batch.addReceiver(new AID("receiver@test-platform", AID.ISGUID));
		batch.setConversationId("run-1");
		batch.setReplyWith("run-1:sender:7");

		ACLMessage acknowledgement = batch.createReply();
		require("run-1".equals(acknowledgement.getConversationId()),
				"batch acknowledgement lost the simulation conversation");
		require("run-1:sender:7".equals(acknowledgement.getInReplyTo()),
				"batch acknowledgement lost its reply correlation identifier");
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
