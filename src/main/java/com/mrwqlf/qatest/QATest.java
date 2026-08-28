package com.mrwqlf.qatest;

import com.mrwqlf.qatest.testler.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class QATest extends JavaPlugin implements Listener {

    private Akis calisanAkis;
    private Cerceve calisanCerceve;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("QATest hazir. /qatest liste ile testleri gorebilirsin.");
    }

    @Override
    public void onDisable() {
        if (calisanAkis != null) calisanAkis.durdur();
        if (calisanCerceve != null) calisanCerceve.durumGeriYukle();
    }

    // ---------- test kayitlari ----------

    private List<Test> testler() {
        List<Test> t = new ArrayList<>();

        List<String> silahlar = getConfig().getStringList("silahlar");
        if (silahlar.isEmpty()) silahlar = List.of("kanli_savas_baltasi");
        t.add(new GenelExploitTesti(silahlar));

        t.add(new KanliBaltaTesti());

        String oreAd = getConfig().getString("ruby.ore-blok", "");
        Material ore = null;
        if (oreAd != null && !oreAd.isBlank()) ore = Material.matchMaterial(oreAd.toUpperCase(Locale.ROOT));
        t.add(new RubyTesti(ore, getConfig().getString("ruby.toz-adi", "Toz")));

        return t;
    }

    // ---------- konsol yakalama ----------
    /** Test sirasinda konsola dusen uyari/hata satirlarini toplar — bir yetenek coktugunde iz burada kalir. */
    private final java.util.logging.Handler logYakalayici = new java.util.logging.Handler() {
        @Override public void publish(java.util.logging.LogRecord r) {
            if (calisanCerceve == null || r.getMessage() == null) return;
            if (r.getLevel().intValue() >= java.util.logging.Level.WARNING.intValue()) {
                calisanCerceve.konsolEkle(r.getMessage());
            }
        }
        @Override public void flush() {}
        @Override public void close() {}
    };

    // ---------- komut ----------

    @Override
    public boolean onCommand(CommandSender g, Command k, String etiket, String[] arg) {
        if (!g.hasPermission("qatest.kullan")) {
            g.sendMessage(Component.text("Bunun icin yetkin yok.", NamedTextColor.RED));
            return true;
        }

        if (arg.length == 0) { kullanim(g); return true; }

        switch (arg[0].toLowerCase(Locale.ROOT)) {
            case "liste" -> {
                g.sendMessage(Component.text("Mevcut testler:", NamedTextColor.GOLD));
                for (Test t : testler())
                    g.sendMessage(Component.text("  " + t.ad(), NamedTextColor.YELLOW)
                            .append(Component.text(" — " + t.aciklama(), NamedTextColor.GRAY)));
                g.sendMessage(Component.text("  hepsi", NamedTextColor.YELLOW)
                        .append(Component.text(" — tumunu sirayla calistirir", NamedTextColor.GRAY)));
            }

            case "dur" -> {
                if (calisanAkis == null) { g.sendMessage(Component.text("Calisan test yok.", NamedTextColor.GRAY)); return true; }
                calisanAkis.durdur();
                if (calisanCerceve != null) calisanCerceve.durumGeriYukle();
                calisanAkis = null; calisanCerceve = null;
                g.sendMessage(Component.text("Test durduruldu, envanterin geri yuklendi.", NamedTextColor.YELLOW));
            }

            case "calistir" -> {
                if (!(g instanceof Player p)) {
                    g.sendMessage(Component.text("Bu komut oyun icinden calistirilmali — testler senin karakterini kullaniyor.", NamedTextColor.RED));
                    return true;
                }
                if (calisanAkis != null) {
                    g.sendMessage(Component.text("Zaten bir test calisiyor. /qatest dur ile kesebilirsin.", NamedTextColor.RED));
                    return true;
                }
                String secim = arg.length > 1 ? arg[1].toLowerCase(Locale.ROOT) : "hepsi";
                baslat(p, secim);
            }

            default -> kullanim(g);
        }
        return true;
    }

    private void kullanim(CommandSender g) {
        g.sendMessage(Component.text("/qatest calistir [grup]", NamedTextColor.YELLOW)
                .append(Component.text(" — testleri calistirir", NamedTextColor.GRAY)));
        g.sendMessage(Component.text("/qatest liste", NamedTextColor.YELLOW)
                .append(Component.text(" — test gruplarini listeler", NamedTextColor.GRAY)));
        g.sendMessage(Component.text("/qatest dur", NamedTextColor.YELLOW)
                .append(Component.text(" — calisan testi keser ve envanteri geri yukler", NamedTextColor.GRAY)));
    }

    // ---------- calistirma ----------

    private void baslat(Player p, String secim) {
        Rapor rapor = new Rapor();
        Cerceve c = new Cerceve(this, p, rapor);
        calisanCerceve = c;

        c.durumKaydet();
        Bukkit.getLogger().addHandler(logYakalayici);

        Akis akis = new Akis(c);
        calisanAkis = akis;

        int eklenen = 0;
        for (Test t : testler()) {
            if (!secim.equals("hepsi") && !secim.equals(t.ad())) continue;
            t.kur(akis, c);
            eklenen++;
        }

        if (eklenen == 0) {
            p.sendMessage(Component.text("\"" + secim + "\" diye bir test grubu yok. /qatest liste", NamedTextColor.RED));
            temizle(c);
            return;
        }

        p.sendMessage(Component.text("──────────────────────────", NamedTextColor.DARK_GRAY));
        p.sendMessage(Component.text("QA testi basliyor", NamedTextColor.AQUA)
                .append(Component.text(" — envanterin gecici olarak temizlenecek, bitince geri yuklenecek.", NamedTextColor.GRAY)));
        p.sendMessage(Component.text("Sakin durma, bot senin karakterini kullaniyor.", NamedTextColor.GRAY));

        akis.baslat(this, () -> bitir(p, c, rapor));
    }

    private void bitir(Player p, Cerceve c, Rapor rapor) {
        temizle(c);

        p.sendMessage(Component.text("──────────────────────────", NamedTextColor.DARK_GRAY));
        for (Rapor.Bulgu b : rapor.bulgular()) p.sendMessage(Rapor.renkli(b));

        String yol = rapor.yaz(getDataFolder());

        p.sendMessage(Component.text("──────────────────────────", NamedTextColor.DARK_GRAY));
        p.sendMessage(Component.text(rapor.acikSayisi() + " ACIK", NamedTextColor.RED)
                .append(Component.text(", " + rapor.uyariSayisi() + " uyari", NamedTextColor.YELLOW))
                .append(Component.text(", " + rapor.okSayisi() + " gecti", NamedTextColor.GREEN)));
        p.sendMessage(Component.text("Rapor: " + yol, NamedTextColor.GRAY));

        getLogger().info("QA testi bitti — " + rapor.acikSayisi() + " acik, rapor: " + yol);
    }

    private void temizle(Cerceve c) {
        c.hedefleriTemizle();
        c.durumGeriYukle();
        Bukkit.getLogger().removeHandler(logYakalayici);
        calisanAkis = null;
        calisanCerceve = null;
    }
}
