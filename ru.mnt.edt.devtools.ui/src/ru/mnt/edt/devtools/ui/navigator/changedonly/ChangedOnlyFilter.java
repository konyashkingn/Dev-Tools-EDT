package ru.mnt.edt.devtools.ui.navigator.changedonly;

import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;

import ru.mnt.edt.devtools.ui.diagnostics.NavigatorNodeDump;

/**
 * Фильтр дерева навигатора «Только изменённые объекты».
 *
 * Шаг 1: заглушка. Пропускает все узлы и записывает их устройство в диагностический файл,
 * чтобы на шаге 3 научиться определять тип узла (проект, группа, объект метаданных).
 */
public class ChangedOnlyFilter extends ViewerFilter {

    @Override
    public boolean select(Viewer viewer, Object parentElement, Object element) {
        NavigatorNodeDump.record(viewer, parentElement, element);
        return true;
    }
}
