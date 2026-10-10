package ru.mnt.edt.devtools.ui.navigator.changedonly;

import java.util.Arrays;

import org.eclipse.core.commands.Command;
import org.eclipse.core.commands.State;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IWindowListener;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.handlers.RegistryToggleState;
import org.eclipse.ui.navigator.CommonViewer;

import ru.mnt.edt.devtools.ui.edt.EdtNavigator;

/**
 * Включает и выключает отбор «Только изменённые» во всех открытых навигаторах.
 * Состояние кнопки хранится в состоянии команды (RegistryToggleState) и переживает перезапуск EDT.
 * Все методы вызываются из UI-потока.
 */
public final class ChangedOnlyFilterController {

    public static final String COMMAND_ID = "ru.mnt.edt.devtools.ui.commands.toggleChangedOnly"; //$NON-NLS-1$

    private static final ChangedOnlyFilterController INSTANCE = new ChangedOnlyFilterController();

    private final ChangedOnlyFilter filter = new ChangedOnlyFilter();
    private boolean installed;

    /** Применяет отбор к навигатору сразу после его открытия. */
    private final IPartListener2 partListener = new IPartListener2() {
        @Override
        public void partOpened(IWorkbenchPartReference ref) {
            if (EdtNavigator.VIEW_ID.equals(ref.getId())) {
                EdtNavigator.viewerOf(ref.getPart(false)).ifPresent(ChangedOnlyFilterController.this::apply);
            }
        }
    };

    /** Подписывает на открытие навигатора и новые окна EDT. */
    private final IWindowListener windowListener = new IWindowListener() {
        @Override
        public void windowOpened(IWorkbenchWindow window) {
            hook(window);
        }

        @Override
        public void windowActivated(IWorkbenchWindow window) {
            // не требуется
        }

        @Override
        public void windowDeactivated(IWorkbenchWindow window) {
            // не требуется
        }

        @Override
        public void windowClosed(IWorkbenchWindow window) {
            window.getPartService().removePartListener(partListener);
        }
    };

    private ChangedOnlyFilterController() {
    }

    public static ChangedOnlyFilterController getInstance() {
        return INSTANCE;
    }

    /** Подключает слушатели и применяет сохранённое состояние. Повторный вызов ничего не делает. */
    public void install() {
        if (installed) {
            return;
        }
        installed = true;
        IWorkbench workbench = PlatformUI.getWorkbench();
        workbench.addWindowListener(windowListener);
        for (IWorkbenchWindow window : workbench.getWorkbenchWindows()) {
            hook(window);
        }
        applyToAllNavigators();
    }

    /** Включён ли отбор (нажата ли кнопка). */
    public boolean isEnabled() {
        State state = toggleState();
        return state != null && Boolean.TRUE.equals(state.getValue());
    }

    /** Переключает отбор и обновляет иконку кнопки. */
    public void setEnabled(boolean enabled) {
        State state = toggleState();
        if (state != null) {
            state.setValue(Boolean.valueOf(enabled));
        }
        applyToAllNavigators();
        commandService().refreshElements(COMMAND_ID, null);
    }

    private void hook(IWorkbenchWindow window) {
        window.getPartService().addPartListener(partListener);
    }

    private void applyToAllNavigators() {
        for (IWorkbenchWindow window : PlatformUI.getWorkbench().getWorkbenchWindows()) {
            for (IWorkbenchPage page : window.getPages()) {
                EdtNavigator.findViewer(page).ifPresent(this::apply);
            }
        }
    }

    /** Добавляет фильтр в дерево или убирает его в соответствии с состоянием кнопки. */
    private void apply(CommonViewer viewer) {
        boolean attached = Arrays.asList(viewer.getFilters()).contains(filter);
        boolean enabled = isEnabled();
        if (enabled && !attached) {
            viewer.addFilter(filter);
        } else if (!enabled && attached) {
            viewer.removeFilter(filter);
        }
    }

    private State toggleState() {
        Command command = commandService().getCommand(COMMAND_ID);
        return command.getState(RegistryToggleState.STATE_ID);
    }

    private static ICommandService commandService() {
        return PlatformUI.getWorkbench().getService(ICommandService.class);
    }
}
