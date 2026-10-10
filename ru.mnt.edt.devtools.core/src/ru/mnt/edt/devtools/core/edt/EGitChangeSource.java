package ru.mnt.edt.devtools.core.edt;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.egit.core.internal.indexdiff.IndexDiffCache;
import org.eclipse.egit.core.internal.indexdiff.IndexDiffCacheEntry;
import org.eclipse.egit.core.internal.indexdiff.IndexDiffChangedListener;
import org.eclipse.egit.core.internal.indexdiff.IndexDiffData;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.jgit.lib.Repository;

import ru.mnt.edt.devtools.core.changes.IChangeSource;

/**
 * Изменения из кэша Git-статуса EGit — того же, по которому EGit подсвечивает файлы,
 * поэтому отбор совпадает с подсветкой и статус большого репозитория не считается повторно.
 *
 * ВНИМАНИЕ: IndexDiffCache — внутренний API EGit (проверено на EGit 6.8). При переходе
 * на новую версию EDT проверять в первую очередь этот класс.
 */
public final class EGitChangeSource implements IChangeSource {

    private volatile Runnable onChange;

    /** EGit пересчитал статус какого-либо репозитория. */
    private final IndexDiffChangedListener indexListener = (repository, data) -> fire();

    /** Проект добавлен, удалён, открыт или закрыт — меняется набор отслеживаемых проектов. */
    private final IResourceChangeListener projectListener = this::projectsChanged;

    @Override
    public void start(Runnable listener) {
        this.onChange = listener;
        IndexDiffCache.INSTANCE.addIndexDiffChangedListener(indexListener);
        ResourcesPlugin.getWorkspace().addResourceChangeListener(projectListener, IResourceChangeEvent.POST_CHANGE);
    }

    @Override
    public void stop() {
        IndexDiffCache.INSTANCE.removeIndexDiffChangedListener(indexListener);
        ResourcesPlugin.getWorkspace().removeResourceChangeListener(projectListener);
        onChange = null;
    }

    @Override
    public Optional<Map<IProject, Set<String>>> collect() {
        Map<IProject, Set<String>> result = new HashMap<>();
        for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
            if (!ProjectKind.of(project).hasMetadataObjects()) {
                continue;
            }
            RepositoryMapping mapping = RepositoryMapping.getMapping(project);
            Repository repository = mapping == null ? null : mapping.getRepository();
            String prefix = mapping == null ? null : mapping.getRepoRelativePath(project);
            if (repository == null || prefix == null) {
                // Проект не под Git: изменений нет
                result.put(project, Set.of());
                continue;
            }
            IndexDiffCacheEntry entry = IndexDiffCache.INSTANCE.getIndexDiffCacheEntry(repository);
            IndexDiffData data = entry == null ? null : entry.getIndexDiff();
            if (data == null) {
                // EGit ещё считает статус; пересчитаем, когда он сообщит о готовности
                return Optional.empty();
            }
            result.put(project, projectFiles(data, prefix));
        }
        return Optional.of(result);
    }

    /** Все изменённые, новые, удалённые и конфликтные файлы проекта, пути относительно проекта. */
    private static Set<String> projectFiles(IndexDiffData data, String projectPrefix) {
        Set<String> files = new HashSet<>();
        String prefix = projectPrefix.isEmpty() ? "" : projectPrefix + '/'; //$NON-NLS-1$
        for (Set<String> paths : java.util.List.of(data.getAdded(), data.getChanged(), data.getModified(),
            data.getRemoved(), data.getMissing(), data.getUntracked(), data.getUntrackedFolders(),
            data.getConflicting())) {
            for (String path : paths) {
                if (path.startsWith(prefix)) {
                    files.add(path.substring(prefix.length()));
                }
            }
        }
        return files;
    }

    private void projectsChanged(IResourceChangeEvent event) {
        IResourceDelta delta = event.getDelta();
        if (delta == null) {
            return;
        }
        for (IResourceDelta child : delta.getAffectedChildren()) {
            boolean structural = child.getKind() != IResourceDelta.CHANGED
                || (child.getFlags() & (IResourceDelta.OPEN | IResourceDelta.DESCRIPTION)) != 0;
            if (structural) {
                fire();
                return;
            }
        }
    }

    private void fire() {
        Runnable listener = onChange;
        if (listener != null) {
            listener.run();
        }
    }
}
