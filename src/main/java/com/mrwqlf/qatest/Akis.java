package com.mrwqlf.qatest;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * Testler tick bazli calisir: her adim calisir, sonra belirtilen kadar tick beklenir.
 * Bu sayede "sag tikla, 20 tick bekle, envanteri say" gibi senaryolar yazilabilir.
 */
public final class Akis {

    private record Adim(String ad, Consumer<Cerceve> is, int beklemeTick) {}

    private final Deque<Adim> kuyruk = new ArrayDeque<>();
    private final Cerceve cerceve;
    private BukkitTask gorev;
    private int sayac;
    private Runnable bitince;

    public Akis(Cerceve cerceve) { this.cerceve = cerceve; }

    /** Adim ekler; varsayilan 2 tick bekler. */
    public Akis adim(String ad, Consumer<Cerceve> is) { return adim(ad, is, 2); }

    public Akis adim(String ad, Consumer<Cerceve> is, int beklemeTick) {
        kuyruk.add(new Adim(ad, is, Math.max(1, beklemeTick)));
        return this;
    }

    /** Sadece bekler. */
    public Akis bekle(int tick) { return adim("bekle", c -> {}, tick); }

    public int kalanAdim() { return kuyruk.size(); }

    public void baslat(Plugin eklenti, Runnable bitince) {
        this.bitince = bitince;
        gorev = Bukkit.getScheduler().runTaskTimer(eklenti, this::tik, 1L, 1L);
    }

    public void durdur() {
        kuyruk.clear();
        if (gorev != null) { gorev.cancel(); gorev = null; }
    }

    private void tik() {
        if (sayac > 0) { sayac--; return; }

        Adim a = kuyruk.poll();
        if (a == null) {
            if (gorev != null) { gorev.cancel(); gorev = null; }
            if (bitince != null) bitince.run();
            return;
        }

        try {
            a.is().accept(cerceve);
        } catch (Throwable t) {
            // Bir testin coktugu yer de bir bulgudur: eklenti orada istisna firlatiyor olabilir.
            cerceve.rapor().acik("Adim coktu — \"" + a.ad() + "\": "
                    + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
        sayac = a.beklemeTick();
    }
}
