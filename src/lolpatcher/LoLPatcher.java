package lolpatcher;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.InflaterInputStream;
import lolpatcher.ReleaseManifest.File;
import org.apache.http.HttpEntity;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;

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
    
    private String type = "projects";
    
    
    private boolean ignoreS_OK, force;
    
    private HashMap<String, RAFDump> archives;
    
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
        percentage = 0;
        
        
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
        
        HttpClient hc = HttpClients.createDefault();
        System.out.println("go!");
        boolean noSync = false;
        ArrayList<File> files = cullFiles(mf, oldmf);
        
        String lastRel = null;
        
        for(int i = 0; i < files.size(); i++){
            File f = files.get(i);
            currentFile = f.name;
            
            if(lastRel != null && !lastRel.equals(f.release)){
                // files are sorted on version because of cullfiles()
                RAFDump rd = getArchive(lastRel); 
                if(rd != null){
                    currentFile = rd.raf.getName();
                    rd.close();
                }
            }
            lastRel = f.release;
            
            // /*
            if(f.fileType == 22 || f.fileType == 6){
                downloadFileToArchive(f, hc);
            }else{
                downloadFile(f, hc);
            }
            percentage = 100f * i / files.size();
            if(done){
                noSync = true;
                break;
            }
            //*/
        }
        
        percentage = 100;
        if(!noSync){
            if(lastRel != null){ // sync the last raf file.
                RAFDump rd = getArchive(lastRel);
                if(rd != null){
                    currentFile = rd.raf.getName();
                    rd.close();
                }
            }
            
            managedFilesCleanup(mf);
            
            new java.io.File("RADS/"+type + "/" + project + "/releases/"
                + targetVersion + "/S_OK").createNewFile();
        }
        done = true;
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
    
    private RAFDump getArchive(String s){
        RAFDump rd = archives.get(s);
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
                rd = new RAFDump(folder + filename);
                archives.put(s, rd);
            } catch (IOException ex) {
                Logger.getLogger(LoLPatcher.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return rd;
    }
    
    private void downloadFileToArchive(File f, HttpClient hc) throws IOException{
        RAFDump archive = getArchive(f.release);
        
        String url = "http://l3cdn.riotgames.com/releases/live/"+type+"/"
            + project + "/releases/" + f.release + "/files/" + 
            f.path.replaceAll(" ", "%20") + f.name.replaceAll(" ", "%20") + (f.fileType > 0 ? ".compressed" : "");
        HttpEntity hte = hc.execute(new HttpGet(url)).getEntity();

        try(InputStream in = (f.fileType == 6 ? new InflaterInputStream(hte.getContent()) : hte.getContent())){
            archive.writeFile(f.path + f.name, in, this);
        }
    }
    
    private java.io.File getFileDir(File f){
        return  new java.io.File("RADS/"+type + "/" + project + (f.fileType == 5 ? "/managedfiles/" : "/releases/")
                + (f.fileType == 5 ? f.release : targetVersion) + (f.fileType == 5 ? "/" : "/deploy/") + f.path);
    }
    
    private void downloadFile(File f, HttpClient hc) throws MalformedURLException, IOException, NoSuchAlgorithmException{
        java.io.File targetDir = getFileDir(f);
        java.io.File target = new java.io.File(targetDir.getPath() + "/" + f.name);
        
        String url = "http://l3cdn.riotgames.com/releases/live/"+type+"/"
                + project + "/releases/" + f.release + "/files/" + 
                f.path.replaceAll(" ", "%20") + f.name.replaceAll(" ", "%20") + (f.fileType > 0 ? ".compressed" : "");
        
        
        
        
        targetDir.mkdirs();
        if(!target.createNewFile()){
            MessageDigest md = MessageDigest.getInstance("MD5");
            try (InputStream is = new DigestInputStream(new BufferedInputStream(new FileInputStream(target)), md)) {
                int read;
                byte[] buffer = new byte[4096];
                
                while((read = is.read(buffer)) != -1){
                    speedStat(read);
                }
            }
            byte[] digest = md.digest();
            if(Arrays.equals(digest, f.checksum)){
                return;
            }
        }
        
        
        HttpEntity hte = hc.execute(new HttpGet(url)).getEntity();
        
        try(InputStream in = (
                f.fileType > 0 ? 
                    new InflaterInputStream(hte.getContent()) :
                    hte.getContent())){
            
            try(OutputStream fo = new BufferedOutputStream(new FileOutputStream(target))){
                int read;
                byte[] buffer = new byte[4096];
                while((read = in.read(buffer)) != -1){
                    fo.write(buffer, 0, read);
                    speedStat(read);
                    if(done) return;
                }
            }
        }
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
