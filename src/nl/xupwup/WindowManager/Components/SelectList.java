/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package nl.xupwup.WindowManager.Components;

import nl.xupwup.WindowManager.Component;
import nl.xupwup.WindowManager.Listener;
import java.awt.Point;
import java.util.ArrayList;
import static org.lwjgl.opengl.GL11.*;

/**
 *
 * @author Rick Hendricksen
 */
public class SelectList extends Component{
    ArrayList<Option> options;
    public int selected;
    Listener c;
    int h, w;
    
    public SelectList(String[] options, Listener c, Point location, int initial){
        this.options = new ArrayList<>();
        this.c = c;
        int padding = 3;
        int offset = 0;
        w = 0;
        for(String s : options){
            Option n = new Option(s, null, new Point(0,offset), false, true);
            offset += n.getSize().y + padding;
            if(w < n.getSize().x){
                w = n.getSize().x;
            }
            this.options.add(n);
        }
        h = offset - padding;
        select(selected = initial);
    }

    @Override
    public Point getSize() {
        return new Point(w, h);
    }

    @Override
    public void draw() {
        for(Option o : options){
            Point loc = o.getLocation();
            glPushMatrix();
            glTranslatef(loc.x, loc.y, 0);
            o.draw();
            glPopMatrix();
        }
    }

    @Override
    public void click(Point p) {
        for(int i = 0; i < options.size(); i++){
            Point loc = options.get(i).getLocation();
            Point dim = options.get(i).getSize();
            if(p.x > loc.x && p.x < loc.x + dim.x 
                    && p.y > loc.y && p.y < loc.y + dim.y){
                
                selected = i;
            }
        }
        select(selected);
        if(c != null) c.click(this);
    }
    
    private void select(int idx){
        for(int i = 0; i < options.size(); i++){
            options.get(i).cb.checked = i == idx;
        }
    }
}
