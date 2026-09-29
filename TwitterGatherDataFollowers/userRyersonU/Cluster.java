package TwitterGatherDataFollowers.userRyersonU;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import TwitterGatherDataFollowers.userRyersonU.Point;
 
public class Cluster {
	
	public List<Point> points;
	public Point centroid;
	public int id;
	public Map<String,Double> allTermsTfidf;
	
	//Creates a new Cluster
	public Cluster(int id) {
		this.id = id;
		this.points = new ArrayList<Point>();
		this.centroid = null;
	}
 
	/**
	 * The live point list.
	 *
	 * Returned by reference: callers that need to hold an assignment across
	 * iterations must copy it, because {@link #clear()} empties this same list.
	 */
	public List<Point> getPoints() {
		return points;
	}
	
	public void addPoint(Point point) {
		points.add(point);
	}
 
	public void setPoints(List<Point> points) {
		this.points = points;
	}
 
	public Point getCentroid() {
		return centroid;
	}
 
	public void setCentroid(Point centroid) {
		this.centroid = centroid;
	}
 
	public int getId() {
		return id;
	}
	
	public void clear() {
		points.clear();
	}
	
	/**
	 * Cluster dump files this JVM has already truncated.
	 *
	 * These files were opened in append mode and never truncated, so the three
	 * clustersIdTFIDF_*.txt files in a working tree held interleaved output from
	 * every run ever executed there. The first write of a run now truncates; the
	 * rest of that run still appends.
	 */
	private static final Set<String> TRUNCATED_OUTPUT_FILES =
			ConcurrentHashMap.newKeySet();

	/** Run whose dumps the tracker currently describes. */
	private static String currentOutputRunId;

	/**
	 * Starts a new run's cluster dumps.
	 *
	 * Every recommender agent calls this, and all of them write to the same
	 * clustersIdTFIDF_*.txt files, so the tracker is cleared only when the run id
	 * actually changes. Clearing per agent would make each node truncate the file
	 * again and discard the previous node's clusters.
	 */
	public static synchronized void resetClusterOutputFiles(String runId)
	{
		String key = runId == null ? "" : runId;
		if (!key.equals(currentOutputRunId))
		{
			currentOutputRunId = key;
			TRUNCATED_OUTPUT_FILES.clear();
		}
	}

	private static FileWriter openClusterOutput(String fileNameOutput) throws IOException
	{
		boolean append = !TRUNCATED_OUTPUT_FILES.add(fileNameOutput);
		return new FileWriter(fileNameOutput, append);
	}

	public void plotCluster() {
		Map<String,Double> tfidf;
		String userName;
		String fileNameOutput = "clustersIdTFIDF_"+id+"_result.txt";
		FileWriter writer;
		try {
			writer = openClusterOutput(fileNameOutput);
			BufferedWriter bufferedWriter = new BufferedWriter(writer);
			//System.out.println("[Cluster: " + id+"]");
			bufferedWriter.write("[Cluster: " + id+"]");
			bufferedWriter.newLine();
			//System.out.println("[Centroid: " + centroid + "]");
			//bufferedWriter.write("[Centroid: " + centroid + "]");
			bufferedWriter.write("[Centroid: ");
			tfidf = centroid.getTfidf_or_Tf();
			String centroidWords="";
			for (String word : tfidf.keySet())
			{
				if (tfidf.get(word) > 0)
					centroidWords = centroidWords + " " + word;
			}
			bufferedWriter.write(centroidWords);
			bufferedWriter.write("]");
			bufferedWriter.newLine();
			//System.out.println("[Points: "+points.size());
			bufferedWriter.write("[Points: "+points.size());
			bufferedWriter.newLine();
			for(Point p : points) {
				//System.out.println(p);
				String words="";
				tfidf = p.getTfidf_or_Tf();
				userName = p.getUserName();
				bufferedWriter.write("userName: " + userName + ",");
				for (String word : tfidf.keySet())
				{
					words = words + " " + word;
				}
				bufferedWriter.write(words);
				//bufferedWriter.write(p.toString());
				bufferedWriter.newLine();
			}
			//System.out.println("]");
			bufferedWriter.write("]");
			bufferedWriter.newLine();
			bufferedWriter.close();
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} //append
		
		
	}
	
	
	public void plotClusterTweets() {
		Map<String,Double> tfidf;
		long tweetId;
		String fileNameOutput = "clustersIdTFIDF_"+id+"_result.txt";
		FileWriter writer;
		try {
			writer = openClusterOutput(fileNameOutput);
			BufferedWriter bufferedWriter = new BufferedWriter(writer);
			//System.out.println("[Cluster: " + id+"]");
			bufferedWriter.write("[Cluster: " + id+"]");
			bufferedWriter.newLine();
			//System.out.println("[Centroid: " + centroid + "]");
			//bufferedWriter.write("[Centroid: " + centroid + "]");
			bufferedWriter.write("[Centroid: ");
			tfidf = centroid.getTfidf_or_Tf();
			String centroidWords="";
			for (String word : tfidf.keySet())
			{
				if (tfidf.get(word) > 0)
					centroidWords = centroidWords + " " + word;
			}
			bufferedWriter.write(centroidWords);
			bufferedWriter.write("]");
			bufferedWriter.newLine();
			//System.out.println("[Points: "+points.size());
			bufferedWriter.write("[Points: "+points.size());
			bufferedWriter.newLine();
			for(Point p : points) {
				//System.out.println(p);
				String words="";
				tfidf = p.getTfidf_or_Tf();
				tweetId = p.getTweetId();
				bufferedWriter.write(tweetId+",");
				for (String word : tfidf.keySet())
				{
					words = words + " " + word;
				}
				bufferedWriter.write(words);
				//bufferedWriter.write(p.toString());
				bufferedWriter.newLine();
			}
			//System.out.println("]");
			bufferedWriter.write("]");
			bufferedWriter.newLine();
			bufferedWriter.close();
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} //append
		
		
	}
	
