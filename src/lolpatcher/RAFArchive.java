package lolpatcher;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import static lolpatcher.StreamUtils.*;
import nl.xupwup.Util.CountingOutputStream;
import nl.xupwup.Util.FileSliceInputStream;

/**
 *
 * @author Rick
 */
public class RAFArchive {
    File raf;
    File datRaf;
    CountingOutputStream out;
    boolean changed = false;
    ArrayList<RafFile> fileList;
    HashMap<String, RafFile> dictionary;
    final private ArrayList<RafFileOutputStream> rafFileOutputStreams = new ArrayList<>();
    
    public RAFArchive(String path) throws IOException{
        raf = new File(path);
        datRaf = new File(path + ".dat");
        datRaf.createNewFile();
        fileList = new ArrayList<>();
        out = new CountingOutputStream(new BufferedOutputStream(new FileOutputStream(datRaf)));
        dictionary = new HashMap<>();
    }
    
    /**
     * 
     */
    private boolean isCompressed(RafFile f) throws IOException{
        try {
            RandomAccessFile in = new RandomAccessFile(datRaf, "r");
            in.seek(f.startindex);
            return (in.readByte() == 0x78 && in.readByte() == 0xffffff9c);
        } catch (FileNotFoundException ex) {
            Logger.getLogger(RAFArchive.class.getName()).log(Level.SEVERE, null, ex);
        }
        return false;
    }
    
    public RAFArchive(File raf, File datRaf) throws FileNotFoundException, IOException{
        this.raf = raf;
        this.datRaf = datRaf;
        fileList = new ArrayList<>();
        try (RandomAccessFile in = new RandomAccessFile(raf, "r")) {
            int magicNumber = getInt(in);
            assert(magicNumber == 0x18be0ef0);
            int version = getInt(in);
            //System.out.println("Raf version: " + version);
            int managerIndex = getInt(in);
            //System.out.println("ManagerIndex: " + managerIndex);
            
            int fileListOffset = getInt(in);
            int pathListOffset = getInt(in);
            int nfiles = getInt(in);
            for(int i = 0; i < nfiles; i++){
                int pathHash = getInt(in);
                long offset = getInt(in) & 0x00000000ffffffffL;
                int size = getInt(in);
                int pathlistindex = getInt(in);
                
                RafFile rf = new RafFile(offset, "");
                rf.pathhash = pathHash;
                rf.size = size;
                rf.pathlistindex = pathlistindex;
                
                fileList.add(rf);
            }
            
            long offset = in.getFilePointer();
            int pathListSize = getInt(in);
            int pathListCount = getInt(in);
            for(int i = 0; i < fileList.size(); i++){
                RafFile rf = fileList.get(i);
                in.seek(offset + 8 + rf.pathlistindex * 8);
                int stringOffset = getInt(in);
                int stringLength = getInt(in);
                
                in.seek(stringOffset + offset);
                rf.name = new String(getBytes(in, stringLength-1)); // -1 to clip \0
            }
        }
        dictionary = new HashMap<>();
        long maxindex = 0;
        for(RafFile f : fileList){
            dictionary.put(f.name, f);
            maxindex = Math.max(maxindex, f.startindex + f.size);
        }
        out = new CountingOutputStream(new BufferedOutputStream(new FileOutputStream(datRaf, true)));
        out.setIndex(datRaf.length());
    }
    
    public class RafFile{
        long startindex; // fits in unsigned int, but not in normal int
        int size = 0;
        String name;
        int pathhash;
        int pathlistindex;
        
        RafFile(long startIndex, String name){
            pathhash = hash(name);
            this.name = name;
            this.startindex = startIndex;
        }

        @Override
        public String toString() {
            return name + " (size: " + size + " startindex: "+startindex + " pathlistindex: "+pathlistindex+")";
        }
    }
    
    
    /**
     * Writes the .raf.dat file
     * @param path
     * @param name
     * @param in
     * @param patcher
     * @throws IOException 
     */
    public void writeFile(String path, InputStream in, PatchTask patcher) throws IOException{
        changed = true;
        RafFile rf = new RafFile(out.getIndex(), path);
        fileList.add(rf);
        rf.pathlistindex = fileList.size()-1;
        
        byte[] buffer = new byte[4096];
        int read;
        while((read = in.read(buffer)) != -1){
            out.write(buffer, 0, read);
            LoLPatcher.speedStat(read);
            rf.size += read;
            if(patcher.done) return;
        }
        dictionary.put(rf.name, rf);
    }
    public OutputStream getRAFFileOutputStream(String path, int offset){
        RafFileOutputStream os = new RafFileOutputStream(out, path, offset);
        synchronized(rafFileOutputStreams){
            rafFileOutputStreams.add(os);
        }
        return os;
    }
    
    /**
     * byte order: least significant first
     * @param in
     * @return
     * @throws IOException 
     */
    private static byte[] getIntBytes(int n){
        byte[] bytes = new byte[4];
        
        for(int i = 0; i < 4; i++){
            bytes[i] = (byte) (n & 0xFF);
            n = n >>> 8;
        }
        return bytes;
    }
    
    /**
     * byte order: least significant first
     * @param in
     * @return
     * @throws IOException 
     */
    private static byte[] getIntBytes(long n){
        byte[] bytes = new byte[4];
        
        for(int i = 0; i < 4; i++){
            bytes[i] = (byte) (n & 0xFF);
            n = n >>> 8;
        }
        return bytes;
    }
    
