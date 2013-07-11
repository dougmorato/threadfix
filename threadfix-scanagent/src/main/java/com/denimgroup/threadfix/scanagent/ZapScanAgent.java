////////////////////////////////////////////////////////////////////////
//
//     Copyright (c) 2009-2013 Denim Group, Ltd.
//
//     The contents of this file are subject to the Mozilla Public License
//     Version 2.0 (the "License"); you may not use this file except in
//     compliance with the License. You may obtain a copy of the License at
//     http://www.mozilla.org/MPL/
//
//     Software distributed under the License is distributed on an "AS IS"
//     basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
//     License for the specific language governing rights and limitations
//     under the License.
//
//     The Original Code is ThreadFix.
//
//     The Initial Developer of the Original Code is Denim Group, Ltd.
//     Portions created by Denim Group, Ltd. are Copyright (C)
//     Denim Group, Ltd. All Rights Reserved.
//
//     Contributor(s): Denim Group, Ltd.
//
////////////////////////////////////////////////////////////////////////

package com.denimgroup.threadfix.scanagent;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.zaproxy.clientapi.core.ApiResponse;
import org.zaproxy.clientapi.core.ApiResponseElement;
import org.zaproxy.clientapi.core.ApiResponseList;
import org.zaproxy.clientapi.core.ClientApi;
import org.zaproxy.clientapi.core.ClientApiException;

import com.denimgroup.threadfix.scanagent.configuration.TaskConfig;
import com.denimgroup.threadfix.scanagent.util.ZipFileUtils;

public class ZapScanAgent extends AbstractScanAgent {
	static final Logger log = Logger.getLogger(ZapScanAgent.class);
	
	/**
	 * This is the XML string that appears at the front end of the ZAP 2.1 XML returned from
	 * the ZAP API call
	 */
	private static final String XML_PRECURSOR_TO_REMOVE = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>";
	
	/**
	 * This is the XML string that should appear at the start of the ZAP XML report that needs
	 * to be uploaded
	 */
	private static final String XML_PRECURSOR_TO_ADD = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
	
	/**
	 * This is the XML string that needs to appear just before the ZAP-API-returned XML in order
	 * to make it look like the standard ZAP XML report. Normally the tag attributes would be filled
	 * in specific to the app. Here we are trying to to stub these out.
	 * TODO - Look at generating the host/name/ssl fields based on the TaskConfig if needed
	 */
	private static final String XML_PRECURSOR_TO_ADD_FINAL = "<site host=\"localhost\" name=\"http://localhost/\" port=\"80\" ssl=\"false\">";
	private static final String XML_POSTSCRIPT_TO_ADD = "<portscan/></site></OWASPZAPReport>";
	
