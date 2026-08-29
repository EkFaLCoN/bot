package com.mrwqlf.qatest.testler;

import com.mrwqlf.qatest.Akis;
import com.mrwqlf.qatest.Cerceve;
import com.mrwqlf.qatest.Kesif;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Testlerin ilk adimi: sunucunun kendisi saglam mi.
 * Eklentiler acilmis mi, datapack'ler yuklenmis mi, dunyalar erisilebilir mi.
 * Bunlar bozuksa sonraki testlerin sonuclari zaten anlamsiz olur.
 */
public final class SunucuTesti implements Test {

    @Override public String ad() { return "sunucu"; }
    @Override public String aciklama() { return "Eklentiler, datapack'ler ve dunyalar saglam mi"; }

    @Override
    public void kur(Akis a, Cerceve c) {

        // ---------------- eklentiler ----------------
        a.adim("bolum eklenti", x -> x.rapor().grup("Eklentiler"));
        a.adim("eklenti tara", x -> {
            List<String> kapali = new ArrayList<>();
            int acik = 0;
            for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
                if (p.isEnabled()) acik++;
                else kapali.add(p.getName() + " v" + p.getPluginMeta().getVersion());
            }
            if (!kapali.isEmpty())
                x.rapor().acik("Yuklu ama ACILMAMIS eklenti var: " + String.join(", ", kapali)
                        + " — konsoldaki acilis hatalarina bak");
            else
                x.rapor().ok(acik + " eklentinin hepsi acik");

            // senin kendi eklentilerin duruyor mu
            for (String gerekli : List.of("SinifSistemi", "Multiverse-Core")) {
                Plugin p = Bukkit.getPluginManager().getPlugin(gerekli);
                if (p == null)      x.rapor().uyari(gerekli + " yuklu degil");
                else if (!p.isEnabled()) x.rapor().acik(gerekli + " yuklu ama acilmamis");
                else                x.rapor().ok(gerekli + " calisiyor (v" + p.getPluginMeta().getVersion() + ")");
            }
        });

        // ---------------- datapack ----------------
        a.adim("bolum datapack", x -> x.rapor().grup("Datapack'ler"));
        a.adim("datapack tara", x -> {
            var paketler = Kesif.datapackler();
            if (paketler.isEmpty()) {
                x.rapor().uyari("Hicbir dunyanin datapacks/ klasorunde paket bulunamadi");
                return;
            }
            for (var d : paketler) {
                String etiket = d.dunya() + "/" + d.ad();

                if (!d.mcmetaVar()) {
                    x.rapor().acik(etiket + ": pack.mcmeta YOK — bu paket sunucu tarafindan hic yuklenmez");
                    continue;
                }
                if (d.hata() != null) {
                    x.rapor().acik(etiket + ": pack.mcmeta okunamadi -> " + d.hata()
                            + " (bozuk JSON ise paket sessizce devre disi kalir)");
                    continue;
                }
                if (d.format() == null && d.minFormat() == null) {
                    x.rapor().acik(etiket + ": pack.mcmeta icinde pack_format yok — paket yuklenmez");
                    continue;
                }

                StringBuilder s = new StringBuilder(etiket + ": format ");
                if (d.format() != null) s.append(d.format());
                if (d.minFormat() != null || d.maxFormat() != null)
                    s.append(" (destek ").append(d.minFormat()).append("–").append(d.maxFormat()).append(")");

                if (d.minFormat() == null && d.maxFormat() == null)
                    x.rapor().uyari(s + " — supported_formats yok, sunucu surumu degisince paket aniden devre disi kalabilir");
                else
                    x.rapor().ok(s.toString());
            }
        });

        // datapack fonksiyonlari gercekten kayitli mi
        a.adim("fonksiyon kontrolu", x -> {
            // Kayitli olmayan bir fonksiyonu cagirmak "Unknown function" verir ve
            // bu, datapack'in yuklenmedigini gosteren en net sinyaldir.
            boolean sonuc = x.komut("function minecraft:qatest_olmayan_fonksiyon");
            if (sonuc)
                x.rapor().uyari("Var olmayan bir fonksiyon cagrisi basarili dondu — fonksiyon denetimi guvenilir degil");
            else
                x.rapor().ok("Fonksiyon komutu calisiyor (var olmayan fonksiyon reddedildi)");
        });

        // ---------------- dunyalar ----------------
        a.adim("bolum dunya", x -> x.rapor().grup("Dunyalar / haritalar"));
        a.adim("dunya tara", x -> {
            var dunyalar = Bukkit.getWorlds();
            x.rapor().ok("Yuklu dunya sayisi: " + dunyalar.size() + " — "
                    + dunyalar.stream().map(World::getName).toList());

            for (World w : dunyalar) {
                Location spawn = w.getSpawnLocation();

                // spawn noktasi guvenli mi (havada veya lav icinde mi)
                Material altta = spawn.clone().add(0, -1, 0).getBlock().getType();
                Material icinde = spawn.getBlock().getType();
                if (altta == Material.AIR)
                    x.rapor().uyari(w.getName() + ": spawn noktasinin altinda blok yok, oyuncu dusuyor");
                else if (icinde == Material.LAVA || altta == Material.LAVA)
                    x.rapor().acik(w.getName() + ": spawn noktasi LAV icinde — giren oyuncu aninda yanar");
                else
                    x.rapor().ok(w.getName() + ": spawn guvenli (" + altta + " uzerinde)");

                // dunya sinirlari makul mu
                double sinir = w.getWorldBorder().getSize();
                if (sinir < 100)
                    x.rapor().acik(w.getName() + ": dunya siniri sadece " + (int) sinir + " blok — yanlislikla kucultulmus olabilir");

                // otomatik kayit acik mi
                if (!w.isAutoSave())
                    x.rapor().acik(w.getName() + ": otomatik kayit KAPALI — cokmede ilerleme kaybi olur");
            }
        });

        // ---------------- bulunanlar ----------------
        a.adim("bolum kesif", x -> x.rapor().grup("Bulunan icerik"));
        a.adim("icerik tara", x -> {
            var silahlar = Kesif.silahKodlari();
            var siniflar = Kesif.sinifKodlari();

            if (silahlar.isEmpty())
                x.rapor().uyari("SinifSistemi'nin Silah enum'u okunamadi — silah testleri config listesine dusecek");
            else
                x.rapor().ok("Bulunan ozel silahlar (" + silahlar.size() + "): " + silahlar);

            if (siniflar.isEmpty())
                x.rapor().uyari("SinifSistemi'nin Sinif enum'u okunamadi — sinif testleri varsayilan listeye dusecek");
            else
                x.rapor().ok("Bulunan siniflar (" + siniflar.size() + "): " + siniflar);

            var eslesme = Kesif.silahSinifi();
            for (var g : eslesme.entrySet()) {
                if (g.getValue() == null)
                    x.rapor().acik(g.getKey() + ": gerekli sinifi tanimsiz — sinif kisiti calismaz");
            }

            // sinifsiz kalan silah var mi, silahsiz kalan sinif var mi
            var kapsanan = new java.util.HashSet<>(eslesme.values());
            for (String s : siniflar)
                if (!kapsanan.contains(s))
                    x.rapor().uyari("\"" + s + "\" sinifinin hic ozel silahi yok");
        });
    }
}
