/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package aegisnfdumpreader;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;

/**
 *
 * @author Leo
 */
public class AegisNfdumpReader {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        AegisNfdumpReader obj = new AegisNfdumpReader();
        
        Date date = new Date();
        Calendar cal = Calendar.getInstance();
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH) + 1;
        int day = cal.get(Calendar.DAY_OF_MONTH);

        String nfdumppath = "/var/cache/nfdump/" + year + "/" + String.format("%02d", month) 
                + "/" + String.format("%02d", day) + "/";

        System.out.println("nfdumppath: " + nfdumppath);  
        String[] getLatestNfdumpCommand = {"/bin/sh", "-c", "cd " + nfdumppath + "&& ls -t | head -n 1"};
        String latestNfdumpFile = obj.executeCommand(getLatestNfdumpCommand);
        System.out.println("Processing: " + latestNfdumpFile);       
        
        
        //in mac oxs
        String[] readNfdumpCommand = {"nfdump", "-r", nfdumppath + latestNfdumpFile.trim(), "-o", "fmt:%ts,%te,%td,%pr,%sa,%sp,%da,%dp,%pkt,%byt,%bps,%pps", "-O", "tstart"};
        System.out.println(String.join("-", readNfdumpCommand));
        String reading = obj.executeCommand(readNfdumpCommand);
        System.out.println(reading);

        //String command ="nfdump -r /var/cache/nfdump/2018/03/12/nfcapd.201803121525 -O tstart";
        //in windows
        //String command = "ping -n 3 " + domainName;
        
        
        FileWriter writer = null;
        try {
            writer = new FileWriter("/home/aegis/" + latestNfdumpFile.trim() + ".csv");
            writer.append(reading);
        } catch (Exception e) {
            System.out.println("Error writing csv file");
            e.printStackTrace();
        } finally {
            try {
                writer.flush();
                writer.close();
            } catch (IOException e) {
                System.out.println("Error while flushing/closing fileWriter !!!");
                e.printStackTrace();
            }
        }
    }

    private String executeCommand(String[] command) {

        StringBuffer output = new StringBuffer();

        Process p;
        try {
            p = Runtime.getRuntime().exec(command);
            p.waitFor();
            BufferedReader reader
                    = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = "";
            while ((line = reader.readLine()) != null) {
                output.append(line + "\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return output.toString();
    }
}
