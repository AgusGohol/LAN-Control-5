import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Arrays;

/** Exact comparison, including single-pixel text changes; row buffers are reused. */
class FrameCache {
    BufferedImage previous;String settings="";int[] rowA=new int[0],rowB=new int[0];
    void clear(){previous=null;settings="";}
    boolean same(BufferedImage image){
        if(previous==null||previous.getWidth()!=image.getWidth()||previous.getHeight()!=image.getHeight())return false;
        if(previous.getType()==image.getType()&&image.getType()!=BufferedImage.TYPE_CUSTOM&&previous.getRaster().getDataBuffer() instanceof java.awt.image.DataBufferInt a&&image.getRaster().getDataBuffer() instanceof java.awt.image.DataBufferInt b&&a.getOffset()==0&&b.getOffset()==0&&a.getNumBanks()==1&&b.getNumBanks()==1&&a.getData().length==image.getWidth()*image.getHeight()&&b.getData().length==a.getData().length){int[] x=a.getData(),y=b.getData();if(image.getType()!=BufferedImage.TYPE_INT_RGB)return Arrays.equals(x,y);for(int i=0;i<x.length;i++)if(((x[i]^y[i])&0xffffff)!=0)return false;return true;}
        int w=image.getWidth();if(rowA.length!=w){rowA=new int[w];rowB=new int[w];}
        for(int y=0;y<image.getHeight();y++){previous.getRGB(0,y,w,1,rowA,0,w);image.getRGB(0,y,w,1,rowB,0,w);if(!Arrays.equals(rowA,rowB))return false;}return true;
    }
    byte[] encode(BufferedImage image,StreamQuality.State state)throws IOException{
        String signature=state.config.toString()+state.size(image.getWidth(),image.getHeight())+state.interactive();
        if(settings.equals(signature)&&same(image))return LanControl.command("FRAME_SAME");
        byte[] bytes=StreamQuality.encode(image,state);previous=image;settings=signature;return bytes;
    }
}
