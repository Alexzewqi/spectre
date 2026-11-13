package net.spectre.client.builder;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Refills the builder's inventory when it runs out: first from a chosen restock chest, then from the
 * server's shop. The shop search walks every page (and every category) looking for full-stack
 * listings, so it never buys blocks one at a time while a stack is on sale somewhere else.
 */
final class Resupply {
    private enum Phase { IDLE, WALK, OPEN_CHEST, LOOT, SHOP_OPEN, SHOP_WAIT, SHOP_SCAN, SHOP_BOUGHT, SHOP_CONFIRM }
    private final GroundNavigator navigator=new GroundNavigator();
    private final Map<Item,Integer> wanted=new LinkedHashMap<>();
    private Phase phase=Phase.IDLE;
    private String status="",failure;
    private int wait,age;
    // Chest
    private BlockPos chest;
    private boolean shopAfterChest;
    // Shop search state for the current item
    private Item item;
    private List<Integer> categories;
    private int category,pages,clickedSlot,containerId,before,confirmClicks;
    private final Set<String> seenPages=new HashSet<>();
    private boolean allowSingles,stackAmountClicked;

    // Settings, shared with the builder panel.
    static boolean useChest=true,useShop=true,stacksOnly=true;
    static BlockPos restockChest;
    static String shopCommand="shop";
    static int maxStacks=9;

    boolean active(){return phase!=Phase.IDLE;}
    String status(){return status;}
    /** Non-null once a resupply attempt has given up; read and cleared by the builder. */
    String takeFailure(){String f=failure;failure=null;return f;}
    void cancel(Minecraft mc){
        if(phase==Phase.IDLE)return;
        phase=Phase.IDLE;GroundNavigator.release(mc);
        if(mc.player!=null&&mc.gui.screen() instanceof AbstractContainerScreen<?>)mc.player.closeContainer();
    }

    /** Starts refilling {@code deficits} (item -> blocks still to place minus blocks carried). */
    boolean begin(Minecraft mc,Map<Item,Integer> deficits){
        wanted.clear();deficits.forEach((k,v)->{if(v>0)wanted.put(k,v);});
        if(wanted.isEmpty())return false;
        failure=null;wait=age=0;
        shopAfterChest=useShop;
        if(useChest&&restockChest!=null&&mc.level.getBlockState(restockChest).hasBlockEntity()){chest=restockChest;startChest(mc);return true;}
        if(useShop){startShop();return true;}
        return false;
    }

    void tick(Minecraft mc){
        if(phase==Phase.IDLE)return;
        try{
            age++;
            if(wait-->0)return;
            switch(phase){
                case WALK->walk(mc);
                case OPEN_CHEST->openChest(mc);
                case LOOT->loot(mc);
                case SHOP_OPEN->shopOpen(mc);
                case SHOP_WAIT->shopWait(mc);
                case SHOP_SCAN->shopScan(mc);
                case SHOP_BOUGHT->shopBought(mc);
                case SHOP_CONFIRM->shopConfirm(mc);
                default->{}
            }
        }catch(RuntimeException ex){fail(mc,ex.getMessage());}
    }

    // ---------------------------------------------------------------- restock chest

