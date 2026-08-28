package com.mrwqlf.qatest.testler;

import com.mrwqlf.qatest.Akis;
import com.mrwqlf.qatest.Cerceve;
import org.bukkit.GameMode;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;

/**
 * Kanli Savas Baltasi'na ozel davranis testleri.
 * config.yml'deki degerlere gore: hasar-carpani 1.30, kanama 4 sn, can-emis 0.35,
 * yetenek-bekleme 20 sn, yetenek-menzili 4.5.
 */
public final class KanliBaltaTesti implements Test {

    private static final String KOD = "kanli_savas_baltasi";

    // testler arasi tasinan olcumler
    private double hasarSavasci;
    private double hasarBuyucu;
    private double hedefCanKanamaOncesi;
    private double oyuncuCanVurustanOnce;
    private LivingEntity yetenekHedef;
    private Cerceve.Enstantane yetenekOnce;

    @Override public String ad() { return "balta"; }
    @Override public String aciklama() { return "Kanli Savas Baltasi: sinif kisiti, kanama, can emisi, yetenek beklemesi"; }

    @Override
    public void kur(Akis a, Cerceve c) {
        a.adim("bolum", x -> x.rapor().grup("Kanli Savas Baltasi"));

        a.adim("hazirlik", x -> {
            x.oyuncu().setGameMode(GameMode.SURVIVAL);
            x.envanterTemizle();
            x.hedefleriTemizle();
        }, 5);

        // ================= 1. SAVASCI ile hasar =================
        a.adim("sinif savasci", x -> x.komut("sinifyonetim ver " + x.oyuncu().getName() + " savasci"), 20);
        a.adim("balta ver", x -> x.komut("silah ver " + KOD + " " + x.oyuncu().getName()), 12);
        a.adim("ele al", x -> {
            ItemStack it = x.ozelSilahBul(KOD);
            if (it == null) { x.rapor().acik("Balta alinamadi — /silah ver " + KOD + " calismiyor"); return; }
            x.eleAl(it);
        }, 5);

        a.adim("savasci hasar olc", x -> {
            LivingEntity h = x.hedefOlustur();
            hasarSavasci = x.vurVeOlc(h, 10.0);
            hedefCanKanamaOncesi = h.getHealth();
        }, 10);

        // kanama: vurustan sonraki saniyelerde can azalmaya devam etmeli
        a.adim("kanama bekle", x -> {}, 70);
        a.adim("kanama olc", x -> {
            LivingEntity h = null;
            for (var e : x.oyuncu().getWorld().getEntities())
                if (e instanceof LivingEntity le && "QATestHedef".equals(e.getCustomName())) h = le;

            if (h == null) { x.rapor().uyari("Kanama olcumu icin hedef kayboldu"); return; }
            double dusen = hedefCanKanamaOncesi - h.getHealth();
            if (dusen <= 0.01)
                x.rapor().acik("Kanama UYGULANMIYOR — vurustan 3.5 sn sonra hedefin cani hic azalmadi (beklenen: 4 sn boyunca saniyede 1.0)");
            else if (dusen < 2.0)
                x.rapor().uyari(String.format("Kanama zayif: 3.5 sn'de %.1f can dustu, config'e gore ~3.5 beklenirdi", dusen));
            else
                x.rapor().ok(String.format("Kanama calisiyor: vurustan sonra %.1f ek hasar", dusen));
        });

        // ================= 2. CAN EMISI =================
        a.adim("can emis hazirla", x -> {
            x.hedefleriTemizle();
            var attr = x.oyuncu().getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            double max = attr != null ? attr.getValue() : 20.0;
            x.oyuncu().setHealth(Math.max(1.0, max / 2.0));   // yarim canla basla
            oyuncuCanVurustanOnce = x.oyuncu().getHealth();
        }, 10);
        a.adim("can emis vur", x -> {
            LivingEntity h = x.hedefOlustur();
            x.vurVeOlc(h, 10.0);
        }, 70);
        a.adim("can emis olc", x -> {
            double kazanc = x.oyuncu().getHealth() - oyuncuCanVurustanOnce;
            if (kazanc <= 0.01)
                x.rapor().acik("Can emisi CALISMIYOR — kanama hasarinin %35'i cana donmeli, oyuncunun cani hic artmadi");
            else
                x.rapor().ok(String.format("Can emisi calisiyor: +%.2f can", kazanc));
            x.hedefleriTemizle();
        }, 5);

        // ================= 3. YETENEK BEKLEME SURESI =================
        a.adim("yetenek 1", x -> {
            x.hedefleriTemizle();
            yetenekHedef = x.yakinHedef();
            yetenekOnce = x.enstantane(yetenekHedef);
            x.sagTik();
        }, 50);
        a.adim("yetenek 1 karari", x -> {
            if (yetenekHedef == null) return;
            if (x.etkiVarMi(yetenekOnce, yetenekHedef))
                x.rapor().ok("Yetenek tetikleniyor — menzildeki hedef etkilendi (Kan Cagrisi)");
            else
                x.rapor().acik("Yetenek HICBIR ETKI uretmedi — 2 blok mesafedeki hedefe kanama uygulanmadi "
                        + "(config: yetenek-menzili 4.5)");
        });

        a.adim("yetenek 2 (hemen tekrar)", x -> {
            if (yetenekHedef == null || !yetenekHedef.isValid()) yetenekHedef = x.yakinHedef();
            yetenekOnce = x.enstantane(yetenekHedef);
            x.sagTik();
        }, 50);
        a.adim("bekleme karari", x -> {
            if (yetenekHedef == null) return;
            if (x.etkiVarMi(yetenekOnce, yetenekHedef))
                x.rapor().acik("BEKLEME SURESI YOK — yetenek arka arkaya iki kez etki uretti, "
                        + "config'deki 20 sn bekleme uygulanmiyor");
            else
                x.rapor().ok("Bekleme suresi calisiyor (ikinci deneme etki uretmedi)");
            x.hedefleriTemizle();
            yetenekHedef = null;
        }, 5);

        // bekleme suresi esyayi birakip alinca sifirlaniyor mu (PDC oyuncuda mi item'da mi)
        a.adim("bekleme sifirlama denemesi", x -> {
            ItemStack it = x.ozelSilahBul(KOD);
            if (it == null) return;
            x.envanterTemizle();
            x.komut("silah ver " + KOD + " " + x.oyuncu().getName());
        }, 15);
        a.adim("yeni esyayla dene", x -> {
            ItemStack it = x.ozelSilahBul(KOD);
            if (it == null) return;
            x.eleAl(it);
            yetenekHedef = x.yakinHedef();
            yetenekOnce = x.enstantane(yetenekHedef);
            x.sagTik();
        }, 50);
        a.adim("esya bagli bekleme karari", x -> {
            if (yetenekHedef == null) return;
            if (x.etkiVarMi(yetenekOnce, yetenekHedef))
                x.rapor().acik("Bekleme suresi ESYAYA bagli — yeni bir balta alinca bekleme sifirlaniyor. "
                        + "Sure oyuncunun PDC'sinde tutulmali, aksi halde oyuncu surekli yeni balta alip yetenegi spamlar");
            else
                x.rapor().ok("Bekleme suresi oyuncuya bagli, yeni esya sifirlamiyor");
            x.hedefleriTemizle();
            yetenekHedef = null;
        }, 5);

        // ================= 4. SINIF KISITI =================
        a.adim("sinif buyucu", x -> {
            x.hedefleriTemizle();
            x.komut("sinifyonetim ver " + x.oyuncu().getName() + " buyucu");
        }, 25);
        a.adim("buyucu balta", x -> {
            x.envanterTemizle();
            x.komut("silah ver " + KOD + " " + x.oyuncu().getName());
        }, 15);
        a.adim("elden cikti mi", x -> {
            ItemStack it = x.ozelSilahBul(KOD);
            if (it == null) {
                x.rapor().ok("Yanlis sinifta balta hic verilmedi / envanterde tutulmuyor");
                return;
            }
            x.eleAl(it);
        }, 25);
        a.adim("buyucu hasar olc", x -> {
            ItemStack el = x.eldeki();
            if (Cerceve.ozelSilahKodu(el) == null) {
                x.rapor().ok("Yanlis sinifta balta elden otomatik cikarildi (yanlis-sinifta-elden-cikar calisiyor)");
                hasarBuyucu = -1;
                return;
            }
            LivingEntity h = x.hedefOlustur();
            hasarBuyucu = x.vurVeOlc(h, 10.0);
        }, 15);
        a.adim("sinif kisiti karari", x -> {
            if (hasarBuyucu < 0) return;  // elden cikarilmis, zaten gecti
            if (hasarSavasci <= 0) { x.rapor().uyari("Savasci hasari olculemedigi icin sinif kisiti karsilastirilamadi"); return; }

            double oran = hasarBuyucu / hasarSavasci;
            if (oran > 0.95)
                x.rapor().acik(String.format(
                        "SINIF KISITI DELIK — buyucu baltayi savasci kadar sert kullaniyor (%.1f / %.1f hasar). "
                        + "Uyari mesaji cikiyor olabilir ama hasar gercekten engellenmiyor", hasarBuyucu, hasarSavasci));
            else
                x.rapor().ok(String.format("Sinif kisiti hasari da engelliyor (buyucu %.1f / savasci %.1f)", hasarBuyucu, hasarSavasci));
        });

        // ================= 5. TAMIRLE / YIPRANMA =================
        a.adim("yipranma", x -> {
            x.komut("sinifyonetim ver " + x.oyuncu().getName() + " savasci");
        }, 20);
        a.adim("yipranma olc", x -> {
            x.envanterTemizle();
            x.komut("silah ver " + KOD + " " + x.oyuncu().getName());
        }, 15);
        a.adim("yipranma karari", x -> {
            ItemStack it = x.ozelSilahBul(KOD);
            if (it == null) return;
            var meta = it.getItemMeta();
            if (meta != null && meta.isUnbreakable())
                x.rapor().uyari("Balta KIRILMAZ isaretli — ekonomi acisindan sorun olabilir, "
                        + "onceki surumde setUnbreakable bilerek kaldirilmisti");
            else
                x.rapor().ok("Balta yipraniyor (unbreakable degil)");
        });

        a.adim("toparla", x -> {
            x.hedefleriTemizle();
            x.yerdekileriSil(12);
        }, 5);
    }
}
