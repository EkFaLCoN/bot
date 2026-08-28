package com.mrwqlf.qatest;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/** Testlerin kullandigi yardimcilar: esya verme, envanter sayma, yetenek tetikleme, mesaj yakalama. */
public final class Cerceve {

    private final Plugin eklenti;
    private final Player oyuncu;
    private final Rapor rapor;

    /** SinifSistemi'nin ozel silahlari isaretledigi PDC anahtari. */
    public static final NamespacedKey OZEL_SILAH = new NamespacedKey("sinifsistemi", "ozel_silah");

    /** Konsola dusen uyari/hata satirlari — bir yetenek coktugunde iz burada kalir. */
    private final List<String> konsol = new ArrayList<>();

    // testten once alinan durum
    private ItemStack[] yedekEnvanter;
    private ItemStack[] yedekZirh;
    private GameMode yedekMod;
    private Location yedekKonum;
    private double yedekCan;

    public Cerceve(Plugin eklenti, Player oyuncu, Rapor rapor) {
        this.eklenti = eklenti;
        this.oyuncu = oyuncu;
        this.rapor = rapor;
    }

    public Plugin eklenti() { return eklenti; }
    public Player oyuncu()  { return oyuncu; }
    public Rapor rapor()    { return rapor; }

    // ---------- durum koruma ----------

    public void durumKaydet() {
        yedekEnvanter = oyuncu.getInventory().getContents().clone();
        yedekZirh     = oyuncu.getInventory().getArmorContents().clone();
        yedekMod      = oyuncu.getGameMode();
        yedekKonum    = oyuncu.getLocation().clone();
        yedekCan      = oyuncu.getHealth();
    }

    public void durumGeriYukle() {
        if (yedekEnvanter == null) return;
        oyuncu.getInventory().setContents(yedekEnvanter);
        oyuncu.getInventory().setArmorContents(yedekZirh);
        oyuncu.setGameMode(yedekMod);
        oyuncu.teleport(yedekKonum);
        try { oyuncu.setHealth(Math.min(yedekCan, oyuncu.getMaxHealth())); } catch (Exception ignored) {}
        oyuncu.setFireTicks(0);
    }

    // ---------- komut ----------

