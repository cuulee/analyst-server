package com.conveyal.analyst.server.utils;

import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.operation.union.UnaryUnionOp;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchIdentifierException;
import org.opengis.referencing.crs.CRSAuthorityFactory;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.crs.GeographicCRS;
import org.opengis.referencing.operation.MathTransform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class GeoUtils {
    private static final Logger LOG = LoggerFactory.getLogger(GeoUtils.class);

   public static double RADIANS = 2 * Math.PI;
   
   public static MathTransform recentMathTransform = null;
   public static GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(),4326);
   public static GeometryFactory projectedGeometryFactory = new GeometryFactory(new PrecisionModel());

   private static MathTransform aeaTransform = null;
   
   /**
    * From
    * http://gis.stackexchange.com/questions/28986/geotoolkit-conversion-from
    * -lat-long-to-utm
    */
   public static int
       getEPSGCodefromUTS(Coordinate refLonLat) {
     // define base EPSG code value of all UTM zones;
     int epsg_code = 32600;
     // add 100 for all zones in southern hemisphere
     if (refLonLat.y < 0) {
       epsg_code += 100;
     }
     // finally, add zone number to code
     epsg_code += getUTMZoneForLongitude(refLonLat.x);

     return epsg_code;
   }

 

   public static double getMetersInAngleDegrees(
     double distance) {
     return distance / (Math.PI / 180d) / 6378137d;
   }

   public static MathTransform getTransform(
     Coordinate refLatLon) {

     try {
       final CRSAuthorityFactory crsAuthorityFactory =
           CRS.getAuthorityFactory(false);
       
       
       final GeographicCRS geoCRS =
           crsAuthorityFactory.createGeographicCRS("EPSG:4326");

       final CoordinateReferenceSystem dataCRS = 
           crsAuthorityFactory
               .createCoordinateReferenceSystem("EPSG:" 
                   + getEPSGCodefromUTS(refLatLon)); //EPSG:32618

       final MathTransform transform =
           CRS.findMathTransform(geoCRS, dataCRS);
       
       GeoUtils.recentMathTransform = transform;
       
       return transform;
     } catch (NoSuchIdentifierException e) {
         LOG.error("Error retrieving EPSG data", e);
     } catch (final FactoryException e) {
         LOG.error("Error creating MathTransform", e);
     }

     return null;

   }

   /*
    * Taken from OneBusAway's UTMLibrary class
    */
   public static int getUTMZoneForLongitude(double lon) {

     if (lon < -180 || lon > 180)
       throw new IllegalArgumentException(
           "Coordinates not within UTM zone limits");

     int lonZone = (int) ((lon + 180) / 6);

     if (lonZone == 60)
       lonZone--;
     return lonZone + 1;
   }
   
   /**
    * Get the area of a geometry, in undefined units, by transforming to an equal-area projection.
    * 
    * The units are undefined because the scale varies across the map. However, two calls to getArea yield
    * units that are comparable.
    */
   public static double getArea (Geometry geom) {
	   // project the geometry to a cylindrical equal-area projection
	   if (aeaTransform == null) {
		   try {
			   CoordinateReferenceSystem aea = CRS.parseWKT("PROJCS[\"unnamed\",GEOGCS[\"WGS 84\",DATUM[\"WGS_1984\",SPHEROID[\"WGS 84\",6378137,298.257223563,AUTHORITY[\"EPSG\",\"7030\"]],TOWGS84[0,0,0,0,0,0,0],AUTHORITY[\"EPSG\",\"6326\"]],PRIMEM[\"Greenwich\",0,AUTHORITY[\"EPSG\",\"8901\"]],UNIT[\"degree\",0.0174532925199433,AUTHORITY[\"EPSG\",\"9108\"]],AUTHORITY[\"EPSG\",\"4326\"]],PROJECTION[\"Albers_Conic_Equal_Area\"],PARAMETER[\"standard_parallel_1\",0],PARAMETER[\"standard_parallel_2\",30],PARAMETER[\"latitude_of_center\",0],PARAMETER[\"longitude_of_center\",0],PARAMETER[\"false_easting\",0],PARAMETER[\"false_northing\",0],UNIT[\"Meter\",1]]");

			   aeaTransform = CRS.findMathTransform(DefaultGeographicCRS.WGS84, aea);
		   } catch (Exception e) {
			   throw new RuntimeException(e);
		   }
	   }
	   
	   // perform the transformation
	   try {
		   Geometry newGeom = JTS.transform(geom, aeaTransform);
		   return newGeom.getArea();
	   } catch (Exception e) {
		   throw new RuntimeException(e);
	   }
   }

    /**
     * Make a polygonal geometry valid, iff it is invalid. Returns non-polygonal geometries
     * and valid geometries untouched.
     */
    public static Geometry makeValid (Geometry in) {
        if (in instanceof Polygon) {
            if (in.isValid())
                return in;

            LOG.warn("Cleaning invalid polygon {}", in);

            List<Polygon> polys = JTS.makeValid((Polygon) in, false);
            return geometryFactory.createMultiPolygon(polys.toArray(new Polygon[polys.size()]));
        }
        else if (in instanceof MultiPolygon) {
            if (in.isValid())
                return in;

            LOG.warn("Cleaning invalid multipolygon {}", in);

            MultiPolygon mp = (MultiPolygon) in;
            List<Polygon> cleanedComponents = new ArrayList<>();

            for (int i = 0; i < mp.getNumGeometries(); i++) {
                Polygon p = (Polygon) mp.getGeometryN(i);

                if (p.isValid())
                    cleanedComponents.add(p);

                else
                    cleanedComponents.addAll(JTS.makeValid(p, false));
            }

            // we can't just build a multipolygon from the components, because the union of
            // many polygons is not necessarily a valid multipolygon (remember that components of
            // multipolygons cannot overlap). So we use a unary union.
            return UnaryUnionOp.union(cleanedComponents);
        }
        else {
            return in;
        }
    }
 }