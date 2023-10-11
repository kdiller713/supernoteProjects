package com.kdiller.supernote.backup;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;

import java.net.URL;
import java.net.URLConnection;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;

import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.json.JSONArray;
import org.json.JSONObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BackupTask implements Runnable {
    public interface BackupTaskListener {
        public void backupStatusUpdate(String status);
        public void onComplete(boolean success, String outputFile);
    }
    
    private Logger logger = LoggerFactory.getLogger(BackupTask.class);
    private String baseUrl;
    private File outputFile;
    private BackupTaskListener listener;
    
    public BackupTask(String baseUrl, File outputFile){
        this(baseUrl, outputFile, null);
    }
    
    public BackupTask(String baseUrl, File outputFile, BackupTaskListener listener){
        this.baseUrl = baseUrl;
        this.listener = listener;
        
        if(outputFile.getName().endsWith(".zip")){
            this.outputFile = outputFile;
        }else{
            this.outputFile = new File(outputFile.getAbsolutePath() + ".zip");
        }
    }
    
    private void statusUpdate(String status){
        logger.debug("BackupTask( baseUrl: '" + baseUrl + "' ): " + status);
        if(listener != null){
            listener.backupStatusUpdate(status);
        }
    }
    
    @Override
    public void run() {
        boolean result = false;
    
        try(
            FileOutputStream fileStream = new FileOutputStream(outputFile);
            ZipOutputStream zipStream = new ZipOutputStream(fileStream)
        ){
            getFilesRecursively("", zipStream);
            result = true;
        }catch(Exception e){
            logger.error("Failed to get files", e);
        }
        
        if(!result){
            outputFile.delete();
        }
        
        if(listener != null){
            listener.onComplete(result, outputFile.getAbsolutePath());
        }
    }
    
    private void getFilesRecursively(String curDirectory, ZipOutputStream outputStream) throws Exception {
        statusUpdate("Getting Files in '" + curDirectory + "'");
        
        String jsonData = getDirectoryData(baseUrl + curDirectory);
        JSONObject directoryObject = new JSONObject(jsonData);
        
        if(directoryObject == null){
            logger.error("Failed to get files from " + curDirectory);
            throw new Exception("Failed to get files from " + curDirectory);
        }
        
        JSONArray directoryContents = directoryObject.getJSONArray("fileList");
        
        for(int i = 0; i < directoryContents.length(); i++){
            JSONObject directoryContent = directoryContents.getJSONObject(i);
            
            if(directoryContent.getBoolean("isDirectory")){
                getFilesRecursively(directoryContent.getString("path"), outputStream);
            }else{
                statusUpdate("Downloading " + directoryContent.getString("path"));
                
                try(
                    BufferedInputStream in = new BufferedInputStream(new URL((baseUrl + directoryContent.getString("path")).replaceAll(" ", "%20")).openStream())
                ){
                    outputStream.putNextEntry(new ZipEntry(directoryContent.getString("path")));
                    
                    byte dataBuffer[] = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                        outputStream.write(dataBuffer, 0, bytesRead);
                    }
                }catch(Exception e){
                    logger.error("Error writing file '" + directoryContent.getString("path") + "'", e);
                    throw new Exception(e);
                }
            }
        }
    }
    
    private String getDirectoryData(String url) throws Exception{
        url = url.replaceAll(" ", "%20");
        logger.debug("Getting contents from \"" + url + "\"");
        String data = null;
        
        try{
            HttpURLConnection httpCon = (HttpURLConnection) new URL(url).openConnection();
            int responseCode = httpCon.getResponseCode();
            
            if(responseCode == 200){
                String line = "";
                Scanner sc = new Scanner(httpCon.getInputStream());
                while(sc.hasNext()){
                    line = sc.nextLine().trim();
                    
                    if(line.startsWith("const json")){
                        data = line;
                    }
                }
                sc.close();
                
                if(data != null){
                    data = data.split("=")[1].trim();
                    data = data.substring(1, data.length() - 1);
                }
                
                logger.debug("Response: " + data);
            }else{
                // Failed HTTP request
                throw new Exception("Failed response code: " + responseCode);
            }
        }catch(Exception e){
            logger.error("Error getting contents", e);
            throw new Exception(e);
        }
        
        return data;
    }
}
