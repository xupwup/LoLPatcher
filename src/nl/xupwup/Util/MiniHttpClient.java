package nl.xupwup.Util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MiniHttpClient implements AutoCloseable {
    private Socket sock;
    private OutputStream os;
    private InputStream in;
    private String server;
    private int port = 80;
    private boolean close;
    private boolean closeConnection;
    
    public HttpResult lastResult;
    public boolean throwExceptionWhenNot200 = false;
    
    public MiniHttpClient(String server) throws IOException{
        this(server, false);
    }
    
    /**
     * Opens a connection to a server.
     * @param server  for example "google.com". Must not include "http://"
     * @param closeConnection  whether the connection should be closed after every file
     * @throws IOException 
     */
    public MiniHttpClient(String server, boolean closeConnection) throws IOException{
        String[] sp = server.split(":");
        
        if(sp.length > 1){
            port = Integer.parseInt(sp[1]);
        }
        this.server = sp[0];
        this.closeConnection = closeConnection;
        close = true;
    }
    
    private static void sendRequest(OutputStream os, String query, String... headers) throws IOException{
        os.write((query + "\r\n").getBytes());
        for(String hdr : headers){
            os.write((hdr + "\r\n").getBytes());
        }
        os.write("\r\n".getBytes("ASCII"));
    }
    
    
    private void readEverything(InputStream in) throws IOException{
        byte[] bytes = new byte[1024];
        while(in.read(bytes) != -1){
            // do nothing
        }
    }
    
    @Override
    public void close() throws IOException{
        if(sock != null){
            sock.close();
        }
        close = true;
    }
    
    /**
     * Issues a get request and flushes the last returned response object.
     * @param url  Relative urls only! For example "/test.html"
     * @return HTTPResult object
     * @throws IOException 
     */
    public HttpResult get(String url) throws IOException{
        if(lastResult != null){
            readEverything(lastResult.in); // make sure everything is read, 
                                             // so we dont read old data instead of headers
        }
        
        if(close || sock.isClosed()){
            if(sock != null){
                close();
            }
            sock = new Socket(server, port);
            in = new BufferedInputStream(sock.getInputStream());
            os = new BufferedOutputStream(sock.getOutputStream());
        }
        close = closeConnection;
        
        sendRequest(os, "GET " + url + " HTTP/1.1", 
                "Host: " + server,
                "Accept: text/html", 
                "Content-Length: 0",
                "Connection: " + (closeConnection ? "close" : "keep-alive"),
                "User-Agent: rickHttpClient"
        );
        os.flush();
        boolean chunked = false;
        
        ArrayList<String> headers = new ArrayList<>();
        
        byte[] left = getHeaders(in, headers);

        int length = -1;
        for(String header : headers){
            String[] split = header.split(":");
            if(split[0].equalsIgnoreCase("Content-Length")){
                length = Integer.parseInt(split[1].trim());
            }
            if(split[0].equalsIgnoreCase("Connection") && split[1].trim().equalsIgnoreCase("close")){
                close = true;
            }
            if(split[0].equalsIgnoreCase("Transfer-Encoding") && split[1].trim().equalsIgnoreCase("chunked")){
                chunked = true;
            }
        }
        if(close){
            length = -1;
        }
        
        int status = Integer.parseInt(headers.get(0).split(" ")[1]);
        
        InputStream httpStream;
        if(chunked){
            assert(length == -1);
            httpStream = new ChunkedInputStream(new HTTPInputStream(left, in, length));
        }else{
            httpStream = new HTTPInputStream(left, in, length);
        }
        lastResult = new HttpResult(httpStream, headers, status);
        if(status != 200 && throwExceptionWhenNot200){
            throw new IOException(headers.get(0) + ", for url: " + url);
        }
        return lastResult;
    }
    
    /**
     * 
     * @param in
     * @param headers
     * @return bytes it read that belong to the response body.
     * @throws IOException 
     */
    private static byte[] getHeaders(InputStream in, ArrayList<String> headers) throws IOException{
        int left = 0;
        byte[] buffer = new byte[2048];
        byte[] obuffer = new byte[2048];
        int read;
        while((read = in.read(buffer, left, buffer.length - left)) != -1){
            int idx = getNewlineInByteArray(buffer, 0, read + left);
            
            if(idx == -1 && read + left == buffer.length){
                throw new IOException("Header line > " + buffer.length);
            }
            
            int start = 0;
            while(idx != -1){
                if(idx == start){
                    start = idx + 2;
                    break;
                }else{
                    headers.add(new String(buffer, start, idx - start));
                }
                start = idx + 2;
                idx = getNewlineInByteArray(buffer, start, read + left);
            }
            left = read + left - start;
            System.arraycopy(buffer, start, obuffer, 0, left);
            byte[] h = buffer;
            buffer = obuffer;
            obuffer = h;
            if(idx + 2 == start){
                break;
            }
        }
        return Arrays.copyOfRange(buffer, 0, left);
    }
    
    /**
     * Gets the first occurrence of a newline in a bytearray.
     * @param in  the bytearray to search in
     * @param o  offset
     * @param max  the length of the array. (if this is less than in.length only the first 'max' elements will be considered)
     * @return 
     */
    private static int getNewlineInByteArray(byte[] in, int o, int max){
        byte r = "\r".getBytes()[0];
        byte n = "\n".getBytes()[0];
        for(int i = o; i < Math.min(max, in.length) -1; i++){
            if(in[i] == r && in[i+1] == n){
                return i;
            }
        }
        return -1;
    }
    
    public static class HttpResult{
        public final List<String> headers;
        public final InputStream in;
        public final int code;
        
        private HttpResult(InputStream in, List<String> headers, int code){
            this.headers = headers;
            this.in = in;
            this.code = code;
        }
    }
    
    private static class ChunkedInputStream extends InputStream{
        private final HTTPInputStream in;
        int chunkRemaining = 0;
        boolean firstChunk = true;
        
        public ChunkedInputStream(HTTPInputStream in) {
            this.in = in;
        }
        
        private String[] getChunkHeader() throws IOException{
            StringBuilder sb = new StringBuilder();
            boolean fi = false;
            while(true){
                int read = in.read();
                if(read == -1) {
                    throw new IOException("Connection closed.");
                }
                if(read == '\n' && fi) break;

                if(read != '\r'){
                    sb.append((char) read);
                }else{
                    fi = true;
                }
            }
            return sb.toString().split(";");
        }
        
        private void updateChunk() throws IOException{
            if(!firstChunk){ // if this is not the first chunk, skip over the CRLF
                int r = in.read();
                int n = in.read();
                if(r != '\r' || n != '\n'){
                    throw new IOException("Invalid chunked encoding");
                }
            }
            firstChunk = false;
            
            String[] chunkHeader = getChunkHeader();
            chunkRemaining = Integer.parseInt(chunkHeader[0], 16);
            if(chunkRemaining == 0){
                byte[] bytesLeft = getHeaders(in, new ArrayList());
                if(bytesLeft.length > 0){
                    throw new IOException("Invalid chunked encoding");
                }
                if(in.left.length > in.alreadyRead){
                    throw new IOException("Invalid chunked encoding");
                }
                chunkRemaining = -1;
            }
        }

        @Override
        public int read() throws IOException {
            if(chunkRemaining == 0){
                updateChunk();
            }
            if(chunkRemaining == -1){
                return -1;
            }
            chunkRemaining--;
            return in.read();
        }

        @Override
        public int read(byte[] bytes, int offset, int count) throws IOException {
            if(chunkRemaining == 0){
                updateChunk();
            }
            if(chunkRemaining == -1){
                return -1;
            }

            count = Math.min(count, chunkRemaining);

            int read = in.read(bytes, offset, count);
            chunkRemaining -= read;
            return read;
        }
    }
    
    private static class HTTPInputStream extends InputStream{
        final byte[] left;
        final int length;
        int alreadyRead = 0;
        final InputStream actual;
        
        public HTTPInputStream(byte[] left, InputStream actual, int length) {
            this.left = left;
            this.actual = actual;
            this.length = length;
        }
        
        
        @Override
        public int read() throws IOException {
            if(alreadyRead >= length && length != -1){
                return -1;
            }else{
                int r;
                if(alreadyRead < left.length){
                    r = left[alreadyRead];
                }else{
                    r = actual.read();
                }
                alreadyRead++;
                return r;
            }
        }
        
        @Override
        public int read(byte[] bytes, int offset, int count) throws IOException {
            // this function reads from the buffer "left" first, then just reads
            // from the inputstream. (when getting headers the get function will likely have
            // read too much, data that was supposed to be the response body)
            
            if(alreadyRead >= length && length != -1){
                return -1;
            }else{
                count = Math.min(count, bytes.length - offset);
                if(alreadyRead < left.length){
                    if(length == -1){
                        count = Math.min(count,                  left.length  - alreadyRead);
                    }else{
                        count = Math.min(count, Math.min(length, left.length) - alreadyRead);
                    }
                    
                    System.arraycopy(left, alreadyRead, bytes, offset, count);
                }else{
                    if(length != -1){
                        count = Math.min(count, length - alreadyRead);
                    }
                    count = actual.read(bytes, offset, count);
                }
                alreadyRead += count;
                return count;
            }
        }

        @Override
        public int read(byte[] bytes) throws IOException {
            return read(bytes, 0, bytes.length);
        }
    }
}
