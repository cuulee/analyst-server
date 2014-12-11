package models;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.opentripplanner.analyst.ResultSet;
import org.opentripplanner.profile.ProfileRequest;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.TraverseModeSet;

import otp.Analyst;
import otp.AnalystProfileRequest;
import play.Logger;
import play.libs.Akka;
import utils.DataStore;
import utils.HashUtils;
import utils.ResultEnvelope;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;

import com.conveyal.otpac.JobItemCallback;
import com.conveyal.otpac.message.JobSpec;
import com.conveyal.otpac.message.JobStatus;
import com.conveyal.otpac.message.WorkResult;
import com.conveyal.otpac.standalone.StandaloneCluster;
import com.conveyal.otpac.standalone.StandaloneExecutive;
import com.conveyal.otpac.standalone.StandaloneWorker;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.vividsolutions.jts.geom.Geometry;

import controllers.Api;
import controllers.Application;
import controllers.Tiles;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Query implements Serializable {

	private static HashMap<String, List<ResultEnvelope>> resultsQueue = new HashMap<String, List<ResultEnvelope>>();
	
	private static final long serialVersionUID = 1L;

	static DataStore<Query> queryData = new DataStore<Query>("queries");

	public String id;
	public String projectId;
	public String name;
	
	public Integer jobId;
	public String akkaId;

	public String mode;
	
	public String shapefileId;
	public String scenarioId;
	public String status;
	
	public Integer totalPoints;
	public Integer completePoints;
	
	@JsonIgnore 
	transient private DataStore<ResultEnvelope> results; 
	
	public Query() {
		
	}
	
	static public Query create() {
		
		Query query = new Query();
		query.save();
		
		return query;
	}
	
	/**
	 * Get the pointset name. This is used in the UI so that we can display the name of the pointset.
	 */
	public String getShapefileName () {
		Shapefile l = Shapefile.getShapefile(shapefileId);
		
		if (l == null)
			return null;
		
		return l.name;
	}
	
	/**
	 * Does this query use transit?
	 */
	public Boolean isTransit () {
		if (this.mode == null)
			return null;
		
		return new TraverseModeSet(this.mode).isTransit();
	}
	
	public void save() {
		
		// assign id at save
		if(id == null || id.isEmpty()) {
			
			Date d = new Date();
			id = HashUtils.hashString("q_" + d.toString());
			
			Logger.info("created query q " + id);
		}
		
		queryData.save(id, this);
		
		Logger.info("saved query q " +id);
	}
	
	public void run() {
		
		ActorRef queryActor = Akka.system().actorOf(Props.create(QueryActor.class));
		System.out.println(queryActor.path());
		
		queryActor.tell(this, null);
		
		akkaId = queryActor.path().name();
		
		save();
		
	}
	
	public void delete() throws IOException {
		queryData.delete(id);
		
		Logger.info("delete query q" +id);
	}

	@JsonIgnore
	public synchronized DataStore<ResultEnvelope> getResults() {
		
		if(results == null) {
			results = new DataStore<ResultEnvelope>(new File(Application.dataPath, "results"), "r_" + id);
		}
		
		return results;
	}
	
	public Integer getPercent() {
		if(this.totalPoints != null && this.completePoints != null && this.totalPoints > 0)
			return Math.round((float)((float)this.completePoints / (float)this.totalPoints) * 100); 
		else 
			return 0;
	}
	
	static public Query getQuery(String id) {
		
		return queryData.getById(id);	
	}
	
	static public Collection<Query> getQueries(String projectId) {
		
		if(projectId == null)
			return queryData.getAll();
		
		else {
			
			Collection<Query> data = new ArrayList<Query>();
			
			for(Query sd : queryData.getAll()) {
				if(sd.projectId != null && sd.projectId.equals(projectId))
					data.add(sd);
				
			}
				
			return data;
		}	
	}
	
	static void saveQueryResult(String id, ResultEnvelope resultEnvelope) {
		
		Query q = getQuery(id);

		if(q == null)
			return;

		ArrayList<ResultEnvelope> writeList = null;
		
		synchronized(resultsQueue) {
			if(!resultsQueue.containsKey(id))
				resultsQueue.put(id, new ArrayList<ResultEnvelope>());
			resultsQueue.get(id).add(resultEnvelope);
			
			if(resultsQueue.get(id).size() > 10) {
				writeList = new ArrayList<ResultEnvelope>(resultsQueue.get(id));
				resultsQueue.get(id).clear();
				Logger.info("flushing queue...");
			}
			
		}
		
		if(writeList != null){
				for(ResultEnvelope rf1 : writeList)
					q.getResults().saveWithoutCommit(rf1.id, rf1);
			
				q.getResults().commit();
				
				Tiles.resetQueryCache(id);
		}
					
		
	}
	
	static void updateStatus(String id, JobStatus js) {
		
		Query q = getQuery(id);
		
		if(q == null)
			return;
		
		synchronized(q) {
			q.totalPoints = (int)js.total;
			q.completePoints = (int)js.complete;
			q.jobId = js.curJobId;
			q.save();
		}
		
		Logger.info("status update: " + js.complete + "/" + js.total);
	}
	
	public static class QueryActor extends UntypedActor {
		
		public void onReceive(Object message) throws Exception {
			if (message instanceof Query) {
				
				final Query q = (Query)message;
	
				Shapefile sl = Shapefile.getShapefile(q.shapefileId);
				sl.writeToClusterCache(true);
				
				StandaloneCluster cluster = new StandaloneCluster("s3credentials", true, Api.analyst.getGraphService());
				
				StandaloneExecutive exec = cluster.createExecutive();
				StandaloneWorker worker = cluster.createWorker();
				
				cluster.registerWorker(exec, worker);
				
				JobSpec js;
				
				if (q.isTransit()) {
					// create a profile request
					ProfileRequest pr = Api.analyst.buildProfileRequest(q.scenarioId, q.mode, null);
					js = new JobSpec(q.scenarioId, q.shapefileId + ".json",  q.shapefileId + ".json", pr);
				}
				else {
					// this is not a transit request, no need for computationally-expensive profile routing 
					RoutingRequest rr = Api.analyst.buildRequest(q.scenarioId, null, q.mode, 120);
					js = new JobSpec(q.scenarioId, q.shapefileId + ".json",  q.shapefileId + ".json", rr);
				}

				// plus a callback that registers how many work items have returned
				class CounterCallback implements JobItemCallback {
					
					@Override
					public synchronized void onWorkResult(WorkResult res) {
						Query.saveQueryResult(q.id, new ResultEnvelope(res));
					}
				}
				
				CounterCallback callback = new CounterCallback();
				js.setCallback(callback);

				// start the job
				exec.find(js);
				
				JobStatus status = null;
				
				// wait for job to complete
				do {
					Thread.sleep(500);
					try {
						status = exec.getJobStatus().get(0);
						
						if(status != null)
							Query.updateStatus(q.id, status);
						else
							Logger.debug("waiting for job status messages, incomplete");
					}
					catch (Exception e) {
						Logger.debug("waiting for job status messages");
					}
					
					
				} while(status == null || !status.isComplete());
					
				cluster.stop(worker);
				
			}
		} 
	}
}