    /** Konsoldan komut calistirir (izin sorunu yasanmasin diye). */
    public boolean komut(String komut) {
        return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), komut.startsWith("/") ? komut.substring(1) : komut);
    }

    /** Oyuncunun kendisi olarak komut calistirir. */
    public boolean komutOyuncu(String komut) {
        return Bukkit.dispatchCommand(oyuncu, komut.startsWith("/") ? komut.substring(1) : komut);
    }

    // ---------- mesaj yakalama ----------

    public void konsolEkle(String m) { konsol.add(m); }
    public int konsolIsareti()       { return konsol.size(); }

    public List<String> konsolSonra(int isaret) {
        return isaret >= konsol.size() ? List.of() : new ArrayList<>(konsol.subList(isaret, konsol.size()));
    }

    public static String duzMetin(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    // ---------- envanter ----------

    public void envanterTemizle() {
        oyuncu.getInventory().clear();
        oyuncu.getInventory().setArmorContents(null);
    }

    /** PDC'de belirtilen kodla isaretli esyalarin toplam adedi. */
    public int ozelSilahAdedi(String kod) {
        int n = 0;
        for (ItemStack it : oyuncu.getInventory().getContents()) {
            if (kod.equalsIgnoreCase(ozelSilahKodu(it))) n += it.getAmount();
        }
        return n;
    }

    /** Esyanin PDC'sindeki ozel silah kodu, yoksa null. */
    public static String ozelSilahKodu(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return null;
        ItemMeta m = it.getItemMeta();
        if (m == null) return null;
        return m.getPersistentDataContainer().get(OZEL_SILAH, PersistentDataType.STRING);
    }

    /** Envanterdeki ilk ozel silahi bulur. */
    public ItemStack ozelSilahBul(String kod) {
        for (ItemStack it : oyuncu.getInventory().getContents()) {
            if (kod.equalsIgnoreCase(ozelSilahKodu(it))) return it;
        }
        return null;
    }

    /** Adinda gecen metne gore esya sayar (datapack esyalari icin). */
    public int adaGoreAdet(String parca) {
        int n = 0;
        String p = parca.toLowerCase(java.util.Locale.ROOT);
        for (ItemStack it : oyuncu.getInventory().getContents()) {
            if (it == null || !it.hasItemMeta()) continue;
            Component ad = it.getItemMeta().displayName();
            if (ad != null && duzMetin(ad).toLowerCase(java.util.Locale.ROOT).contains(p)) n += it.getAmount();
        }
        return n;
    }

    /** Verilen esyayi ele alir ve slot 0'a koyar. */
    public void eleAl(ItemStack it) {
        oyuncu.getInventory().setItem(0, it);
        oyuncu.getInventory().setHeldItemSlot(0);
    }

    public ItemStack eldeki() { return oyuncu.getInventory().getItemInMainHand(); }

    // ---------- yetenek tetikleme ----------

    /** Istemci olmadan sag tik simule eder — yetenek dinleyicileri bunu gorur. */
    public void sagTik() {
        ItemStack el = eldeki();
        PlayerInteractEvent ev = new PlayerInteractEvent(
                oyuncu, Action.RIGHT_CLICK_AIR, el, null, org.bukkit.block.BlockFace.SELF);
        Bukkit.getPluginManager().callEvent(ev);
    }

    /** Ard arda hizli sag tik — bekleme suresi asiliyor mu diye. */
    public void sagTikSpam(int adet) {
        for (int i = 0; i < adet; i++) sagTik();
    }

    // ---------- hedef ----------

    /** Onunde hareketsiz, cok canli bir test hedefi olusturur. */
    public LivingEntity hedefOlustur() {
        Location l = oyuncu.getLocation().clone().add(oyuncu.getLocation().getDirection().normalize().multiply(2));
        l.setY(oyuncu.getLocation().getY());
        var z = oyuncu.getWorld().spawn(l, org.bukkit.entity.Zombie.class, e -> {
            e.setAI(false);
            e.setSilent(true);
            e.setPersistent(true);
            e.setRemoveWhenFarAway(false);
            e.setCustomName("QATestHedef");
            e.setCustomNameVisible(false);
            var attr = e.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            if (attr != null) attr.setBaseValue(1024.0);
            e.setHealth(1024.0);
        });
        return z;
    }

    public void hedefleriTemizle() {
        for (Entity e : oyuncu.getWorld().getEntities()) {
            if ("QATestHedef".equals(e.getName()) || "QATestHedef".equals(e.getCustomName())) e.remove();
        }
    }

    /** Oyuncunun eldeki silahiyla hedefe vurmasini simule eder, verilen hasari doner. */
    public double vurVeOlc(LivingEntity hedef, double tabanHasar) {
        double once = hedef.getHealth();
        hedef.damage(tabanHasar, oyuncu);
        hedef.setNoDamageTicks(0);   // ard arda vurabilmek icin
        return once - hedef.getHealth();
    }

    // ---------- yetenek etkisi tespiti ----------
    // Giden sohbet mesajlarini dinlemenin guvenilir bir yolu yok, o yuzden
    // yetenegin CALISIP calismadigini etkisinden anliyoruz: menzildeki hedefin
    // cani sag tiktan sonra azaliyor mu, oyuncuya gecici bir etki bindi mi.

    public record Enstantane(double hedefCan, double oyuncuCan, int etkiSayisi) {}

    public Enstantane enstantane(LivingEntity hedef) {
        return new Enstantane(
                hedef == null ? -1 : hedef.getHealth(),
                oyuncu.getHealth(),
                oyuncu.getActivePotionEffects().size());
    }

    /** Sag tiktan sonra gozle gorulur bir etki olustu mu? */
    public boolean etkiVarMi(Enstantane once, LivingEntity hedef) {
        if (hedef != null && hedef.isValid() && once.hedefCan() >= 0
                && hedef.getHealth() < once.hedefCan() - 0.01) return true;
        if (Math.abs(oyuncu.getHealth() - once.oyuncuCan()) > 0.01) return true;
        return oyuncu.getActivePotionEffects().size() != once.etkiSayisi();
    }

    /** Menzil icinde duran, kanamayi gorebilecegimiz sabit bir hedef. */
    public LivingEntity yakinHedef() {
        LivingEntity h = hedefOlustur();
        h.teleport(oyuncu.getLocation().clone().add(2, 0, 0));
        return h;
    }

    // ---------- dunya / blok ----------

    public Block blokKoy(int dx, int dy, int dz, Material tur) {
        Block b = oyuncu.getLocation().clone().add(dx, dy, dz).getBlock();
        b.setType(tur, false);
        return b;
    }

    public void bolgeTemizle(int r) {
        Location l = oyuncu.getLocation();
        for (int x = -r; x <= r; x++)
            for (int y = -1; y <= r; y++)
                for (int z = -r; z <= r; z++) {
                    Block b = l.clone().add(x, y, z).getBlock();
                    if (b.getType() != Material.AIR) b.setType(Material.AIR, false);
                }
    }

    /** Yakindaki yere dusmus esyalari toplar (dupe testleri icin). */
    public void yerdekileriTopla(double yaricap) {
        for (Entity e : oyuncu.getNearbyEntities(yaricap, yaricap, yaricap)) {
            if (e instanceof org.bukkit.entity.Item item) {
                oyuncu.getInventory().addItem(item.getItemStack());
                item.remove();
            }
        }
    }

    public void yerdekileriSil(double yaricap) {
        for (Entity e : oyuncu.getNearbyEntities(yaricap, yaricap, yaricap)) {
            if (e instanceof org.bukkit.entity.Item) e.remove();
        }
    }
}
