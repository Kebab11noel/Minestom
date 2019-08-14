package fr.themode.minestom.inventory;

import fr.themode.minestom.entity.Player;
import fr.themode.minestom.item.ItemStack;
import fr.themode.minestom.net.packet.server.play.SetSlotPacket;
import fr.themode.minestom.net.packet.server.play.WindowItemsPacket;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;

public class Inventory implements InventoryModifier, InventoryClickHandler {

    private static AtomicInteger lastInventoryId = new AtomicInteger();

    private int id;
    private InventoryType inventoryType;
    private String title;

    private int offset;

    private ItemStack[] itemStacks;
    private Set<Player> viewers = new CopyOnWriteArraySet<>();
    private ConcurrentHashMap<Player, ItemStack> cursorPlayersItem = new ConcurrentHashMap<>();

    public Inventory(InventoryType inventoryType, String title) {
        this.id = generateId();
        this.inventoryType = inventoryType;
        this.title = title;

        this.offset = inventoryType.getAdditionalSlot();

        this.itemStacks = new ItemStack[inventoryType.getAdditionalSlot()];
        Arrays.fill(itemStacks, ItemStack.AIR_ITEM);
    }

    private static int generateId() {
        return lastInventoryId.incrementAndGet();
    }

    public InventoryType getInventoryType() {
        return inventoryType;
    }

    public String getTitle() {
        return title;
    }

    public int getUniqueId() {
        return id;
    }

    @Override
    public void setItemStack(int slot, ItemStack itemStack) {
        if (slot < 0 || slot > inventoryType.getAdditionalSlot())
            throw new IllegalArgumentException(inventoryType.toString() + " does not have slot " + slot);

        safeItemInsert(slot, itemStack);
    }

    @Override
    public boolean addItemStack(ItemStack itemStack) {
        return false;
    }

    @Override
    public ItemStack getItemStack(int slot) {
        return itemStacks[slot];
    }

    public void updateItems() {
        WindowItemsPacket windowItemsPacket = getWindowItemsPacket();
        getViewers().forEach(p -> p.getPlayerConnection().sendPacket(windowItemsPacket));
    }

    public Set<Player> getViewers() {
        return Collections.unmodifiableSet(viewers);
    }

    public void addViewer(Player player) {
        this.viewers.add(player);
        WindowItemsPacket windowItemsPacket = getWindowItemsPacket();
        player.getPlayerConnection().sendPacket(windowItemsPacket);
    }

    public void removeViewer(Player player) {
        this.viewers.remove(player);
    }

    public ItemStack getCursorItem(Player player) {
        return cursorPlayersItem.getOrDefault(player, ItemStack.AIR_ITEM);
    }

    private void safeItemInsert(int slot, ItemStack itemStack) {
        synchronized (this) {
            itemStack = itemStack == null ? ItemStack.AIR_ITEM : itemStack;
            this.itemStacks[slot] = itemStack;
            SetSlotPacket setSlotPacket = new SetSlotPacket();
            setSlotPacket.windowId = 1;
            setSlotPacket.slot = (short) slot;
            setSlotPacket.itemStack = itemStack;
            getViewers().forEach(player -> player.getPlayerConnection().sendPacket(setSlotPacket));
        }
    }

    private WindowItemsPacket getWindowItemsPacket() {
        WindowItemsPacket windowItemsPacket = new WindowItemsPacket();
        windowItemsPacket.windowId = getUniqueId();
        windowItemsPacket.count = (short) itemStacks.length;
        windowItemsPacket.items = itemStacks;
        return windowItemsPacket;
    }

    private void setCursorPlayerItem(Player player, ItemStack itemStack) {
        this.cursorPlayersItem.put(player, itemStack);
    }

    private boolean isClickInWindow(int slot) {
        return slot < inventoryType.getAdditionalSlot();
    }

