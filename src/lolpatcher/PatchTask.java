package lolpatcher;

import java.io.IOException;
import java.net.MalformedURLException;
import java.security.NoSuchAlgorithmException;
import static lolpatcher.LoLPatcher.bytesThisSample;

/**
 *
 * @author Rick
 */
public abstract class PatchTask extends Thread{
    public boolean done = false;
    public static int speed = 0;
    public String currentFile;
    public float percentage = 100;
    public Exception error;
    
    static long lastSample = 0;
    static int bytesThisSample = 0;
    
    public abstract void patch() throws MalformedURLException, IOException, NoSuchAlgorithmException;
    
    public static void speedStat(int read){
        long now = System.currentTimeMillis();
        bytesThisSample += read;
        if(now - lastSample > 1000){
            speed = (int) ((bytesThisSample / ((now - lastSample) / 1000f)) / 1024);
            bytesThisSample = 0;
            lastSample = now;
        }
    }
    
    @Override
    public void run() {
        try {
            patch();
        } catch (Exception ex) {
            ex.printStackTrace();
            error = ex;
        }
    }
}
