package dev.cp;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.NotePlayEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/** Obsługa stawiania i niszczenia własnych bloków (na stanach note blocka). */
public final class BlockListener implements Listener {

    private final CPPlugin plugin;

    public BlockListener(CPPlugin plugin) {
        this.plugin = plugin;
    }

    private String customAt(Block b) {
        if (b.getType() != Material.NOTE_BLOCK) {
            return null;
        }
        BlockData d = b.getBlockData();
        if (!(d instanceof NoteBlock)) {
            return null;
        }
        int idx = BlockStates.indexOf((NoteBlock) d);
        return idx > 0 ? plugin.resources().blockAt(idx) : null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Block b = e.getBlockPlaced();
        if (b.getType() != Material.NOTE_BLOCK) {
            return;
        }
        String id = plugin.resources().idOf(e.getItemInHand());
        int idx = plugin.resources().noteIndex(id);
        // zwykły note block dostaje stan 0, żeby nie udawał bloku CP
        b.setBlockData(BlockStates.dataFor(Math.max(idx, 0)), false);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        String name = customAt(b);
        if (name == null) {
            return;
        }
        e.setDropItems(false);
        if (e.getPlayer().getGameMode() != GameMode.CREATIVE) {
            ItemStack item = plugin.resources().build(name, 1);
            if (item != null) {
                b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), item);
            }
        }
    }

    /** Blokuje zmianę stanu note blocka przez sąsiednie bloki. */
    @EventHandler(ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent e) {
        if (e.getBlock().getType() == Material.NOTE_BLOCK) {
            e.setCancelled(true);
        }
    }

    /** Blokuje strojenie note blocków (zmieniałoby stan bloku CP). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block b = e.getClickedBlock();
        if (b != null && b.getType() == Material.NOTE_BLOCK) {
            e.setUseInteractedBlock(Event.Result.DENY);
        }
    }

    /** Własne bloki nie grają dźwięków nut. */
    @EventHandler(ignoreCancelled = true)
    public void onNote(NotePlayEvent e) {
        if (customAt(e.getBlock()) != null) {
            e.setCancelled(true);
        }
    }
}
