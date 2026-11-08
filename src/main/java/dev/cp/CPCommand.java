package dev.cp;

import dev.cp.ResourceManager.Type;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.SoundCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class CPCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = Arrays.asList(
            "generate", "texture", "add", "edit", "delete", "give", "rename", "sound", "language", "list", "info", "help");

    private final CPPlugin plugin;

    public CPCommand(CPPlugin plugin) {
        this.plugin = plugin;
    }

    private Lang lang() {
        return plugin.lang();
    }

    private ResourceManager rm() {
        return plugin.resources();
    }

    private void send(CommandSender s, String key, Object... args) {
        s.sendMessage(lang().get("prefix") + lang().get(key, args));
    }

    private boolean perm(CommandSender s, String permission) {
        if (s.hasPermission(permission)) {
            return true;
        }
        send(s, "no-perm");
        return false;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        try {
            if (a.length == 0 || a[0].equalsIgnoreCase("help")) {
                if (perm(s, "cp.info")) {
                    help(s);
                }
                return true;
            }
            switch (a[0].toLowerCase(Locale.ROOT)) {
                case "generate": generate(s); break;
                case "texture": texture(s, a); break;
                case "add": add(s, a); break;
                case "edit": edit(s, a); break;
                case "delete": delete(s, a); break;
                case "give": give(s, a); break;
                case "rename": rename(s, a); break;
                case "sound": sound(s, a); break;
                case "language": language(s, a); break;
                case "list": list(s); break;
                case "info": info(s, a); break;
                default: send(s, "unknown"); break;
            }
        } catch (IOException ex) {
            send(s, "io-error", String.valueOf(ex.getMessage()));
        }
        return true;
    }

    // ---------- /cp help ----------

    private void help(CommandSender s) {
        s.sendMessage(lang().get("h.title"));
        section(s, "h.sec1", new String[][]{
                {"/cp generate", "h.generate"},
                {"/cp texture all", "h.texture-all"},
                {"/cp texture player <nick>", "h.texture-player"}});
        section(s, "h.sec2", new String[][]{
                {"/cp add item <nazwa>", "h.add-item"},
                {"/cp add armor <nazwa>", "h.add-armor"},
                {"/cp add block <nazwa>", "h.add-block"},
                {"/cp add food <nazwa>", "h.add-food"},
                {"/cp add sound <grupa.nazwa>", "h.add-sound"},
                {"/cp edit <nazwa> <opcja> <wartość>", "h.edit"},
                {"/cp delete <nazwa>", "h.delete"}});
        section(s, "h.sec3", new String[][]{
                {"/cp give <gracz> <nazwa> [ilość]", "h.give"},
                {"/cp rename <stara> <nowa>", "h.rename"},
                {"/cp sound play <nazwa>", "h.sound-play"}});
        section(s, "h.sec4", new String[][]{
                {"/cp language <pl/en>", "h.language"},
                {"/cp list", "h.list"},
                {"/cp info <nazwa>", "h.info"},
                {"/cp help", "h.help"}});
    }

    private void section(CommandSender s, String titleKey, String[][] rows) {
        s.sendMessage("");
        s.sendMessage(lang().get(titleKey));
        for (String[] r : rows) {
            s.sendMessage(ChatColor.YELLOW + r[0]);
            s.sendMessage(ChatColor.GRAY + "  " + lang().get(r[1]));
        }
    }

    // ---------- /cp generate ----------

    private void generate(CommandSender s) {
        if (!perm(s, "cp.generate")) {
            return;
        }
        send(s, "gen-start");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                PackGenerator.Result r = plugin.generator().generate();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    send(s, "gen-done", r.file().getName(), r.kb(), r.items(), r.sounds());
                    r.warnings().stream().limit(10).forEach(w -> send(s, "gen-warn", w));
                });
            } catch (Exception ex) {
                String msg = String.valueOf(ex.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> send(s, "gen-fail", msg));
            }
        });
    }

    // ---------- /cp texture ----------

    private File zipFile() {
        return new File(rm().assets(), "pack/txt.zip");
    }

    private String url() {
        return plugin.getConfig().getString("texture.download-url", "");
    }

    private boolean packReady(CommandSender s) {
        String u = url();
        if (u.isEmpty() || u.contains("TWOJ_LINK")) {
            send(s, "tex-no-url");
            return false;
        }
        if (!zipFile().isFile()) {
            send(s, "tex-no-file");
        }
        return true;
    }

    private void push(Player p) {
        byte[] hash = null;
        try {
            if (zipFile().isFile()) {
                hash = PackGenerator.sha1(zipFile());
            }
        } catch (IOException ignored) {
            // wysylamy bez hasha
        }
        String prompt = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("texture.prompt", ""));
        p.setResourcePack(url(), hash, prompt, plugin.getConfig().getBoolean("texture.force-pack", false));
    }

    private void texture(CommandSender s, String[] a) {
        if (!perm(s, "cp.texture")) {
            return;
        }
        if (a.length >= 2 && a[1].equalsIgnoreCase("all")) {
            if (!packReady(s)) {
                return;
            }
            int n = 0;
            for (Player p : Bukkit.getOnlinePlayers()) {
                push(p);
                n++;
            }
            send(s, "tex-sent-all", n);
        } else if (a.length >= 3 && a[1].equalsIgnoreCase("player")) {
            Player p = Bukkit.getPlayerExact(a[2]);
            if (p == null) {
                send(s, "player-offline", a[2]);
                return;
            }
            if (!packReady(s)) {
                return;
            }
            push(p);
            send(s, "tex-sent-one", p.getName());
        } else {
            send(s, "usage", "/cp texture all | /cp texture player <nick>");
        }
    }

    // ---------- zarzadzanie zasobami ----------

    private static Type typeOf(String s) {
        switch (s.toLowerCase(Locale.ROOT)) {
            case "item": return Type.ITEM;
            case "armor": return Type.ARMOR;
            case "block": return Type.BLOCK;
            case "food": return Type.FOOD;
            default: return null;
        }
    }

    private void add(CommandSender s, String[] a) throws IOException {
        if (!perm(s, "cp.manage")) {
            return;
        }
        if (a.length < 3) {
            send(s, "usage", "/cp add <item|armor|block|sound> <nazwa>");
            return;
        }
        String name = a[2].toLowerCase(Locale.ROOT);
        if (a[1].equalsIgnoreCase("sound")) {
            if (!ResourceManager.SOUND.matcher(name).matches()) {
                send(s, "bad-sound-name");
                return;
            }
            if (rm().soundExists(name)) {
                send(s, "exists", name);
                return;
            }
            rm().addSound(name);
            send(s, "created-sound", name, name.replace('.', '/'));
            return;
        }
        Type t = typeOf(a[1]);
        if (t == null) {
            send(s, "usage", "/cp add <item|armor|block|sound> <nazwa>");
            return;
        }
        if (!ResourceManager.NAME.matcher(name).matches()) {
            send(s, "bad-name");
            return;
        }
        if (rm().exists(name)) {
            send(s, "exists", name);
            return;
        }
        rm().create(t, name);
        send(s, "created", name, t.dir);
    }

    private void edit(CommandSender s, String[] a) throws IOException {
        if (!perm(s, "cp.manage")) {
            return;
        }
        if (a.length < 4) {
            send(s, "usage", "/cp edit <nazwa> <opcja> <wartość>");
            return;
        }
        String name = a[1].toLowerCase(Locale.ROOT);
        String value = String.join(" ", Arrays.copyOfRange(a, 3, a.length));
        String err = rm().edit(name, a[2], value);
        if (err == null) {
            send(s, "edited", name, a[2].toLowerCase(Locale.ROOT), value);
        } else if (err.equals("bad-option")) {
            send(s, "bad-option", ResourceManager.OPTIONS);
        } else {
            send(s, err, name);
        }
    }

    private void delete(CommandSender s, String[] a) throws IOException {
        if (!perm(s, "cp.manage")) {
            return;
        }
        if (a.length < 2) {
            send(s, "usage", "/cp delete <nazwa>");
            return;
        }
        String name = a[1].toLowerCase(Locale.ROOT);
        if (rm().delete(name)) {
            send(s, "deleted", name);
        } else {
            send(s, "not-found", name);
        }
    }

    private void rename(CommandSender s, String[] a) throws IOException {
        if (!perm(s, "cp.manage")) {
            return;
        }
        if (a.length < 3) {
            send(s, "usage", "/cp rename <stara> <nowa>");
            return;
        }
        String from = a[1].toLowerCase(Locale.ROOT);
        String to = a[2].toLowerCase(Locale.ROOT);
        String err = rm().rename(from, to);
        if (err == null) {
            send(s, "renamed", from, to);
        } else {
            send(s, err, err.equals("exists") ? to : from);
        }
    }

    // ---------- przedmioty i dzwieki ----------

    private void give(CommandSender s, String[] a) {
        if (!perm(s, "cp.give")) {
            return;
        }
        if (a.length < 3) {
            send(s, "usage", "/cp give <gracz> <nazwa> [ilość]");
            return;
        }
        Player target = Bukkit.getPlayerExact(a[1]);
        if (target == null) {
            send(s, "player-offline", a[1]);
            return;
        }
        int amount = 1;
        if (a.length >= 4) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(a[3])));
            } catch (NumberFormatException ex) {
                send(s, "bad-value");
                return;
            }
        }
        String name = a[2].toLowerCase(Locale.ROOT);
        ItemStack item = rm().build(name, amount);
        if (item == null) {
            send(s, "not-found", name);
            return;
        }
        for (ItemStack left : target.getInventory().addItem(item).values()) {
            target.getWorld().dropItem(target.getLocation(), left);
        }
        send(s, "given", target.getName(), amount, name);
    }

    private void sound(CommandSender s, String[] a) {
        if (!perm(s, "cp.sound")) {
            return;
        }
        if (a.length < 3 || !a[1].equalsIgnoreCase("play")) {
            send(s, "usage", "/cp sound play <nazwa>");
            return;
        }
        if (!(s instanceof Player)) {
            send(s, "only-player");
            return;
        }
        Player p = (Player) s;
        String name = a[2].toLowerCase(Locale.ROOT);
        if (!rm().soundExists(name)) {
            send(s, "sound-bad", name);
            return;
        }
        SoundCategory cat = rm().soundCategory(name);
        p.playSound(p.getLocation(), "cp:" + name, cat, 1.0f, 1.0f);
        send(s, "sound-play", name);
    }

    // ---------- informacje i ustawienia ----------

    private void language(CommandSender s, String[] a) {
        if (!perm(s, "cp.language")) {
            return;
        }
        if (a.length < 2) {
            send(s, "usage", "/cp language <pl/en>");
            return;
        }
        if (!Lang.valid(a[1])) {
            send(s, "lang-bad");
            return;
        }
        plugin.setLanguage(a[1]);
        send(s, "lang-set", a[1].toLowerCase(Locale.ROOT));
    }

    private void list(CommandSender s) {
        if (!perm(s, "cp.info")) {
            return;
        }
        s.sendMessage(lang().get("list-header"));
        boolean any = false;
        for (Type t : Type.values()) {
            List<String> n = rm().names(t);
            if (!n.isEmpty()) {
                any = true;
                s.sendMessage(ChatColor.YELLOW + t.dir + ChatColor.GRAY + " (" + n.size() + "): " + ChatColor.WHITE + String.join(", ", n));
            }
        }
        List<String> snd = rm().soundNames();
        if (!snd.isEmpty()) {
            any = true;
            s.sendMessage(ChatColor.YELLOW + "sound" + ChatColor.GRAY + " (" + snd.size() + "): " + ChatColor.WHITE + String.join(", ", snd));
        }
        if (!any) {
            s.sendMessage(lang().get("list-empty"));
        }
    }

    private void info(CommandSender s, String[] a) {
        if (!perm(s, "cp.info")) {
            return;
        }
        if (a.length < 2) {
            send(s, "usage", "/cp info <nazwa>");
            return;
        }
        String name = a[1].toLowerCase(Locale.ROOT);
        List<String> lines = rm().info(name);
        if (lines == null) {
            send(s, "not-found", name);
            return;
        }
        s.sendMessage(lang().get("info-header", name));
        for (String l : lines) {
            int i = l.indexOf(": ");
            s.sendMessage(ChatColor.YELLOW + l.substring(0, i) + ChatColor.GRAY + ": " + ChatColor.WHITE + l.substring(i + 2));
        }
    }

    // ---------- tab complete ----------

    private List<String> allNames() {
        List<String> r = new ArrayList<>();
        for (Type t : Type.values()) {
            r.addAll(rm().names(t));
        }
        r.addAll(rm().soundNames());
        return r;
    }

    private List<String> players() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] a) {
        List<String> options = new ArrayList<>();
        if (a.length == 1) {
            options = SUBS;
        } else {
            String sub = a[0].toLowerCase(Locale.ROOT);
            if (sub.equals("texture")) {
                if (a.length == 2) {
                    options = Arrays.asList("all", "player");
                } else if (a.length == 3 && a[1].equalsIgnoreCase("player")) {
                    options = players();
                }
            } else if (sub.equals("add")) {
                if (a.length == 2) {
                    options = Arrays.asList("item", "armor", "block", "food", "sound");
                }
            } else if (sub.equals("edit")) {
                if (a.length == 2) {
                    options = allNames();
                } else if (a.length == 3) {
                    options = Arrays.asList("name", "material", "model-data", "glow", "category", "damage", "color", "hunger", "saturation", "effects", "eat-sound", "file");
                }
            } else if (sub.equals("delete") || sub.equals("info") || sub.equals("rename")) {
                if (a.length == 2) {
                    options = allNames();
                }
            } else if (sub.equals("give")) {
                if (a.length == 2) {
                    options = players();
                } else if (a.length == 3) {
                    List<String> r = new ArrayList<>();
                    for (Type t : Type.values()) {
                        r.addAll(rm().names(t));
                    }
                    options = r;
                }
            } else if (sub.equals("sound")) {
                if (a.length == 2) {
                    options = Arrays.asList("play");
                } else if (a.length == 3) {
                    options = rm().soundNames();
                }
            } else if (sub.equals("language")) {
                if (a.length == 2) {
                    options = Arrays.asList("pl", "en");
                }
            }
        }
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(last)).collect(Collectors.toList());
    }
}
