/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.aegis.nfdumpreader;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.log4j.Logger;
import com.opencsv.CSVReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Properties;

/**
 *
 * @author Leo
 */
public class NfdumpReader {

    final static Logger LOGGER = Logger.getLogger("NetFlowAgent");
    private static Properties config;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        NfdumpReader obj = new NfdumpReader();
        loadConfig();

        Date date = new Date();
        Calendar cal = Calendar.getInstance();
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH) + 1;
        int day = cal.get(Calendar.DAY_OF_MONTH);

        // Fetch latest nfdump file of current day to process
        String nfdumppath = config.getProperty("nfdumpBase") + year + "/" + String.format("%02d", month)
                + "/" + String.format("%02d", day) + "/";

        System.out.println("nfdumppath: " + nfdumppath);
        String[] getLatestNfdumpCommand = {"/bin/sh", "-c", "cd " + nfdumppath + "&& ls -t | head -n 1"};
        String latestNfdumpFile = obj.executeCommand(getLatestNfdumpCommand);
        System.out.println("Processing file: " + latestNfdumpFile);
        String nfdumpCSV = config.getProperty("nfdumpCsvPath") + latestNfdumpFile.trim() + ".csv";

        if (config.getProperty("latestReadNfumpCSV") == null
                || (config.getProperty("latestReadNfumpCSV") != null
                && !config.getProperty("latestReadNfumpCSV").equals(nfdumpCSV))) {

            // Read the file and create logs for the results
            String[] readNfdumpCommand = {"nfdump", "-r", nfdumppath + latestNfdumpFile.trim(), "-o", "fmt:%ts,%te,%td,%pr,%sa,%sp,%da,%dp,%pkt,%byt,%bps,%pps", "-O", "tstart"};

            System.out.println("Command: " + String.join(" ", readNfdumpCommand));
            String reading = obj.executeCommand(readNfdumpCommand);
            System.out.println(reading);

            FileWriter writer = null;
            try {
                writer = new FileWriter(nfdumpCSV);
                writer.append(reading);
            } catch (Exception e) {
                System.out.println("Error writing csv file");
                LOGGER.error("Error writing csv file for " + latestNfdumpFile);
                e.printStackTrace();
            } finally {
                if (writer != null) {
                    try {
                        writer.flush();
                        writer.close();
                    } catch (IOException e) {
                        System.out.println("Error while flushing/closing fileWriter !!!");
                        LOGGER.error("Error while flushing/closing fileWriter for the csv " + nfdumpCSV);
                        e.printStackTrace();
                    }
                }
            }
            // Parse nfdump CSV and log all relevant cipis        
            logNetworkConnections(config.getProperty("hostIp"), nfdumpCSV);
            logNetworkSpeed(config.getProperty("hostIp"), nfdumpCSV);
            logNetworkLoad(config.getProperty("hostIp"), nfdumpCSV);

            config.setProperty("latestReadNfumpCSV", nfdumpCSV);
            saveConfig();
        } else {
            LOGGER.info("No reading executed, latest csv file allready processed: " + nfdumpCSV);
            System.out.println("No reading executed, latest csv file allready processed: " + nfdumpCSV);
        }
    }

    /**
     * Executes the given shell command in the host OS and returns the output
     *
     * @param command the command name and arguments
     * @return the command output
     */
    private String executeCommand(String[] command) {

        StringBuilder output = new StringBuilder();
        Process p;
        BufferedReader reader = null;
        try {
            p = Runtime.getRuntime().exec(command);
            reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = "";
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            p.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return output.toString();
    }

    /**
     * Reads an nfdump csv and logs the network connections per second
     *
     * @param srcHost the host machine
     * @param nfdumpCSV the nfdump CSV to parse
     */
    public static void logNetworkConnections(String srcHost, String nfdumpCSV) {
        // CSV fields:
        //Date first seen,Date last seen,Duration,Proto,Src,IP,Addr,Src,Pt,Dst,IP,Addr,Dst,Pt,Packets,Bytes,bps,pps        
        CSVReader reader = null;
        //String netflowCsv = props.getProperty("netflowcsv");        

        try {
            reader = new CSVReader(new FileReader(nfdumpCSV));
            String[] line = reader.readNext();
            boolean finished = false;
            Map<String, Integer> netvals = new LinkedHashMap<>();

            String datetime = "";
            while ((line = reader.readNext()) != null && !finished) {
                if (!line[0].contains("Summary")) {
                    //Read flow's start datetime e.g. 2017-06-26 6:17:17
                    datetime = line[0].substring(0, line[0].indexOf("."));
                    if (netvals.containsKey(datetime)) {
                        netvals.put(datetime, netvals.get(datetime) + 1);
                    } else {
                        netvals.put(datetime, 1);
                    }
                } else {
                    finished = true;
                }
            }
            int nconns;
            String nseverity = "";
            for (Map.Entry pair : netvals.entrySet()) {
                nconns = (int) pair.getValue();
                if (nconns <= 2) {
                    nseverity = "OK";
                } else if (nconns > 2 && nconns < 4) {
                    nseverity = "WARNING";
                } else {// nconns >4
                    nseverity = "CRITICAL";
                }
                LOGGER.info(srcHost + "; Network Connections;" + nseverity + ";Network Connections " + nconns + " at " + pair.getKey().toString());
                System.out.println(srcHost + "; Network Connections;" + nseverity + ";Network Connections " + nconns + " at " + pair.getKey().toString());
            }
        } catch (IOException e) {
            LOGGER.error("Error trying to read network connections from " + nfdumpCSV);
            e.printStackTrace();
        }

    }//end logNetworkConnections

    /**
     * Reads an nfdump csv and logs the network speed in bits per second
     *
     * @param srcHost the host machine
     * @param nfdumpCSV the nfdump CSV to parse
     */
    public static void logNetworkSpeed(String srcHost, String nfdumpCSV) {
        // CSV fields:
        //Date first seen,Date last seen,Duration,Proto,Src,IP,Addr,Src,Pt,Dst,IP,Addr,Dst,Pt,Packets,Bytes,bps,pps        
        CSVReader reader = null;
        //String netflowCsv = props.getProperty("netflowcsv");        

        try {
            reader = new CSVReader(new FileReader(nfdumpCSV));
            String[] line = reader.readNext();
            boolean finished = false;
            Map<String, Double> netvals = new LinkedHashMap<>();
            String datetime = "";
            double bps;

            while ((line = reader.readNext()) != null && !finished) {
                if (!line[0].contains("Summary")) {
                    if (!line[10].contains("M")) {
                        bps = Double.parseDouble(line[10]);
                    } else {
                        bps = Double.parseDouble(line[10].substring(0, line[10].indexOf(" M"))) * 1000000;
                    }

                    //Read flow's start datetime e.g. 2017-06-26 6:17:17
                    datetime = line[0].substring(0, line[0].indexOf("."));
                    if (netvals.containsKey(datetime)) {
                        netvals.put(datetime, (netvals.get(datetime) + bps) / 2);
                    } else {
                        netvals.put(datetime, bps);
                    }
                } else {
                    finished = true;
                }
            }
            double nspeed;
            String nseverity = "";
            for (Map.Entry pair : netvals.entrySet()) {
                nspeed = (double) pair.getValue();
                if (nspeed > 2) {
                    nseverity = "OK";
                } else if (nspeed > 1.71 && nspeed < 2) {
                    nseverity = "WARNING";
                } else // nspeed < 2
                {
                    nseverity = "CRITICAL";
                }
                LOGGER.info(srcHost + "; Network Speed;" + nseverity + ";Network Speed " + nspeed + " bits/sec at " + pair.getKey().toString());
                System.out.println(srcHost + "; Network Speed;" + nseverity + ";Network Speed " + nspeed + " bits/sec at " + pair.getKey().toString());
            }
        } catch (IOException e) {
            LOGGER.error("Error trying to read network speed from " + nfdumpCSV);
            e.printStackTrace();
        }
    }//end logNetworkSpeed

    /**
     * Reads an nfdump csv and logs the network load in packets per second
     *
     * @param srcHost the host machine
     * @param nfdumpCSV the nfdump CSV to parse
     */
    public static void logNetworkLoad(String srcHost, String nfdumpCSV) {
        // CSV fields:
        //Date first seen,Date last seen,Duration,Proto,Src,IP,Addr,Src,Pt,Dst,IP,Addr,Dst,Pt,Packets,Bytes,bps,pps        
        CSVReader reader = null;
        //String netflowCsv = props.getProperty("netflowcsv");        

        try {
            reader = new CSVReader(new FileReader(nfdumpCSV));
            String[] line = reader.readNext();
            boolean finished = false;
            Map<String, Double> netvals = new LinkedHashMap<>();
            String datetime = "";
            double pps;

            while ((line = reader.readNext()) != null && !finished) {
                if (!line[0].contains("Summary")) {
                    pps = Double.parseDouble(line[11]);

                    //Read flow's start datetime e.g. 2017-06-26 6:17:17
                    datetime = line[0].substring(0, line[0].indexOf("."));
                    if (netvals.containsKey(datetime)) {
                        netvals.put(datetime, (netvals.get(datetime) + pps) / 2);
                    } else {
                        netvals.put(datetime, pps);
                    }
                } else {
                    finished = true;
                }
            }
            double nload;
            String nseverity = "";
            for (Map.Entry pair : netvals.entrySet()) {
                nload = (double) pair.getValue();
                if (nload <= 900) {
                    nseverity = "OK";
                } else if (nload > 900 && nload < 1500) {
                    nseverity = "WARNING";
                } else // nload >1500
                {
                    nseverity = "CRITICAL";
                }
                LOGGER.info(srcHost + "; Network Load;" + nseverity + ";Network Load " + nload + " packets/sec at " + pair.getKey().toString());
                System.out.println(srcHost + "; Network Load;" + nseverity + ";Network Load " + nload + " packets/sec at " + pair.getKey().toString());
            }
        } catch (IOException e) {
            LOGGER.error("Error trying to read network load from " + nfdumpCSV);
            e.printStackTrace();
        }
    }//end logNetworkLoad

    private static void loadConfig() {

        String resourceName = "config.properties";
        config = new Properties();
        InputStream resourceStream = null;

        String localResourcePath = System.getProperty("user.home") + "/NetFlowAgent/" + resourceName;
        File f = new File(localResourcePath);
        if (f.exists() && !f.isDirectory()) {
            try {
                resourceStream = new FileInputStream(localResourcePath);
            } catch (FileNotFoundException ex) {
                LOGGER.error("Error loading local config file");
            }
        } else {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            resourceStream = loader.getResourceAsStream(resourceName);
        }
        try {
            config.load(resourceStream);
        } catch (IOException ex) {
            LOGGER.fatal("Error loading config file");
        }
    }

    private static void saveConfig() {

        String resourceName = System.getProperty("user.home") + "/NetFlowAgent/" + "config.properties";
        FileOutputStream resourceOutStream = null;
        try {
            resourceOutStream = new FileOutputStream(resourceName);
            config.store(resourceOutStream, null);
        } catch (IOException ex) {
            LOGGER.fatal("Error saving config file");
        } finally {
            if (resourceOutStream != null) {
                try {
                    resourceOutStream.close();
                } catch (IOException e) {
                    LOGGER.error("Error closing FileOutputStream for config properties");
                    e.printStackTrace();
                }
            }
        }
    }
}
