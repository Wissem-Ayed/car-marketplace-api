package com.carmarketplace.car.domain;

public enum Governorate {
    ARIANA("Ariana"),
    BEJA("Béja"),
    BEN_AROUS("Ben Arous"),
    BIZERTE("Bizerte"),
    GABES("Gabès"),
    GAFSA("Gafsa"),
    JENDOUBA("Jendouba"),
    KAIROUAN("Kairouan"),
    KASSERINE("Kasserine"),
    KEBILI("Kébili"),
    KEF("Le Kef"),
    MAHDIA("Mahdia"),
    MANOUBA("La Manouba"),
    MEDENINE("Médenine"),
    MONASTIR("Monastir"),
    NABEUL("Nabeul"),
    SFAX("Sfax"),
    SIDI_BOUZID("Sidi Bouzid"),
    SILIANA("Siliana"),
    SOUSSE("Sousse"),
    TATAOUINE("Tataouine"),
    TOZEUR("Tozeur"),
    TUNIS("Tunis"),
    ZAGHOUAN("Zaghouan");

    private final String displayName;

    Governorate(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
