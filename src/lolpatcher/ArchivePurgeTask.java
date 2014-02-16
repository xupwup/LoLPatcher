package lolpatcher;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 *
 * @author Rick
 */
public class ArchivePurgeTask extends PatchTask {

    float globalPercentage, archivePercentage;
    int nArchives = 0;
    
    String project, targetVersion, branch, type;

    public ArchivePurgeTask(String project, String targetVersion, String branch, String type) {
        this.project = project;
        this.targetVersion = targetVersion;
        this.branch = branch;
        this.type = type;
    }
    
    
    
    @Override
    public void patch() throws MalformedURLException, IOException, NoSuchAlgorithmException {
        currentFile = "Reading manifest";
        ReleaseManifest mf = ReleaseManifest.getReleaseManifest(project, targetVersion, branch, type);
        
        ArrayList<LoLPatcher.Archive> archivesToPurge = new ArrayList<>();
        
        ArrayList<ReleaseManifest.File> files = new ArrayList<>(mf.files.length);
        Collections.addAll(files, mf.files);
        
        Collections.sort(files, new Comparator<ReleaseManifest.File>() {
            @Override
            public int compare(ReleaseManifest.File o1, ReleaseManifest.File o2) {
                return Integer.compare(o1.releaseInt , o2.releaseInt);
            }
        });
        
        LoLPatcher.Archive lastArchive = null;
        for(ReleaseManifest.File f : files){
            if(f.fileType == 22 || f.fileType == 6){
                if(lastArchive == null || !lastArchive.versionName.equals(f.release)){
                    lastArchive = new LoLPatcher.Archive(f.release, new ArrayList<ReleaseManifest.File>());
                    archivesToPurge.add(lastArchive);
                }
                lastArchive.files.add(f);
            }
        }
        nArchives = archivesToPurge.size();
        for(int i = 0; i < archivesToPurge.size(); i++){
            globalPercentage = ((float) i / nArchives);
            purgeArchive(archivesToPurge.get(i));
            if(done) return;
        }
        done = true;
        globalPercentage = 1;
        archivePercentage = 0;
    }
    
    private void purgeArchive(LoLPatcher.Archive ar) throws IOException{
        String folderName = "RADS/"+type + "/" + project + "/filearchives/"
            + ar.versionName + "/";
        File folder = new java.io.File(folderName);
        if(!folder.exists()){
            throw new IOException("Invalid installation. Run quick repair first.");
        }
        String[] archives = folder.list(new FilenameFilter() {
            @Override
            public boolean accept(java.io.File dir, String name) {
                return name.matches("Archive_[0-9]+\\.raf");
            }
        });
        if(archives.length != 1){
            throw new IOException("Invalid installation. Expected one archive, "
                    + "found " + archives.length  + " in " +folder.getCanonicalPath()+".");
        }
        if(!new File(folder, archives[0]+".dat").exists()){
            throw new IOException("Invalid installation. Missing .raf.dat file in " +folder.getCanonicalPath()+".");
        }
        File sourceRaf = new File(folder, archives[0]);
        File sourceRafDat = new File(folder, archives[0]+".dat");
        RAFArchive source = new RAFArchive(sourceRaf, sourceRafDat);

        File tempDir = new File(folder, "temp");
        if(tempDir.exists()){
            LoLPatcher.deleteDir(tempDir);
        }
        
        // nothing to do
        if(source.fileList.size() == ar.files.size()){
            return;
        }
        
        tempDir.mkdir();
        currentFile = "Loading " + ar.versionName;
        RAFArchive target = new RAFArchive(folderName + "/temp/Archive_1.raf");

        for(int i = 0; i < ar.files.size(); i++){
            ReleaseManifest.File f = ar.files.get(i);
            archivePercentage = (float) i / ar.files.size();
            currentFile = f.name;
            try (InputStream in = source.readFile(f.path + f.name)) {
                target.writeFile(f.path + f.name, in, this);
                if(done) return;
            }
        }
        target.close();
        if(target.fileList.isEmpty()){
            LoLPatcher.deleteDir(folder);
        }else{
            sourceRaf.delete();
            sourceRafDat.delete();
            new File(folderName + "/temp/Archive_1.raf").renameTo(sourceRaf);
            new File(folderName + "/temp/Archive_1.raf.dat").renameTo(sourceRafDat);
            new File(folderName + "/temp/").delete();
        }
    }

    @Override
    public float getPercentage() {
        return 100 * (globalPercentage + archivePercentage / nArchives);
    }
    
}
