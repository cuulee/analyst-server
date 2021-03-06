package com.conveyal.analyst.server.jobs;

import com.conveyal.analyst.server.AnalystMain;
import com.conveyal.analyst.server.utils.HashUtils;
import com.conveyal.analyst.server.utils.ZipUtils;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import models.Bundle;
import org.apache.commons.io.FileUtils;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Process an uploaded GTFS file or shapefile. Warning: will delete upload file when job completes.
 */
public class ProcessTransitBundleJob implements Runnable {
	private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(ProcessTransitBundleJob.class);

	private Bundle bundle;
	private File uploadFile;
	private String bundleType;
	private String augmentBundleId;
	private boolean deleteWhenDone;

	public ProcessTransitBundleJob(Bundle bundle, File uploadFile,
			String bundleType, String augmentBundleId, boolean deleteWhenDone) {
		this.bundle = bundle;
		this.uploadFile = uploadFile;
		this.bundleType = bundleType;
		this.augmentBundleId = augmentBundleId;
		this.deleteWhenDone = deleteWhenDone;
	}
	
	public void run() {

		bundle.processingGtfs = true;
		bundle.save();

		try {

			ZipFile zipFile = new ZipFile(uploadFile);

			Enumeration<? extends ZipEntry> entries = zipFile.entries();

			String shpFile = null;
			String confFile = null;
			ZipEntry osmFile = null;
			
			// this allows one to upload a ZIP file full of GTFS files and have them all added.
			List<ZipEntry> zips = Lists.newArrayList(); 
			
			while(entries.hasMoreElements()) {

				ZipEntry entry = entries.nextElement();
				
				// don't try to use the weird Apple files
				if (entry.getName().startsWith("__MACOSX"))
					continue;

				if (entry.getName().toLowerCase().endsWith(".shp") && !entry.isDirectory())
					shpFile = entry.getName();
				
				if (entry.getName().toLowerCase().endsWith(".json") && !entry.isDirectory())
					confFile = entry.getName();
				
				if (entry.getName().toLowerCase().endsWith(".zip") && !entry.isDirectory())
					zips.add(entry);

				if (entry.getName().toLowerCase().endsWith(".pbf") && !entry.isDirectory())
					osmFile = entry;
			}

			File newFile;

			File outputDirectory = bundle.getTempShapeDirPath();
			
			// the files that are needed for this graph build
			List<File> graphFiles = new ArrayList<File>(2);
			
			if (confFile != null && shpFile != null) {				
				zipFile = new ZipFile(uploadFile);

				ZipUtils.unzip(zipFile, outputDirectory);

				File shapeFile = new File(outputDirectory, shpFile);
				File configFile = new File(outputDirectory, confFile);
				
				newFile = new File(bundle.getBundleDataPath(), HashUtils.hashFile(uploadFile) + ".zip");
				new Geom2GtfsJob(bundle, configFile, shapeFile, newFile).run(); // run in current thread

				FileUtils.deleteDirectory(outputDirectory);
				zipFile.close();
				graphFiles.add(newFile);
			}
			else if (!zips.isEmpty()) {
				int i = 0;
				for (ZipEntry ze : zips) {
					File file = new File(bundle.getBundleDataPath(), bundle.id + "_gtfs_" + (i++) + ".zip");
					ZipUtils.unzip(zipFile, ze, file);
					graphFiles.add(file);
				}
			}
			else  {
				newFile = new File(bundle.getBundleDataPath(), bundle.id + "_gtfs.zip");
				FileUtils.copyFile(uploadFile, newFile);
				graphFiles.add(newFile);
			}
			
			if((bundleType != null && augmentBundleId != null && bundleType.equals("augment"))) 
			{	
				for(File f : Bundle.getBundle(augmentBundleId).getBundleDataPath().listFiles()) {
					if(f.getName().toLowerCase().endsWith(".zip")) {
						FileUtils.copyFileToDirectory(f, bundle.getBundleDataPath());
						graphFiles.add(new File(bundle.getBundleDataPath(), f.getName()));
					}
				}
			}

			bundle.processGtfs();
			bundle.processingGtfs = false;

			if (bundle.failed) {
				bundle.save();
				return;
			}

			bundle.processingOsm = true;
			bundle.save();

			File osmPbfFile = new File(bundle.getBundleDataPath(), bundle.id + ".osm.pbf");

			if (osmFile != null) {
				ZipUtils.unzip(zipFile, osmFile, osmPbfFile);
			}
			else {
				Double south = bundle.bounds.north < bundle.bounds.south ? bundle.bounds.north : bundle.bounds.south;
				Double west = bundle.bounds.east < bundle.bounds.west ? bundle.bounds.east : bundle.bounds.west;
				Double north = bundle.bounds.north > bundle.bounds.south ? bundle.bounds.north : bundle.bounds.south;
				Double east = bundle.bounds.east > bundle.bounds.west ? bundle.bounds.east : bundle.bounds.west;

				String vexUrl = AnalystMain.config.getProperty("application.vex");

				if (!vexUrl.endsWith("/"))
					vexUrl += "/";

				vexUrl += String.format("%.6f,%.6f,%.6f,%.6f.pbf", south, west, north, east);

				HttpURLConnection conn = (HttpURLConnection) new URL(vexUrl).openConnection();

				conn.connect();

				if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
					LOG.warn("Received response code {} from vex server", conn.getResponseCode());
					bundle.failed = true;
					bundle.save();
					return;
				}

				// download the file
				LOG.info("Beginning to download OSM data...");
				InputStream is = conn.getInputStream();
				OutputStream os = new FileOutputStream(osmPbfFile);
				ByteStreams.copy(is, os);
				is.close();
				os.close();
				LOG.info("OSM PBF retrieved.");
			}

			graphFiles.add(osmPbfFile);

			zipFile.close();

			if (deleteWhenDone)
				uploadFile.delete(); // don't need this file anymore

		} catch (IOException e) {
			LOG.error("Failed to process GTFS", e);

			bundle.failed = true;
			bundle.save();

			return;
		}

		bundle.processingGtfs = false;
		bundle.processingOsm = false;
		bundle.save();
		
		try {
			bundle.writeToClusterCache();
		} catch (IOException e) {
			LOG.error("Failed to write graph to cluster cache", e);
		}
	}
}
