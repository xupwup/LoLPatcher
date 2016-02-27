package lolpatcher;

import java.awt.Point;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import nl.xupwup.WindowManager.Component;
import nl.xupwup.WindowManager.Components.Button;
import nl.xupwup.WindowManager.Components.SelectList;
import nl.xupwup.WindowManager.Listener;
import nl.xupwup.WindowManager.Window;

/**
 *
 * @author Rick
 */
public class ConfigurationTask extends PatchTask{
    Main main;
    String slnversion;
    String server;
    float percentage = 0;
    String language;
    
    public ConfigurationTask(Main main){
        this.main = main;
    }
    
    
    private void selectLanguage(final String server) throws IOException{
        slnversion = LoLPatcher.getVersion("solutions", "lol_game_client_sln", server); // temporarily use EUW to get language list
        String branch = (server.equals("PBE") ? "pbe" : "live");
        getSolutionManifest(slnversion, branch);
        File solutionmanifest = new java.io.File("RADS/solutions/lol_game_client_sln/releases/" + slnversion + "/solutionmanifest");
        BufferedReader br = new BufferedReader(new FileReader(solutionmanifest));
        String line;
        final ArrayList<String> languages = new ArrayList<>();
        while((line = br.readLine()) != null){
            if(line.matches("lol_game_client_[a-z]+_[a-z]+")){
                String nlang = line.substring("lol_game_client_".length());
                boolean found = false;
                for(String lang : languages){
                    if(lang.equals(nlang)){
                        found = true;
                        break;
                    }
                }
                if(!found){
                    languages.add(nlang);
                }
            }
        }
        final Window languageSelector = new Window(new Point(50, 10), "Select language");
        
        final SelectList languagelist = new SelectList(
                    languages.toArray(new String[languages.size()]), 2
                    , null, null, 0);
        languageSelector.addComponent(languagelist);
        languageSelector.addComponent(new Button("Save", new Listener() {
            @Override
            public void click(Component c) {
                main.wm.closeWindow(languageSelector);
                language = languages.get(languagelist.selected);

                Properties props = new Properties();
                try {
                    props.setProperty("server", server);
                    props.setProperty("language", language);
                    try(FileWriter fw = new FileWriter("settings.txt")){
                        props.store(fw, null);
                    }
                } catch (IOException ex) {
                    error = ex;
                    Logger.getLogger(ConfigurationTask.class.getName()).log(Level.SEVERE, null, ex);
                }
                addPatchers();
            }
        } , null));
        
        main.wm.addWindow(languageSelector);
    }
    
    @Override
    public void patch() throws MalformedURLException, IOException, NoSuchAlgorithmException {
        if(! new File("settings.txt").exists()){
            
            
            final Window serverSelector = new Window(new Point(70, 15), "Select server");
            final String[] serversHuman = new String[]{"EUW", "EUNE", "BR", "NA", "PBE", "LAN", "LAS", "JP", "OCE"};
            final String[] serversActual = new String[]{"EUW", "EUNE", "BR", "NA", "PBE", "LA1", "LA2", "JP", "OC1"};
            final SelectList serverlist = new SelectList(
                    serversHuman, 1
                    , null, null, 0);
            serverSelector.addComponent(serverlist);
            serverSelector.addComponent(new Button("Save", new Listener() {
                @Override
                public void click(Component c) {
                    main.wm.closeWindow(serverSelector);
                    server = serversActual[serverlist.selected];
                    try {
                        selectLanguage(server);
                    } catch (IOException ex) {
                        Logger.getLogger(ConfigurationTask.class.getName()).log(Level.SEVERE, null, ex);
                        error = ex;
                    }
                }
            }, null));
            
            main.wm.addWindow(serverSelector);
        }else{
            Properties props = new Properties();
            try(FileReader fr = new FileReader("settings.txt")){
                props.load(fr);
            }
            server = props.getProperty("server");
            language = props.getProperty("language");
            
            addPatchers();
        }
    }
    
