package lolpatcher;

import java.awt.Font;
import java.awt.Point;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import nl.xupwup.Util.Color;
import nl.xupwup.Util.GLFramework;
import nl.xupwup.Util.TextRenderer;
import nl.xupwup.WindowManager.Component;
import nl.xupwup.WindowManager.Components.Button;
import nl.xupwup.WindowManager.Components.CheckBox;
import nl.xupwup.WindowManager.Components.Option;
import nl.xupwup.WindowManager.Components.SelectList;
import nl.xupwup.WindowManager.Components.TextField;
import nl.xupwup.WindowManager.Listener;
import nl.xupwup.WindowManager.Window;
import org.lwjgl.input.Mouse;
import static org.lwjgl.opengl.GL11.*;

/**
 *
 * @author Rick Hendricksen
 */
public class Main extends GLFramework {
    public static final int patcherVersion = 5;
    public List<PatchTask> patchers;
    int currentPatcher = -1;
    PatchTask patcher;
    TextRenderer tr;
    News news = new News();
    public String airversion;
    long patcherStartTime;
    boolean ignoreS_OK = false, force = false;
    Window repairWindow;
    boolean changeRegionSettings = false;
    
    float playw, playh, playx, playy, repairw;
    
    public Main(){
        super("XUPWUP's League Of Legends Patcher", false);
        showFPS = false;
        patchers = new ArrayList<>();
    }
    
    public void rerun(){
        patchers.clear();
        if(currentPatcher == -1){
            patchers.add(new RunTask(new Runnable() {
                @Override
                public void run() {
                    try {
                        URL u = new URL("http://lolpatcher.xupwup.nl/version");
                        URLConnection con = u.openConnection();
                        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                        StringBuilder response = new StringBuilder();
                        String inputLine;
                        
                        while ((inputLine = in.readLine()) != null){
                            response.append(inputLine);
                        }
                        in.close();
                        int version = Integer.parseInt(response.toString().trim());
                        if(version > patcherVersion){
                            Window w = new Window(new Point(60,120), "Update available");
                            w.addComponent(new TextField(300, "An update is available"
                                    + " for XUPWUP's League of Legends Patcher. Get"
                                    + " it at http://lolpatcher.xupwup.nl/", null));
                            wm.addWindow(w);
                        }
                    } catch (IOException ex) {
                        Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                    }

                }
            }, "Update check"));
        }
        String slnversion = LoLPatcher.getVersion("solutions", "lol_game_client_sln", "EUW");
        
        
        patchers.add(new ConfigurationTask(slnversion, this));
        
        patcher = null;
        currentPatcher = -1;
    }

