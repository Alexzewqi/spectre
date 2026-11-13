package net.spectre.client.gui;

import imgui.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.spectre.client.builder.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/** First builder milestone: imports, preview, placement and persistent material plans. */
public final class BuilderPanel {
    private static boolean openRequested, initialized;
    private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"Spectre schematic loader");t.setDaemon(true);return t;});
    private record Loaded(Path file,String hash,Schematic schematic,List<Schematic.Block> blocks,Map<String,Long> counts) { }
    private static List<Path> files=List.of();
    private static CompletableFuture<Loaded> pending;
    private static Loaded loaded;
    private static MaterialPlan materials;
    private static BuildJobStore.Job restoring;
    private static String status="Import a schematic to begin.";
    private static int rotation;
    private static final int[] origin={0,0,0};
    private static float previewAngle=0.65F;
    private BuilderPanel(){ }
    public static Path folder(){return FabricLoader.getInstance().getGameDir().resolve("spectre/schematics");}
    private static Path jobs(){return FabricLoader.getInstance().getConfigDir().resolve("spectre/builder/jobs");}
    public static void open(){openRequested=true;refresh();initialized=true;}
    public static boolean takeOpenRequest(){boolean requested=openRequested;openRequested=false;return requested;}
    private static void refresh(){try{Files.createDirectories(folder());try(var s=Files.list(folder())){files=s.filter(Files::isRegularFile).filter(p->{String n=p.getFileName().toString().toLowerCase(Locale.ROOT);return n.endsWith(".schem")||n.endsWith(".litematic");}).sorted().toList();}}catch(IOException e){status=e.getMessage();}}
    private static void load(Path file){
        if(pending!=null||CreativeBuilder.INSTANCE.running())return;loaded=null;materials=null;SchematicPreview.INSTANCE.clear();status="Reading schematic…";
        pending=CompletableFuture.supplyAsync(()->{try{
            String before=BuildJobStore.hash(file);Schematic schematic=SchematicReader.read(file);
            if(!before.equals(BuildJobStore.hash(file)))throw new IOException("Schematic changed while loading");
            List<Schematic.Block> blocks=schematic.blocks();return new Loaded(file,before,schematic,blocks,MaterialPlan.countStates(blocks));
        }catch(Exception e){throw new CompletionException(e);}},IO);
    }
    private static void finishLoad(){
        if(pending==null||!pending.isDone())return;
        try{loaded=pending.join();materials=MaterialPlan.calculate(loaded.counts,BuilderMaterials::supply);
            if(restoring!=null){origin[0]=restoring.origin().x();origin[1]=restoring.origin().y();origin[2]=restoring.origin().z();rotation=restoring.rotation();status="Restored plan. Purchasing and building remain stopped.";}
            else {var player=Minecraft.getInstance().player;if(player!=null){var p=player.blockPosition();origin[0]=p.getX();origin[1]=p.getY();origin[2]=p.getZ();}status="Loaded "+loaded.blocks.size()+" non-air blocks. Place the ghost preview in your world before building.";}
            if(Minecraft.getInstance().level!=null)SchematicPreview.INSTANCE.set(loaded.schematic.name(),loaded.blocks,new Schematic.Pos(origin[0],origin[1],origin[2]),rotation);
        }catch(CompletionException e){status="Import failed: "+e.getCause().getMessage();}
        finally{pending=null;restoring=null;}
    }
    private static String world(){var mc=Minecraft.getInstance();String server=mc.getCurrentServer()!=null?mc.getCurrentServer().ip:mc.getSingleplayerServer()!=null?"singleplayer:"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize():"not-connected";return server+" / "+(mc.level==null?"not-in-world":mc.level.dimension().identifier());}
    private static void save(){try{
        if(loaded==null||materials==null)return;
        if(!loaded.hash.equals(BuildJobStore.hash(loaded.file)))throw new IOException("Schematic changed; reload it first");
        if(Minecraft.getInstance().level==null)throw new IOException("Enter the build world before saving its placement");
        var job=BuildJobStore.create(loaded.file,world(),new Schematic.Pos(origin[0],origin[1],origin[2]),rotation,materials);
        BuildJobStore.save(jobs(),job);status="Saved build plan "+job.id()+". No orders have been placed.";
    }catch(IOException|RuntimeException e){status="Could not save: "+e.getMessage();}}
    private static void restore(Path file){try{
        BuildJobStore.Job job=BuildJobStore.load(file,folder());if(!job.world().equals(world()))throw new IOException("This plan belongs to another server, world or dimension");
        restoring=job;load(folder().resolve(job.schematicFile()));
    }catch(IOException|RuntimeException e){status="Could not restore: "+e.getMessage();}}
    /** Drawn inside the main workspace; owns no floating window. */
    public static void renderContents(){
        if(!initialized){refresh();initialized=true;}finishLoad();
        var ghost=SchematicPreview.INSTANCE;
        if(loaded!=null&&ghost.matches(loaded.blocks)){
            origin[0]=ghost.origin().x();origin[1]=ghost.origin().y();origin[2]=ghost.origin().z();rotation=ghost.rotation();
        }
            ImGui.textColored(0.34F,0.86F,0.8F,1,"BASE BUILDER / SCHEMATIC WORKSPACE");
            ImGui.textWrapped(status);ImGui.separator();
            if(ImGui.button("Refresh schematics"))refresh();ImGui.sameLine();ImGui.textDisabled("spectre/schematics | .litematic / .schem");
            float available=ImGui.getContentRegionAvailX();
            if(ImGui.beginChild("##builder_files",Math.min(240,available*.26F),0,true)){
                ImGui.textUnformatted("Schematics");
                if(files.isEmpty())ImGui.textWrapped("Drop a schematic into the folder, then refresh.");
                for(Path p:files){ImGui.beginDisabled(pending!=null);if(ImGui.selectable(p.getFileName().toString(),loaded!=null&&loaded.file.equals(p))){restoring=null;load(p);}ImGui.endDisabled();}
                ImGui.separator();ImGui.textUnformatted("Saved plans");
                try{if(Files.isDirectory(jobs()))try(var stream=Files.list(jobs())){for(Path p:stream.filter(p->p.getFileName().toString().endsWith(".json")).sorted().toList()){
                    ImGui.beginDisabled(pending!=null);if(ImGui.selectable(p.getFileName().toString().substring(0,8)))restore(p);ImGui.endDisabled();
                }}}catch(IOException e){ImGui.textWrapped("Could not read saved plans.");}
            }ImGui.endChild();ImGui.sameLine();
            if(ImGui.beginChild("##builder_content",0,0,false)){
                if(loaded==null)ImGui.textWrapped(pending==null?"Select a schematic to view its geometry and material requirements.":"Loading…");
                else{
                    boolean columns=ImGui.getContentRegionAvailX()>=760;
                    float detailWidth=columns?ImGui.getContentRegionAvailX()*.57F:ImGui.getContentRegionAvailX();
                    if(columns)ImGui.beginChild("##builder_placement",detailWidth,0,false);
                    ImGui.textUnformatted(loaded.schematic.name());
                    ImGui.textWrapped(loaded.schematic.regions().size()+" regions | Block-state properties preserved");
                    preview(ImGui.getContentRegionAvailX(),Math.max(160,Math.min(260,ImGui.getContentRegionAvailY()*.36F)));
                    controls(ghost);
                    if(columns){ImGui.endChild();ImGui.sameLine();ImGui.beginChild("##builder_supplies",0,0,false);}
                    ImGui.separator();ImGui.textUnformatted("Placement materials");
                    ImGui.textWrapped(materials.storageSlots()+" storage slots | "+materials.shulkers()+" supply shulkers (before inventory credits).");
                    if(ImGui.beginTable("##builder_materials",4,128|256|512|1024)){
                        ImGui.tableSetupColumn("Item");ImGui.tableSetupColumn("Required");ImGui.tableSetupColumn("Stacks");ImGui.tableSetupColumn("Packing");ImGui.tableHeadersRow();
                        for(var r:materials.rows()){
                            ImGui.tableNextRow();ImGui.tableNextColumn();
                            var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(net.minecraft.resources.Identifier.parse(r.item())).orElse(null);
                            float ix=ImGui.getCursorScreenPosX(),iy=ImGui.getCursorScreenPosY();
                            ItemIcons.draw(ImGui.getWindowDrawList(),item,ix,iy,32,1);
                            ImGui.dummy(32,32);ImGui.sameLine();ImGui.setCursorPosY(ImGui.getCursorPosY()+7);
                            ImGui.textUnformatted(item==null?r.item():item.getDefaultInstance().getHoverName().getString());
                            ImGui.tableNextColumn();ImGui.textUnformatted(Long.toString(r.required()));
                            ImGui.tableNextColumn();ImGui.textUnformatted(Long.toString((r.required()+r.maxStack()-1)/r.maxStack()));
                            ImGui.tableNextColumn();ImGui.textUnformatted(r.packable()?"Shulker":"Carry separately");
                        }ImGui.endTable();
                    }
                    if(ImGui.collapsingHeader("Shulker packing plan")) {
                        var depot=SupplyDepotPlan.pack(materials);
                        for(var slot:depot.slots())ImGui.textUnformatted("Box "+(slot.box()+1)+" • slot "+(slot.slot()+1)+" • "+slot.item().replace("minecraft:","")+" × "+slot.count());
                        for(var row:depot.carrySeparately())ImGui.textWrapped(row.item()+" × "+row.required()+" travels outside the supply shulkers.");
                    }
                    if(!materials.unresolved().isEmpty()){ImGui.textColored(1,0.7F,0.46F,1,"Needs placement recipes or review");for(String s:materials.unresolved())ImGui.textWrapped(s);}
                    boolean wet=loaded.counts.keySet().stream().anyMatch(s->s.contains("waterlogged=true"));if(wet)ImGui.textWrapped("Waterlogged blocks require water setup; reusable water supplies are not included yet.");
                    ImGui.textDisabled("(?) About this estimate");
                    if(ImGui.isItemHovered())ClickGuiRenderer.showTooltip("Container contents are not included. Scaffolding is supplied separately; terrain and inventory credits are not counted.");
                    if(columns)ImGui.endChild();
                }
            }ImGui.endChild();
    }
    // Layer selection: 0 = whole schematic, 1 = one layer, 2 = a range. Values are schematic-space Y.
    private static int layerMode,layerA,layerB;
    private static final imgui.type.ImString shopCommand=new imgui.type.ImString("shop",64);

    /** All builder controls as compact rows of buttons; explanations live in hover tooltips. */
    private static void controls(SchematicPreview ghost){
        var builder=CreativeBuilder.INSTANCE;
        int bottom=Integer.MAX_VALUE,top=Integer.MIN_VALUE;
        for(var b:loaded.blocks){bottom=Math.min(bottom,b.pos().y());top=Math.max(top,b.pos().y());}
        layerA=Math.max(bottom,Math.min(top,layerA));layerB=Math.max(layerA,Math.min(top,layerB));
        rowOpen=false;

        group("Place");
        ImGui.beginDisabled(builder.running());
        ImGui.setNextItemWidth(Math.min(220,ImGui.getContentRegionAvailX()));
        if(ImGui.inputInt3("##origin",origin))updateGhost();
        tip("Build origin X / Y / Z");
        if(button("My position",false,"Move the build origin to where you stand")){var player=Minecraft.getInstance().player;if(player!=null){var p=player.blockPosition();origin[0]=p.getX();origin[1]=p.getY();origin[2]=p.getZ();updateGhost();}}
        if(button("Rotate "+rotation*90+"°",false,"Turn the build 90°")){rotation=(rotation+1)%4;updateGhost();}
        if(button("Place in world",false,"Aim at terrain to move the ghost; R rotates, PgUp/PgDn adjusts height, Enter confirms")){updateGhost();ghost.beginPlacement();}
        if(button(ghost.confirmed()?"Confirmed":"Confirm",ghost.confirmed(),"Lock the ghost at this position")){updateGhost();ghost.confirm();}
        ImGui.endDisabled();
        if(button("Ghost",ghost.visible(),"Show the ghost blocks (cyan = missing, red = occupied)"))ghost.visible(!ghost.visible());

        group("Layers");
        ImGui.beginDisabled(builder.running());
        if(button("All",layerMode==0,"Build the whole schematic"))layerMode=0;
        if(button("One layer",layerMode==1,"Build just one Y layer"))layerMode=1;
        if(button("Range",layerMode==2,"Build a range of Y layers"))layerMode=2;
        if(layerMode>0){
            int count=top-bottom+1;
            if(button("-##a",false,"Lower layer"))layerA=Math.max(bottom,layerA-1);
            label((layerMode==1?"Layer ":"From ")+(layerA-bottom+1)+" / "+count);
            if(button("+##a",false,"Higher layer"))layerA=Math.min(top,layerA+1);
            if(layerMode==2){
                if(button("-##b",false,"Lower top layer"))layerB=Math.max(layerA,layerB-1);
                label("To "+(layerB-bottom+1));
                if(button("+##b",false,"Higher top layer"))layerB=Math.min(top,layerB+1);
            }
        }
        ImGui.endDisabled();
        if(layerMode>0&&button("Auto next layer",builder.autoAdvance(),"When a layer is verified, start the next one automatically"))builder.autoAdvance(!builder.autoAdvance());

        group("Supply");
        if(button("Auto Buy",builder.autoBuy(),"Buy missing blocks from the server shop automatically whenever you run out"))builder.autoBuy(!builder.autoBuy());
        if(builder.autoBuy()){
            if(button("Stacks only",builder.stacksOnly(),"Only buy full stacks: search every shop page and category for a 64-stack listing instead of buying single blocks"))builder.stacksOnly(!builder.stacksOnly());
            label("/");ImGui.sameLine(0,2);ImGui.setNextItemWidth(70);
            if(ImGui.inputText("##shopcmd",shopCommand))builder.shopCommand(shopCommand.get());
            tip("Shop command");
            if(button("-##stacks",false,"Fewer stacks per trip"))builder.maxStacks(builder.maxStacks()-1);
            label(builder.maxStacks()+" stacks/trip");
            if(button("+##stacks",false,"More stacks per trip"))builder.maxStacks(builder.maxStacks()+1);
        }
        if(button("Restock chest",builder.restockFromChest(),"Walk to your restock chest and take missing blocks before buying"))builder.restockFromChest(!builder.restockFromChest());
        if(builder.restockFromChest()){
            var chest=builder.restockChest();
            if(button(chest==null?"Set chest (look at it)":"Chest "+chest.toShortString(),chest!=null,"Look at a chest or barrel with the GUI open, then click to make it the restock chest"))status=builder.pickRestockChest();
        }

        group("Access");
        ImGui.beginDisabled(builder.running());
        if(button("Scaffolding",builder.automaticScaffolding(),"Place temporary Scaffolding towers and spans to reach high blocks (carry Scaffolding)"))builder.automaticScaffolding(!builder.automaticScaffolding());
        if(builder.automaticScaffolding()&&button("Remove after",builder.removeScaffolding(),"Take the scaffolding down after a successful build"))builder.removeScaffolding(!builder.removeScaffolding());
        ImGui.endDisabled();

        group("Build");
        if(builder.running()){
            if(button(builder.paused()?"Resume":"Pause",builder.paused(),"Close the GUI to let the builder move"))builder.pause();
            if(button("Stop",false,"Stop; placed blocks stay"))builder.stop();
        }else{
            ImGui.beginDisabled(!ghost.matches(loaded.blocks)||!ghost.confirmed());
            String what=layerMode==0?"Build":layerMode==1?"Build layer "+(layerA-bottom+1):"Build layers "+(layerA-bottom+1)+"-"+(layerB-bottom+1);
            if(button(what,true,"Starts building. Close this GUI to let it run")){
                try{builder.start(loaded.blocks,ghost.origin(),ghost.rotation(),layerMode==0?null:new int[]{layerA,layerMode==1?layerA:layerB});}
                catch(RuntimeException ex){status="Cannot start: "+ex.getMessage();}
            }
            ImGui.endDisabled();
            if(!ghost.confirmed())tip("Place and confirm the ghost first");
        }
        if(button("Save plan",false,"Save this schematic's placement and material plan"))save();
        ImGui.newLine();
        ImGui.textWrapped(builder.status());
    }
    private static boolean rowOpen;
    /** Starts a new labelled row of buttons. */
    private static void group(String title){
        if(rowOpen)ImGui.newLine();
        rowOpen=true;
        ImGui.setCursorPosY(ImGui.getCursorPosY()+SpectreWidgets.s(4));
        ImGui.textDisabled(title);ImGui.sameLine(SpectreWidgets.s(64));ImGui.setCursorPosY(ImGui.getCursorPosY()-SpectreWidgets.s(4));
    }
    /** A toggle-style chip that flows onto the next line when the row is full. */
    private static boolean button(String text,boolean active,String tooltip){
        String shown=text.contains("##")?text.substring(0,text.indexOf("##")):text;
        float w=SpectreWidgets.textWidth(ImGui.getFont(),shown)+SpectreWidgets.s(18),h=SpectreWidgets.s(24);
        flow(w);
        boolean clicked=SpectreWidgets.chipButton("##"+text,shown,w,h,active);
        tip(tooltip);ImGui.sameLine(0,SpectreWidgets.s(4));
        return clicked;
    }
    private static void label(String text){
        flow(SpectreWidgets.textWidth(ImGui.getFont(),text));
        ImGui.setCursorPosY(ImGui.getCursorPosY()+SpectreWidgets.s(4));ImGui.textUnformatted(text);ImGui.sameLine(0,SpectreWidgets.s(4));
    }
    private static void flow(float w){
        float right=ImGui.getWindowPosX()+ImGui.getWindowContentRegionMaxX();
        if(ImGui.getCursorScreenPosX()+w>right){ImGui.newLine();ImGui.setCursorPosX(SpectreWidgets.s(64));}
    }
    private static void tip(String text){if(text!=null&&ImGui.isItemHovered())ClickGuiRenderer.showTooltip(text);}
    private static void updateGhost(){
        if(loaded==null||Minecraft.getInstance().level==null)return;
        var ghost=SchematicPreview.INSTANCE;var at=new Schematic.Pos(origin[0],origin[1],origin[2]);
        if(!ghost.matches(loaded.blocks))ghost.set(loaded.schematic.name(),loaded.blocks,at,rotation);
        else ghost.transform(at,rotation);
    }
    private static void preview(float w,float h){
        float x=ImGui.getCursorScreenPosX(),y=ImGui.getCursorScreenPosY();ImGui.invisibleButton("##builder_preview",w,h);
        if(ImGui.isItemHovered()&&ImGui.isMouseDragging(0))previewAngle+=ImGui.getIO().getMouseDeltaX()*.008F;
        ImDrawList dl=ImGui.getWindowDrawList();dl.addRectFilled(x,y,x+w,y+h,GuiPalette.abgr(0xFF101823),8);
        dl.pushClipRect(x,y,x+w,y+h,true);
        List<Schematic.Block> blocks=loaded.blocks;if(blocks.isEmpty()){dl.popClipRect();return;}
        double angle=previewAngle+rotation*Math.PI/2,cos=Math.cos(angle),sin=Math.sin(angle);
        int step=Math.max(1,(blocks.size()+11999)/12000);List<double[]> points=new ArrayList<>();double loX=Double.POSITIVE_INFINITY,hiX=-loX,loY=loX,hiY=-loX;
        for(int i=0;i<blocks.size();i+=step){var b=blocks.get(i);double px=b.pos().x()*cos-b.pos().z()*sin,pz=b.pos().x()*sin+b.pos().z()*cos,py=pz*.48-b.pos().y();points.add(new double[]{px,py,pz,previewColor(b.state())});loX=Math.min(loX,px);hiX=Math.max(hiX,px);loY=Math.min(loY,py);hiY=Math.max(hiY,py);}
        double scale=Math.min((w-40)/(hiX-loX+2),(h-40)/(hiY-loY+2)),cx=(loX+hiX)/2,cy=(loY+hiY)/2;
        points.sort(Comparator.comparingDouble(a->a[2]));
        for(var p:points){float sx=x+w/2+(float)((p[0]-cx)*scale),sy=y+h/2+(float)((p[1]-cy)*scale);int color=(int)p[3];
            float nearX=sin>=0?1:0,nearZ=cos>=0?1:0;
            face(dl,sx,sy,scale,cos,sin,new float[][]{{nearX,0,0},{nearX,0,1},{nearX,1,1},{nearX,1,0}},shade(color,.72F));
            face(dl,sx,sy,scale,cos,sin,new float[][]{{0,0,nearZ},{1,0,nearZ},{1,1,nearZ},{0,1,nearZ}},shade(color,.88F));
            face(dl,sx,sy,scale,cos,sin,new float[][]{{0,1,0},{1,1,0},{1,1,1},{0,1,1}},color);
        }
        dl.popClipRect();dl.addText(x+12,y+10,GuiPalette.abgr(0xFFB6C2CD),"Geometry preview | drag to orbit"+(step>1?" | sampled for display":""));
    }
    private static int previewColor(String state){String id=state.split("\\[")[0];return switch(id){case "minecraft:stone"->0xFF929CA5;case "minecraft:oak_planks"->0xFFC4A16F;case "minecraft:glass"->0xFF72C4C5;case "minecraft:chest"->0xFFCB923F;case "minecraft:redstone_wire"->0xFFD46B65;case "minecraft:repeater"->0xFFC3AAA6;default->0xFF69B9AB;};}
    private static int shade(int argb,float factor){return 0xFF000000|((int)((argb>>>16&255)*factor)<<16)|((int)((argb>>>8&255)*factor)<<8)|(int)((argb&255)*factor);}
    private static void face(ImDrawList dl,float x,float y,double scale,double cos,double sin,float[][] corners,int color){
        float[] p=new float[8];for(int i=0;i<4;i++){float[] c=corners[i];p[i*2]=x+(float)((c[0]*cos-c[2]*sin)*scale);p[i*2+1]=y+(float)(((c[0]*sin+c[2]*cos)*.48-c[1])*scale);}
        dl.addQuadFilled(p[0],p[1],p[2],p[3],p[4],p[5],p[6],p[7],GuiPalette.abgr(color));
    }

}
