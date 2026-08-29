package com.mrwqlf.qatest.testler;

import com.mrwqlf.qatest.Akis;
import com.mrwqlf.qatest.Cerceve;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Datapack'leri dosya seviyesinde inceler — calistirmadan, oyuna girmeden.
 *
 * Yakaladiklari:
 *  - Cagrilan ama var olmayan fonksiyonlar (sessizce hicbir sey yapmaz)
 *  - tick/load tag'inde listelenip dosyasi olmayan fonksiyonlar
 *  - Her tick calisan fonksiyonlarda KOSULSUZ particle/playsound —
 *    "elde kilic olmasa da parcacik cikiyor" hatasinin kaynagi tam olarak budur
 *  - Sinirsiz @a / @e secicileri (her tick tum oyunculari/varliklari tarar)
 *  - Bozuk veya eksik pack.mcmeta
 */
public final class DatapackTesti implements Test {

    @Override public String ad() { return "datapack"; }
    @Override public String aciklama() { return "Datapack fonksiyonlarini dosya seviyesinde tarar (kosulsuz efekt, kirik cagri)"; }

    // function ns:yol/adi
    private static final Pattern CAGRI = Pattern.compile("\\bfunction\\s+([a-z0-9_.-]+:[a-z0-9_./-]+)");
    // satirin efekt komutu ile BASLAMASI = hicbir kosul yok
    private static final Pattern KOSULSUZ_EFEKT = Pattern.compile("^\\s*(particle|playsound|summon)\\s+");
    private static final Pattern GENIS_SECICI = Pattern.compile("(as|at)\\s+@[ae](?!\\[)");

    private record Fonksiyon(String tamAd, String icerik) {}

    @Override
    public void kur(Akis a, Cerceve c) {
        a.adim("bolum", x -> x.rapor().grup("Datapack dosya analizi"));

        a.adim("tara", x -> {
            List<File> paketler = paketleriBul();
            if (paketler.isEmpty()) {
                x.rapor().uyari("Hicbir dunyada datapack bulunamadi");
                return;
            }

            for (File paket : paketler) {
                String paketAdi = paket.getParentFile().getParentFile().getName() + "/" + paket.getName();
                try {
                    analizEt(x, paketAdi, paket);
                } catch (Throwable t) {
                    x.rapor().acik(paketAdi + ": analiz sirasinda hata -> "
                            + t.getClass().getSimpleName() + ": " + t.getMessage());
                }
            }
        });
    }

    private List<File> paketleriBul() {
        List<File> hepsi = new ArrayList<>();
        for (World w : Bukkit.getWorlds()) {
            File klasor = new File(w.getWorldFolder(), "datapacks");
            File[] icerik = klasor.listFiles();
            if (icerik == null) continue;
            for (File f : icerik) if (!f.getName().startsWith(".")) hepsi.add(f);
        }
        return hepsi;
    }

    // ---------------------------------------------------------------

