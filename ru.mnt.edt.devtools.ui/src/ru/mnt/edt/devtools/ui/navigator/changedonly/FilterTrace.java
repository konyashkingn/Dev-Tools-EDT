package ru.mnt.edt.devtools.ui.navigator.changedonly;

import ru.mnt.edt.devtools.core.DevToolsLog;

/**
 * ВРЕМЕННО: решения фильтра по папкам и группам в ~/mnt-devtools.log,
 * чтобы разобраться, почему видна лишняя папка. Удалить после отладки.
 */
final class FilterTrace {

    private FilterTrace() {
    }

    static void log(String message) {
        DevToolsLog.log("[ChangedOnly] " + message); //$NON-NLS-1$
    }
}
