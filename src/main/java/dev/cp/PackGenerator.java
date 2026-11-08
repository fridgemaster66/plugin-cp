package dev.cp;

import dev.cp.ResourceManager.Type;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class PackGenerator {

    public record Result(File file, int items, int sounds, List<String> warnings, long kb) {
    }

    private record Ov(int cmd, String model) {
    }

    private final CPPlugin plugin;
    private final ResourceManager rm;

    public PackGenerator(CPPlugin plugin, ResourceManager rm) {
        this.plugin = plugin;
        this.rm = rm;
    }

    public static byte[] sha1(File f) throws IOException {
        try {
            return MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(f.toPath()));
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    public Result generate() throws IOException {
        FileConfiguration cfg = plugin.getConfig();
        File assets = rm.assets();
        File zip = new File(assets, "pack/txt.zip");
        if (zip.isFile() && !cfg.getBoolean("settings.overwrite-old-pack", true)) {
            throw new IOException("txt.zip already exists (settings.overwrite-old-pack: false)");
        }
        boolean check = cfg.getBoolean("settings.check-missing-textures", true);

        Map<String, byte[]> out = new TreeMap<>();
        List<String> warn = new ArrayList<>();

        out.put("pack.mcmeta", utf8("{\"pack\":{\"pack_format\":" + cfg.getInt("texture.pack-format", 34)
                + ",\"description\":\"" + esc(cfg.getString("texture.description", "CP - CustomPack")) + "\"}}"));

        // 1. pliki wgrane przez uzytkownika: tekstury, modele, dzwieki
        for (Type t : Type.values()) {
            copyTree(new File(assets, "textures/" + t.dir), "assets/cp/textures/" + t.dir, out);
            copyTree(new File(assets, "models/" + t.dir), "assets/cp/models/" + t.dir, out);
        }
        copyTree(new File(assets, "sounds"), "assets/cp/sounds", out);

        // 2. itemy, zbroje, bloki -> modele + nadpisania custom_model_data
        Map<Material, List<Ov>> overrides = new TreeMap<>();
        Map<Integer, String> blockModels = new TreeMap<>();
        int items = 0;
        for (Type t : Type.values()) {
            for (String name : rm.names(t)) {
                YamlConfiguration c = rm.load(t, name);
                Material m = t == Type.BLOCK
                        ? Material.NOTE_BLOCK
                        : Material.matchMaterial(c.getString("material", t.defMaterial));
                if (m == null || !m.isItem()) {
                    warn.add(t.dir + "/" + name + ": zły material / invalid material");
                    continue;
                }
                int cmd = c.getInt("custom-model-data", 0);
                if (cmd < 1) {
                    warn.add(t.dir + "/" + name + ": brak custom-model-data / missing custom-model-data");
                    continue;
                }
                String modelKey = "assets/cp/models/" + t.dir + "/" + name + ".json";
                if (!out.containsKey(modelKey)) {
                    boolean hasTexture = out.containsKey("assets/cp/textures/" + t.dir + "/" + name + ".png");
                    if (!hasTexture && check) {
                        warn.add("brak tekstury / missing texture: assets/textures/" + t.dir + "/" + name + ".png");
                        continue;
                    }
                    String parent = t == Type.BLOCK
                            ? "minecraft:block/cube_all"
                            : (isHandheld(m) ? "minecraft:item/handheld" : "minecraft:item/generated");
                    String texKey = t == Type.BLOCK ? "all" : "layer0";
                    out.put(modelKey, utf8("{\"parent\":\"" + parent + "\",\"textures\":{\"" + texKey
                            + "\":\"cp:" + t.dir + "/" + name + "\"}}"));
                }
                if (t == Type.BLOCK) {
                    int idx = c.getInt("note-block", 0);
                    if (idx < 1 || idx >= BlockStates.capacity()) {
                        warn.add("block/" + name + ": zły note-block / invalid note-block (" + idx + ")");
                        continue;
                    }
                    if (blockModels.containsKey(idx)) {
                        warn.add("block/" + name + ": powtórzony note-block / duplicate note-block (" + idx + ")");
                        continue;
                    }
                    blockModels.put(idx, "cp:block/" + name);
                }
                overrides.computeIfAbsent(m, k -> new ArrayList<>()).add(new Ov(cmd, "cp:" + t.dir + "/" + name));
                items++;
            }
        }

        for (Map.Entry<Material, List<Ov>> e : overrides.entrySet()) {
            Material m = e.getKey();
            String id = m.name().toLowerCase(Locale.ROOT);
            StringBuilder sb = new StringBuilder("{");
            if (m.isBlock() && m.isOccluding()) {
                sb.append("\"parent\":\"minecraft:block/").append(id).append("\"");
            } else {
                sb.append("\"parent\":\"").append(isHandheld(m) ? "minecraft:item/handheld" : "minecraft:item/generated")
                        .append("\",\"textures\":{\"layer0\":\"minecraft:item/").append(id).append("\"");
                if (m.name().startsWith("LEATHER_") && !m.name().equals("LEATHER") && !m.name().equals("LEATHER_HORSE_ARMOR")) {
                    sb.append(",\"layer1\":\"minecraft:item/").append(id).append("_overlay\"");
                }
                sb.append("}");
            }
            List<Ov> list = e.getValue();
            list.sort(Comparator.comparingInt(Ov::cmd));
            sb.append(",\"overrides\":[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    sb.append(",");
                }
                sb.append("{\"predicate\":{\"custom_model_data\":").append(list.get(i).cmd())
                        .append("},\"model\":\"").append(esc(list.get(i).model())).append("\"}");
            }
            sb.append("]}");
            out.put("assets/minecraft/models/item/" + id + ".json", utf8(sb.toString()));
        }

        // 2b. wlasne bloki -> blockstate note_block
        if (!blockModels.isEmpty()) {
            List<String> inst = BlockStates.instrumentNames();
            List<String> variants = new ArrayList<>();
            for (int ii = 0; ii < inst.size(); ii++) {
                for (int n = 0; n < BlockStates.NOTES; n++) {
                    String model = blockModels.getOrDefault(ii * BlockStates.NOTES + n, "minecraft:block/note_block");
                    for (String powered : new String[]{"false", "true"}) {
                        variants.add("\"instrument=" + inst.get(ii) + ",note=" + n + ",powered=" + powered
                                + "\":{\"model\":\"" + esc(model) + "\"}");
                    }
                }
            }
            out.put("assets/minecraft/blockstates/note_block.json",
                    utf8("{\"variants\":{" + String.join(",", variants) + "}}"));
        }

        // 3. dzwieki -> sounds.json
        List<String> soundEntries = new ArrayList<>();
        int sounds = 0;
        for (String full : rm.soundNames()) {
            String[] p = full.split("\\.", 2);
            String file = YamlConfiguration.loadConfiguration(rm.soundFile(p[0])).getString("sounds." + p[1] + ".file");
            if (file == null || file.isEmpty()) {
                continue;
            }
            if (!out.containsKey("assets/cp/sounds/" + file + ".ogg")) {
                warn.add("brak pliku dźwięku / missing sound file: assets/sounds/" + file + ".ogg");
                if (check) {
                    continue;
                }
            }
            soundEntries.add("\"" + esc(full) + "\":{\"sounds\":[\"cp:" + esc(file) + "\"]}");
            sounds++;
        }
        if (!soundEntries.isEmpty()) {
            out.put("assets/cp/sounds.json", utf8("{" + String.join(",", soundEntries) + "}"));
        }

        // 4. ZIP
        zip.getParentFile().mkdirs();
        try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(zip))) {
            for (Map.Entry<String, byte[]> e : out.entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue());
                z.closeEntry();
            }
        }
        return new Result(zip, items, sounds, warn, Math.max(1, zip.length() / 1024));
    }

    private static boolean isHandheld(Material m) {
        String n = m.name();
        return n.endsWith("_SWORD") || n.endsWith("_PICKAXE") || n.endsWith("_AXE")
                || n.endsWith("_SHOVEL") || n.endsWith("_HOE") || n.equals("STICK");
    }

    private static void copyTree(File srcDir, String prefix, Map<String, byte[]> out) throws IOException {
        if (!srcDir.isDirectory()) {
            return;
        }
        Path root = srcDir.toPath();
        List<Path> files;
        try (Stream<Path> s = Files.walk(root)) {
            files = s.filter(Files::isRegularFile).collect(Collectors.toList());
        }
        for (Path p : files) {
            String rel = root.relativize(p).toString().replace(File.separatorChar, '/');
            out.put(prefix + "/" + rel, Files.readAllBytes(p));
        }
    }

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
