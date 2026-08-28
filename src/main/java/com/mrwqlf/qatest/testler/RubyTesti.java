package com.mrwqlf.qatest.testler;

import com.mrwqlf.qatest.Akis;
import com.mrwqlf.qatest.Cerceve;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

/**
 * Ruby datapack'i: ore drop kurallari kazmaya gore dogru mu.
 * Ore blogu ve toz adi config.yml'den okunur; ayarlanmamissa test atlanir.
 */
public final class RubyTesti implements Test {

    private final Material oreBlok;
    private final String tozAdi;

    public RubyTesti(Material oreBlok, String tozAdi) {
        this.oreBlok = oreBlok;
        this.tozAdi = tozAdi;
    }

    @Override public String ad() { return "ruby"; }
    @Override public String aciklama() { return "Ruby ore: kazma kisiti ve Servet carpani dogru mu"; }

    @Override
    public void kur(Akis a, Cerceve c) {
        a.adim("bolum", x -> x.rapor().grup("Ruby ore drop kurallari"));

        if (oreBlok == null) {
            a.adim("atla", x -> x.rapor().uyari(
                    "Ruby testi atlandi — config.yml'de ruby.ore-blok ayarlanmamis (orn. DEEPSLATE_EMERALD_ORE)"));
            return;
        }

        // (kazma, servet, beklenen drop var mi, deneme sayisi)
        record Senaryo(Material kazma, int servet, Boolean beklenen, int deneme, String etiket) {}
        var senaryolar = java.util.List.of(
                new Senaryo(Material.IRON_PICKAXE,      0, Boolean.FALSE, 4,  "demir kazma"),
                new Senaryo(Material.DIAMOND_PICKAXE,   0, Boolean.FALSE, 4,  "elmas kazma"),
                new Senaryo(Material.NETHERITE_PICKAXE, 0, null,          14, "netherite kazma"),
                new Senaryo(Material.NETHERITE_PICKAXE, 3, Boolean.TRUE,  6,  "netherite + Servet III")
        );

        for (Senaryo s : senaryolar) {
            final int[] dusen = { 0 };

            a.adim("hazirla " + s.etiket(), x -> {
                x.oyuncu().setGameMode(GameMode.SURVIVAL);
                x.envanterTemizle();
                x.yerdekileriSil(10);
                dusen[0] = 0;
            }, 5);

            for (int i = 0; i < s.deneme(); i++) {
                a.adim("kaz " + s.etiket(), x -> {
                    // her denemede taze kazma ve taze ore
                    x.envanterTemizle();
                    ItemStack kazma = new ItemStack(s.kazma());
                    if (s.servet() > 0) kazma.addUnsafeEnchantment(Enchantment.FORTUNE, s.servet());
                    x.eleAl(kazma);

                    Block b = x.blokKoy(2, 0, 2, oreBlok);
                    // gercek kirma: drop kurallari ve datapack tetikleyicileri calissin
                    b.breakNaturally(kazma);
                }, 10);

                a.adim("say " + s.etiket(), x -> {
                    x.yerdekileriTopla(8);
                    int n = tozAdi == null || tozAdi.isBlank()
                            ? 0
                            : x.adaGoreAdet(tozAdi);
                    dusen[0] += n;
                    x.envanterTemizle();
                    x.yerdekileriSil(8);
                }, 8);
            }

            a.adim("karar " + s.etiket(), x -> {
                int n = dusen[0], d = s.deneme();
                if (Boolean.FALSE.equals(s.beklenen())) {
                    if (n > 0) x.rapor().acik(s.etiket() + " ile toz dustu (" + n + "/" + d
                            + ") — kazma kisiti delik, bu kazmayla hicbir sey dusmemeli");
                    else       x.rapor().ok(s.etiket() + ": dogru sekilde hicbir sey dusmedi");
                } else if (Boolean.TRUE.equals(s.beklenen())) {
                    if (n == 0) x.rapor().acik(s.etiket() + " ile HIC toz dusmedi — Servet carpani calismiyor");
                    else        x.rapor().ok(s.etiket() + ": " + n + "/" + d + " dusus");
                } else {
                    double oran = d == 0 ? 0 : (double) n / d;
                    x.rapor().ok(String.format("%s: %d/%d dusus (~%%%.0f sans)", s.etiket(), n, d, oran * 100));
                    if (n == 0) x.rapor().acik("netherite kazma ile hic dusmedi — dogru kazmayla bile drop yok");
                }
            });
        }

        a.adim("toparla", x -> {
            x.bolgeTemizle(3);
            x.yerdekileriSil(12);
            x.envanterTemizle();
        }, 5);
    }
}
