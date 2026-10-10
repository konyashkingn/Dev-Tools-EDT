package ru.mnt.edt.devtools.ui;

import org.eclipse.osgi.util.NLS;

/**
 * Локализуемые строки интерфейса (messages.properties рядом с классом).
 */
public final class Messages extends NLS {

    private static final String BUNDLE_NAME = "ru.mnt.edt.devtools.ui.messages"; //$NON-NLS-1$

    public static String ChangedOnly_tooltipOff;
    public static String ChangedOnly_tooltipOn;

    static {
        NLS.initializeMessages(BUNDLE_NAME, Messages.class);
    }

    private Messages() {
    }
}
