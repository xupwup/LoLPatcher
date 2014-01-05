/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package lolpatcher;

import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import javax.imageio.ImageIO;
import nl.xupwup.Util.Color;
import nl.xupwup.Util.TextRenderer;
import nl.xupwup.Util.Texture;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.lwjgl.opengl.GL11.*;

/**
 *
 * @author Rick
 */
public class News {
    int serverStatus;
    boolean status;
    private ArrayList<CommunityItem> communityItems;
    private ArrayList<NewsItem> newsItems;
    public TextRenderer bold;
    TextRenderer normal;
    
    public News(){
        communityItems = new ArrayList<>();
        newsItems = new ArrayList<>();
    }
    
    
    public void genTextures(){
        bold = new TextRenderer(new Font("SansSerif", Font.BOLD, 15), true);
        normal = new TextRenderer(new Font("SansSerif", Font.BOLD, 13), true);
        for(CommunityItem c : communityItems){
            c.imageTex = new Texture(c.image);
            c.promoText = TextRenderer.wordWrap(c.promoText, normal, 590);
        }
    }
    
    int currentCommunityItem = 0;
    float currentShift = 0;
    float targetShift = 0;
    long shiftStart = 0;
    
    public int draw(){
        glColor3f(1,1,1);
        int index = 0;
        for(NewsItem ni : newsItems){
            int datew = (int) bold.getWidth(ni.date);
            glColor3f(0.8f, 0.8f,0.8f);
            bold.draw(ni.date, 0, index);
            
            Color.WHITE.bind();
            normal.draw(ni.title, datew + 10, index);
            index += bold.getHeight();
        }
        
        if(communityItems.isEmpty()){
            return index;
        }
        index += 10;
        long now = System.currentTimeMillis();
        if(now - shiftStart > 5000){
            currentCommunityItem = (currentCommunityItem + 1) % communityItems.size();
            targetShift = currentCommunityItem;
            shiftStart = now;
        }
        currentShift = (10 * currentShift + targetShift) / 11;
        
        int floor = (int) Math.floor(currentShift);
        int ceil = (int) Math.ceil(currentShift);
        
        float x = (-currentShift + floor) * 600;
        communityItems.get(floor).draw((int) x + 1, index);
        if(floor != ceil && ceil < communityItems.size()){
            x = (-currentShift + ceil) * 600;
            communityItems.get(ceil).draw((int) x + 1, index);
        }
        
        return index + communityItems.get(floor).imageTex.height;
    }
    
    private class NewsItem{
        String date;
        String title;
        String url;

        public NewsItem(String date, String title, String url) {
            this.date = date;
            this.title = title;
            this.url = url;
        }
    }
    
    private class CommunityItem{
        BufferedImage image;
        BufferedImage thumb;
        Texture imageTex, thumbTex;
        String link;
        String title;
        String promoText;

        void draw(int x, int y){
            imageTex.draw(x, y);
            float texty = y + imageTex.height - normal.getHeight(promoText) - 10;
            glColor4f(0,0,0,0.4f);
            glBegin(GL_QUADS);
                glVertex2f(x, texty);
                glVertex2f(x, y + imageTex.height);
                glVertex2f(x + imageTex.width, y + imageTex.height);
                glVertex2f(x + imageTex.width, texty);
            glEnd();
            int titleh = bold.getHeight();
            glBegin(GL_QUADS);
                glVertex2f(x, y);
                glVertex2f(x, y + titleh + 5);
                glVertex2f(x + imageTex.width, y + titleh + 5);
                glVertex2f(x + imageTex.width, y);
            glEnd();
            glColor4f(1,1,1,1);
            bold.draw(title, x + 5, y + 5);
            normal.draw(promoText, x+5, texty + 5);
        }
        
        public CommunityItem(BufferedImage image, BufferedImage thumb, String link, String title, String promoText) {
            this.image = image;
            this.thumb = thumb;
            this.link = link;
            this.title = title;
            this.promoText = promoText;
        }
    }
    
    public void get() throws IOException{
        try (CloseableHttpClient hc = HttpClients.createDefault()) {
            HttpEntity hte = hc.execute(new HttpGet("http://ll.leagueoflegends.com/pages/launcher/euw?lang=en")).getEntity();
            
            InputStream in = hte.getContent();
            StringBuilder sb = new StringBuilder();
            int read;
            byte[] buffer = new byte[4096];
            while((read = in.read(buffer)) != -1){
                sb.append(new String(buffer, 0, read));
            }
            String result = sb.toString();
            result = result.substring(result.indexOf("(") + 1);
            result = result.substring(0, result.lastIndexOf(")"));
            JSONObject obj = new JSONObject(result);
            
            JSONArray community = obj.getJSONArray("community");
            JSONArray news = obj.getJSONArray("news");
            serverStatus = obj.getInt("serverStatus");
            status = obj.getBoolean("status");
            
            
            
            for(int i = 0; i < community.length(); i++){
                JSONObject o2 = community.getJSONObject(i);
                String imageurl = o2.getString("imageUrl");
                String linkurl = o2.getString("linkUrl");
                String thumbUrl = o2.getString("thumbUrl");
                HttpEntity http = hc.execute(new HttpGet(imageurl)).getEntity();
                //HttpEntity http2 = hc.execute(new HttpGet(thumbUrl)).getEntity();
                communityItems.add(new CommunityItem(ImageIO.read(http.getContent()), null, linkurl, o2.getString("title"), o2.getString("promoText")));
            }
            
            
            for(int i = 0; i < news.length(); i++){
                JSONObject o2 = news.getJSONObject(i);
                newsItems.add(new NewsItem(o2.getString("date"), o2.getString("title"), o2.getString("url")));
            }
        }
    }
}
