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
    String branch;
    
    public String type = "projects";
    
    Worker[] workers;
   
    
    
    public final boolean ignoreS_OK, force;
    public boolean forceSingleFiles = false;
    private FilenameFilter filter;
    
    private final HashMap<String, RAFArchive> archives;
    float percentageInArchive;
    
    public RingBuffer<File> filesToPatch;
    public RingBuffer<Archive> archivesToPatch;
    
    public static class Archive{
        String versionName;
        ArrayList<File> files;

        public Archive(String versionName, ArrayList<File> files) {
            this.versionName = versionName;
            this.files = files;
        }
    }
    
    
    public LoLPatcher(String target, String project, String branch, boolean ignoreS_OK, boolean force, FilenameFilter filter){
        this(target, project, branch, ignoreS_OK, force);
        this.filter = filter;
    }
    
    public LoLPatcher(String target, String project, String branch, boolean ignoreS_OK, boolean force){
        targetVersion = target;
        this.project = project;
        this.ignoreS_OK = ignoreS_OK;
        this.force = force;
        this.branch = branch;
        archives = new HashMap<>();
        filter = new FilenameFilter() {
            @Override
            public boolean accept(java.io.File dir, String name) {
                return true;
            }
        };
    }
    
    @Override
    public void patch() throws MalformedURLException, IOException, NoSuchAlgorithmException{
        boolean S_OKExists = new java.io.File("RADS/"+type + "/" + project + "/releases/"
                + targetVersion + "/S_OK").exists();
        if(S_OKExists && !ignoreS_OK){
            done = true;
            return;
        }
        
        ReleaseManifest oldmf = null;
        java.io.File target = new java.io.File("RADS/" + type + "/" + project + "/releases/");
        
        if(target.exists()){
            String[] list = target.list(new FilenameFilter() {
                                @Override
                                public boolean accept(java.io.File dir, String name) {
                                    return name.matches("((0|[1-9][0-9]{0,2})\\.){3}(0|[1-9][0-9]{0,2})");
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
                    }else{
                        forceSingleFiles = true;
                    }
                }else{
                    throw new IOException("New release version already exists! Rename failed from " + old + " to " + targetVersion);
                }
            }
        }
        currentFile = "Reading manifest";
        ReleaseManifest mf = ReleaseManifest.getReleaseManifest(project, targetVersion, branch, type);

        currentFile = "Calculating differences";
        ArrayList<File> files = cullFiles(mf, oldmf);
        if(error != null){
            return;
        }
        currentFile = "Organizing files";
        
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
        
        ArrayList<Archive> atp = new ArrayList<>();
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
        Collections.sort(atp, new Comparator<Archive>() {
            @Override
            public int compare(Archive o1, Archive o2) {
                return -Integer.compare(o1.files.size(), o2.files.size());
            }
        });
        archivesToPatch = new RingBuffer<>(atp.size());
        archivesToPatch.addAll(atp);
        currentFile = "Patching Separate files";
        Worker[] workers2 = new FileDownloadWorker[6];
        for(int i = 0; i < workers2.length; i++){
            workers2[i] = new FileDownloadWorker(this);
            workers2[i].start();
        }
        workers = workers2;
        // wait for file downloading to finish
        for(Worker w : workers){
            try {
                w.join();
            } catch (InterruptedException ex) {
                Logger.getLogger(LoLPatcher.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        currentFile = "Patching Archives";
        workers2 = new ArchiveDownloadWorker[6];
        for(int i = 0; i < workers2.length; i++){
            workers2[i] = new ArchiveDownloadWorker(this);
            workers2[i].start();
        }
        workers = workers2;
        // wait for archive downloading to finish
        for(Worker w : workers){
            try {
                w.join();
            } catch (InterruptedException ex) {
                Logger.getLogger(LoLPatcher.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        
        
        managedFilesCleanup(mf);
        if(!done && error == null){
            new java.io.File("RADS/"+type + "/" + project + "/releases/"
                + targetVersion + "/S_OK").createNewFile();
            done = true;
        }
    }
    
    @Override
    public float getPercentage(){
        if(archivesToPatch == null){
            return 0;
        }
        int total = filesToPatch.max();
        float finished = total - filesToPatch.size();
        
        if(workers != null && workers instanceof FileDownloadWorker[]){
            for(Worker fw : workers){
                if(fw != null){
                    finished -= (1 - fw.progress);
                }
            }
        }
        float filePart = finished / total;
        
        total = archivesToPatch.max();
        finished = total - archivesToPatch.size();
        
        if(workers != null && workers instanceof ArchiveDownloadWorker[]){
            for(Worker w : workers){
                if(w != null){
                    finished -= (1 - w.progress);
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
        int cores = Runtime.getRuntime().availableProcessors();
        DifferenceCalculator[] calculators = new DifferenceCalculator[cores];
        int slicesize = 1 + mf.files.length / cores;
        for(int i = 0; i < calculators.length; i++){
            int off = slicesize * i;
            int len = Math.max(0, Math.min(mf.files.length - off, slicesize));
            calculators[i] = new DifferenceCalculator(this, mf, oldmf, filter, off, len);
            calculators[i].start();
        }
        for(DifferenceCalculator c : calculators){
            try {
                c.join();
            } catch (InterruptedException ex) {
                Logger.getLogger(LoLPatcher.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        ArrayList<File>[] filesArr = new ArrayList[cores];
        for(int i = 0; i < calculators.length; i++){
            filesArr[i] = calculators[i].result;
        }
        return DifferenceCalculator.mergeLists(filesArr);
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
    
    public RAFArchive getArchive(String s) throws IOException{
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
            if(files.length > 0){
                rd = new RAFArchive(new java.io.File(folder+ files[0]), new java.io.File(folder+ files[0] + ".dat"));
                synchronized(archives){
                    archives.put(s, rd);
                }
                return rd;
            }
            try {
                rd = new RAFArchive(folder + filename);
                synchronized(archives){
                    archives.put(s, rd);
                }
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
            URL u = new URL("http://l3cdn.riotgames.com/releases/"+(server.equals("PBE") ? "pbe" : "live")+"/"+type+"/"+project+"/releases/releaselisting_"+server);
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
