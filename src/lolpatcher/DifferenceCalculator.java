package lolpatcher;

import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

/**
 * "Calculating differences..."
 * @author Rick
 */
public class DifferenceCalculator extends Thread{
    
    LoLPatcher patcher;
    int off;
    int len;
    ReleaseManifest mf, oldmf;
    FilenameFilter filter;
    public ArrayList<ReleaseManifest.File> result = new ArrayList<>();

    public DifferenceCalculator(LoLPatcher patcher, ReleaseManifest mf, ReleaseManifest oldmf, FilenameFilter filter, int off, int len) {
        this.patcher = patcher;
        this.off = off;
        this.len = len;
        this.mf = mf;
        this.oldmf = oldmf;
        this.filter = filter;
    }

    @Override
    public void run() {
        try{
            for(int i = 0; i < len; i++){
                ReleaseManifest.File f = mf.files[off + i];
                if(filter.accept(null, f.name) && needPatch(f, oldmf)){
                    result.add(f);
                }
            }
            Collections.sort(result, new Comparator<ReleaseManifest.File>() {
                @Override
                public int compare(ReleaseManifest.File o1, ReleaseManifest.File o2) {
                    return Integer.compare(o1.releaseInt , o2.releaseInt);
                }
            });
        } catch (IOException ex) {
            patcher.error = ex;
            ex.printStackTrace();
        }
    }
    
    
    private boolean needPatch(ReleaseManifest.File f, ReleaseManifest oldmf) throws IOException{
        if(patcher.force) return true;
        
        if(f.fileType == 22 || f.fileType == 6){
            RAFArchive archive = patcher.getArchive(f.release);
            boolean res = archive.dictionary.get(f.path + f.name) == null;
            if(res){
                System.out.println("need patch " + f.name);
            }
            return res;
        }else{
            if(oldmf != null && !patcher.forceSingleFiles){
                ReleaseManifest.File oldFile = oldmf.getFile(f.path + f.name);
                if(oldFile != null && Arrays.equals(oldFile.checksum, f.checksum)
                        && new java.io.File(patcher.getFileDir(f), f.name).exists()){
                    
                    return false;
                }
            }
            return true;
        }
    }
    
    public static ArrayList<ReleaseManifest.File> mergeLists(ArrayList<ReleaseManifest.File>... lists){
        ArrayList<ReleaseManifest.File> total = new ArrayList<>();
        int[] indices = new int[lists.length]; // init 0
        
        ReleaseManifest.File smallest;
        do{
            int smallestIndex = -1;
            smallest = null;
            for(int i = 0; i < lists.length; i++){
                if(indices[i] < lists[i].size()){
                    ReleaseManifest.File f = lists[i].get(indices[i]);
                    if(smallest == null || f.releaseInt < smallest.releaseInt){
                        smallestIndex = i;
                        smallest = f;
                    }
                }
            }
            if(smallest != null){
                indices[smallestIndex]++;
                total.add(smallest);
            }
        }while(smallest != null);
        
        return total;
    }
     
}