    @Override
    public void post_glInit() {
        new SelectList(new String[]{"a"}, null, null, 0); // (ugly hack) make it initialise its textures on this thread
        
        repairWindow = new Window(new Point(5,5), "Options");
        final Option sokopt = new Option("Quick repair", null, null, ignoreS_OK);
        repairWindow.addComponent(sokopt);
        final Option forcopt = new Option("Thorough repair", new Listener() {
            @Override
            public void click(Component c) {
                force = ((CheckBox) c).checked;
                if(force){
                    ignoreS_OK = true;
                }
                sokopt.cb.checked = ignoreS_OK;
            }
        }, null, ignoreS_OK);
        sokopt.cb.call = new Listener() {
            @Override
            public void click(Component c) {
                ignoreS_OK = ((CheckBox) c).checked;
                if(!ignoreS_OK){
                    force = false;
                }
                forcopt.cb.checked = force;
            }
        };
        repairWindow.addComponent(forcopt);
        repairWindow.addComponent(new Option("Change region settings", new Listener() {
            @Override
            public void click(Component c) {
                changeRegionSettings = ((CheckBox) c).checked;
            }
        }, null));
        repairWindow.addComponent(new Button("Go", new Listener() {
            @Override
            public void click(Component c) {
                if(changeRegionSettings){
                    new File("settings.txt").delete();
                }
                wm.closeWindow(repairWindow);
                rerun();
            }
        }, null));
        
        tr = new TextRenderer(new Font("SansSerif", Font.PLAIN, 15), true);
        try {
            news.get();
            news.genTextures();
            playw = news.bold.getWidth("PLAY");
            repairw = news.bold.getWidth("Settings");
            playh = news.bold.getHeight();
            playx = (float) ((windowSize.x/2) - playw/2) - 60;
            playy = (float) (windowSize.y - playh - 3);
        } catch (IOException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        rerun();
    }

    private boolean updatePatcher(){
        if(patcher == null || patcher.done){
            if(patcher != null){
                try {
                    patcher.join();
                } catch (InterruptedException ex) {
                    Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            if(currentPatcher == patchers.size()-1){
                return true;
            }
            currentPatcher++;
            patcher = patchers.get(currentPatcher);
            patcher.start();
            patcherStartTime = System.currentTimeMillis();
        }
        
        return false;
    }
    
    @Override
    public void pre_glInit() {
        
        
    }

    @Override
    public void glInit() {
        
    }

    @Override
    public void draw(int w, int h) {
        inhibitFXAA = true;
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, w, h, 0, 0, 1f);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        
        
        
        int newsHeight = news.draw() + 5;
        glColor3f(0.8f, 0.8f, 0.8f);
        glBegin(GL_QUADS);
            glVertex2i(0, newsHeight);
            glVertex2i(0, newsHeight + 200);
            glVertex2i(600, newsHeight + 200);
            glVertex2i(600, newsHeight);
        glEnd();
        Color.BLACK.bind();
        
        if(patcher != null){
            if(patcher.error != null){
                try {
                    java.io.File log = new java.io.File("PATCHLOG.txt");
                    log.createNewFile();
                    try(PrintWriter pw = new PrintWriter(log)){
                        patcher.error.printStackTrace(pw);
                    }
                } catch (IOException ex) {
                    Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                }
                StringWriter sw = new StringWriter();
                try(PrintWriter pw = new PrintWriter(sw)){
                    patcher.error.printStackTrace(pw);
                }
                patcher.error = null;
                Window win = new Window(new Point(0, 200), "Error");
                win.addComponent(new TextField(1000, 200, sw.toString().replaceAll("[\r\t]", ""), null));
                wm.addWindow(win);
            }
            PatchTask lp = patcher;
            String currentFile = lp.currentFile;
            int speed = LoLPatcher.speed;
            float percentage = Math.max(0.01f, lp.getPercentage());

            long spent = (System.currentTimeMillis() - patcherStartTime);
            int etaSec = (int) (spent / (10 * percentage)) - (int) (spent / 1000);
            int etaMin = etaSec / 60;
            etaSec -= etaMin * 60;
            int texth = newsHeight + 5;
            
            if(currentFile != null && !patcher.done){
                tr.draw(currentFile, 400, texth);
            }
            tr.draw("Task: " + (currentPatcher + 1) + "/" + patchers.size(), 5, texth);
            if(!patcher.done){
                tr.draw(speed + " KiB/s", 100, texth);
                String esec = "" + etaSec;
                if(esec.length() == 1) { // leading zeros
                    esec = "0" + esec;
                }
                tr.draw("Remaining: " + etaMin + ":" + esec, 240, texth);
            }
            int bary = texth + tr.getHeight();
            int barh = 10;

            int xoff = 5;
            glBegin(GL_QUADS);
                glVertex2i(xoff, bary);
                glVertex2i(xoff, bary + barh);
                glVertex2i(600 - xoff, bary + barh);
                glVertex2i(600 - xoff, bary);
            glEnd();
            Color.WHITE.bind();
            glBegin(GL_QUADS);
                glVertex2f(2 + xoff, bary + 2);
                glVertex2f(2 + xoff, bary + barh - 2);
                glVertex2f(xoff + 2 + (598 - 2 * xoff - 2) * (percentage / 100), bary + barh - 2);
                glVertex2f(xoff + 2 + (598 - 2 * xoff - 2) * (percentage / 100), bary + 2);
            glEnd();
        }
        
        
        if(patcher != null && patcher.error == null && patcher.done && currentPatcher == patchers.size() - 1){
            int x = Mouse.getX();
            int y = Mouse.getY();
            y = (int) (windowSize.y - y);
            if(x > playx && x < playx + playw &&
                    y > playy && y < playy + playh){

                glColor3f(1, 0.5f, 0);
            }else{
                glColor3f(1, 0.8f, 0);
            }
            
            glBegin(GL_QUADS);
                glVertex2f(playx - 3, playy - 4);
                glVertex2f(playx - 3, playy + playh);
                glVertex2f(playx + playw + 3, playy + playh);
                glVertex2f(playx + playw + 3, playy - 4);
            glEnd();
            glColor3f(0, 0, 0);
            news.bold.draw("PLAY", playx, playy);
            
            if(x > playx + 100 && x < playx + repairw + 100 &&
                    y > playy && y < playy + playh){

                glColor3f(1, 0.5f, 0);
            }else{
                glColor3f(1, 0.8f, 0);
            }
            
            glBegin(GL_QUADS);
                glVertex2f(100 + playx - 3, playy - 4);
                glVertex2f(100 + playx - 3, playy + playh);
                glVertex2f(100 + playx + repairw + 3, playy + playh);
                glVertex2f(100 + playx + repairw + 3, playy - 4);
            glEnd();
            glColor3f(0, 0, 0);
            news.bold.draw("Settings", 100 + playx, playy);
        }
        
        repaint();
        if(updatePatcher()){
            //System.out.println("done");
        }
    }

    @Override
    public void onClose() {
        if(!patchers.get(currentPatcher).done){
            patchers.get(currentPatcher).done = true;
            try {
                patchers.get(currentPatcher).join();
            } catch (InterruptedException ex) {
                Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
    @Override
    public void resize(int w, int h) {
        
    }

    @Override
    public void onClick(int x, int y) {
        if(patcher != null && patcher.error == null && patcher.done && currentPatcher == patchers.size() - 1){
            y = (int) (windowSize.y - y);
            if(x > playx && x < playx + playw &&
                    y > playy && y < playy + playh){
                try {
                    Runtime.getRuntime().exec(new String[]{"java", "-jar", "Maestro.jar", new java.io.File("RADS/solutions/lol_game_client_sln/releases/").getAbsolutePath()});

                    Runtime.getRuntime().exec(new String[]{new java.io.File("RADS/projects/lol_air_client/releases/"+airversion+"/deploy/LolClient.exe").getAbsolutePath()});
                    System.exit(0);
                } catch (IOException ex) {
                    Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                }

            }else if(x > playx + 100 && x < 100 + playx + repairw &&
                    y > playy && y < playy + playh){

                wm.addWindow(repairWindow);
            }
        }
    }

    @Override
    public void onDrag(int x, int y) {
        
    }

    @Override
    public void onRelease() {
        
    }
    
    public static void main(String[] args){
        //System.setProperty("org.lwjgl.librarypath", new File("lwjgllib").getAbsolutePath());
        try{
            new Main().run();
        }catch(Exception e){
            e.printStackTrace();
            try {
                java.io.File log = new java.io.File("GUILOG.txt");
                log.createNewFile();
                try(PrintWriter pw = new PrintWriter(log)){
                    e.printStackTrace(pw);
                }
            } catch (IOException ex) {
                Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            }
            
        }
    }
}
