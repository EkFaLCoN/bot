package com.mrwqlf.qatest;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Test sonuclarini toplar, sohbete basar ve dosyaya yazar. */
public final class Rapor {

    public enum Seviye { OK, UYARI, ACIK, BOLUM }

    public record Bulgu(Seviye seviye, String grup, String mesaj) {}

    private final List<Bulgu> bulgular = new ArrayList<>();
    private final long baslangic = System.currentTimeMillis();
    private String aktifGrup = "genel";

    public void grup(String ad) {
        aktifGrup = ad;
        bulgular.add(new Bulgu(Seviye.BOLUM, ad, ad));
    }

    public void ok(String mesaj)    { bulgular.add(new Bulgu(Seviye.OK, aktifGrup, mesaj)); }
    public void uyari(String mesaj) { bulgular.add(new Bulgu(Seviye.UYARI, aktifGrup, mesaj)); }
    public void acik(String mesaj)  { bulgular.add(new Bulgu(Seviye.ACIK, aktifGrup, mesaj)); }

    /** Kosul saglanmazsa acik olarak isaretler. */
    public void bekle(boolean kosul, String acikMesaji, String okMesaji) {
        if (kosul) ok(okMesaji); else acik(acikMesaji);
    }

    public List<Bulgu> bulgular() { return bulgular; }

    public long acikSayisi()  { return bulgular.stream().filter(b -> b.seviye() == Seviye.ACIK).count(); }
    public long uyariSayisi() { return bulgular.stream().filter(b -> b.seviye() == Seviye.UYARI).count(); }
    public long okSayisi()    { return bulgular.stream().filter(b -> b.seviye() == Seviye.OK).count(); }

    public static Component renkli(Bulgu b) {
        return switch (b.seviye()) {
            case ACIK  -> Component.text("  ✖ ", NamedTextColor.RED)
                    .append(Component.text(b.mesaj(), NamedTextColor.RED));
            case UYARI -> Component.text("  ▲ ", NamedTextColor.GOLD)
                    .append(Component.text(b.mesaj(), NamedTextColor.YELLOW));
            case OK    -> Component.text("  ✔ ", NamedTextColor.GREEN)
                    .append(Component.text(b.mesaj(), NamedTextColor.GRAY));
            case BOLUM -> Component.text("§8──── ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(b.mesaj(), NamedTextColor.AQUA));
        };
    }

    /** Raporu plugins/QATest/ altina markdown olarak yazar, dosya yolunu doner. */
    public String yaz(File klasor) {
        String ts = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").format(LocalDateTime.now());
        File dosya = new File(klasor, "rapor-" + ts + ".md");

        StringBuilder sb = new StringBuilder();
        sb.append("# QA Raporu — ")
          .append(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").format(LocalDateTime.now()))
          .append("\n\n");
        sb.append("**").append(acikSayisi()).append(" acik**, ")
          .append(uyariSayisi()).append(" uyari, ")
          .append(okSayisi()).append(" gecti — sure ")
          .append((System.currentTimeMillis() - baslangic) / 1000).append(" sn\n\n");

        if (acikSayisi() > 0) {
            sb.append("## Bulunan aciklar\n\n");
            for (Bulgu b : bulgular) {
                if (b.seviye() == Seviye.ACIK) sb.append("- **[").append(b.grup()).append("]** ").append(b.mesaj()).append('\n');
            }
            sb.append('\n');
        }

        sb.append("## Tum sonuclar\n\n");
        for (Bulgu b : bulgular) {
            switch (b.seviye()) {
                case BOLUM -> sb.append("\n### ").append(b.mesaj()).append("\n\n");
                case ACIK  -> sb.append("- ❌ ").append(b.mesaj()).append('\n');
                case UYARI -> sb.append("- ⚠️ ").append(b.mesaj()).append('\n');
                case OK    -> sb.append("- ✅ ").append(b.mesaj()).append('\n');
            }
        }

        try {
            klasor.mkdirs();
            Files.writeString(dosya.toPath(), sb.toString(), StandardCharsets.UTF_8);
            return dosya.getPath();
        } catch (IOException e) {
            return "yazilamadi: " + e.getMessage();
        }
    }
}
