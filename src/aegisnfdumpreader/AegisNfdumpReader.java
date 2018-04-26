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

        String nfdumppath = "/var/cache/nfdump/2018/03/12/";

        String[] getLatestNfdumpCommand = {"/bin/sh", "-c", "cd " + nfdumppath + "&& ls -t | head -n 1"};

        //in mac oxs
        String[] readNfdumpCommand = {"nfdump", "-r", nfdumppath + "nfcapd.201803121525", "-o", "fmt:%ts,%te,%td,%pr,%sa,%sp,%da,%dp,%pkt,%byt,%bps,%pps", "-O", "tstart"};
        //String command ="nfdump -r /var/cache/nfdump/2018/03/12/nfcapd.201803121525 -O tstart";
        //in windows
        //String command = "ping -n 3 " + domainName;

        String latestNfdumpFile = obj.executeCommand(getLatestNfdumpCommand);
        System.out.println("Processing: " + latestNfdumpFile);
        String reading = obj.executeCommand(readNfdumpCommand);
        System.out.println(reading);
        FileWriter writer = null;
        try {
            writer = new FileWriter("/home/aegis/test.csv");
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
