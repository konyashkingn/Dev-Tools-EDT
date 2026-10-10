package ru.mnt.edt.devtools.core.changes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

import ru.mnt.edt.devtools.core.edt.EGitChangeSource;
import ru.mnt.edt.devtools.core.edt.EdtSourcePaths;

/**
 * Следит за изменёнными объектами метаданных и рассылает подписчикам свежие снимки.
 *
 * Слежение включается с первым подписчиком и выключается с последним.
 * Пересчёт идёт в фоне с задержкой: серия событий (сохранение, переключение ветки)
 * даёт один пересчёт. Подписчики вызываются из фонового потока.
 */
public final class ChangeTracker {

    private static final long DELAY_MS = 300;
    private static final ChangeTracker INSTANCE = new ChangeTracker(new EGitChangeSource());

    private final IChangeSource source;
    private final List<Consumer<ChangedObjectsSnapshot>> listeners = new CopyOnWriteArrayList<>();
    private final Job job;
    private volatile ChangedObjectsSnapshot snapshot = ChangedObjectsSnapshot.NOT_READY;

    ChangeTracker(IChangeSource source) {
        this.source = source;
        this.job = Job.create("MNT DevTools: changed objects", monitor -> { //$NON-NLS-1$
            recompute();
            return Status.OK_STATUS;
        });
        this.job.setSystem(true);
    }

    public static ChangeTracker getInstance() {
        return INSTANCE;
    }

    /** Последний посчитанный снимок. */
    public ChangedObjectsSnapshot current() {
        return snapshot;
    }

    public synchronized void addListener(Consumer<ChangedObjectsSnapshot> listener) {
        listeners.add(listener);
        if (listeners.size() == 1) {
            source.start(this::scheduleUpdate);
            scheduleUpdate();
        }
    }

    public synchronized void removeListener(Consumer<ChangedObjectsSnapshot> listener) {
        listeners.remove(listener);
        if (listeners.isEmpty()) {
            source.stop();
            job.cancel();
            snapshot = ChangedObjectsSnapshot.NOT_READY;
        }
    }

    private void scheduleUpdate() {
        job.schedule(DELAY_MS);
    }

    private void recompute() {
        ChangedObjectsSnapshot next;
        try {
            next = source.collect().map(ChangeTracker::toSnapshot).orElse(ChangedObjectsSnapshot.NOT_READY);
        } catch (RuntimeException e) {
            // Ошибка источника не должна ломать навигатор: показываем всё, как без отбора
            ILog.of(ChangeTracker.class).error("Не удалось получить изменения из Git", e); //$NON-NLS-1$
            next = ChangedObjectsSnapshot.NOT_READY;
        }
        if (next.equals(snapshot)) {
            return;
        }
        snapshot = next;
        for (Consumer<ChangedObjectsSnapshot> listener : listeners) {
            listener.accept(next);
        }
    }

    /** Пути файлов → объекты метаданных. */
    private static ChangedObjectsSnapshot toSnapshot(Map<IProject, Set<String>> files) {
        Map<String, Set<MdObjectRef>> objects = new HashMap<>();
        files.forEach((project, paths) -> {
            Set<MdObjectRef> refs = new HashSet<>();
            for (String path : paths) {
                Optional<MdObjectRef> ref = EdtSourcePaths.toObject(path);
                ref.ifPresent(refs::add);
            }
            objects.put(project.getName(), refs);
        });
        return new ChangedObjectsSnapshot(objects, true);
    }
}
