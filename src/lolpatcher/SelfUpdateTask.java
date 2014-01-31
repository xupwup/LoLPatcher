package lolpatcher;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import static lolpatcher.Main.patcherVersion;
import nl.xupwup.Util.MiniHttpClient;

/**
 *
 * @author Rick
 */
public class SelfUpdateTask extends PatchTask{
    float percentage = 0;
    
    @Override
    public void patch() throws MalformedURLException, IOException, NoSuchAlgorithmException {
        currentFile = "Checking for updates";
        MiniHttpClient hc = new MiniHttpClient("lolpatcher.xupwup.nl");
        MiniHttpClient.HttpResult versionRequest = hc.get("/version2");
        
        ArrayList<String> response = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(versionRequest.in))) {
            String inputLine;
            while ((inputLine = in.readLine()) != null){
                response.add(inputLine);
            }
        }
        int version = Integer.parseInt(response.get(0).trim());
        if(version <= patcherVersion){
            percentage = 100;
            done = true;
            return;
        }
        currentFile = "Downloading update";
        for(int i = 1; i < response.size(); i++){
            MiniHttpClient.HttpResult get = hc.get("/data/"+response.get(i));
            String filename = response.get(i);
            if(!filename.equals("SelfPatchFinalizer.jar")){
                filename = filename + ".new";
            }
            try (OutputStream fw = new BufferedOutputStream(new FileOutputStream(filename))) {
                int read;
                byte[] buffer = new byte[1024];
                while((read = get.in.read(buffer)) != -1){
                    fw.write(buffer, 0, read);
                }
            }
            percentage = 100f * i / response.size();
        }
        String[] args = new String[3 + response.size()-1];
        args[0] = System.getProperty("java.home")+"/bin/java";
        args[1] = "-jar";
        args[2] = "SelfPatchFinalizer.jar";
        for(int i = 1; i < response.size(); i++){
            args[i+2] = response.get(i);
        }
        Runtime.getRuntime().exec(args);
        System.exit(0);
    }

    @Override
    public float getPercentage() {
        return percentage;
    }
    
}
