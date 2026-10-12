package ru.mnt.edt.devtools.ui.navigator.changedonly;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.jface.viewers.IContentProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.ITreePathContentProvider;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;

import ru.mnt.edt.devtools.core.changes.ChangedObjectsSnapshot;
import ru.mnt.edt.devtools.core.changes.MdObjectRef;
import ru.mnt.edt.devtools.core.edt.EdtSourcePaths;
import ru.mnt.edt.devtools.core.edt.ProjectKind;
import ru.mnt.edt.devtools.ui.edt.EdtNavigatorNodes;

/**
 * Фильтр навигатора «Только изменённые объекты».
 *
 * Правила:
 * <ul>
 * <li>пока изменения не посчитаны — показываем всё;</li>
 * <li>проекты внешних обработок/отчётов и не-1С проекты — целиком;</li>
 * <li>проект конфигурации/расширения — если в нём есть изменения;</li>
 * <li>всё внутри изменённого объекта (реквизиты, формы…) — показываем;</li>
 * <li>объект верхнего уровня — если изменён;</li>
 * <li>папка («Справочники») — если в ней есть видимый изменённый объект;</li>
 * <li>прочие группы («Общие») — если виден хотя бы один дочерний узел.</li>
 * </ul>
 * Вызывается только из UI-потока.
 */
public class ChangedOnlyFilter extends ViewerFilter {

    /** Защита от слишком глубокой проверки вложенных групп. */
    private static final int MAX_GROUP_DEPTH = 4;

    private ChangedObjectsSnapshot snapshot = ChangedObjectsSnapshot.NOT_READY;
    private final Map<TreePath, Boolean> groupCache = new HashMap<>();
    private final Map<String, ProjectKind> projectKinds = new HashMap<>();

    /** Новый снимок изменений; после вызова дерево нужно обновить. */
    public void setSnapshot(ChangedObjectsSnapshot snapshot) {
        this.snapshot = snapshot;
        groupCache.clear();
        projectKinds.clear();
    }

    @Override
    public boolean select(Viewer viewer, Object parentElement, Object element) {
        if (!snapshot.isReady()) {
            return true;
        }
        if (element instanceof IProject project) {
            return !isFilterable(project) || snapshot.hasChanges(project.getName());
        }
        // Внутри проекта навигатор EDT передаёт родителя как путь от проекта (TreePath)
        if (!(parentElement instanceof TreePath parentPath)) {
            return true;
        }
        return isVisible(viewer, parentPath, element, 0);
    }

    private boolean isVisible(Viewer viewer, TreePath parentPath, Object element, int depth) {
        IProject project = EdtNavigatorNodes.project(parentPath);
        if (project == null || !isFilterable(project)) {
            return true;
        }
        String projectName = project.getName();
        if (isInsideChangedObject(projectName, parentPath)) {
            return true;
        }

        MdObjectRef ref = EdtNavigatorNodes.mdObject(element);
        if (ref != null) {
            if (ref.isConfiguration()) {
                return snapshot.hasChanges(projectName);
            }
            // Подчинённые объекты вне изменённого объекта сюда не попадают; на всякий случай показываем
            return !EdtSourcePaths.isTopLevelType(ref.type()) || snapshot.isChanged(projectName, ref);
        }

        String folderType = EdtNavigatorNodes.folderType(element);
        if (folderType != null) {
            // Быстрая проверка по типу, затем по содержимому: изменения могут относиться
            // к удалённым объектам, которых в папке уже нет, — тогда папка пустая и не нужна
            return snapshot.hasChangesOfType(projectName, folderType)
                && folderHasChangedObject(viewer, parentPath.createChildPath(element), projectName, folderType);
        }

        TreePath groupPath = parentPath.createChildPath(element);
        boolean known = groupCache.containsKey(groupPath);
        boolean visible = hasVisibleChild(viewer, groupPath, depth);
        if (!known && visible) {
            FilterTrace.log("группа видна: " + element.getClass().getName() + " depth=" + depth); //$NON-NLS-1$ //$NON-NLS-2$
        }
        return visible;
    }

    /** Есть ли среди предков узла изменённый объект верхнего уровня. */
    private boolean isInsideChangedObject(String projectName, TreePath parentPath) {
        for (int i = 1; i < parentPath.getSegmentCount(); i++) {
            MdObjectRef ref = EdtNavigatorNodes.mdObject(parentPath.getSegment(i));
            if (ref != null && !ref.isConfiguration() && EdtSourcePaths.isTopLevelType(ref.type())
                && snapshot.isChanged(projectName, ref)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Есть ли в папке изменённый объект. Проверяем только непосредственное содержимое папки,
     * без рекурсии: это точнее и быстрее. Если содержимое не удалось распознать как объекты,
     * доверяем проверке по типу (папку не скрываем).
     */
    private boolean folderHasChangedObject(Viewer viewer, TreePath folderPath, String projectName, String type) {
        Boolean cached = groupCache.get(folderPath);
        if (cached != null) {
            return cached;
        }
        Object[] children = children(viewer, folderPath);
        boolean result;
        String reason;
        if (children == null) {
            result = true;
            reason = "содержимое недоступно"; //$NON-NLS-1$
        } else {
            boolean sawObjects = false;
            MdObjectRef changed = null;
            for (Object child : children) {
                MdObjectRef ref = EdtNavigatorNodes.mdObject(child);
                if (ref == null) {
                    continue;
                }
                sawObjects = true;
                if (snapshot.isChanged(projectName, ref)) {
                    changed = ref;
                    break;
                }
            }
            result = changed != null || !sawObjects && children.length > 0;
            reason = changed != null ? "изменён " + changed //$NON-NLS-1$
                : "объектов " + (sawObjects ? "распознано" : "не распознано") + ", элементов " + children.length //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                    + (children.length > 0 ? ", первый: " + children[0].getClass().getName() : ""); //$NON-NLS-1$ //$NON-NLS-2$
        }
        FilterTrace.log("папка " + type + (result ? " видна" : " скрыта") + ": " + reason); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        groupCache.put(folderPath, result);
        return result;
    }

    /** Группа без собственного типа видна, если виден хотя бы один её дочерний узел. */
    private boolean hasVisibleChild(Viewer viewer, TreePath path, int depth) {
        if (depth >= MAX_GROUP_DEPTH) {
            return true;
        }
        Boolean cached = groupCache.get(path);
        if (cached != null) {
            return cached;
        }
        Object[] children = children(viewer, path);
        boolean visible = children == null; // не смогли узнать содержимое — не скрываем
        if (children != null) {
            for (Object child : children) {
                if (isVisible(viewer, path, child, depth + 1)) {
                    visible = true;
                    break;
                }
            }
        }
        groupCache.put(path, visible);
        return visible;
    }

    private static Object[] children(Viewer viewer, TreePath path) {
        if (!(viewer instanceof StructuredViewer structured)) {
            return null;
        }
        IContentProvider provider = structured.getContentProvider();
        if (provider instanceof ITreePathContentProvider pathProvider) {
            return pathProvider.getChildren(path);
        }
        if (provider instanceof ITreeContentProvider treeProvider) {
            return treeProvider.getChildren(path.getLastSegment());
        }
        return null;
    }

    private boolean isFilterable(IProject project) {
        return projectKinds.computeIfAbsent(project.getName(), name -> ProjectKind.of(project)).hasMetadataObjects();
    }
}
