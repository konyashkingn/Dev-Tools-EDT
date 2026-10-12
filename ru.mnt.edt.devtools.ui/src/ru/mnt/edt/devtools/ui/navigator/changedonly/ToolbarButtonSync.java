package ru.mnt.edt.devtools.ui.navigator.changedonly;

import java.util.List;

import org.eclipse.e4.ui.model.application.MApplication;
import org.eclipse.e4.ui.model.application.commands.MCommand;
import org.eclipse.e4.ui.model.application.ui.menu.MHandledToolItem;
import org.eclipse.e4.ui.workbench.modeling.EModelService;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PlatformUI;

/**
 * Приводит вид кнопки в модели окна EDT к фактическому состоянию отбора.
 *
 * Иконка и «нажатость» кнопки хранятся в модели окна (MHandledToolItem), которую EDT сохраняет
 * при выходе и восстанавливает при запуске. Поэтому после перезапуска кнопка могла выглядеть
 * нажатой, хотя отбор выключен. Вызывается из UI-потока.
 */
final class ToolbarButtonSync {

    private static final String ICON_ON =
        "platform:/plugin/ru.mnt.edt.devtools.ui/icons/etool16/changed_only_on.png"; //$NON-NLS-1$
    private static final String ICON_OFF =
        "platform:/plugin/ru.mnt.edt.devtools.ui/icons/etool16/changed_only_off.png"; //$NON-NLS-1$

    private ToolbarButtonSync() {
    }

    /** Выставляет всем кнопкам отбора (во всех окнах) вид «включено» или «выключено». */
    static void sync(boolean enabled) {
        IWorkbench workbench = PlatformUI.getWorkbench();
        MApplication application = workbench.getService(MApplication.class);
        EModelService modelService = workbench.getService(EModelService.class);
        if (application == null || modelService == null) {
            return;
        }
        List<MHandledToolItem> items = modelService.findElements(application, null, MHandledToolItem.class, null,
            EModelService.ANYWHERE | EModelService.IN_PART);
        for (MHandledToolItem item : items) {
            MCommand command = item.getCommand();
            if (command != null && ChangedOnlyFilterController.COMMAND_ID.equals(command.getElementId())) {
                item.setSelected(enabled);
                item.setIconURI(enabled ? ICON_ON : ICON_OFF);
            }
        }
    }
}
