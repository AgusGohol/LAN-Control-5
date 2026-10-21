import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;

/** Slim, rounded scrollbar that follows the blue theme (dark/light) and has no arrow buttons.
 *  Colors are read live from Ui on each paint, so a live theme switch repaints correctly. */
public class ThemeScrollBarUI extends BasicScrollBarUI {
    public static ComponentUI createUI(JComponent c){return new ThemeScrollBarUI();}
    static Color thumbBase(){return Ui.mix(Ui.LINE,Ui.MUTED,0.35);}
    protected void configureScrollBarColors(){trackColor=Ui.BG;thumbColor=thumbBase();}
    protected JButton createDecreaseButton(int orientation){return zeroButton();}
    protected JButton createIncreaseButton(int orientation){return zeroButton();}
    private JButton zeroButton(){JButton b=new JButton();Dimension d=new Dimension(0,0);b.setPreferredSize(d);b.setMinimumSize(d);b.setMaximumSize(d);b.setFocusable(false);b.setBorder(null);return b;}
    protected Dimension getMinimumThumbSize(){return new Dimension(10,34);}
    protected void paintTrack(Graphics g,JComponent c,Rectangle r){g.setColor(Ui.BG);g.fillRect(r.x,r.y,r.width,r.height);}
    protected void paintThumb(Graphics g,JComponent c,Rectangle r){
        if(r.isEmpty()||!scrollbar.isEnabled())return;
        Graphics2D a=(Graphics2D)g.create();
        a.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        int pad=3,x=r.x+pad,y=r.y+pad,w=Math.max(3,r.width-pad*2),h=Math.max(3,r.height-pad*2),arc=Math.min(w,h);
        a.setColor(isDragging?Ui.ACCENT:isThumbRollover()?Ui.MUTED:thumbBase());
        a.fillRoundRect(x,y,w,h,arc,arc);
        a.dispose();
    }
}
