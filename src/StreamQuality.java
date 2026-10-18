import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.*;
import javax.imageio.stream.MemoryCacheImageOutputStream;

class StreamQuality {
    record Config(String mode,int width,int height) {
        Config {if(!java.util.Set.of("AUTO","HD","NATIVE","HEMAT","PREVIEW").contains(mode))mode="AUTO";width=Math.max(320,Math.min(7680,width));height=Math.max(180,Math.min(4320,height));}
    }
    static Dimension fit(int width,int height,int maxWidth,int maxHeight){
        double scale=Math.min(1,Math.min(maxWidth/(double)width,maxHeight/(double)height));return new Dimension(Math.max(1,(int)Math.round(width*scale)),Math.max(1,(int)Math.round(height*scale)));
    }
    static class State {
        volatile Config config=new Config("PREVIEW",640,360);volatile int interval=300;
        final Semaphore ready=new Semaphore(1);final AtomicBoolean outstanding=new AtomicBoolean();
        volatile long sentAt;volatile double milliseconds=150;volatile double factor=1;
        volatile long interactiveUntil=0;
        int slow=0,fast=0;
        void configure(String[] p){try{config=new Config(p[1],Integer.parseInt(p[2]),Integer.parseInt(p[3]));interval=p.length>4?Math.max(80,Math.min(2000,Integer.parseInt(p[4]))):120;}catch(RuntimeException ignored){}}
        void begin(){outstanding.set(true);sentAt=System.nanoTime();}
        void ack(){if(outstanding.compareAndSet(true,false)){sample((System.nanoTime()-sentAt)/1_000_000.0);ready.release();}}
        synchronized void sample(double ms){
            milliseconds=milliseconds*.7+ms*.3;
            if(milliseconds>430){slow++;fast=0;if(slow>=3){factor=Math.max(.5,factor*.82);slow=0;}}
            else if(milliseconds<190){fast++;slow=0;if(fast>=8){factor=Math.min(1,factor+.1);fast=0;}}
            else {slow=0;fast=0;}
        }
        Dimension size(int w,int h){Config c=config;
            if(c.mode.equals("PREVIEW"))return fit(w,h,640,360);
            if(c.mode.equals("NATIVE"))return fit(w,h,3840,2160);
            if(c.mode.equals("HD"))return fit(w,h,1920,1080);
            if(c.mode.equals("HEMAT"))return fit(w,h,960,540);
            // Bound automatic processing to 1600x900; explicit HD/native retain their limits.
            return fit(w,h,Math.max(640,(int)(Math.min(1600,c.width)*factor)),Math.max(360,(int)(Math.min(900,c.height)*factor)));
        }
        void interact(){interactiveUntil=System.nanoTime()+350_000_000L;}
        boolean interactive(){return System.nanoTime()<interactiveUntil;}
        int pause(){return interactive()?Math.min(interval,80):Math.max(interval,config.mode.equals("NATIVE")?350:config.mode.equals("HEMAT")?220:120);}
    }
    static byte[] encode(BufferedImage raw,State state)throws IOException {
        Dimension size=state.size(raw.getWidth(),raw.getHeight());BufferedImage img=raw;
        if(size.width!=raw.getWidth()||size.height!=raw.getHeight()){
            img=new BufferedImage(size.width,size.height,BufferedImage.TYPE_INT_RGB);Graphics2D g=img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);g.drawImage(raw,0,0,size.width,size.height,null);g.dispose();
        }
        if(state.config.mode.equals("NATIVE")&&!state.interactive()){ByteArrayOutputStream png=new ByteArrayOutputStream();ImageIO.write(img,"png",png);if(png.size()<LanControl.MAX-16)return png.toByteArray();}
        byte[] jpeg=jpeg(img,state.config.mode.equals("PREVIEW")?.65f:state.config.mode.equals("HEMAT")?.75f:state.config.mode.equals("AUTO")?.85f:.95f);
        if(jpeg.length>=LanControl.MAX-16)jpeg=jpeg(img,.80f);
        if(jpeg.length>=LanControl.MAX-16)throw new IOException("Gambar melampaui batas paket.");return jpeg;
    }
    static byte[] jpeg(BufferedImage image,float quality)throws IOException {
        ImageWriter writer=ImageIO.getImageWritersByFormatName("jpeg").next();ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(MemoryCacheImageOutputStream stream=new MemoryCacheImageOutputStream(bytes)){
            writer.setOutput(stream);ImageWriteParam p=writer.getDefaultWriteParam();p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);p.setCompressionQuality(quality);
            writer.write(null,new IIOImage(image,null,null),p);stream.flush();
        }finally{writer.dispose();}return bytes.toByteArray();
    }
}
