package lolpatcher;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import static lolpatcher.PatchTask.speedStat;

/**
 *
 * @author Rick
 */
public abstract class Worker extends Thread{
    public float progress = 1;
    public long startTime = -1;
    String current;
    boolean alternative; // for example true when hashing, false when downloading
    
    
    protected boolean checkHash(InputStream in, LoLPatcher patcher, ReleaseManifest.File f, boolean updateProgress) {
        try {
            long total = 0;
            MessageDigest md = MessageDigest.getInstance("MD5");
            try (InputStream is = new DigestInputStream(in, md)) {
                int read;
                byte[] buffer = new byte[4096];
                while((read = is.read(buffer)) != -1){
                    total += read;
                    if(updateProgress){
                        progress = (float) total / f.size;
                    }
                    speedStat(read);
                    if(patcher.done) return true;
                }
            } catch (IOException ex) {
                return false;
            }
            byte[] digest = md.digest();
            return Arrays.equals(digest, f.checksum);
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(Worker.class.getName()).log(Level.SEVERE, null, ex);
        }
        throw new Error("This should never happen. md5 not found");
    }
    protected boolean checkHash(InputStream in, LoLPatcher patcher, ReleaseManifest.File f) throws IOException{
        return checkHash(in, patcher, f, true);
    }
}
