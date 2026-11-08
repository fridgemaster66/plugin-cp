package dev.cp;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class ResourceManager {

    public enum Type {
        ITEM("item", "PAPER"),
        ARMOR("armor", "LEATHER_CHESTPLATE"),
        BLOCK("block", "NOTE_BLOCK"),
        FOOD("food", "GOLDEN_APPLE");

        public final String dir;
        public final String defMaterial;

        Type(String dir, String defMaterial) {
            this.dir = dir;
            this.defMaterial = defMaterial;
        }
    }

    public static final Pattern NAME = Pattern.compile("[a-z0-9_]{1,40}");
    public static final Pattern SOUND = Pattern.compile("[a-z0-9_]{1,30}\\.[a-z0-9_]{1,30}");
    public static final String OPTIONS = "name, material, model-data, glow, category, damage, color, hunger, saturation, effects, eat-sound (food only), file (sounds only)";

    private final CPPlugin plugin;
    private final NamespacedKey idKey;
    private volatile Map<Integer, String> blockCache;

    public ResourceManager(CPPlugin plugin) {
        this.plugin = plugin;
        this.idKey = new NamespacedKey(plugin, "id");
    }

    // ---------- foldery ----------

    public File assets() {
        return new File(plugin.getDataFolder(), "assets");
    }

    public void ensureFolders() {
        String[] dirs = {
                "configs/item", "configs/armor", "configs/block", "configs/food", "configs/sounds",
                "textures/item", "textures/armor", "textures/block", "textures/food",
                "models/item", "models/block", "models/food",
                "sounds/magic", "sounds/sword", "sounds/opening",
                "pack"
        };
        for (String d : dirs) {
            new File(assets(), d).mkdirs();
        }
    }

    // ---------- itemy / zbroje / bloki ----------

    public File configFile(Type t, String name) {
        return new File(assets(), "configs/" + t.dir + "/" + name + ".yml");
    }

    public Type findType(String name) {
        if (!NAME.matcher(name).matches()) {
            return null;
        }
        for (Type t : Type.values()) {
            if (configFile(t, name).isFile()) {
                return t;
            }
        }
        return null;
    }

    public boolean exists(String name) {
        return findType(name) != null;
    }

    public List<String> names(Type t) {
        File[] files = new File(assets(), "configs/" + t.dir).listFiles((d, n) -> n.endsWith(".yml"));
        List<String> r = new ArrayList<>();
        if (files != null) {
            for (File f : files) {
                String n = f.getName();
                r.add(n.substring(0, n.length() - 4));
            }
        }
        Collections.sort(r);
        return r;
    }

    public YamlConfiguration load(Type t, String name) {
        return YamlConfiguration.loadConfiguration(configFile(t, name));
    }

    public int nextModelData() {
        int max = 1000;
        for (Type t : Type.values()) {
            for (String n : names(t)) {
                max = Math.max(max, load(t, n).getInt("custom-model-data", 0));
            }
        }
        return max + 1;
    }

    public void create(Type t, String name) throws IOException {
        File f = configFile(t, name);
        f.getParentFile().mkdirs();
        YamlConfiguration c = new YamlConfiguration();
        c.set("display-name", "&f" + name);
        c.set("material", t.defMaterial);
        c.set("custom-model-data", nextModelData());
        c.set("category", "misc");
        c.set("glow", false);
        c.set("damage", 0);
        if (t == Type.ARMOR) {
            c.set("color", "FFFFFF");
        }
        if (t == Type.BLOCK) {
            c.set("note-block", nextNoteIndex());
        }
        if (t == Type.FOOD) {
            c.set("hunger", 4);
            c.set("saturation", 2.4);
            c.set("effects", new ArrayList<String>());
            c.set("eat-sound", "none");
        }
        c.save(f);
        blockCache = null;
    }

    /** Zwraca null gdy OK, w przeciwnym razie klucz komunikatu z bledem. */
    public String edit(String name, String option, String value) throws IOException {
        if (SOUND.matcher(name).matches()) {
            return editSound(name, option, value);
        }
        Type t = findType(name);
        if (t == null) {
            return "not-found";
        }
        File f = configFile(t, name);
        YamlConfiguration c = YamlConfiguration.loadConfiguration(f);
        switch (option.toLowerCase(Locale.ROOT)) {
            case "name": {
                c.set("display-name", value);
                break;
            }
            case "material": {
                if (t == Type.BLOCK) {
                    return "bad-value";
                }
                Material m = Material.matchMaterial(value);
                if (m == null || !m.isItem()) {
                    return "bad-value";
                }
                if (t == Type.FOOD && !m.isEdible()) {
                    return "bad-value";
                }
                c.set("material", m.name());
                break;
            }
            case "model-data": {
                Integer i = parseInt(value);
                if (i == null || i < 1) {
                    return "bad-value";
                }
                c.set("custom-model-data", i);
                break;
            }
            case "glow": {
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                    return "bad-value";
                }
                c.set("glow", Boolean.parseBoolean(value));
                break;
            }
            case "category": {
                c.set("category", value);
                break;
            }
            case "damage": {
                Integer i = parseInt(value);
                if (i == null || i < 0) {
                    return "bad-value";
                }
                c.set("damage", i);
                break;
            }
            case "color": {
                String hex = value.replace("#", "");
                if (!hex.matches("[0-9a-fA-F]{6}")) {
                    return "bad-value";
                }
                c.set("color", hex.toUpperCase(Locale.ROOT));
                break;
            }
            case "hunger": {
                if (t != Type.FOOD) {
                    return "bad-option";
                }
                Integer i = parseInt(value);
                if (i == null || i < 0 || i > 20) {
                    return "bad-value";
                }
                c.set("hunger", i);
                break;
            }
            case "saturation": {
                if (t != Type.FOOD) {
                    return "bad-option";
                }
                double d;
                try {
                    d = Double.parseDouble(value.trim().replace(',', '.'));
                } catch (NumberFormatException ex) {
                    return "bad-value";
                }
                if (d < 0 || d > 20) {
                    return "bad-value";
                }
                c.set("saturation", d);
                break;
            }
            case "effects": {
                if (t != Type.FOOD) {
                    return "bad-option";
                }
                List<String> list = new ArrayList<>();
                if (!value.equalsIgnoreCase("none")) {
                    for (String part : value.split(",")) {
                        String s = part.trim();
                        if (parseEffect(s) == null) {
                            return "bad-value";
                        }
                        list.add(s.toUpperCase(Locale.ROOT));
                    }
                }
                c.set("effects", list);
                break;
            }
            case "eat-sound": {
                if (t != Type.FOOD) {
                    return "bad-option";
                }
                if (!value.matches("[A-Za-z0-9_.:/-]+")) {
                    return "bad-value";
                }
                c.set("eat-sound", value.toLowerCase(Locale.ROOT));
                break;
            }
            default: {
                return "bad-option";
            }
        }
        c.save(f);
        blockCache = null;
        return null;
    }

    public boolean delete(String name) throws IOException {
        if (SOUND.matcher(name).matches()) {
            return deleteSound(name);
        }
        Type t = findType(name);
        if (t == null) {
            return false;
        }
        Files.deleteIfExists(configFile(t, name).toPath());
        blockCache = null;
        return true;
    }

    /** Zwraca null gdy OK, w przeciwnym razie klucz komunikatu z bledem. */
    public String rename(String oldName, String newName) throws IOException {
        if (!NAME.matcher(newName).matches()) {
            return "bad-name";
        }
        Type t = findType(oldName);
        if (t == null) {
            return "not-found";
        }
        if (exists(newName)) {
            return "exists";
        }
        File newCfg = configFile(t, newName);
        Files.move(configFile(t, oldName).toPath(), newCfg.toPath(), StandardCopyOption.REPLACE_EXISTING);
        blockCache = null;

        YamlConfiguration c = YamlConfiguration.loadConfiguration(newCfg);
        if (("&f" + oldName).equals(c.getString("display-name"))) {
            c.set("display-name", "&f" + newName);
            c.save(newCfg);
        }

        moveIfExists(new File(assets(), "textures/" + t.dir + "/" + oldName + ".png"),
                new File(assets(), "textures/" + t.dir + "/" + newName + ".png"));
        moveIfExists(new File(assets(), "textures/" + t.dir + "/" + oldName + ".png.mcmeta"),
                new File(assets(), "textures/" + t.dir + "/" + newName + ".png.mcmeta"));

        File oldModel = new File(assets(), "models/" + t.dir + "/" + oldName + ".json");
        if (oldModel.isFile()) {
            String text = new String(Files.readAllBytes(oldModel.toPath()), StandardCharsets.UTF_8);
            text = text.replace(":" + t.dir + "/" + oldName + "\"", ":" + t.dir + "/" + newName + "\"");
            File newModel = new File(assets(), "models/" + t.dir + "/" + newName + ".json");
            Files.write(newModel.toPath(), text.getBytes(StandardCharsets.UTF_8));
            Files.delete(oldModel.toPath());
        }
        return null;
    }

    public List<String> info(String name) {
        List<String> lines = new ArrayList<>();
        if (SOUND.matcher(name).matches()) {
            if (!soundExists(name)) {
                return null;
            }
            String[] p = name.split("\\.", 2);
            ConfigurationSection s = YamlConfiguration.loadConfiguration(soundFile(p[0])).getConfigurationSection("sounds." + p[1]);
            lines.add("type: sound");
            if (s != null) {
                for (String k : s.getKeys(false)) {
                    lines.add(k + ": " + s.get(k));
                }
            }
            return lines;
        }
        Type t = findType(name);
        if (t == null) {
            return null;
        }
        YamlConfiguration c = load(t, name);
        lines.add("type: " + t.dir);
        for (String k : c.getKeys(false)) {
            lines.add(k + ": " + c.get(k));
        }
        return lines;
    }

    public ItemStack build(String name, int amount) {
        Type t = findType(name);
        if (t == null) {
            return null;
        }
        YamlConfiguration c = load(t, name);
        Material m = Material.matchMaterial(c.getString("material", t.defMaterial));
        if (m == null || !m.isItem()) {
            m = Material.PAPER;
        }
        if (t == Type.BLOCK) {
            m = Material.NOTE_BLOCK;
        }
        ItemStack item = new ItemStack(m, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', c.getString("display-name", "&f" + name)));

        int cmd = c.getInt("custom-model-data", 0);
        if (cmd > 0) {
            meta.setCustomModelData(cmd);
        }
        if (c.getBoolean("glow", false)) {
            Enchantment e = Enchantment.getByKey(NamespacedKey.minecraft("unbreaking"));
            if (e != null) {
                meta.addEnchant(e, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
        }
        int dmg = c.getInt("damage", 0);
        if (dmg > 0 && meta instanceof Damageable) {
            ((Damageable) meta).setDamage(dmg);
        }
        String hex = c.getString("color");
        if (hex != null && meta instanceof LeatherArmorMeta) {
            try {
                ((LeatherArmorMeta) meta).setColor(Color.fromRGB(Integer.parseInt(hex.replace("#", ""), 16)));
            } catch (IllegalArgumentException ignored) {
                // zly kolor w configu - pomijamy
            }
        }
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, name);
        item.setItemMeta(meta);
        return item;
    }

    // ---------- wlasne bloki (stany note blocka) ----------

    public int nextNoteIndex() {
        int max = 0;
        for (String n : names(Type.BLOCK)) {
            max = Math.max(max, load(Type.BLOCK, n).getInt("note-block", 0));
        }
        return max + 1;
    }

    /** Numer stanu bloku CP albo 0, gdy to nie jest blok CP. */
    public int noteIndex(String name) {
        if (name == null || !NAME.matcher(name).matches() || !configFile(Type.BLOCK, name).isFile()) {
            return 0;
        }
        return load(Type.BLOCK, name).getInt("note-block", 0);
    }

    /** Nazwa bloku CP dla danego numeru stanu albo null. */
    public String blockAt(int index) {
        Map<Integer, String> m = blockCache;
        if (m == null) {
            m = new HashMap<>();
            for (String n : names(Type.BLOCK)) {
                int i = load(Type.BLOCK, n).getInt("note-block", 0);
                if (i > 0) {
                    m.put(i, n);
                }
            }
            blockCache = m;
        }
        return m.get(index);
    }

    /** Nazwa zasobu CP zapisana w przedmiocie albo null. */
    public String idOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

    // ---------- dzwieki ----------

    public File soundFile(String group) {
        return new File(assets(), "configs/sounds/" + group + ".yml");
    }

    public boolean soundExists(String full) {
        if (!SOUND.matcher(full).matches()) {
            return false;
        }
        String[] p = full.split("\\.", 2);
        File f = soundFile(p[0]);
        return f.isFile() && YamlConfiguration.loadConfiguration(f).isConfigurationSection("sounds." + p[1]);
    }

    public void addSound(String full) throws IOException {
        String[] p = full.split("\\.", 2);
        File f = soundFile(p[0]);
        f.getParentFile().mkdirs();
        YamlConfiguration c = YamlConfiguration.loadConfiguration(f);
        c.set("sounds." + p[1] + ".file", p[0] + "/" + p[1]);
        c.set("sounds." + p[1] + ".category", "master");
        c.save(f);
    }

    public boolean deleteSound(String full) throws IOException {
        if (!soundExists(full)) {
            return false;
        }
        String[] p = full.split("\\.", 2);
        File f = soundFile(p[0]);
        YamlConfiguration c = YamlConfiguration.loadConfiguration(f);
        c.set("sounds." + p[1], null);
        c.save(f);
        return true;
    }

    public List<String> soundNames() {
        List<String> r = new ArrayList<>();
        File[] files = new File(assets(), "configs/sounds").listFiles((d, n) -> n.endsWith(".yml"));
        if (files != null) {
            for (File f : files) {
                String fn = f.getName();
                String group = fn.substring(0, fn.length() - 4);
                ConfigurationSection s = YamlConfiguration.loadConfiguration(f).getConfigurationSection("sounds");
                if (s != null) {
                    for (String key : s.getKeys(false)) {
                        r.add(group + "." + key);
                    }
                }
            }
        }
        Collections.sort(r);
        return r;
    }

    public SoundCategory soundCategory(String full) {
        String[] p = full.split("\\.", 2);
        String cat = YamlConfiguration.loadConfiguration(soundFile(p[0])).getString("sounds." + p[1] + ".category", "master");
        try {
            return SoundCategory.valueOf(cat.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return SoundCategory.MASTER;
        }
    }

    private String editSound(String full, String option, String value) throws IOException {
        if (!soundExists(full)) {
            return "not-found";
        }
        String[] p = full.split("\\.", 2);
        File f = soundFile(p[0]);
        YamlConfiguration c = YamlConfiguration.loadConfiguration(f);
        switch (option.toLowerCase(Locale.ROOT)) {
            case "file": {
                if (!value.matches("[a-z0-9_/]+")) {
                    return "bad-value";
                }
                c.set("sounds." + p[1] + ".file", value);
                break;
            }
            case "category": {
                try {
                    SoundCategory.valueOf(value.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    return "bad-value";
                }
                c.set("sounds." + p[1] + ".category", value.toLowerCase(Locale.ROOT));
                break;
            }
            default: {
                return "bad-option";
            }
        }
        c.save(f);
        return null;
    }

    // ---------- pomocnicze ----------

    /** Format: TYP:sekundy:poziom, np. REGENERATION:10:1 (poziom 0 = I, 1 = II). Zwraca null gdy zły. */
    public static PotionEffect parseEffect(String s) {
        String[] p = s.trim().split(":");
        if (p.length < 2 || p.length > 3) {
            return null;
        }
        PotionEffectType type = PotionEffectType.getByName(p[0].trim().toUpperCase(Locale.ROOT));
        Integer seconds = parseInt(p[1]);
        Integer amp = p.length == 3 ? parseInt(p[2]) : Integer.valueOf(0);
        if (type == null || seconds == null || amp == null || seconds < 1 || seconds > 3600 || amp < 0 || amp > 255) {
            return null;
        }
        return new PotionEffect(type, seconds * 20, amp);
    }

    private static Integer parseInt(String s) {
        try {
            return Integer.valueOf(s.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static void moveIfExists(File from, File to) throws IOException {
        if (from.isFile()) {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
