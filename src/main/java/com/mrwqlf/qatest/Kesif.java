package com.mrwqlf.qatest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Sunucuda ne varsa kendisi bulur — elle liste tutmaya gerek kalmasin diye.
 * Silah ve sinif kodlari SinifSistemi'nin enum'larindan yansimayla okunur,
 * datapack'ler dunya klasorlerinden taranir.
 */
public final class Kesif {

    private Kesif() {}

    // ---------------- silahlar ----------------

    /**
     * SinifSistemi'nin Silah enum'undaki tum kodlari doner.
     * Eklentiye yeni silah eklendiginde test kendiliginden onu da kapsar.
     */
    public static List<String> silahKodlari() {
        List<String> kodlar = new ArrayList<>();
        Plugin ss = Bukkit.getPluginManager().getPlugin("SinifSistemi");
        if (ss == null) return kodlar;

        try {
            Class<?> silah = Class.forName("com.sinifsistemi.silah.Silah", false, ss.getClass().getClassLoader());
            var kodMetodu = silah.getMethod("kod");
            for (Object sabit : silah.getEnumConstants()) {
                Object k = kodMetodu.invoke(sabit);
                if (k != null) kodlar.add(k.toString());
            }
        } catch (Throwable t) {
            // enum bulunamadi — konfigurasyona dus
        }
        return kodlar;
    }

    /** Silah enum'unun gerekli sinif eslesmesi: kod -> sinif kodu. */
    public static Map<String, String> silahSinifi() {
        Map<String, String> harita = new LinkedHashMap<>();
        Plugin ss = Bukkit.getPluginManager().getPlugin("SinifSistemi");
        if (ss == null) return harita;
        try {
            Class<?> silah = Class.forName("com.sinifsistemi.silah.Silah", false, ss.getClass().getClassLoader());
            var kodM = silah.getMethod("kod");
            var sinifM = silah.getMethod("gerekliSinif");
            for (Object sabit : silah.getEnumConstants()) {
                Object k = kodM.invoke(sabit);
                Object s = sinifM.invoke(sabit);
                if (k == null || s == null) continue;
                Object sk = s.getClass().getMethod("kod").invoke(s);
                harita.put(k.toString(), sk == null ? null : sk.toString());
            }
        } catch (Throwable ignored) {}
        return harita;
    }

    // ---------------- siniflar ----------------

    public static List<String> sinifKodlari() {
        List<String> kodlar = new ArrayList<>();
        Plugin ss = Bukkit.getPluginManager().getPlugin("SinifSistemi");
        if (ss == null) return kodlar;
        try {
            Class<?> sinif = Class.forName("com.sinifsistemi.Sinif", false, ss.getClass().getClassLoader());
            var kodM = sinif.getMethod("kod");
            for (Object sabit : sinif.getEnumConstants()) {
                Object k = kodM.invoke(sabit);
                if (k != null) kodlar.add(k.toString());
            }
        } catch (Throwable ignored) {}
        return kodlar;
    }

    // ---------------- datapack ----------------

    public record Datapack(String dunya, String ad, Integer format, Integer minFormat, Integer maxFormat,
                           boolean mcmetaVar, String hata) {}

    /** Tum dunyalarin datapacks/ klasorlerini tarar. */
    public static List<Datapack> datapackler() {
        List<Datapack> liste = new ArrayList<>();
        for (World w : Bukkit.getWorlds()) {
            File klasor = new File(w.getWorldFolder(), "datapacks");
            if (!klasor.isDirectory()) continue;
            File[] icerik = klasor.listFiles();
            if (icerik == null) continue;

            for (File p : icerik) {
                if (p.getName().startsWith(".")) continue;
                try {
                    String ham = mcmetaOku(p);
                    if (ham == null) {
                        liste.add(new Datapack(w.getName(), p.getName(), null, null, null, false, null));
                        continue;
                    }
                    JsonObject kok = JsonParser.parseString(ham).getAsJsonObject();
                    JsonObject pack = kok.getAsJsonObject("pack");
                    Integer format = null, min = null, max = null;
                    if (pack != null) {
                        if (pack.has("pack_format")) format = pack.get("pack_format").getAsInt();
                        JsonElement destek = pack.get("supported_formats");
                        if (destek != null) {
                            if (destek.isJsonObject()) {
                                JsonObject d = destek.getAsJsonObject();
                                if (d.has("min_inclusive")) min = d.get("min_inclusive").getAsInt();
                                if (d.has("max_inclusive")) max = d.get("max_inclusive").getAsInt();
                            } else if (destek.isJsonArray() && destek.getAsJsonArray().size() == 2) {
                                min = destek.getAsJsonArray().get(0).getAsInt();
                                max = destek.getAsJsonArray().get(1).getAsInt();
                            }
                        }
                    }
                    liste.add(new Datapack(w.getName(), p.getName(), format, min, max, true, null));
                } catch (Throwable t) {
                    liste.add(new Datapack(w.getName(), p.getName(), null, null, null, true,
                            t.getClass().getSimpleName() + ": " + t.getMessage()));
                }
            }
        }
        return liste;
    }

    private static String mcmetaOku(File paket) throws Exception {
        if (paket.isDirectory()) {
            File m = new File(paket, "pack.mcmeta");
            return m.isFile() ? Files.readString(m.toPath(), StandardCharsets.UTF_8) : null;
        }
        if (paket.getName().toLowerCase(Locale.ROOT).endsWith(".zip")) {
            try (ZipFile zip = new ZipFile(paket)) {
                ZipEntry e = zip.getEntry("pack.mcmeta");
                if (e == null) return null;
                try (var r = new InputStreamReader(zip.getInputStream(e), StandardCharsets.UTF_8)) {
                    StringBuilder sb = new StringBuilder();
                    char[] buf = new char[4096];
                    int n;
                    while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
                    return sb.toString();
                }
            }
        }
        return null;
    }

    /** Sunucunun bekledigi datapack format numarasi (surumden tahmin). */
    public static Integer sunucuDatapackFormati() {
        // Paper surum dizesinden kesin format cikaramayiz; null donunce
        // format kontrolu "uyari" seviyesinde kalir.
        return null;
    }
}