    private void analizEt(Cerceve x, String paketAdi, File paket) throws Exception {
        Map<String, String> dosyalar = dosyalariOku(paket);   // yol -> icerik

        if (!dosyalar.containsKey("pack.mcmeta")) {
            x.rapor().acik(paketAdi + ": pack.mcmeta yok — bu paket hic yuklenmez");
            return;
        }

        // fonksiyonlari topla: data/<ns>/function(s)/<yol>.mcfunction
        Map<String, Fonksiyon> fonksiyonlar = new LinkedHashMap<>();
        Set<String> tickFonksiyonlari = new LinkedHashSet<>();
        List<String> tagDosyalari = new ArrayList<>();

        Pattern yolKalibi = Pattern.compile("^data/([a-z0-9_.-]+)/functions?/(.+)\\.mcfunction$");

        for (var giris : dosyalar.entrySet()) {
            Matcher m = yolKalibi.matcher(giris.getKey().replace('\\', '/'));
            if (m.matches()) {
                String tamAd = m.group(1) + ":" + m.group(2);
                fonksiyonlar.put(tamAd, new Fonksiyon(tamAd, giris.getValue()));
            } else if (giris.getKey().replace('\\', '/').matches("^data/minecraft/tags/functions?/(tick|load)\\.json$")) {
                tagDosyalari.add(giris.getKey());
                for (String deger : tagDegerleri(giris.getValue())) {
                    if (giris.getKey().contains("tick")) tickFonksiyonlari.add(deger);
                    // load fonksiyonlari da varlik kontrolune girer
                    if (!fonksiyonlar.containsKey(deger) && !deger.startsWith("#")) {
                        x.rapor().acik(paketAdi + ": " + (giris.getKey().contains("tick") ? "tick" : "load")
                                + " tag'inde \"" + deger + "\" listeli ama boyle bir fonksiyon dosyasi YOK");
                    }
                }
            }
        }

        if (fonksiyonlar.isEmpty()) {
            x.rapor().uyari(paketAdi + ": hic .mcfunction dosyasi yok (sadece tarif/loot paketi olabilir)");
        } else {
            x.rapor().ok(paketAdi + ": " + fonksiyonlar.size() + " fonksiyon, "
                    + tickFonksiyonlari.size() + " tanesi her tick calisiyor");
        }

        if (tagDosyalari.isEmpty() && !fonksiyonlar.isEmpty()) {
            x.rapor().uyari(paketAdi + ": tick/load tag dosyasi yok — fonksiyonlar sadece elle cagrilabiliyor");
        }

        // --- kirik fonksiyon cagrilari ---
        int kirik = 0;
        for (Fonksiyon f : fonksiyonlar.values()) {
            for (String satir : f.icerik().split("\n")) {
                String s = satir.strip();
                if (s.isEmpty() || s.startsWith("#")) continue;
                Matcher m = CAGRI.matcher(s);
                while (m.find()) {
                    String hedef = m.group(1);
                    if (fonksiyonlar.containsKey(hedef)) continue;
                    if (hedef.startsWith("minecraft:")) continue;   // vanilla olabilir
                    kirik++;
                    x.rapor().acik(paketAdi + " / " + f.tamAd() + ": var olmayan fonksiyon cagriliyor -> "
                            + hedef + " (sessizce hicbir sey yapmaz)");
                }
            }
        }
        if (kirik == 0 && !fonksiyonlar.isEmpty())
            x.rapor().ok(paketAdi + ": tum fonksiyon cagrilari gecerli");

        // --- her tick calisan fonksiyonlarda kosulsuz efekt ---
        Set<String> gezildi = new HashSet<>();
        Deque<String> kuyruk = new ArrayDeque<>(tickFonksiyonlari);
        int kosulsuz = 0, genisSecici = 0;

        while (!kuyruk.isEmpty()) {
            String ad = kuyruk.poll();
            if (!gezildi.add(ad)) continue;
            Fonksiyon f = fonksiyonlar.get(ad);
            if (f == null) continue;

            int satirNo = 0;
            for (String satir : f.icerik().split("\n")) {
                satirNo++;
                String s = satir.strip();
                if (s.isEmpty() || s.startsWith("#")) continue;

                // cagrilan alt fonksiyonlari da tick zincirine kat
                Matcher cm = CAGRI.matcher(s);
                while (cm.find()) kuyruk.add(cm.group(1));

                if (KOSULSUZ_EFEKT.matcher(s).find()) {
                    kosulsuz++;
                    x.rapor().acik(String.format(
                            "%s / %s satir %d: her tick KOSULSUZ calisan efekt -> \"%s\". "
                            + "Bu komut hicbir sarta bagli degil; ozel esya elde olmasa bile calisir. "
                            + "Basina \"execute as @a[nbt={SelectedItem:{...}}] at @s run\" gibi bir kosul koy.",
                            paketAdi, f.tamAd(), satirNo, kisalt(s)));
                }

                Matcher gm = GENIS_SECICI.matcher(s);
                if (gm.find()) {
                    genisSecici++;
                    x.rapor().uyari(String.format(
                            "%s / %s satir %d: her tick sinirsiz secici -> \"%s\". "
                            + "@a veya @e koseli parantezsiz kullaniliyor; buyuk sunucuda TPS dusurur "
                            + "ve istenmeyen hedeflere de uygulanir.",
                            paketAdi, f.tamAd(), satirNo, kisalt(s)));
                }
            }
        }

        if (!tickFonksiyonlari.isEmpty() && kosulsuz == 0)
            x.rapor().ok(paketAdi + ": tick zincirinde kosulsuz efekt komutu yok");
        if (!tickFonksiyonlari.isEmpty() && genisSecici == 0)
            x.rapor().ok(paketAdi + ": tick zincirinde sinirsiz secici yok");
    }

    private static String kisalt(String s) {
        return s.length() <= 70 ? s : s.substring(0, 67) + "...";
    }

    /** tag json'undan values listesini cikarir (basit ayristirma, gson'suz da calisir). */
    private static List<String> tagDegerleri(String json) {
        List<String> liste = new ArrayList<>();
        Matcher m = Pattern.compile("\"([a-z0-9_.-]+:[a-z0-9_./-]+)\"").matcher(json);
        while (m.find()) liste.add(m.group(1));
        return liste;
    }

    /** Paketi klasor ya da zip olarak okur: yol -> icerik. */
    private static Map<String, String> dosyalariOku(File paket) throws Exception {
        Map<String, String> harita = new LinkedHashMap<>();

        if (paket.isDirectory()) {
            var kok = paket.toPath();
            try (var akis = Files.walk(kok)) {
                for (var p : akis.toList()) {
                    if (!Files.isRegularFile(p)) continue;
                    String ad = kok.relativize(p).toString().replace('\\', '/');
                    if (!(ad.endsWith(".mcfunction") || ad.endsWith(".json") || ad.equals("pack.mcmeta"))) continue;
                    try { harita.put(ad, Files.readString(p, StandardCharsets.UTF_8)); }
                    catch (Exception yoksay) { /* ikili dosya */ }
                }
            }
            return harita;
        }

        if (paket.getName().toLowerCase(Locale.ROOT).endsWith(".zip")) {
            try (ZipFile zip = new ZipFile(paket)) {
                var e = zip.entries();
                while (e.hasMoreElements()) {
                    ZipEntry z = e.nextElement();
                    if (z.isDirectory()) continue;
                    String ad = z.getName().replace('\\', '/');
                    if (!(ad.endsWith(".mcfunction") || ad.endsWith(".json") || ad.equals("pack.mcmeta"))) continue;
                    try (var r = new InputStreamReader(zip.getInputStream(z), StandardCharsets.UTF_8)) {
                        StringBuilder sb = new StringBuilder();
                        char[] buf = new char[8192];
                        int n;
                        while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
                        harita.put(ad, sb.toString());
                    }
                }
            }
        }
        return harita;
    }
}
