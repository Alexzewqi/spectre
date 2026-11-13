package net.spectre.client.map;

import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.spectre.client.config.ThemeStore;
import net.spectre.client.media.MediaPlayer;
import net.spectre.client.module.*;
import net.spectre.client.module.setting.*;
import net.spectre.client.module.impl.render.*;

/** Local world, client rendering and public media pages only. Never joins a game server. */
final class FeaturePreview {
    private int ticks,stage;
    private long began=System.nanoTime();
    private volatile String videoEvidence="";
    private boolean probeInstalled;
    private final List<String> checks=new ArrayList<>();
    private final AtmosphereModule sky=ModuleRegistry.get(AtmosphereModule.class);
    FeaturePreview(Minecraft mc)throws Exception{
        for(String bad:List.of("javascript:alert(1)","https://youtube.com.example.org/","https://youtube.com@evil.test/","file:///etc/passwd")){
            try{MediaPlayer.normalize(bad);throw new AssertionError("Unsafe media URL accepted");}catch(IllegalArgumentException expected){}
        }
        checks.add("Media address validation rejects executable and spoofed hosts");
        MediaPlayer.normalize("https://www.youtube.com/watch?v=M7lc1UVf-VE");MediaPlayer.normalize("https://soundcloud.com/");
        for(var module:ModuleRegistry.all())module.setEnabled(false);
        ModuleRegistry.get(SpectralHoodModule.class).setEnabled(true);ModuleRegistry.get(FloatingRelicsModule.class).setEnabled(true);
        setting("theme",ModeSetting.class).set("Follow GUI");setting("sky-style",ModeSetting.class).set("Nebula");setting("night-sky",BooleanSetting.class).set(true);sky.setEnabled(true);
        mc.player.setXRot(-70);mc.player.setYRot(0);mc.gui.setScreen(null);
        mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.FANCY);
        setting("star-brightness",NumberSetting.class).set(1.3F);
        if(Boolean.getBoolean("spectre.guiPreview")){
            ThemeStore.get().applyPreset(ThemeStore.PRESETS.stream().filter(p->p.name().equals("Violet")).findFirst().orElseThrow());
            mc.gui.setScreen(new net.spectre.client.gui.ImGuiScreen());stage=30;return;
        }
        if(Boolean.getBoolean("spectre.knifePreview")){
            // Each knife design in the main hand, one capture per style; quits when done.
            ModuleRegistry.get(SpectralHoodModule.class).setEnabled(false);ModuleRegistry.get(FloatingRelicsModule.class).setEnabled(false);
            var server=mc.getSingleplayerServer();
            server.execute(()->server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"item replace entity @a weapon.mainhand with diamond_sword"));
            mc.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            stage=39;return;
        }
        if(Boolean.getBoolean("spectre.handPreview")){
            // Held-item effect check: sword in hand, outline, glow, then off; quits when done.
            ModuleRegistry.get(SpectralHoodModule.class).setEnabled(false);ModuleRegistry.get(FloatingRelicsModule.class).setEnabled(false);
            var server=mc.getSingleplayerServer();
            server.execute(()->server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"item replace entity @a weapon.mainhand with diamond_sword"));
            handModule("Outline");stage=20;
        }
    }
    /** Switches the open click GUI to a category and expands its first card, through reflection. */
    @SuppressWarnings("unchecked")
    private static void guiState(Minecraft mc,String category)throws Exception{
        var screen=mc.gui.screen();var f=screen.getClass().getDeclaredField("clickGui");f.setAccessible(true);var gui=f.get(screen);
        var cat=ModuleCategory.values().stream().filter(c->c.name().equals(category)).findFirst().orElseThrow();
        var wc=gui.getClass().getDeclaredField("workspaceCategory");wc.setAccessible(true);wc.set(gui,cat);
        var sel=gui.getClass().getDeclaredField("selectedModule");sel.setAccessible(true);
        var first=ModuleRegistry.inCategory(cat).getFirst();sel.set(gui,first);
    }
    /** Writes the off-screen hand capture texture as a PNG so the capture and the composite can be checked separately. */
    private static void dumpHandCapture()throws Exception{
        var field=Class.forName("net.spectre.client.module.impl.render.HandAuraPass").getDeclaredField("hand");field.setAccessible(true);
        var target=(com.mojang.blaze3d.pipeline.RenderTarget)field.get(null);
        if(target==null){Files.writeString(Path.of("hand-capture.txt"),"no capture target\n");return;}
        int id=((com.mojang.blaze3d.opengl.GlTexture)target.getColorTexture()).glId(),w=target.width,h=target.height;
        var pixels=org.lwjgl.BufferUtils.createByteBuffer(w*h*4);
        org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,id);
        org.lwjgl.opengl.GL11.glPixelStorei(org.lwjgl.opengl.GL11.GL_PACK_ALIGNMENT,1);
        org.lwjgl.opengl.GL11.glGetTexImage(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,0,org.lwjgl.opengl.GL11.GL_RGBA,org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE,pixels);
        var img=new java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        long opaque=0,black=0;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int i=(x+(h-1-y)*w)*4,r=pixels.get(i)&255,g=pixels.get(i+1)&255,b=pixels.get(i+2)&255,a=pixels.get(i+3)&255;
            if(a>0)opaque++;if(a==255&&r==0&&g==0&&b==0)black++;
            img.setRGB(x,y,a<<24|r<<16|g<<8|b);
        }
        javax.imageio.ImageIO.write(img,"png",new java.io.File("hand-capture.png"));
        Files.writeString(Path.of("hand-capture.txt"),"size "+w+"x"+h+" pixels with alpha>0: "+opaque+" opaque black: "+black+"\n");
    }
    private void handModule(String style){
        var module=ModuleRegistry.get(ItemOutlineModule.class);
        module.settings().stream().filter(s->s.id().equals("style")).findFirst().map(ModeSetting.class::cast).orElseThrow().set(style);
        module.setEnabled(true);
    }
    private <T extends Setting>T setting(String id,Class<T> type){return type.cast(sky.settings().stream().filter(s->s.id().equals(id)).findFirst().orElseThrow());}
    void tick(Minecraft mc)throws Exception{
        if(stage==99)return;
        if(stage>=39&&stage<50)mc.player.setXRot(20);
        else if(stage>=30)mc.player.setXRot(-20);
        else if(stage>=20){mc.player.setXRot(10);mc.player.setYRot(0);}
        else if(stage<3||stage==7){mc.player.setXRot(stage==1||stage==2?-35:-80);mc.player.setYRot(stage==1||stage==2?180:0);}
        if(System.nanoTime()-began>600_000_000_000L){Files.writeString(Path.of("feature-preview-limited.txt"),"Browser did not finish within ten minutes\n"+String.join("\n",checks));stage=99;return;}
        if(++ticks<50)return;ticks=0;
        switch(stage){
            case 0 -> {dumpSky();AtlasSmokeClient.captureBack(mc,"feature-sky-teal.png");setting("theme",ModeSetting.class).set("Violet Aurora");setting("sky-style",ModeSetting.class).set("Aurora");stage++;}
            case 1 -> {AtlasSmokeClient.captureBack(mc,"feature-sky-violet.png");ThemeStore.get().accent.setArgb(0xFFFFAA55);setting("theme",ModeSetting.class).set("Follow GUI");setting("sky-style",ModeSetting.class).set("Both");stage++;}
            case 2 -> {AtlasSmokeClient.captureBack(mc,"feature-sky-custom.png");ThemeStore.get().applyPreset(ThemeStore.PRESETS.getFirst());mc.gui.setScreen(new InventoryScreen(mc.player));stage++;}
            case 3 -> {AtlasSmokeClient.captureBack(mc,"feature-ghost-hood.png");mc.gui.screen().onClose();if(Boolean.getBoolean("spectre.skyOnly")){setting("night-sky",BooleanSetting.class).set(false);var server=mc.getSingleplayerServer();server.execute(()->server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"time set noon"));stage=7;return;}MediaPlayer.load("https://www.youtube.com/watch?v=M7lc1UVf-VE");MediaPlayer.open();stage++;}
            case 20 -> {
                if(mc.player.getMainHandItem().isEmpty()){
                    // The give command lands a tick or two later; re-select and wait until the sword is really held.
                    mc.player.getInventory().setSelectedSlot(0);
                    Files.writeString(Path.of("hand-state.txt"),"hand="+mc.player.getMainHandItem()+" mode="+mc.gameMode.getPlayerMode()+" camera="+mc.options.getCameraType()+" slot0="+mc.player.getInventory().getItem(0)+"\n");
                    var server=mc.getSingleplayerServer();
                    server.execute(()->server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"give @a diamond_sword"));
                    return;
                }
                AtlasSmokeClient.captureBack(mc,"hand-outline.png");dumpHandCapture();handModule("Glow");stage++;
            }
            case 21 -> {AtlasSmokeClient.captureBack(mc,"hand-glow.png");ModuleRegistry.get(ItemOutlineModule.class).setEnabled(false);stage++;}
            case 22 -> {AtlasSmokeClient.captureBack(mc,"hand-plain.png");Files.writeString(Path.of("hand-preview-ready.txt"),"done\n");stage=99;mc.stop();}
            case 39 -> {AtlasSmokeClient.captureBack(mc,"knife-vanilla.png");ModuleRegistry.get(KnivesModule.class).setEnabled(true);stage++;}
            case 40,41,42,43,44 -> {
                String[] styles={"Tactical","Claw","Folding","Hunting","Dagger"};
                var knives=ModuleRegistry.get(KnivesModule.class);
                if(mc.player.getMainHandItem().isEmpty())mc.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));
                if(stage>40)AtlasSmokeClient.captureBack(mc,"knife-"+styles[stage-41].toLowerCase()+".png");
                knives.settings().stream().filter(x->x.id().equals("style")).findFirst().map(ModeSetting.class::cast).orElseThrow().set(styles[stage-40]);
                stage++;
            }
            case 45 -> {AtlasSmokeClient.captureBack(mc,"knife-dagger.png");Files.writeString(Path.of("knife-preview-ready.txt"),"done\n");stage=99;mc.stop();}
            case 30 -> {AtlasSmokeClient.captureBack(mc,"gui-combat.png");guiState(mc,"BASE_RAID");stage++;}
            case 31 -> {AtlasSmokeClient.captureBack(mc,"gui-baseraid.png");ThemeStore.get().applyPreset(ThemeStore.PRESETS.stream().filter(p->p.name().equals("Halloween")).findFirst().orElseThrow());stage++;}
            case 32 -> {AtlasSmokeClient.captureBack(mc,"gui-halloween.png");Files.writeString(Path.of("gui-preview-ready.txt"),"done\n");stage=99;mc.stop();}
            case 7 -> {AtlasSmokeClient.captureBack(mc,"feature-sky-day.png");stage=99;Files.writeString(Path.of("sky-preview-ready.txt"),String.join("\n",checks));}
            case 4 -> {
                var f=MediaPlayer.class.getDeclaredField("browser");f.setAccessible(true);var b=(net.dimaskama.mcef.api.MCEFBrowser)f.get(null);
                if(b==null||b.getTextureView()==null)return;
                if(!probeInstalled){
                    b.getCefBrowser().getClient().addDisplayHandler(new org.cef.handler.CefDisplayHandlerAdapter(){
                        @Override public boolean onConsoleMessage(org.cef.browser.CefBrowser browser,org.cef.CefSettings.LogSeverity severity,String text,String source,int line){if(text.startsWith("SPECTRE_MEDIA_CHECK:")){videoEvidence=text;return true;}return false;}
                    });probeInstalled=true;
                }
                b.getCefBrowser().executeJavaScript("(()=>{let v=document.querySelector('video');if(v){v.muted=true;v.play().catch(()=>{});console.log('SPECTRE_MEDIA_CHECK:'+JSON.stringify({ready:v.readyState,time:v.currentTime,paused:v.paused}));}})()",b.getCefBrowser().getURL(),0);
                if(!videoEvidence.contains("\"paused\":false"))return;
                AtlasSmokeClient.captureBack(mc,"feature-youtube.png");checks.add("YouTube HTML video reports playing: "+videoEvidence);
                MediaPlayer.pin();mc.gui.setScreen(null);stage++;
            }
            case 5 -> {AtlasSmokeClient.captureBack(mc,"feature-media-pinned.png");MediaPlayer.open();MediaPlayer.load("https://soundcloud.com/");stage++;}
            case 6 -> {
                var f=MediaPlayer.class.getDeclaredField("browser");f.setAccessible(true);var b=(net.dimaskama.mcef.api.MCEFBrowser)f.get(null);
                if(b==null||b.getCefBrowser().isLoading())return;
                AtlasSmokeClient.captureBack(mc,"feature-soundcloud.png");checks.add("SoundCloud browser navigation completed");
                Files.writeString(Path.of("feature-preview-ready.txt"),String.join("\n",checks));stage=99;
            }
        }
    }
    private void dumpSky()throws Exception{
        var lines=new ArrayList<String>();dump(sky,"sky",lines,0);Files.writeString(Path.of("sky-debug.txt"),String.join("\n",lines));
    }
    private static void dump(Object o,String path,List<String> lines,int depth)throws Exception{
        for(var f:o.getClass().getDeclaredFields()){
            if(java.lang.reflect.Modifier.isStatic(f.getModifiers()))continue;f.setAccessible(true);Object v=f.get(o);
            if(v==null||f.getType().isPrimitive())lines.add(path+"."+f.getName()+"="+v);
            else if(depth<3&&v.getClass().getPackageName().endsWith("impl.render"))dump(v,path+"."+f.getName(),lines,depth+1);
        }
    }
}
