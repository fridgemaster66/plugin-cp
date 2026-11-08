package dev.cp;

import dev.cp.ResourceManager.Type;
import org.bukkit.GameMode;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

/** Własne jedzenie: wartości głodu, efekty i dźwięk bierzemy z configu zasobu. */
public final class FoodListener implements Listener {

    private final CPPlugin plugin;

    public FoodListener(CPPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        ResourceManager rm = plugin.resources();
        String id = rm.idOf(e.getItem());
        if (id == null || rm.findType(id) != Type.FOOD) {
            return;
        }
        // anulujemy vanillowe zjedzenie (żeby bazowy materiał nie dawał swoich efektów) i robimy własne
        e.setCancelled(true);
        Player p = e.getPlayer();
        YamlConfiguration c = rm.load(Type.FOOD, id);

        if (p.getGameMode() != GameMode.CREATIVE) {
            EquipmentSlot hand = e.getHand();
            ItemStack held = p.getInventory().getItem(hand);
            if (held != null) {
                held.setAmount(held.getAmount() - 1);
                p.getInventory().setItem(hand, held.getAmount() > 0 ? held : null);
            }
        }

        p.setFoodLevel(Math.min(20, p.getFoodLevel() + c.getInt("hunger", 0)));
        p.setSaturation((float) Math.min(p.getFoodLevel(), p.getSaturation() + c.getDouble("saturation", 0)));

        for (String s : c.getStringList("effects")) {
            PotionEffect effect = ResourceManager.parseEffect(s);
            if (effect != null) {
                p.addPotionEffect(effect);
            }
        }

        String snd = c.getString("eat-sound", "none");
        if (snd != null && !snd.isEmpty() && !snd.equalsIgnoreCase("none")) {
            String key = rm.soundExists(snd) ? "cp:" + snd : snd;
            p.getWorld().playSound(p.getLocation(), key, SoundCategory.PLAYERS, 1.0f, 1.0f);
        }
    }
}