    private void getSolutionManifest(String version, String branch) throws IOException{
        URL u = new URL("http://l3cdn.riotgames.com/releases/"+branch+"/solutions/lol_game_client_sln/releases/"+version+"/solutionmanifest");
        URLConnection con = u.openConnection();
        
        File f = new java.io.File("RADS/solutions/lol_game_client_sln/releases/" + version + "/solutionmanifest");
        new File(f.getParent()).mkdirs();
        f.createNewFile();
        
        try (InputStream in = con.getInputStream()) {
            try (OutputStream fo = new FileOutputStream(f)) {
                int read;
                byte[] buffer = new byte[2048];
                while((read = in.read(buffer)) != -1){
                    fo.write(buffer, 0, read);
                }
            }
        }
    }
    
    
    public void dumpConfig() throws IOException{
        File f = new java.io.File("RADS/solutions/lol_game_client_sln/releases/" + slnversion + "/configurationmanifest");
        f.createNewFile();
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(f))) {
            bw.write("RADS Configuration Manifest\r\n" +
                    "1.0.0.0\r\n" +
                    language + "\r\n" +
                    "2\r\n" +
                    "lol_game_client\r\n" +
                    "lol_game_client_" + language + "\r\n");
        }
        
        File confdir = new java.io.File("RADS/solutions/lol_game_client_sln/releases/" + slnversion + "/deploy/DATA/cfg/defaults/");
        confdir.mkdirs();
        File conf = new File(confdir, "locale.cfg");
        
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(conf))) {
            String[] lang = language.split("_");
            lang[1] = lang[1].toUpperCase();
            bw.write("[General]\r\n" +
                     "LanguageLocaleRegion="+lang[0] + "_"+lang[1]);
        }
    }
    
    public void addPatchers(){
        slnversion = LoLPatcher.getVersion("solutions", "lol_game_client_sln", server);
        String branch = (server.equals("PBE") ? "pbe" : "live");
        try {
            getSolutionManifest(slnversion, branch);
            dumpConfig();
        } catch (IOException ex) {
            Logger.getLogger(ConfigurationTask.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        
        main.airversion = LoLPatcher.getVersion("projects", "lol_air_client", server);
        
        String clientConfigName = "lol_air_client_config"+(server.equals("PBE") ? "" : "_"+server.toLowerCase());
        
        String gameversion = LoLPatcher.getVersion("projects", "lol_game_client", server);
        String airconfigversion = LoLPatcher.getVersion("projects", clientConfigName, server);
        String gamelanguageversion = LoLPatcher.getVersion("projects", "lol_game_client_"+language, server);

        if(main.purgeAfterwards){
            main.patchers.add(new ArchivePurgeTask("lol_game_client", gameversion, branch, "projects"));
            done = true;
            return;
        }
        main.patchers.add(new LoLPatcher(main.airversion, "lol_air_client", branch, main.ignoreS_OK, main.force));
        main.patchers.add(new LoLPatcher(gameversion, "lol_game_client", branch, main.ignoreS_OK, main.force));
        main.patchers.add(new LoLPatcher(airconfigversion, clientConfigName, branch, main.ignoreS_OK, main.force));
        main.patchers.add(new LoLPatcher(gamelanguageversion, "lol_game_client_"+language, branch, main.ignoreS_OK, main.force));
        
        main.patchers.add(new CopyTask(
                new File("RADS/projects/"+clientConfigName+"/releases/"+airconfigversion+"/deploy/"),
                new File("RADS/projects/lol_air_client/releases/"+main.airversion+"/deploy/"), true));
        
        
        main.patchers.add(new SLNPatcher(gameversion, slnversion, main.ignoreS_OK));
        
        main.patchers.add(new RunTask(new Runnable() {
            @Override
            public void run() {
                try {
                    File f = new java.io.File("RADS/projects/lol_air_client/releases/" + main.airversion + "/deploy/locale.properties");
                    f.createNewFile();
                    try (BufferedWriter bw = new BufferedWriter(new FileWriter(f))) {
                        String[] lang = language.split("_");
                        lang[1] = lang[1].toUpperCase();
                        bw.write("locale=" + lang[0] + "_" + lang[1]);
                    }
                }   catch (IOException ex) {
                    Logger.getLogger(ConfigurationTask.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }, "Locale config"));
        
        done = true;
    }

    @Override
    public float getPercentage() {
        return percentage;
    }
}
