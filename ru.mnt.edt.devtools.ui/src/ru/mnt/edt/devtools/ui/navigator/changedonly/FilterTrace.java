package ru.mnt.edt.devtools.ui.navigator.changedonly;

import org.eclipse.core.runtime.ILog;

/**
 * ВРЕМЕННО (0.2.4): решения фильтра по папкам и группам в журнал EDT,
 * чтобы разобраться, почему видна лишняя папка. Удалить после отладки.
 */
final class FilterTrace {

    private FilterTrace() {
    }

    static void log(String message) {
        ILog.of(FilterTrace.class).info("[ChangedOnly] " + message); //$NON-NLS-1$
    }
}
