import java.io.IOException;

import models.Bundle;
import models.Query;
import models.Shapefile;
import models.User;
import controllers.Api;
import play.Application;
import play.GlobalSettings;
import play.api.mvc.EssentialFilter;
import utils.QueryResultStore;

public class Global extends GlobalSettings {
  
	@Override
	public void onStart(Application app) {
		Bundle.importBundlesAsNeeded();			
		
		// upload to S3
		try {
			Bundle.writeAllToClusterCache();
			Shapefile.writeAllToClusterCache();
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}

		for (Query q : Query.getAll()) {
			if (!q.completePoints.equals(q.totalPoints)) {
				QueryResultStore.accumulate(q);
			}
		}
	}
}
