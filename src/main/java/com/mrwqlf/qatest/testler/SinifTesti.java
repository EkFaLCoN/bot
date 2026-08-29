package com.mrwqlf.qatest.testler;

import com.mrwqlf.qatest.Akis;
import com.mrwqlf.qatest.Cerceve;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Siniflarin pasif ozellikleri gercekten uygulaniyor mu.
 *
 * Beklenen degerler SinifSistemi'nin KENDI config.yml'sinden okunur, boylece
 * ayarlari degistirdiginde test de kendini gunceller. Olcum icin attribute'un
 * getBaseValue() (modifiersiz) ve getValue() (modifierli) degerleri karsilastirilir —
 * aradaki fark tam olarak sinifin ekledigi seydir.
 */
public final class SinifTesti implements Test {

    /** Sunucudan kesfedilir; okunamazsa bu liste kullanilir. */
    private static final List<String> VARSAYILAN_SINIFLAR =
            List.of("savasci", "suikastci", "rahip", "okcu", "buyucu", "avci");

    /** config anahtari -> (attribute, oransal mi) */
    private static final Map<String, Object[]> ESLESME = new LinkedHashMap<>() {{
        put("max-can",              new Object[]{ Attribute.MAX_HEALTH,               false, "azami can" });
        put("zirh",                 new Object[]{ Attribute.ARMOR,                    false, "zirh" });
        put("zirh-saglamligi",      new Object[]{ Attribute.ARMOR_TOUGHNESS,          false, "zirh saglamligi" });
        put("geri-tepme-direnci",   new Object[]{ Attribute.KNOCKBACK_RESISTANCE,     false, "geri tepme direnci" });
        put("sans",                 new Object[]{ Attribute.LUCK,                     false, "sans" });
        put("etkilesim-menzili",    new Object[]{ Attribute.ENTITY_INTERACTION_RANGE, false, "etkilesim menzili" });
        put("saldiri-hasari-yuzde", new Object[]{ Attribute.ATTACK_DAMAGE,            true,  "saldiri hasari" });
        put("hareket-hizi-yuzde",   new Object[]{ Attribute.MOVEMENT_SPEED,           true,  "hareket hizi" });
        put("saldiri-hizi-yuzde",   new Object[]{ Attribute.ATTACK_SPEED,             true,  "saldiri hizi" });
    }};

    private double kilicHasar;
    private double referansHasar;
    private double rahipCanBaslangic;

    @Override public String ad() { return "sinif"; }
    @Override public String aciklama() { return "6 sinifin pasif ozellikleri config'e uyuyor mu"; }

