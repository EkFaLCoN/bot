package com.mrwqlf.qatest.testler;

import com.mrwqlf.qatest.Akis;
import com.mrwqlf.qatest.Cerceve;

public interface Test {
    /** /qatest calistir <ad> ile secilebilen kisa ad. */
    String ad();

    /** Ne test ettigini bir satirda anlatir. */
    String aciklama();

    /** Adimlari akisa ekler. Adimlar tick bazli sirayla calisir. */
    void kur(Akis akis, Cerceve c);
}
