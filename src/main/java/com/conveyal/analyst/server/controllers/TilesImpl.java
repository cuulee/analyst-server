package com.conveyal.analyst.server.controllers;

/** These are tiles which require authentication in all cases */
public class TilesImpl extends Tiles {
/*
	public static Promise<Result> spatial(String shapefileId, Integer x, Integer y, Integer z, String selectedAttributes) {


		AnalystTileRequest tileRequest = new SpatialTile(shapefileId, x, y, z, selectedAttributes);
		return tileBuilder(tileRequest);
    }

	public static Promise<Result> shape(String shapefileId, Integer x, Integer y, Integer z, String attributeName) {

		AnalystTileRequest tileRequest = new ShapefileTile(shapefileId, x, y, z, attributeName);
		return tileBuilder(tileRequest);
    }

	public static Promise<Result> query(String queryId, Integer x, Integer y, Integer z,
			Integer timeLimit, String weightByShapefile, String weightByAttribute, String groupBy,
			String which, String attributeName, String compareTo) {

		ResultEnvelope.Which whichEnum;
		try {
			whichEnum = ResultEnvelope.Which.valueOf(which);
		} catch (Exception e) {
			// no need to pollute the console with a stack trace
			return Promise.promise(new Function0<Result> () {
				@Override
				public Result apply() throws Throwable {
				    return badRequest("Invalid value for which parameter");
				}
			});
		}

		AnalystTileRequest tileRequest;
		
		if (compareTo == null)
			tileRequest = new QueryTile(queryId, x, y, z, timeLimit, weightByShapefile, weightByAttribute, groupBy, whichEnum, attributeName);
		else
			tileRequest = new QueryComparisonTile(queryId, compareTo, x, y, z, timeLimit, weightByShapefile, weightByAttribute, groupBy, whichEnum, attributeName);
		
		return tileBuilder(tileRequest);
    }

	public static Promise<Result> transit(final String scenarioId, final Integer x, final Integer y, final Integer z) {

		AnalystTileRequest tileRequest = new TransitTile(scenarioId, x, y, z);
		return tileBuilder(tileRequest);

	}

	public static Promise<Result> transitComparison(String scenarioId1, String scenarioId2, Integer x, Integer y, Integer z) {

		AnalystTileRequest tileRequest = new TransitComparisonTile(scenarioId1, scenarioId2, x, y, z);
		return tileBuilder(tileRequest);

	}

	public static Result traffic(String scenarioId, Integer x, Integer y, Integer z) {

		response().setHeader(CACHE_CONTROL, "no-cache, no-store, must-revalidate");
		response().setHeader(PRAGMA, "no-cache");
		response().setHeader(EXPIRES, "0");

		String tileIdPrefix = "transit_" + scenarioId;

    	Tile tile = new Tile(tileIdPrefix, x, y, z);

    	try {
	    	if(!tileCache.containsKey(tile.tileId)) {
	    		//Api.analyst.getGraph(scenarioId).getGeomIndex().

	    		//for(TransitSegment ts : segments) {
	    		//	Color color;

	    		//	color = new Color(0.6f,0.6f,1.0f,0.25f);

	    		//	tile.renderLineString(ts.geom, color);
	    		//}
	    		//tileCache.put(tile.tileId, tile.generateImage());
	    	}

			ByteArrayInputStream bais = new ByteArrayInputStream(tileCache.get(tile.tileId));

		    response().setContentType("image/png");
			return ok(bais);


		} catch (Exception e) {
	    	e.printStackTrace();
	    	return badRequest();
	    }

		return ok();

	}*/
}