    @Override
    public void kur(Akis a, Cerceve c) {
        a.adim("bolum", x -> x.rapor().grup("Sinif pasif ozellikleri"));

        a.adim("hazirlik", x -> {
            x.oyuncu().setGameMode(GameMode.SURVIVAL);
            x.envanterTemizle();          // zirh ARMOR attribute'unu bozmasin
            x.hedefleriTemizle();
        }, 10);

        // op oyuncu sinif kisitlarini asar — sonuclar yaniltici olmasin
        a.adim("izin uyarisi", x -> {
            if (x.oyuncu().hasPermission("sinif.silah.serbest"))
                x.rapor().uyari("Bu hesapta \"sinif.silah.serbest\" izni var (op varsayilani). "
                        + "Silah sinif kisiti testleri bu yuzden HEP gecer gorunur — "
                        + "gercek kisiti olcmek icin op olmayan bir hesapla test et.");
            if (x.oyuncu().hasPermission("sinif.degistir"))
                x.rapor().ok("sinif.degistir izni var — testler sinif degistirebiliyor");
        });

        var ss = Bukkit.getPluginManager().getPlugin("SinifSistemi");
        if (ss == null) {
            a.adim("eksik", x -> x.rapor().acik("SinifSistemi eklentisi yuklu degil — sinif testleri calistirilamaz"));
            return;
        }

        List<String> siniflar = com.mrwqlf.qatest.Kesif.sinifKodlari();
        if (siniflar.isEmpty()) siniflar = VARSAYILAN_SINIFLAR;

        for (String sinif : siniflar) {
            a.adim("sinif " + sinif, x -> {
                x.envanterTemizle();
                x.komut("sinifyonetim ver " + x.oyuncu().getName() + " " + sinif);
            }, 30);

            a.adim("olc " + sinif, x -> {
                ConfigurationSection bolum = ss.getConfig().getConfigurationSection("siniflar." + sinif);
                if (bolum == null) {
                    x.rapor().uyari(sinif + ": config'de \"siniflar." + sinif + "\" bolumu yok, atlandi");
                    return;
                }

                int kontrol = 0, hatali = 0;
                for (var giris : ESLESME.entrySet()) {
                    if (!bolum.contains(giris.getKey())) continue;

                    double beklenen = bolum.getDouble(giris.getKey());
                    if (Math.abs(beklenen) < 1e-9) continue;

                    Attribute attr = (Attribute) giris.getValue()[0];
                    boolean oransal = (Boolean) giris.getValue()[1];
                    String etiket = (String) giris.getValue()[2];

                    AttributeInstance ai = x.oyuncu().getAttribute(attr);
                    if (ai == null) {
                        x.rapor().uyari(sinif + "/" + etiket + ": attribute sunucuda yok");
                        continue;
                    }

                    kontrol++;
                    double taban = ai.getBaseValue();
                    double gercek = ai.getValue();

                    if (oransal) {
                        double beklenenDeger = taban * (1 + beklenen);
                        if (Math.abs(gercek - beklenenDeger) > Math.max(0.02, Math.abs(beklenenDeger) * 0.05)) {
                            hatali++;
                            x.rapor().acik(String.format(
                                    "%s / %s: config %%%.0f artis diyor ama gercek deger %.3f (beklenen %.3f, taban %.3f)",
                                    sinif, etiket, beklenen * 100, gercek, beklenenDeger, taban));
                        }
                    } else {
                        double fark = gercek - taban;
                        if (Math.abs(fark - beklenen) > 0.05) {
                            hatali++;
                            x.rapor().acik(String.format(
                                    "%s / %s: config %+.1f diyor ama gercek fark %+.2f (taban %.2f, guncel %.2f)",
                                    sinif, etiket, beklenen, fark, taban, gercek));
                        }
                    }
                }

                if (kontrol == 0)
                    x.rapor().uyari(sinif + ": config'de olculebilir attribute ayari yok");
                else if (hatali == 0)
                    x.rapor().ok(sinif + ": " + kontrol + " pasif ozelligin hepsi config'e uyuyor");
            });
        }

        // ---------- davranissal carpanlar ----------
        a.adim("davranis bolumu", x -> x.rapor().grup("Sinif davranis carpanlari"));

        // referans: buyucu + kilic (kilic carpani yok, sadece taban)
        a.adim("referans sinif", x -> x.komut("sinifyonetim ver " + x.oyuncu().getName() + " buyucu"), 30);
        a.adim("referans olc", x -> {
            x.envanterTemizle();
            x.eleAl(new ItemStack(Material.IRON_SWORD));
            LivingEntity h = x.tazeHedef();
            referansHasar = x.vurVeOlc(h, 10.0);
        }, 20);

        // suikastci: kilic-hasar-carpani 1.25
        a.adim("suikastci ver", x -> x.komut("sinifyonetim ver " + x.oyuncu().getName() + " suikastci"), 30);
        a.adim("suikastci kilic", x -> {
            x.envanterTemizle();
            x.eleAl(new ItemStack(Material.IRON_SWORD));
            LivingEntity h = x.tazeHedef();
            kilicHasar = x.vurVeOlc(h, 10.0);
        }, 20);
        a.adim("suikastci karari", x -> {
            double bekCarpan = ss.getConfig().getDouble("siniflar.suikastci.kilic-hasar-carpani", 1.0);
            if (referansHasar <= 0) { x.rapor().uyari("Referans hasar olculemedi, kilic carpani kontrol edilemedi"); return; }
            double gercekCarpan = kilicHasar / referansHasar;
            // saldiri-hasari attribute farki da isin icinde, o yuzden genis tolerans
            if (bekCarpan > 1.01 && gercekCarpan < 1.05)
                x.rapor().acik(String.format(
                        "Suikastci kilic carpani UYGULANMIYOR — config %.2f diyor, olculen oran %.2f (%.1f / %.1f hasar)",
                        bekCarpan, gercekCarpan, kilicHasar, referansHasar));
            else
                x.rapor().ok(String.format("Suikastci kilicla daha sert vuruyor (oran %.2f, config %.2f)", gercekCarpan, bekCarpan));
            x.hedefleriTemizle();
        });

        // rahip: can-yenileme (dogal yenilenme kapatilarak olculur)
        a.adim("rahip ver", x -> {
            x.hedefleriTemizle();
            x.komut("sinifyonetim ver " + x.oyuncu().getName() + " rahip");
        }, 30);
        a.adim("rahip hazirla", x -> {
            x.envanterTemizle();
            x.dogalYenilenmeKapat();
            var ai = x.oyuncu().getAttribute(Attribute.MAX_HEALTH);
            double max = ai != null ? ai.getValue() : 20.0;
            x.oyuncu().setHealth(Math.max(1.0, max / 2.0));
        }, 20);
        a.adim("rahip baslangic", x -> rahipCanBaslangic = x.oyuncu().getHealth(), 120);
        a.adim("rahip karari", x -> {
            double kazanc = x.oyuncu().getHealth() - rahipCanBaslangic;
            double bek = ss.getConfig().getDouble("siniflar.rahip.can-yenileme", 0);
            if (bek > 0 && kazanc < 0.5)
                x.rapor().acik(String.format(
                        "Rahip can yenilemesi CALISMIYOR — 6 sn'de +%.2f can (config: her 2 sn'de %.1f, dogal yenilenme kapali)",
                        kazanc, bek));
            else if (bek > 0)
                x.rapor().ok(String.format("Rahip can yenilemesi calisiyor: 6 sn'de +%.2f can", kazanc));
            x.dogalYenilenmeAc();
        });

        // okcu: dusme hasari muafiyeti
        a.adim("okcu ver", x -> x.komut("sinifyonetim ver " + x.oyuncu().getName() + " okcu"), 30);
        a.adim("okcu dusme", x -> {
            x.envanterTemizle();
            var ai = x.oyuncu().getAttribute(Attribute.MAX_HEALTH);
            double max = ai != null ? ai.getValue() : 20.0;
            x.oyuncu().setHealth(max);
            rahipCanBaslangic = x.oyuncu().getHealth();
            x.oyuncu().damage(6.0, org.bukkit.damage.DamageSource
                    .builder(org.bukkit.damage.DamageType.FALL).build());
        }, 20);
        a.adim("okcu dusme karari", x -> {
            boolean muaf = ss.getConfig().getBoolean("siniflar.okcu.dusme-hasari-muafiyeti", false);
            double alinan = rahipCanBaslangic - x.oyuncu().getHealth();
            if (muaf && alinan > 0.1)
                x.rapor().acik(String.format("Okcu dusme hasari muafiyeti CALISMIYOR — %.1f dusme hasari aldi", alinan));
            else if (muaf)
                x.rapor().ok("Okcu dusme hasarindan muaf");
            var ai = x.oyuncu().getAttribute(Attribute.MAX_HEALTH);
            x.oyuncu().setHealth(ai != null ? ai.getValue() : 20.0);
        });

        a.adim("toparla", x -> {
            x.hedefleriTemizle();
            x.envanterTemizle();
            x.dogalYenilenmeAc();
            x.rapor().uyari("Test bitti — sinifin su an OKCU. Kendi sinifini geri vermeyi unutma: "
                    + "/sinifyonetim ver " + x.oyuncu().getName() + " <sinif>");
        }, 5);
    }
}