	public void plotClusterTweets(LinkedHashMap<Long,String> tweetIdUser) {
		Map<String,Double> tfidf;
		long tweetId;
		String fileNameOutput = "clustersIdTFIDF_"+id+"_result.txt";
		FileWriter writer;
		try {
			writer = openClusterOutput(fileNameOutput);
			BufferedWriter bufferedWriter = new BufferedWriter(writer);
			//System.out.println("[Cluster: " + id+"]");
			bufferedWriter.write("[Cluster: " + id+"]");
			bufferedWriter.newLine();
			//System.out.println("[Centroid: " + centroid + "]");
			//bufferedWriter.write("[Centroid: " + centroid + "]");
			bufferedWriter.write("[Centroid: ");
			tfidf = centroid.getTfidf_or_Tf();
			String centroidWords="";
			for (String word : tfidf.keySet())
			{ 
				if (tfidf.get(word) > 0)
					centroidWords = centroidWords + " " + word;
			}
			bufferedWriter.write(centroidWords);
			bufferedWriter.write("]");
			bufferedWriter.newLine();
			//System.out.println("[Points: "+points.size());
			bufferedWriter.write("[Cluster Member(s): "+points.size());
			bufferedWriter.newLine();
			for(Point p : points) {
				//System.out.println(p);
				String words="";
				tfidf = p.getTfidf_or_Tf();
				tweetId = p.getTweetId();
				bufferedWriter.write(tweetIdUser.get(tweetId)+",");
				bufferedWriter.write(tweetId+",");
				for (String word : tfidf.keySet())
				{
					words = words + " " + word;
				}
				bufferedWriter.write(words);
				//bufferedWriter.write(p.toString());
				bufferedWriter.newLine();
			}
			//System.out.println("]");
			bufferedWriter.write("]");
			bufferedWriter.newLine();
			bufferedWriter.close();
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} //append
		
		
	}
	
	public String getClusterResultAreaText() {
		Map<String,Double> tfidf;
		String userName;
		String resultText = "";
		
		//Original result text
		// resultText+="[Cluster: " + id+"]\n";
		// resultText+="[Centroid: ";
		// tfidf = centroid.getTfidf_or_Tf();	
		// String centroidWords="";
		// for (String word : tfidf.keySet())
		// {
			// if (tfidf.get(word) > 0)
				// centroidWords = centroidWords + " " + word;
		// }
		// resultText+=centroidWords+"]"+"\n";
		
		// resultText+="[Points: "+points.size()+"\n";
		// for(Point p : points) {
			// String words="";
			// tfidf = p.getTfidf_or_Tf();
			// userName = p.getUserName();
			// resultText+="userName: " + userName + ",";
			// for (String word : tfidf.keySet())
			// {
				// words = words + " " + word;
			// }
			// resultText+=words+"\n";
		// }
		// resultText+="]\n"
		
		//Modified Result Text
		resultText+="[Cluster: " + id+"]\n";
				
		resultText+="[Points: "+points.size()+"]\n";
		resultText+="[Usernames: ";
		for (int i = 0; i < points.size(); i++)
		{
			userName = points.get(i).getUserName();
			if (i == points.size()-1)
			{
				resultText+=userName+"]\n";
			}
			else
			{
				resultText+=userName+", ";
			}
		}
			
		return resultText;
	}
 
}