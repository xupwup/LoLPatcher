package lolpatcher;

import java.io.BufferedReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import lolpatcher.ReleaseManifest.File;
import nl.xupwup.Util.RingBuffer;

/**
 * Query these urls for versions
 * 
 * http://l3cdn.riotgames.com/releases/live/system/rads_user_kernel.exe.version
 * http://l3cdn.riotgames.com/releases/live/projects/lol_launcher/releases/releaselisting_EUW
 * http://l3cdn.riotgames.com/releases/live/projects/lol_air_client/releases/releaselisting_EUW
 * http://l3cdn.riotgames.com/releases/live/projects/lol_air_client_config_euw/releases/releaselisting_EUW
 * http://l3cdn.riotgames.com/releases/live/solutions/lol_game_client_sln/releases/releaselisting_EUW
 * 
 * http://ll.leagueoflegends.com/pages/launcher/euw?lang=en
 * 
 * 
 * http://l3cdn.riotgames.com/releases/live/projects/lol_air_client/releases/0.0.1.55/releasemanifest
 * http://l3cdn.riotgames.com/releases/live/projects/lol_game_client/releases/0.0.0.140/releasemanifest
 * 
 * @author Rick
 */
public class LoLPatcher extends PatchTask{
    String targetVersion;
    String project;
    
    public String type = "projects";
    
    FileDownloadWorker[] fworkers;
    ArchiveDownloadWorker[] aworkers;
    
    
    private boolean ignoreS_OK, force;
    
    private HashMap<String, RAFArchive> archives;
    float percentageInArchive;
    
    public RingBuffer<File> filesToPatch;
    public RingBuffer<Archive> archivesToPatch;
    
    public class Archive{
        String versionName;
        ArrayList<File> files;

        public Archive(String versionName, ArrayList<File> files) {
            this.versionName = versionName;
            this.files = files;
        }
    }
    
    
    public LoLPatcher(String target, String project, boolean ignoreS_OK, boolean force, String type){
        this(target, project, ignoreS_OK, force);
        this.type = type;
    }
    
    public LoLPatcher(String target, String project, boolean ignoreS_OK, boolean force){
        targetVersion = target;
        this.project = project;
        this.ignoreS_OK = ignoreS_OK;
        this.force = force;
        archives = new HashMap<>();
    }
    
