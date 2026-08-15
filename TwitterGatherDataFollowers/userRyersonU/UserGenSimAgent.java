package TwitterGatherDataFollowers.userRyersonU;

import java.io.File;

import jade.core.Agent;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

public class UserGenSimAgent extends Agent
{
	private static final long serialVersionUID = 1L;

	private int numArtificialTweets;
	private File corpusGenFile;
	private transient ControllerAgentGui myGui;
	private long randomSeed;

	protected void setup()
	{
		Object[] args = getArguments();
		if (args == null || args.length < 3)
		{
			System.err.println("User generation agent did not receive its required arguments.");
			doDelete();
			return;
		}

		numArtificialTweets = ((Integer) args[0]).intValue();
		corpusGenFile = (File) args[1];
		myGui = (ControllerAgentGui) args[2];
		randomSeed = args.length >= 4
				? ((Long) args[3]).longValue()
				: System.nanoTime();

		try
		{
			DFAgentDescription description = new DFAgentDescription();
			description.setName(getAID());
			ServiceDescription service = new ServiceDescription();
			service.setName("Distributed Recommender System");
			service.setType("User Generation Simulation Agent");
			description.addServices(service);
			DFService.register(this, description);
		}
		catch (FIPAException e)
		{
			System.err.println("Could not register the user generation agent: "
					+ e.getMessage());
		}

		addBehaviour(new UserGenerationBehaviour());
	}

	private final class UserGenerationBehaviour extends OneShotBehaviour
	{
		private static final long serialVersionUID = 1L;

		public void action()
		{
			try
			{
				UserGenerationEngine.Options options =
						new UserGenerationEngine.Options(
							corpusGenFile,
							new File("Dataset/TwitterObtained/GENERATED"),
							numArtificialTweets,
							randomSeed);
				UserGenerationEngine engine = new UserGenerationEngine(
						options,
						new UserGenerationEngine.ProgressListener() {
							public void onProgress(
									String stage,
									String detail,
									long completed,
									long total)
							{
								if (myGui != null)
								{
									myGui.updateUserGenerationProgress(
											stage, detail, completed, total);
								}
							}
						});

				UserGenerationEngine.Result result = engine.run();
				if (myGui != null)
				{
					myGui.showUserGenerationClusters(result.getClusters());
					myGui.completeUserGeneration(result);
				}
			}
			catch (Throwable failure)
			{
				failure.printStackTrace();
				if (myGui != null)
				{
					String message = failure.getMessage();
					if (message == null || message.trim().isEmpty())
					{
						message = failure.getClass().getSimpleName();
					}
					myGui.failUserGeneration(message);
				}
			}
			finally
			{
				doDelete();
			}
		}
	}

	protected void takeDown()
	{
		try
		{
			DFService.deregister(this);
		}
		catch (FIPAException e)
		{
			System.err.println("Could not deregister the user generation agent: "
					+ e.getMessage());
		}
	}
}
