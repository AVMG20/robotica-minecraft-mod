package com.arno.robotica.storage.client;

/**
 * A recipe viewer's search box the Storage Terminal can sync with. The JEI plugin installs one with {@link #set}
 * while its runtime is available; with JEI absent {@link #get} stays null and the terminal hides its sync button.
 */
public interface ExternalSearch {
    String getText();

    void setText(String text);

    static ExternalSearch get() {
        return Holder.current;
    }

    static void set(ExternalSearch search) {
        Holder.current = search;
    }

    final class Holder {
        private static ExternalSearch current;

        private Holder() {}
    }
}
