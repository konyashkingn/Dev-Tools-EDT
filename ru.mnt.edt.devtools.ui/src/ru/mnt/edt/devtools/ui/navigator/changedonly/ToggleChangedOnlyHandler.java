package ru.mnt.edt.devtools.ui.navigator.changedonly;

import java.util.Map;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.ui.commands.IElementUpdater;
import org.eclipse.ui.menus.UIElement;

import ru.mnt.edt.devtools.ui.DevToolsImages;
import ru.mnt.edt.devtools.ui.Messages;

/**
 * Обработчик кнопки «Только изменённые объекты».
 * Помимо переключения отбора меняет иконку и подсказку кнопки в зависимости от состояния.
 */
public class ToggleChangedOnlyHandler extends AbstractHandler implements IElementUpdater {

    @Override
    public Object execute(ExecutionEvent event) {
        ChangedOnlyFilterController controller = ChangedOnlyFilterController.getInstance();
        controller.install();
        controller.setEnabled(!controller.isEnabled());
        return null;
    }

    @Override
    public void updateElement(UIElement element, @SuppressWarnings("rawtypes") Map parameters) {
        boolean enabled = ChangedOnlyFilterController.getInstance().isEnabled();
        element.setChecked(enabled);
        element.setIcon(enabled ? DevToolsImages.CHANGED_ONLY_ON : DevToolsImages.CHANGED_ONLY_OFF);
        element.setTooltip(enabled ? Messages.ChangedOnly_tooltipOn : Messages.ChangedOnly_tooltipOff);
    }
}
