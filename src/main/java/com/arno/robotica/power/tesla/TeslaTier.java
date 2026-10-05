package com.arno.robotica.power.tesla;

import com.arno.robotica.power.PowerConfig;

/** Tesla Coil tiers. The link count is fixed per tier; rate and range come from the server config. */
public enum TeslaTier {
    I(4), II(8), III(12), IV(16), V(32);

    public final int maxLinks;

    TeslaTier(int maxLinks) {
        this.maxLinks = maxLinks;
    }

    /** 1-5 */
    public int number() {
        return ordinal() + 1;
    }

    public String id() {
        return "tesla_coil_" + number();
    }

    /** FE/t the coil sends in total over all its links. */
    public int rate() {
        return PowerConfig.teslaRate(number());
    }

    /** Link range in blocks (centre to centre). */
    public int range() {
        return PowerConfig.teslaRange(number());
    }
}