    private void startChest(Minecraft mc){
        var routes=GroundNavigator.routes(mc);double reach=mc.player.blockInteractionRange()-.4;
        BlockPos best=null;double bestCost=Double.POSITIVE_INFINITY;
        for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)for(int y=-3;y<=2;y++){
            var feet=chest.offset(x,y,z);
            if(!GroundNavigator.standable(mc,feet))continue;
            Vec3 eye=Vec3.atBottomCenterOf(feet).add(0,mc.player.getEyeHeight(),0);
            if(eye.distanceTo(Vec3.atCenterOf(chest))>reach)continue;
            double cost=routes.cost(feet);
            if(cost<bestCost){bestCost=cost;best=feet;}
        }
        if(best==null){
            if(shopAfterChest){status="Restock chest is out of walking range, using the shop";startShop();return;}
            throw new IllegalStateException("Restock chest is out of walking range (stand within 24 blocks of it)");
        }
        navigator.follow(routes.path(best));phase=Phase.WALK;age=0;
        status="Walking to the restock chest";
    }
    private void walk(Minecraft mc){
        if(age>600)throw new IllegalStateException("Could not reach the restock chest");
        if(navigator.tick(mc)){phase=Phase.OPEN_CHEST;age=0;}
    }
    private void openChest(Minecraft mc){
        mc.options.keyShift.setDown(false);
        if(mc.gui.screen() instanceof AbstractContainerScreen<?>){phase=Phase.LOOT;age=0;wait=6;status="Taking blocks from the restock chest";return;}
        if(age>80)throw new IllegalStateException("Restock chest did not open");
        Vec3 target=Vec3.atCenterOf(chest),d=target.subtract(mc.player.getEyePosition());
        if(!CreativeBuilder.turn(mc,(float)Math.toDegrees(Math.atan2(-d.x,d.z)),(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z)))))return;
        if(age%20!=1)return;
        var hit=CreativeBuilder.trace(mc);
        if(hit.getType()!=HitResult.Type.BLOCK||!hit.getBlockPos().equals(chest))throw new IllegalStateException("Restock chest is not visible from here");
        mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,hit);mc.player.swing(InteractionHand.MAIN_HAND);
    }
    private void loot(Minecraft mc){
        if(!(mc.gui.screen() instanceof AbstractContainerScreen<?>))throw new IllegalStateException("Restock chest closed");
        var menu=mc.player.containerMenu;
        if(freeSlots(mc)>0)for(Slot slot:menu.slots){
            if(slot.container instanceof Inventory||!slot.hasItem())continue;
            var stack=slot.getItem();Integer need=wanted.get(stack.getItem());
            if(need==null||need<=0)continue;
            wanted.put(stack.getItem(),need-stack.getCount());
            mc.gameMode.handleContainerInput(menu.containerId,slot.index,0,ContainerInput.QUICK_MOVE,mc.player);
            wait=3;return;
        }
        mc.player.closeContainer();
        wanted.values().removeIf(v->v<=0);
        if(wanted.isEmpty()||freeSlots(mc)==0){done(mc);return;}
        if(shopAfterChest){startShop();wait=10;return;}
        throw new IllegalStateException("Restock chest has no "+name(wanted.keySet().iterator().next()));
    }

    // ---------------------------------------------------------------- shop

    private void startShop(){
        item=wanted.keySet().iterator().next();
        categories=null;category=-1;pages=0;seenPages.clear();allowSingles=false;
        phase=Phase.SHOP_OPEN;age=0;
        status="Auto Buy: opening /"+shopCommand+" for "+name(item);
    }
    private void shopOpen(Minecraft mc){
        if(mc.gui.screen() instanceof AbstractContainerScreen<?>){mc.player.closeContainer();wait=4;return;}
        var connection=mc.getConnection();if(connection==null)throw new IllegalStateException("Not connected");
        String command=shopCommand.startsWith("/")?shopCommand.substring(1):shopCommand;
        connection.sendCommand(command);
        phase=Phase.SHOP_WAIT;age=0;
    }
    /** Waits for the root shop menu and its contents, then enters the next category if one is being searched. */
    private void shopWait(Minecraft mc){
        if(age>100)throw new IllegalStateException("/"+shopCommand+" did not open a menu");
        if(!(mc.gui.screen() instanceof AbstractContainerScreen<?>)||containerSlots(mc).stream().noneMatch(Slot::hasItem)){return;}
        if(age<8)return;
        if(categories==null){
            categories=new ArrayList<>();
            for(Slot s:containerSlots(mc))if(s.hasItem()&&!filler(s.getItem())&&!navigation(s.getItem()))categories.add(s.index);
        }
        if(category>=0){click(mc,categories.get(category));wait=10;}
        pages=0;seenPages.clear();phase=Phase.SHOP_SCAN;age=0;
    }
    private void shopScan(Minecraft mc){
        if(!(mc.gui.screen() instanceof AbstractContainerScreen<?>)){phase=Phase.SHOP_OPEN;return;}
        var slots=containerSlots(mc);
        if(!seenPages.add(signature(slots))){nextCategory(mc);return;}
        Slot stack=null,single=null;
        for(Slot s:slots){
            if(!s.hasItem()||!s.getItem().is(item))continue;
            if(s.getItem().getCount()>=s.getItem().getMaxStackSize()){stack=s;break;}
            if(single==null)single=s;
        }
        Slot listing=stack!=null?stack:allowSingles?single:null;
        if(listing!=null){
            status="Auto Buy: buying "+(listing==stack?"stacks of ":"")+name(item)+(pages>0?" (page "+(pages+1)+")":"");
            buy(mc,listing.index);return;
        }
        // No full stack on this page: try the next page before anything else.
        for(Slot s:slots)if(s.hasItem()&&next(s.getItem())&&pages<40){
            pages++;status="Auto Buy: no stack of "+name(item)+" here, checking page "+(pages+1);
            click(mc,s.index);wait=10;return;
        }
        nextCategory(mc);
    }
    /** Searches the shop's categories one by one, reopening the root menu between them. */
    private void nextCategory(Minecraft mc){
        if(categories!=null&&category+1<categories.size()){
            category++;phase=Phase.SHOP_OPEN;wait=4;
            status="Auto Buy: searching shop category "+(category+1)+" / "+categories.size()+" for "+name(item);
            return;
        }
        if(!stacksOnly&&!allowSingles){allowSingles=true;category=-1;phase=Phase.SHOP_OPEN;wait=4;return;}
        throw new IllegalStateException("No "+(stacksOnly?"full-stack ":"")+"listing for "+name(item)+" found in /"+shopCommand);
    }
    private void buy(Minecraft mc,int slot){
        clickedSlot=slot;containerId=mc.player.containerMenu.containerId;before=count(mc,item);confirmClicks=0;stackAmountClicked=false;
        click(mc,slot);phase=Phase.SHOP_BOUGHT;age=0;wait=6;
    }
    private void shopBought(Minecraft mc){
        int now=count(mc,item);
        if(now>before){purchased(mc,now);return;}
        if(mc.gui.screen() instanceof AbstractContainerScreen<?>&&mc.player.containerMenu.containerId!=containerId){
            phase=Phase.SHOP_CONFIRM;age=0;wait=4;return;
        }
        if(age>40)throw new IllegalStateException("The shop did not sell "+name(item)+" (not enough money?)");
    }
    /** A quantity / confirmation menu: pick a full stack if offered, then press the confirm button. */
    private void shopConfirm(Minecraft mc){
        int now=count(mc,item);
        if(now>before){purchased(mc,now);return;}
        if(!(mc.gui.screen() instanceof AbstractContainerScreen<?>)){phase=Phase.SHOP_OPEN;return;}
        if(age>60||confirmClicks>3)throw new IllegalStateException("Could not confirm the purchase of "+name(item)+" (not enough money?)");
        var slots=containerSlots(mc);
        if(!stackAmountClicked){
            stackAmountClicked=true;
            for(Slot s:slots)if(s.hasItem()&&stackAmount(s.getItem())){click(mc,s.index);wait=5;return;}
        }
        for(Slot s:slots)if(s.hasItem()&&confirm(s.getItem())){click(mc,s.index);confirmClicks++;wait=8;return;}
        throw new IllegalStateException("No confirm button in the shop's purchase menu");
    }
    private void purchased(Minecraft mc,int now){
        int gained=now-before,need=wanted.get(item)-gained;
        wanted.put(item,need);before=now;
        int limit=maxStacks*item.getDefaultMaxStackSize();
        int bought=boughtSoFar.merge(item,gained,Integer::sum);
        if(need<=0||freeSlots(mc)==0||bought>=limit){
            wanted.remove(item);
            if(wanted.isEmpty()||freeSlots(mc)==0){done(mc);return;}
            if(mc.gui.screen() instanceof AbstractContainerScreen<?>)mc.player.closeContainer();
            startShop();wait=6;return;
        }
        // Keep buying from the same listing / confirm screen.
        status="Auto Buy: "+name(item)+" — "+Math.max(0,need)+" still needed";
        if(mc.gui.screen() instanceof AbstractContainerScreen<?>){
            if(phase==Phase.SHOP_CONFIRM&&mc.player.containerMenu.containerId!=containerId){
                for(Slot s:containerSlots(mc))if(s.hasItem()&&confirm(s.getItem())){click(mc,s.index);confirmClicks=0;age=0;wait=8;return;}
            }
            if(mc.player.containerMenu.containerId==containerId&&clickedSlot<mc.player.containerMenu.slots.size()
                &&mc.player.containerMenu.getSlot(clickedSlot).getItem().is(item)){buy(mc,clickedSlot);return;}
            // Shop returned to its root or another page: search again.
            phase=Phase.SHOP_SCAN;seenPages.clear();age=0;wait=4;return;
        }
        startShop();wait=6;
    }
    private final Map<Item,Integer> boughtSoFar=new HashMap<>();

    // ---------------------------------------------------------------- helpers

    private void done(Minecraft mc){
        if(mc.gui.screen() instanceof AbstractContainerScreen<?>)mc.player.closeContainer();
        GroundNavigator.release(mc);boughtSoFar.clear();
        phase=Phase.IDLE;status="Supplies restocked";
    }
    private void fail(Minecraft mc,String message){
        cancel(mc);boughtSoFar.clear();failure=message==null?"Resupply failed":message;status=failure;
    }
    private static void click(Minecraft mc,int slot){
        mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId,slot,0,ContainerInput.PICKUP,mc.player);
    }
    private static List<Slot> containerSlots(Minecraft mc){
        var out=new ArrayList<Slot>();
        for(Slot s:mc.player.containerMenu.slots)if(!(s.container instanceof Inventory))out.add(s);
        return out;
    }
    private static String signature(List<Slot> slots){
        var b=new StringBuilder();
        for(Slot s:slots)b.append(BuiltInRegistries.ITEM.getKey(s.getItem().getItem())).append('x').append(s.getItem().getCount()).append(s.getItem().getHoverName().getString()).append('|');
        return b.toString();
    }
    static int count(Minecraft mc,Item item){
        var inv=mc.player.getInventory();int n=0;
        for(int i=0;i<36;i++)if(inv.getItem(i).is(item))n+=inv.getItem(i).getCount();
        return n;
    }
    private static int freeSlots(Minecraft mc){
        var inv=mc.player.getInventory();int n=0;
        for(int i=0;i<36;i++)if(inv.getItem(i).isEmpty())n++;
        return n;
    }
    private static String name(Item item){return item.getDefaultInstance().getHoverName().getString();}
    private static String label(ItemStack stack){return stack.getHoverName().getString().toLowerCase(Locale.ROOT);}
    private static boolean filler(ItemStack stack){
        String id=BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        return id.endsWith("glass_pane")||label(stack).isBlank();
    }
    private static boolean next(ItemStack stack){
        String l=label(stack);
        if(l.contains("prev")||l.contains("back"))return false;
        return l.contains("next")||l.contains("→")||l.contains("»")||l.contains("forward")||l.matches(".*\\bpage\\s*\\d+\\s*>.*")||(l.length()<=3&&l.contains(">"));
    }
    private static boolean navigation(ItemStack stack){
        String l=label(stack);
        return next(stack)||stack.is(Items.BARRIER)||l.contains("close")||l.contains("back")||l.contains("prev")||l.contains("exit")||l.contains("balance")||l.contains("search");
    }
    private static boolean stackAmount(ItemStack stack){
        String l=label(stack);
        return l.contains("stack")||l.matches(".*(set to|buy|\\+|x)\\s*64.*")||l.matches("64");
    }
    private static boolean confirm(ItemStack stack){
        String l=label(stack),id=BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if(l.contains("cancel")||l.contains("deny")||l.contains("back"))return false;
        return l.contains("confirm")||l.contains("purchase")||l.contains("accept")||l.equals("buy")||l.startsWith("buy ")
            ||id.startsWith("lime_")||id.startsWith("green_");
    }
}
