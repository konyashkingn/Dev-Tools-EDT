package ru.mnt.edt.devtools.ui.navigator.changedonly;

import org.eclipse.ui.IStartup;
import org.eclipse.ui.PlatformUI;

/**
 * При запуске EDT восстанавливает отбор, если кнопка была нажата в прошлой сессии.
 */
public class ChangedOnlyStartup implements IStartup {

    @Override
    public void earlyStartup() {
        // earlyStartup вызывается в фоновом потоке, а работать с видами можно только из UI-потока
        PlatformUI.getWorkbench().getDisplay().asyncExec(
            () -> ChangedOnlyFilterController.getInstance().install());
    }
}