    @Override
    public void patch() throws MalformedURLException, IOException, NoSuchAlgorithmException{
        if(new java.io.File("RADS/"+type + "/" + project + "/releases/"
                + targetVersion + "/S_OK").exists() && !ignoreS_OK){
            
            done = true;
            return;
        }
        
        
        ReleaseManifest oldmf = null;
        java.io.File target = new java.io.File("RADS/" + type + "/" + project + "/releases/");
        
        if(target.exists()){
            String[] list = target.list(new FilenameFilter() {
                                @Override
                                public boolean accept(java.io.File dir, String name) {
                                    return !name.equals(targetVersion) && name.matches("((0|[1-9][0-9]{0,2})\\.){3}(0|[1-9][0-9]{0,2})");
                                }
                            });
            if(list.length > 0){
                int max = 0;
                String old = null;
                for(String s : list){
                    int v = ReleaseManifest.getReleaseInt(s);
                    if(v > max || old == null){
                        max = v;
                        old = s;
                    }
                }
                java.io.File oldDir = new java.io.File(target, old);
                java.io.File newname = new java.io.File(target, targetVersion);
                if(oldDir.renameTo(newname)){
                    if(new java.io.File(newname, "S_OK").delete()){ // only use old manifest if S_OK existed
                        oldmf = new ReleaseManifest(new java.io.File(newname, "releasemanifest"));
                    }
                }else{
                    throw new IOException("New release version already exists!");
                }
            }
        }
        
        ReleaseManifest mf = ReleaseManifest.getReleaseManifest(project, targetVersion, type);

        ArrayList<File> files = cullFiles(mf, oldmf);
        
        
        int nrOfFiles = 0;
        int nrOfArchiveFiles = 0;
        
        for(File f : files){
            if(f.fileType == 22 || f.fileType == 6){
                nrOfArchiveFiles++;
            }else{
                nrOfFiles++;
            }
        }
        percentageInArchive = (float) nrOfArchiveFiles / (nrOfArchiveFiles + nrOfFiles);
        
        ArrayList atp = new ArrayList<>();
        filesToPatch = new RingBuffer<>(nrOfFiles);
        
        Archive lastArchive = null;
        for(File f : files){
            if(f.fileType == 22 || f.fileType == 6){
                if(lastArchive == null || !lastArchive.versionName.equals(f.release)){
                    lastArchive = new Archive(f.release, new ArrayList<File>());
                    atp.add(lastArchive);
                }
                lastArchive.files.add(f);
            }else{
                filesToPatch.add(f);
            }
        }
        archivesToPatch = new RingBuffer<>(atp.size());
        archivesToPatch.addAll(atp);
        
        fworkers = new FileDownloadWorker[6];
        for(int i = 0; i < fworkers.length; i++){
            fworkers[i] = new FileDownloadWorker(this);
            fworkers[i].start();
        }
        // wait for file downloading to finish
        for(FileDownloadWorker fw : fworkers){
            try {
                fw.join();
            } catch (InterruptedException ex) {
                Logger.getLogger(LoLPatcher.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        aworkers = new ArchiveDownloadWorker[6];
        for(int i = 0; i < aworkers.length; i++){
            aworkers[i] = new ArchiveDownloadWorker(this);
            aworkers[i].start();
        }
        // wait for archive downloading to finish
        for(ArchiveDownloadWorker aw : aworkers){
            try {
                aw.join();
            } catch (InterruptedException ex) {
                Logger.getLogger(LoLPatcher.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        
        managedFilesCleanup(mf);
            
        new java.io.File("RADS/"+type + "/" + project + "/releases/"
            + targetVersion + "/S_OK").createNewFile();
        done = true;
    }
    
    @Override
    public float getPercentage(){
        if(archivesToPatch == null){
            return 0;
        }
        int total = filesToPatch.max();
        float finished = total - filesToPatch.size();
        
        if(fworkers != null){
            for(FileDownloadWorker fw : fworkers){
                if(fw != null){
                    finished -= (1 - fw.progress);
                }
            }
        }
        float filePart = finished / total;
        
        total = archivesToPatch.max();
        finished = total - archivesToPatch.size();
        
        if(aworkers != null){
            for(ArchiveDownloadWorker aw : aworkers){
                if(aw != null){
                    finished -= (1 - aw.progress);
                }
            }
        }
        float archivePart = finished / total;
        if(total == 0){
            archivePart = 0;
        }
        return (filePart * (1 - percentageInArchive) + archivePart * percentageInArchive) * 100;
    }
    
    private ArrayList<File> cullFiles(ReleaseManifest mf, ReleaseManifest oldmf){
        ArrayList<File> files = new ArrayList<>();
        for(File f : mf.files){
            if(needPatch(f, oldmf)){
                files.add(f);
            }
        }
        Collections.sort(files, new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                return Integer.compare(o1.releaseInt , o2.releaseInt);
            }
        });
        return files;
    }
    
    private boolean needPatch(File f, ReleaseManifest oldmf){
        if(f.fileType == 22 || f.fileType == 6){
            return getArchive(f.release) != null;
        }else{
            if(oldmf != null && !force){
                File oldFile = oldmf.getFile(f.path + f.name);
                if(oldFile != null && Arrays.equals(oldFile.checksum, f.checksum)
                        && new java.io.File(getFileDir(f), f.name).exists()){
                    
                    return false;
                }
            }
            return true;
        }
    }
    
    private void managedFilesCleanup(ReleaseManifest mf){
        java.io.File managedFileDir = new java.io.File("RADS/"+type + "/" + project + "/managedfiles/");
        if(managedFileDir.exists()){
            String[] versions = managedFileDir.list();
            for (String v : versions){
                boolean found = false;
                for(File f : mf.files){
                    if(f.fileType == 5 && f.release.equals(v)){
                        found = true;
                        break;
                    }
                }
                if(!found){
                    deleteDir(new java.io.File(managedFileDir, v));
                }
            }
        }
    }
    
    public RAFArchive getArchive(String s){
        RAFArchive rd = archives.get(s);
        if(!archives.containsKey(s)){
            String folder = "RADS/"+type + "/" + project + "/filearchives/"
                + s + "/";
            new java.io.File(folder).mkdirs();
            String filename = "Archive_1.raf";
            String[] files = new java.io.File(folder).list(new FilenameFilter() {
                @Override
                public boolean accept(java.io.File dir, String name) {
                    return name.matches("Archive_[0-9]+\\.raf");
                }
            });
            if(files.length > 0 && !force){
                archives.put(s, null); // set it to null, so you dont have to list
                                       // the directory every time
                return null;
            }else if (files.length > 0){
                deleteDir(new java.io.File(folder));
                new java.io.File(folder).mkdirs();
            }
            try {
                rd = new RAFArchive(folder + filename);
                archives.put(s, rd);
            } catch (IOException ex) {
                Logger.getLogger(LoLPatcher.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return rd;
    }
    
    
    
    public final java.io.File getFileDir(File f){
        return  new java.io.File("RADS/"+type + "/" + project + (f.fileType == 5 ? "/managedfiles/" : "/releases/")
                + (f.fileType == 5 ? f.release : targetVersion) + (f.fileType == 5 ? "/" : "/deploy/") + f.path);
    }
    
    
    
    
    public final static void deleteDir(java.io.File dir){
        if(dir.isDirectory()){
            String[] children = dir.list();
            for(String c : children){
                deleteDir(new java.io.File(dir, c));
            }
        }
        dir.delete();
    }
    
    public static String getVersion(String type, String project, String server){
        try {
            URL u = new URL("http://l3cdn.riotgames.com/releases/live/"+type+"/"+project+"/releases/releaselisting_"+server);
            try(BufferedReader rd = new BufferedReader(new InputStreamReader(u.openStream()))){
                return rd.readLine();
            } catch (IOException ex) {
                Logger.getLogger(LoLPatcher.class.getName()).log(Level.SEVERE, null, ex);
            }
        } catch (MalformedURLException ex) {
            Logger.getLogger(LoLPatcher.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
}
