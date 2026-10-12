package ru.mnt.edt.devtools.ui.navigator.changedonly;

import org.eclipse.core.runtime.Platform;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.PlatformUI;

import ru.mnt.edt.devtools.core.DevToolsLog;

/**
 * При запуске EDT: отбор выключен, поэтому приводим кнопку к виду «выключено»
 * и подключаем контроллер (он следит за открытием навигатора и выходом из EDT).
 */
public class ChangedOnlyStartup implements IStartup {

    @Override
    public void earlyStartup() {
        DevToolsLog.log("Запуск EDT, MNT DevTools " //$NON-NLS-1$
            + Platform.getBundle("ru.mnt.edt.devtools.ui").getVersion()); //$NON-NLS-1$
        // earlyStartup вызывается в фоновом потоке, а работать с окном можно только из UI-потока
        PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
            ToolbarButtonSync.sync(false);
            ChangedOnlyFilterController.getInstance().install();
        });
    }
}
