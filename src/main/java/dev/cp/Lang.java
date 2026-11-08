package dev.cp;

import org.bukkit.ChatColor;

import java.util.HashMap;
import java.util.Map;

public final class Lang {

    private static final Map<String, String> PL = new HashMap<>();
    private static final Map<String, String> EN = new HashMap<>();

    private final Map<String, String> m;

    public Lang(String code) {
        this.m = "en".equalsIgnoreCase(code) ? EN : PL;
    }

    public static boolean valid(String code) {
        return "pl".equalsIgnoreCase(code) || "en".equalsIgnoreCase(code);
    }

    public String get(String key, Object... args) {
        String s = m.getOrDefault(key, key);
        for (int i = 0; i < args.length; i++) {
            s = s.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private static void t(String key, String pl, String en) {
        PL.put(key, pl);
        EN.put(key, en);
    }

    static {
        t("prefix", "&6[CP] &r", "&6[CP] &r");
        t("no-perm", "&cNie masz uprawnień.", "&cYou don't have permission.");
        t("usage", "&cUżycie: &e{0}", "&cUsage: &e{0}");
        t("unknown", "&cNieznana komenda. Wpisz &e/cp help&c.", "&cUnknown command. Type &e/cp help&c.");
        t("bad-name", "&cNieprawidłowa nazwa (dozwolone: a-z, 0-9, _).", "&cInvalid name (allowed: a-z, 0-9, _).");
        t("bad-sound-name", "&cNazwa dźwięku musi mieć format &egrupa.nazwa&c (a-z, 0-9, _).", "&cSound name must look like &egroup.name&c (a-z, 0-9, _).");
        t("exists", "&cZasób &e{0}&c już istnieje.", "&cResource &e{0}&c already exists.");
        t("not-found", "&cNie znaleziono zasobu &e{0}&c.", "&cResource &e{0}&c not found.");
        t("created", "&aUtworzono &e{0}&a ({1}). Wgraj teksturę: &fassets/textures/{1}/{0}.png",
                "&aCreated &e{0}&a ({1}). Put the texture in: &fassets/textures/{1}/{0}.png");
        t("created-sound", "&aDodano dźwięk &e{0}&a. Wgraj plik: &fassets/sounds/{1}.ogg",
                "&aSound &e{0}&a added. Put the file in: &fassets/sounds/{1}.ogg");
        t("deleted", "&aUsunięto konfigurację &e{0}&a (pliki tekstur i modeli zostały zachowane).",
                "&aDeleted config of &e{0}&a (texture and model files were kept).");
        t("renamed", "&aZmieniono nazwę &e{0}&a na &e{1}&a.", "&aRenamed &e{0}&a to &e{1}&a.");
        t("edited", "&aUstawiono &e{1}&a = &f{2}&a dla &e{0}&a.", "&aSet &e{1}&a = &f{2}&a for &e{0}&a.");
        t("bad-option", "&cNieznana opcja. Dostępne: &e{0}", "&cUnknown option. Available: &e{0}");
        t("bad-value", "&cNieprawidłowa wartość dla tej opcji.", "&cInvalid value for this option.");
        t("player-offline", "&cGracz &e{0}&c nie jest online.", "&cPlayer &e{0}&c is not online.");
        t("given", "&aDano &e{1}x {2}&a graczowi &e{0}&a.", "&aGave &e{1}x {2}&a to &e{0}&a.");
        t("lang-set", "&aJęzyk zmieniony na &e{0}&a.", "&aLanguage changed to &e{0}&a.");
        t("lang-bad", "&cDostępne języki: &epl&c, &een&c.", "&cAvailable languages: &epl&c, &een&c.");
        t("gen-start", "&7Generowanie TexturePacka...", "&7Generating TexturePack...");
        t("gen-done", "&aWygenerowano &e{0}&a ({1} KB). Itemy/zbroje/bloki: &e{2}&a, dźwięki: &e{3}&a.",
                "&aGenerated &e{0}&a ({1} KB). Items/armor/blocks: &e{2}&a, sounds: &e{3}&a.");
        t("gen-warn", "&e! {0}", "&e! {0}");
        t("gen-fail", "&cBłąd generowania: {0}", "&cGeneration failed: {0}");
        t("tex-sent-all", "&aWysłano TexturePack do &e{0}&a graczy.", "&aTexturePack sent to &e{0}&a players.");
        t("tex-sent-one", "&aWysłano TexturePack graczowi &e{0}&a.", "&aTexturePack sent to &e{0}&a.");
        t("tex-no-url", "&cUstaw prawdziwy link w &etexture.download-url&c (config.yml).",
                "&cSet a real link in &etexture.download-url&c (config.yml).");
        t("tex-no-file", "&eBrak lokalnego txt.zip - wysyłam bez sumy SHA-1 (użyj /cp generate).",
                "&eNo local txt.zip - sending without SHA-1 (run /cp generate).");
        t("list-header", "&6Zasoby CP:", "&6CP resources:");
        t("list-empty", "&7Brak zasobów.", "&7No resources.");
        t("info-header", "&6Informacje o &e{0}&6:", "&6Info about &e{0}&6:");
        t("sound-play", "&aOdtwarzam &e{0}&a.", "&aPlaying &e{0}&a.");
        t("sound-bad", "&cNie ma dźwięku &e{0}&c.", "&cSound &e{0}&c does not exist.");
        t("only-player", "&cTa komenda jest tylko dla graczy.", "&cThis command is for players only.");
        t("io-error", "&cBłąd pliku: {0}", "&cFile error: {0}");

        t("h.title", "&6&lCP - CustomPack &7- lista dostępnych komend", "&6&lCP - CustomPack &7- available commands");
        t("h.sec1", "&6&lGENEROWANIE TEXTUREPACKA", "&6&lTEXTUREPACK GENERATION");
        t("h.sec2", "&6&lZARZĄDZANIE ZASOBAMI", "&6&lRESOURCE MANAGEMENT");
        t("h.sec3", "&6&lPRZEDMIOTY I DŹWIĘKI", "&6&lITEMS AND SOUNDS");
        t("h.sec4", "&6&lINFORMACJE I USTAWIENIA", "&6&lINFO AND SETTINGS");
        t("h.generate", "Generuje kompletny TexturePack (itemy, zbroje, bloki, dźwięki). Zapisuje ZIP w assets/pack/.",
                "Generates a full TexturePack (items, armor, blocks, sounds). Saves the ZIP in assets/pack/.");
        t("h.texture-all", "Wysyła TexturePack wszystkim graczom online.", "Sends the TexturePack to all online players.");
        t("h.texture-player", "Wysyła TexturePack wybranemu graczowi.", "Sends the TexturePack to the chosen player.");
        t("h.add-item", "Tworzy nowy przedmiot i jego konfigurację.", "Creates a new item and its config.");
        t("h.add-armor", "Tworzy nową część zbroi.", "Creates a new armor piece.");
        t("h.add-block", "Tworzy nowy własny blok (stawiany jak prawdziwy blok, nie podmienia żadnego vanillowego).",
                "Creates a new custom block (placeable, does not replace any vanilla block).");
        t("h.add-food", "Tworzy nowe jedzenie (głód, nasycenie, efekty i własny dźwięk jedzenia).",
                "Creates new food (hunger, saturation, effects and a custom eating sound).");
        t("h.add-sound", "Dodaje konfigurację nowego dźwięku (format: grupa.nazwa).", "Adds a new sound config (format: group.name).");
        t("h.edit", "Edytuje zasób: name, material, model-data, glow, category, damage, color; jedzenie: hunger, saturation, effects (np. SPEED:30:1), eat-sound; dźwięki: file, category.",
                "Edits a resource: name, material, model-data, glow, category, damage, color; food: hunger, saturation, effects (e.g. SPEED:30:1), eat-sound; sounds: file, category.");
        t("h.delete", "Usuwa wybrany zasób i jego konfigurację.", "Deletes the chosen resource and its config.");
        t("h.give", "Daje graczowi wybrany przedmiot CP.", "Gives the player a CP item.");
        t("h.rename", "Zmienia nazwę zasobu, aktualizując jego pliki i odwołania.", "Renames a resource, updating its files and references.");
        t("h.sound-play", "Odtwarza wybrany dźwięk CP.", "Plays the chosen CP sound.");
        t("h.language", "Zmienia język komunikatów pluginu.", "Changes the plugin's message language.");
        t("h.list", "Wyświetla listę utworzonych zasobów.", "Lists created resources.");
        t("h.info", "Wyświetla informacje o wybranym zasobie.", "Shows info about the chosen resource.");
        t("h.help", "Wyświetla wszystkie komendy i ich opisy.", "Shows all commands and their descriptions.");
    }
}
