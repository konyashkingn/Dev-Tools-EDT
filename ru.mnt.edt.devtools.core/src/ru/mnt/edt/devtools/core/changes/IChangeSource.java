package ru.mnt.edt.devtools.core.changes;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.core.resources.IProject;

/**
 * Источник изменённых файлов. Реализация скрывает, откуда берётся статус
 * (сейчас — кэш EGit), чтобы её можно было заменить, не трогая остальной код.
 */
public interface IChangeSource {

    /**
     * Начинает слежение.
     *
     * @param onChange вызывается из любого потока, когда изменения могли поменяться
     */
    void start(Runnable onChange);

    void stop();

    /**
     * Изменённые файлы: проект → пути относительно проекта (например, src/Catalogs/Товары/Товары.mdo).
     * Пусто, если статус ещё считается и данные неполные.
     */
    Optional<Map<IProject, Set<String>>> collect();
}
