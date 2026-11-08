package dev.cp;

import org.bukkit.Bukkit;
import org.bukkit.Instrument;
import org.bukkit.Material;
import org.bukkit.Note;
import org.bukkit.block.data.type.NoteBlock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mapowanie: numer bloku CP -> stan note blocka (instrument + nuta).
 * Indeks 0 (harp, nuta 0) zostaje zwykłym note blockiem z vanilli.
 */
public final class BlockStates {

    public static final int NOTES = 25;

    private static final List<Instrument> INSTRUMENTS = new ArrayList<>();
    private static final List<String> NAMES = new ArrayList<>();
    private static final Pattern P = Pattern.compile("instrument=([a-z_]+)");

    private BlockStates() {
    }

    /** Wywołaj na głównym wątku przy starcie pluginu. */
    public static synchronized void init() {
        if (!INSTRUMENTS.isEmpty()) {
            return;
        }
        NoteBlock nb = (NoteBlock) Bukkit.createBlockData(Material.NOTE_BLOCK);
        for (Instrument i : Instrument.values()) {
            try {
                nb.setInstrument(i);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            Matcher m = P.matcher(nb.getAsString());
            if (m.find() && !NAMES.contains(m.group(1))) {
                INSTRUMENTS.add(i);
                NAMES.add(m.group(1));
            }
        }
    }

    public static int capacity() {
        return NAMES.size() * NOTES;
    }

    public static List<String> instrumentNames() {
        return Collections.unmodifiableList(NAMES);
    }

    public static NoteBlock dataFor(int index) {
        NoteBlock nb = (NoteBlock) Bukkit.createBlockData(Material.NOTE_BLOCK);
        int ii = index / NOTES;
        if (index >= 0 && ii < INSTRUMENTS.size()) {
            nb.setInstrument(INSTRUMENTS.get(ii));
            nb.setNote(new Note(index % NOTES));
        }
        return nb;
    }

    /** Zwraca indeks stanu albo -1, gdy instrument nie jest na liście. */
    public static int indexOf(NoteBlock nb) {
        int ii = INSTRUMENTS.indexOf(nb.getInstrument());
        if (ii < 0) {
            return -1;
        }
        return ii * NOTES + nb.getNote().getId();
    }
}