	private int maxSpiderWaitInSeconds;
	private int maxScanWaitInSeconds;
	private int spiderPollWaitInSeconds;
	private int scanPollWaitInSeconds;
	private String zapHost;
	private int zapPort;
	private String zapExecutablePath;
	private long zapStartupWaitTime;
	
	
	public boolean doTask(TaskConfig theConfig) {
		
		boolean retVal = false;
		
		log.info("Attempting to do ZAP task with config: " + theConfig);
		log.info("Target URL is " + theConfig.getTargetUrlString());
		
		boolean status;
		
		status = startZap();
		if(status) {
			log.info("ZAP should be started");
		} else {
			log.warn("ZAP does not appear to have started. This will end well...");
		}
		
		log.info("Creating ZAP ClientApi");
		ClientApi zap = new ClientApi(zapHost, zapPort);
		log.info("ZAP ClientApi created");
		
		//	Determine if we need to set up a session for ZAP or if this is
		//	just an authenticated scan of the URL
		byte[] configFileData = theConfig.getDataBlob("configFileData");
		if(configFileData != null) {
			log.debug("Task configuration has configuration file data. Attempting to set session");
			//	Set up the session for ZAP to use
			try {
				FileUtils.deleteDirectory(new File(this.getWorkDir()));
				log.debug("Deleted old working directory. Going to attempt to re-create");
				boolean dirCreate = new File(this.getWorkDir()).mkdirs();
				if(!dirCreate) {
					log.warn("Unable to re-create working directory. This will end well...");
				}
				
				//	Take the config file ZIP data, save it to the filesystem and extract it
				//	TODO - Look at streaming this from memory. Should be faster than saving/reloading
				String zippedSessionFilename = this.getWorkDir() + File.separator + "ZAPSESSION.zip";
				FileUtils.writeByteArrayToFile(new File(zippedSessionFilename), configFileData);
				ZipFileUtils.unzipFile(zippedSessionFilename, this.getWorkDir());
				
				//	Now point ZAP toward the unpacked session file
				ApiResponse response;
				log.debug("Setting ZAP home directory to: " + this.getWorkDir());
				response = zap.core.setHomeDirectory(this.getWorkDir());
				log.debug("Loading session");
				response = zap.core.loadSession("ZAPTEST");
				log.debug("Response after attempting set session: " + response.toString(0));
			} catch (ClientApiException e) {
				log.error("Problems setting session: " + e.getMessage(), e);
			} catch (IOException e) {
				log.error("Problems unpacking the ZAP session data into the working directory: " + e.getMessage(), e);
			}
		} else {
			log.debug("Task configuration had no configuration file data. Will run a default unauthenticated scan.");
		}
		
		status = attemptRunSpider(theConfig, zap);
		if(status) {
			log.info("Appears that spider run was successful. Going to attempt a scan.");
			
			status = attemptRunScan(theConfig, zap);
			
			if(status) {
				log.info("Appears that scan run was successful. Going to attempt to pull results");
				
				String resultsXml = attemptRetrieveResults(zap);
				try {
					String resultsFilename = this.getWorkDir() + File.separator + "ZAPRESULTS.xml";
					log.debug("Writing results to file: " + resultsFilename);
					FileUtils.writeStringToFile(new File(resultsFilename), resultsXml);
				} catch (IOException ioe) {
					log.error("Unable to write results file: " + ioe.getMessage(), ioe);
				}
				//	TOFIX - Send the results to the ThreadFix server
				retVal = true;
				
			} else {
				log.warn("Appears that scan run was unsuccessful. Not goign to pull results");
			}
		} else {
			log.warn("Appears that spider run was unsuccessful. Not going to attempt a scan.");
		}
		
		status = stopZap(zap);
		if(status) {
			log.info("ZAP appears to have shut down");
		} else {
			log.warn("Problems closing down ZAP");
		}
		
		log.info("Finished attempting to do ZAP task with config: " + theConfig);
		return(retVal);
	}

	public boolean readConfig(Configuration config) {
		boolean retVal = false;
		
		this.maxSpiderWaitInSeconds = config.getInt("zap.maxSpiderWaitInSeconds");
		this.maxScanWaitInSeconds = config.getInt("zap.maxScanWaitInSeconds");
		this.spiderPollWaitInSeconds = config.getInt("zap.spiderPollWaitInSeconds");
		this.scanPollWaitInSeconds = config.getInt("zap.scanPollWaitInSeconds");
		this.zapHost = config.getString("zap.zapHost");
		this.zapPort = config.getInt("zap.zapPort");
		this.zapExecutablePath = config.getString("zap.zapExecutablePath");
		//	TODO rename this to reflect that it is in seconds (also requires change to .properties file)
		this.zapStartupWaitTime = config.getInt("zap.zapStartupWaitTime");
		
		//	TODO - Perform some input validation on the supplied properties so this retVal means something
		retVal = true;
		
		return(retVal);
	}
	
	private boolean startZap() {
		boolean retVal = false;
		
		log.info("Attempting to start ZAP instance");
		
		String[] args = { zapExecutablePath + "zap.sh", "-daemon" };
		
		ProcessBuilder pb = new ProcessBuilder(args);
		pb.directory(new File(zapExecutablePath));
		
		try {
			pb.start();
			log.info("ZAP started successfully. Waiting " + (this.zapStartupWaitTime) + "s for ZAP to come online");
			Thread.sleep(this.zapStartupWaitTime * 1000);
			retVal = true;
		} catch (IOException e) {
			log.error("Problems starting ZAP instance: " + e.getMessage(), e);
		} catch (InterruptedException ie) {
			log.error("Problems waiting for ZAP instance to start up: " + ie.getMessage(), ie);
		}
		
		return(retVal);
	}
	
	private boolean stopZap(ClientApi zap) {
		boolean retVal = false;
		ApiResponse result;
		
		log.info("Attempting to shut down ZAP instance");
		
		try {
			result = zap.core.shutdown();
			if(didCallSucceed(result)) {
				log.info("ZAP shutdown appears to have been successful");
				retVal = true;
			} else if(didCallFail(result)) {
				log.info("ZAP shutdown request appears to have failed");
			} else {
				log.warn("Got unexpected result from ZAP shutdown request");
			}
			
		} catch (ClientApiException e) {
			log.error("Problems telling ZAP to shut down: " + e.getMessage(), e);
		}
		
		log.info("Finished trying to shut down ZAP instance");
	
		return(retVal);
	}
	