    /**
     * Writes the .raf file itself
     */
    public void close() throws IOException{
        sync();
        out.close();
    }
    
    public void sync() throws IOException{
        if(!changed){
            return;
        }
        out.flush();
        raf.createNewFile();
        try (OutputStream rafOut = new BufferedOutputStream(new FileOutputStream(raf))){
            rafOut.write(getIntBytes(0x18be0ef0)); // magic number
            rafOut.write(getIntBytes(1)); // raf version

            rafOut.write(getIntBytes(0)); // raf manager index (why zero??)

            rafOut.write(getIntBytes(20)); // File list offset
            rafOut.write(getIntBytes(20 + 4 + fileList.size() * 16)); // Path list offset

            rafOut.write(getIntBytes(fileList.size())); // count of file entries

            Collections.sort(fileList, new Comparator<RafFile>(){
                @Override
                public int compare(RafFile o1, RafFile o2) {
                    long o1hash = o1.pathhash & 0xffffffff;
                    long o2hash = o2.pathhash & 0xffffffff;
                    if(o1hash > o2hash){
                        return 1;
                    }else if (o1hash < o2hash){
                        return -1;
                    }else{
                        return o1.name.compareTo(o2.name);
                    }
                }
            });
            int pathlistindex = 0;
            for(RafFile f : fileList){
                rafOut.write(getIntBytes(f.pathhash)); // path hash
                rafOut.write(getIntBytes(f.startindex)); // start index
                rafOut.write(getIntBytes(f.size)); // size
                rafOut.write(getIntBytes(pathlistindex++)); // path list index
            }

            int stringSum = 0;
            for(RafFile f : fileList){
                stringSum += f.name.getBytes().length + 1; // include nul byte
            }
            rafOut.write(getIntBytes(stringSum)); // path list size
            rafOut.write(getIntBytes(fileList.size())); // path list count

            int pathOffset = 8 + fileList.size() * 8;
            for(RafFile f : fileList){
                rafOut.write(getIntBytes(pathOffset)); // path offset
                int l = f.name.getBytes().length + 1;
                pathOffset += l;
                rafOut.write(getIntBytes(l)); // path length
            }
            for(RafFile f : fileList){
                rafOut.write(f.name.getBytes()); // path list count
                rafOut.write(0x00); // path list count
            }
            changed = false;
        } catch (FileNotFoundException ex) {
            Logger.getLogger(RAFArchive.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    
    /**
     * Calculates a hash for a given file path. This is the hash that is part of
     * each FileEntry in a .raf file. Note that this is the file path to which
     * RAFUnpacker will unpack the file.
     *
     * @param filePath the file path from which to construct the hash
     * @return the hash that needs to be set in a FileEntry
     * @author ArcadeStorm
     */
    public static int hash(String filePath) {
        long hash = 0;
        long temp;
        for (int i = 0; i < filePath.length(); i++) {
            hash = ((hash << 4) + Character.toLowerCase(filePath.charAt(i))) & 0xffffffff;
            temp = hash & 0xf0000000;
            hash ^= (temp >>> 24);
            hash ^= temp;
        }
        return (int) hash;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        try{
            for(RafFile rf : fileList){
                sb.append(rf.name.replace("\0","\\0")).append(" hash=").append(rf.pathhash).append(" -- ").append(isCompressed(rf)).append("\n");
            }
        }catch(IOException e){
            e.printStackTrace();
        }
        return sb.toString();
    }
    
    public InputStream readFile(RafFile selectedFile) throws IOException{
        return new FileSliceInputStream(datRaf, selectedFile.startindex, selectedFile.size);
    }
    
    public InputStream readFile(String path) throws IOException{
        RafFile selectedFile = dictionary.get(path);
        if(selectedFile == null){
            throw new FileNotFoundException("\"" + path +"\" was not found in archive " + raf.getPath());
        }
        
        return readFile(selectedFile);
    }
    
    private class RafFileOutputStream extends OutputStream{
        long startIndex = -1;
        long currentIndex;
        String path;
        CountingOutputStream out;
        
        /**
         * @param out
         * @param path
         * @param offset 
         */
        public RafFileOutputStream(CountingOutputStream out, String path, int offset) {
            this.path = path;
            this.out = out;
        }

        private int preWrite(int c){
            if(startIndex == -1){
                startIndex = out.getIndex();
                currentIndex = out.getIndex();
            }
            long realIndex = out.getIndex();
            currentIndex += c;
            return (int) (currentIndex - realIndex);
        }
        
        @Override
        public void write(byte[] b) throws IOException {
            int toWrite = preWrite(b.length);
            if(toWrite > 0){
                out.write(b, b.length - toWrite, toWrite);
            }
        }

        @Override
        public void write(int b) throws IOException {
            int toWrite = preWrite(1);
            if(toWrite > 0){
                out.write(b);
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            int toWrite = preWrite(len);
            if(toWrite > 0){
                out.write(b, off + (len - toWrite), toWrite);
            }
        }
        

        @Override
        public void close() throws IOException {
            RafFile rf = new RafFile(startIndex, path);
            rf.size = (int) (currentIndex - startIndex);
            fileList.add(rf);
            rf.pathlistindex = fileList.size()-1;
            dictionary.put(rf.name, rf);
            synchronized(rafFileOutputStreams){
                rafFileOutputStreams.remove(this);
            }
            changed = true;
        }
    }
}

