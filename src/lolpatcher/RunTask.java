/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package lolpatcher;

import java.io.IOException;
import java.net.MalformedURLException;
import java.security.NoSuchAlgorithmException;

/**
 *
 * @author Rick
 */
public class RunTask extends PatchTask{
    public boolean neverContinue = false;
    private String description;
    Runnable r;
    public RunTask(Runnable r, String description){
        this.r = r;
        this.description = description;
    }
    
    @Override
    public void patch() throws MalformedURLException, IOException, NoSuchAlgorithmException {
        percentage = 0;
        currentFile = description;
        r.run();
        percentage = 100;
        done = !neverContinue;
    }
    
}