	/**
	 * @param zap
	 * @return
	 */
	private String attemptRetrieveResults(ClientApi zap) {
		String retVal = null;
		String intermediateXml;
		
		try {
			Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(this.zapHost, this.zapPort));
			intermediateXml = openUrlViaProxy(proxy, "http://zap/XML/core/view/alerts/?zapapiformat=XML&baseurl=&start=&count=").toString();
			retVal = reformatResults(intermediateXml);
		} catch (Exception e) {
			log.error("Problems attaching to ZAP via proxy connection to get results XML: " + e.getMessage(), e);
		}
		
		return(retVal);
	}
	
	/**
	 * Reformats the XML returned by the ZAP API to look like the actual ZAP XML report
	 * 
	 * @param starterXml XML returned from the ZAP API 
	 * @return XML that looks like ZAP's normal XML report
	 */
	private String reformatResults(String starterXml) {
		String retVal;
		
		//	Chop off the '[' and ']' at the front and back of the returned XML string
		retVal = starterXml.substring(1, starterXml.length() - 1);
		
		//	Chop off the "bad" beginning of the API-returned XML
		retVal = retVal.replace(XML_PRECURSOR_TO_REMOVE, "");
		
		//	Create the tag with the datestamp
		//	Should look like:
		//		<OWASPZAPReport generated="Fri, 6 Jul 2012 15:17:03" version="1.2">
		
		SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy kk:mm:ss", Locale.US);
		
		StringBuilder sb = new StringBuilder();
		sb.append("<OWASPZAPReport generated=\"");
		sb.append(dateFormat.format(new Date()));
		sb.append("\" version=\"1.2\">");
		
		//	Prepend the needed stuff
		retVal = XML_PRECURSOR_TO_ADD
					+ sb.toString()
					+ XML_PRECURSOR_TO_ADD_FINAL
					+  retVal;
		
		//	Tack on the stuff we need at the end
		retVal += XML_POSTSCRIPT_TO_ADD;
		
		return(retVal);
	}
	
	/**
	 * This code taken from:
	 * https://code.google.com/p/zaproxy-test/source/browse/branches/beta/src/org/zaproxy/zap/DaemonWaveIntegrationTest.java
	 * 
	 * TODO - Look through and clean up if necessary
	 * TODO - Clean up the massive Exception being thrown
	 * 
	 * @param proxy
	 * @param apiurl
	 * @return
	 * @throws Exception
	 */
    private static List<String> openUrlViaProxy (Proxy proxy, String apiurl) throws Exception {
        List<String> response = new ArrayList<>();
        URL url = new URL(apiurl);
        HttpURLConnection uc = (HttpURLConnection)url.openConnection(proxy);
        uc.connect();
        
        BufferedReader in = new BufferedReader(new InputStreamReader(uc.getInputStream()));

        String inputLine;

        while ((inputLine = in.readLine()) != null) {
                response.add(inputLine);
        }

        in.close();
        return response;
}
	
	private boolean attemptRunScan(TaskConfig theConfig, ClientApi zap) {
		boolean retVal = false;
		ApiResponse response;
		
		try {
			log.info("Attempting to start scan");
			
			//	TODO - Need better info about what strings are expected for the second and third arguments
			response = zap.ascan.scan(theConfig.getTargetUrlString(), "true", "true");
			log.info("Call to start scan returned successfull. Checking to see if scan actually started");
			
			if(didCallSucceed(response)) {
				log.info("Attempt to start scan was successful");
				
				// Now wait for the spider to finish
				boolean keepScanning = true;
				boolean scanFinished = false;
				
				long startTime = System.currentTimeMillis();
				long endTime = startTime + (maxScanWaitInSeconds * 1000);
				
				log.info("Scan started around " + startTime + ", will wait until " + endTime);
				
				while(keepScanning) {
					response = zap.ascan.status();
					log.debug("Current scan status: " + extractResponseString(response) + "%");
					try {
						Thread.sleep(scanPollWaitInSeconds * 1000);
					} catch (InterruptedException e) {
						log.error("Thread interruption problem: " + e.getMessage(), e);
					}
				
					if("100".equals(extractResponseString(response))) {
						log.info("Scanning completed at 100%");
						
						//	Check to see if we had any results
						//	TOFIX - Check into these arguments to see what we should really be passing
						response = zap.core.alerts("", "", "");
						// log.debug("Results of scan: " + response.toString(0));
						int numAlerts = extractResponseCount(response);
						log.debug("Got " + numAlerts + " alerts");
						if(numAlerts <= 0) {
							//	TODO - Need to look at how we evaluate success and failure here.
							log.warn("Scan returned no alerts. That is kind of strange");
						} else {
							log.info("Scanning found " + numAlerts + " alerts and appears to have been successful");
							retVal = true;
						}
						
						keepScanning = false;
					} else if(System.currentTimeMillis() > endTime ) {
						log.debug("Scanning timed out");
						keepScanning = false;
					}
				}
				
			} else if(didCallFail(response)) {
				log.warn("Attempt to start scan was NOT succcessful");
			} else {
				log.warn("Got an ApiResponse we didn't expect: " + response.toString(0));
			}
			
			
		} catch (ClientApiException e) {
			log.error("Problems communicating with ZAP:" + e.getMessage(), e);
		}
		
		return(retVal);
	}

	private boolean attemptRunSpider(TaskConfig theConfig, ClientApi zap) {
		
		boolean retVal = false;
		ApiResponse response;
		
		try {
			log.info("Attempting to start spider");
			
			response = zap.spider.scan(theConfig.getTargetUrlString());
			log.info("Call to start spider returned successfully. Checking to see if spider actually started.");
			
			if(didCallSucceed(response)) {
				log.info("Attempt to start spider was succcessful");
				
				//	Now wait for the spider to finish
				boolean keepSpidering = true;
				
				long startTime = System.currentTimeMillis();
				long endTime = startTime + (maxSpiderWaitInSeconds * 1000);
				
				log.info("Spider started around " + startTime + ", will wait until " + endTime);
				
				while(keepSpidering) {
					response = zap.spider.status();
					log.debug("Current spider status: " + extractResponseString(response) + "%");
					try {
						Thread.sleep(spiderPollWaitInSeconds * 1000);
					} catch (InterruptedException e) {
						log.error("Thread interruption problem: " + e.getMessage(), e);
					}
				
					if("100".equals(extractResponseString(response))) {
						log.info("Spidering completed at 100%");
						
						//	Check to see if we had any results
						response = zap.spider.results();
						// log.debug("Results of spider: " + response.toString(0));
						int numUrls = extractResponseCount(response);
						log.debug("Got " + numUrls + " URLs");
						if(numUrls <= 1) {
							//	TODO - Need to look at how we evaluate success and failure here. I could see
							//	a scenario where an app with a single page would come back with unsuccessful
							//	spiders and never get cleared from the queue.
							log.warn("Spidering process only returned a single URL and we started with that one. "
										+ "Spidering probably not successful.");
						} else {
							log.info("Spidering found " + numUrls + " URLs and appears to have been successful");
							retVal = true;
						}
						
						keepSpidering = false;
					} else if(System.currentTimeMillis() > endTime ) {
						log.debug("Spidering timed out");
						keepSpidering = false;
					}
					
				}
				
			} else if(didCallFail(response)) {
				log.warn("Attempt to start spider was NOT succcessful");
			} else {
				log.warn("Got an ApiResponse we didn't expect: " + response.toString(0));
			}
			
		} catch (ClientApiException e) {
			log.error("Problems communicating with ZAP:" + e.getMessage(), e);
		}
		
		return(retVal);
	}
	
	/**
	 * 	TOFIX - This is kind of gross, but the ZAP Java API is a little goofy here so we have to compensate a bit.
	 * @param response
	 * @return
	 */
	private static boolean didCallSucceed(ApiResponse response) {
		boolean retVal = false;
		if(response != null && "OK".equals(extractResponseString(response))) {
			retVal = true;
		}
		return(retVal);
	}
	
	/**
	 * 	TOFIX - This is kind of gross, but the ZAP Java API is a little goofy here so we have to compensate a bit.
	 * @param response
	 * @return
	 */
	private static boolean didCallFail(ApiResponse response) {
		boolean retVal = false;
		if(response != null && "FAIL".equals(extractResponseString(response))) {
			retVal = true;
		}
		return(retVal);
	}
	
	/**
	 * 	TOFIX - This is kind of gross, but the ZAP Java API is a little goofy here so we have to compensate a bit.
	 * @param response
	 * @return
	 */
	private static String extractResponseString(ApiResponse response) {
		String retVal = null;
		if(response != null && response instanceof ApiResponseElement) {
			retVal = ((ApiResponseElement)response).getValue();
		}
		return(retVal);
	}
	
	/**
	 * 	TOFIX - This is kind of gross, but the ZAP Java API is a little goofy here so we have to compensate a bit.
	 * @param response
	 * @return
	 */
	private static int extractResponseCount(ApiResponse response) {
		int retVal = -1;
		if(response != null && response instanceof ApiResponseList) {
			retVal = ((ApiResponseList)response).getItems().size();
		}
		return(retVal);
	}
}
