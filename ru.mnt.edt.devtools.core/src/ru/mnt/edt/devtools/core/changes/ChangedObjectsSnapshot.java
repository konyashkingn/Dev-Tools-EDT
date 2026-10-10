package ru.mnt.edt.devtools.core.changes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Неизменяемый снимок изменённых объектов метаданных по проектам.
 * Безопасно читается из любого потока.
 */
public final class ChangedObjectsSnapshot {

    /** Данные ещё не готовы (Git-статус не посчитан): фильтровать нельзя. */
    public static final ChangedObjectsSnapshot NOT_READY = new ChangedObjectsSnapshot(Map.of(), false);

    private final boolean ready;
    /** Имя проекта → изменённые объекты. */
    private final Map<String, Set<MdObjectRef>> objects;
    /** Имя проекта → типы, у которых есть изменённые объекты (для групп «Справочники» и т.п.). */
    private final Map<String, Set<String>> types;

    public ChangedObjectsSnapshot(Map<String, Set<MdObjectRef>> objectsByProject, boolean ready) {
        this.ready = ready;
        Map<String, Set<MdObjectRef>> objectsCopy = new HashMap<>();
        Map<String, Set<String>> typesCopy = new HashMap<>();
        objectsByProject.forEach((project, refs) -> {
            if (refs.isEmpty()) {
                return;
            }
            objectsCopy.put(project, Set.copyOf(refs));
            Set<String> projectTypes = new HashSet<>();
            refs.forEach(ref -> projectTypes.add(ref.type()));
            typesCopy.put(project, Set.copyOf(projectTypes));
        });
        this.objects = Map.copyOf(objectsCopy);
        this.types = Map.copyOf(typesCopy);
    }

    public boolean isReady() {
        return ready;
    }

    /** Есть ли в проекте хоть одно изменение. */
    public boolean hasChanges(String project) {
        return objects.containsKey(project);
    }

    public boolean isChanged(String project, MdObjectRef ref) {
        return objects.getOrDefault(project, Set.of()).contains(ref);
    }

    /** Есть ли в проекте изменённые объекты указанного типа (например, Catalog). */
    public boolean hasChangesOfType(String project, String type) {
        return types.getOrDefault(project, Set.of()).contains(type);
    }

    /** Общее число изменённых объектов. */
    public int size() {
        return objects.values().stream().mapToInt(Set::size).sum();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ChangedObjectsSnapshot other && ready == other.ready && objects.equals(other.objects);
    }

    @Override
    public int hashCode() {
        return objects.hashCode() * 31 + Boolean.hashCode(ready);
    }

    @Override
    public String toString() {
        return ready ? objects.toString() : "NOT_READY"; //$NON-NLS-1$
    }
}