    @Override
    public void leftClick(Player player, int slot) {
        PlayerInventory playerInventory = player.getInventory();
        ItemStack cursorItem = getCursorItem(player);
        boolean isInWindow = isClickInWindow(slot);
        ItemStack clicked = isInWindow ? getItemStack(slot) : playerInventory.getItemStack(slot, offset);

        if (cursorItem.isAir() && clicked.isAir())
            return;

        ItemStack resultCursor;
        ItemStack resultClicked;

        if (cursorItem.isSimilar(clicked)) {
            resultCursor = cursorItem.clone();
            resultClicked = clicked.clone();
            int amount = cursorItem.getAmount() + clicked.getAmount();
            if (amount > 64) {
                resultCursor.setAmount((byte) (amount - 64));
                resultClicked.setAmount((byte) 64);
            } else {
                resultCursor = ItemStack.AIR_ITEM;
                resultClicked.setAmount((byte) amount);
            }
        } else {
            resultCursor = clicked.clone();
            resultClicked = cursorItem.clone();
        }

        if (isInWindow) {
            setItemStack(slot, resultClicked);
            setCursorPlayerItem(player, resultCursor);
            //updateItems();
        } else {
            playerInventory.setItemStack(slot, offset, resultClicked);
            setCursorPlayerItem(player, resultCursor);
            //playerInventory.update();
        }
    }

    @Override
    public void rightClick(Player player, int slot) {
        PlayerInventory playerInventory = player.getInventory();
        ItemStack cursorItem = getCursorItem(player);
        boolean isInWindow = isClickInWindow(slot);
        ItemStack clicked = isInWindow ? getItemStack(slot) : playerInventory.getItemStack(slot, offset);

        if (cursorItem.isAir() && clicked.isAir())
            return;

        ItemStack resultCursor;
        ItemStack resultClicked;

        if (cursorItem.isSimilar(clicked)) {
            resultCursor = cursorItem.clone();
            resultClicked = clicked.clone();
            int amount = clicked.getAmount() + 1;
            if (amount > 64) {
                return;
            } else {
                resultCursor = cursorItem.clone();
                resultCursor.setAmount((byte) (resultCursor.getAmount() - 1));
                if (resultCursor.getAmount() < 1)
                    resultCursor = ItemStack.AIR_ITEM;
                resultClicked.setAmount((byte) amount);
            }
        } else {
            if (cursorItem.isAir()) {
                int amount = (int) Math.ceil((double) clicked.getAmount() / 2d);
                resultCursor = clicked.clone();
                resultCursor.setAmount((byte) amount);
                resultClicked = clicked.clone();
                resultClicked.setAmount((byte) (clicked.getAmount() / 2));
            } else {
                if (clicked.isAir()) {
                    int amount = cursorItem.getAmount();
                    resultCursor = cursorItem.clone();
                    resultCursor.setAmount((byte) (amount - 1));
                    if (resultCursor.getAmount() < 1)
                        resultCursor = ItemStack.AIR_ITEM;
                    resultClicked = cursorItem.clone();
                    resultClicked.setAmount((byte) 1);
                } else {
                    resultCursor = clicked.clone();
                    resultClicked = cursorItem.clone();
                }
            }
        }

        if (isInWindow) {
            setItemStack(slot, resultClicked);
            setCursorPlayerItem(player, resultCursor);
        } else {
            playerInventory.setItemStack(slot, offset, resultClicked);
            setCursorPlayerItem(player, resultCursor);
        }
    }

    @Override
    public void shiftClick(Player player, int slot) {

    }

    @Override
    public void changeHeld(Player player, int slot, int key) {
        PlayerInventory playerInventory = player.getInventory();

        if (!getCursorItem(player).isAir())
            return;

        boolean isInWindow = isClickInWindow(slot);
        ItemStack heldItem = playerInventory.getItemStack(key);
        ItemStack clicked = isInWindow ? getItemStack(slot) : playerInventory.getItemStack(slot, offset);

        ItemStack resultClicked;
        ItemStack resultHeld;

        if (clicked.isAir()) {
            // Set held item [key] to slot
            resultClicked = ItemStack.AIR_ITEM;
            resultHeld = clicked.clone();
        } else {
            if (heldItem.isAir()) {
                // if held item [key] is air then set clicked to held
                resultClicked = ItemStack.AIR_ITEM;
                resultHeld = clicked.clone();
            } else {
                System.out.println("CASE3");
                // Otherwise replace held item and held
                resultClicked = heldItem.clone();
                resultHeld = clicked.clone();
            }
        }

        if (isInWindow) {
            setItemStack(slot, resultClicked);
        } else {
            playerInventory.setItemStack(slot, offset, resultClicked);
        }
        playerInventory.setItemStack(key, resultHeld);
    }

    @Override
    public void middleClick(Player player, int slot) {

    }

    @Override
    public void dropOne(Player player, int slot) {

    }

    @Override
    public void dropItemStack(Player player, int slot) {

    }
}