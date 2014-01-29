/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package lolpatcher;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.InflaterInputStream;
import nl.xupwup.Util.MiniHttpClient;

/**
 *
 * @author Rick
 */
public class ArchiveDownloadWorker extends Thread{
    
    LoLPatcher patcher;
    public float progress = 1;

    public ArchiveDownloadWorker(LoLPatcher patcher) {
        this.patcher = patcher;
    }

    @Override
    public void run() {
        try {
            try (MiniHttpClient htc = new MiniHttpClient("l3cdn.riotgames.com")) {
                htc.throwExceptionWhenNot200 = true;
                
                LoLPatcher.Archive task;
                while(true){
                    synchronized(patcher.archivesToPatch){
                        if(patcher.archivesToPatch.isEmpty() || patcher.done || patcher.error != null){
                            break;
                        }
                        task = patcher.archivesToPatch.remove(0);
                    }
                    progress = 0;
                    RAFArchive archive = patcher.getArchive(task.versionName);
                    for(int i = 0; i < task.files.size(); i++){
                        if(patcher.done){
                            break;
                        }
                        
                        downloadFileToArchive(task.files.get(i), htc, archive);
                        progress = (float) i / task.files.size();
                    }
                    archive.close();
                    progress = 1;
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(ArchiveDownloadWorker.class.getName()).log(Level.SEVERE, null, ex);
            if(patcher.error == null){
                patcher.error = ex;
            }
        }
    }
    
    private void downloadFileToArchive(ReleaseManifest.File f, MiniHttpClient hc, RAFArchive archive) throws IOException{
        String url = "/releases/live/"+patcher.type+"/"
            + patcher.project + "/releases/" + f.release + "/files/" + 
            f.path.replaceAll(" ", "%20") + f.name.replaceAll(" ", "%20") + (f.fileType > 0 ? ".compressed" : "");
        MiniHttpClient.HttpResult hte = hc.get(url);

        try(InputStream in = (f.fileType == 6 ? new InflaterInputStream(hte.in) : hte.in)){
            archive.writeFile(f.path, f.name, in, patcher);
        }
    }
    
}
