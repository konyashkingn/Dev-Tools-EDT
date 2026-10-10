package ru.mnt.edt.devtools.core.edt;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;

/**
 * Вид проекта рабочей области с точки зрения плагина.
 */
public enum ProjectKind {

    CONFIGURATION,
    EXTENSION,
    EXTERNAL_OBJECTS,
    /** Не проект 1С (например, проект сервера) либо закрытый проект. */
    OTHER;

    /** Определяет вид проекта по его natures. */
    public static ProjectKind of(IProject project) {
        if (project == null || !project.isOpen()) {
            return OTHER;
        }
        if (hasNature(project, EdtNatures.CONFIGURATION)) {
            return CONFIGURATION;
        }
        if (hasNature(project, EdtNatures.EXTENSION)) {
            return EXTENSION;
        }
        if (hasNature(project, EdtNatures.EXTERNAL_OBJECTS)) {
            return EXTERNAL_OBJECTS;
        }
        return OTHER;
    }

    /** Конфигурации и расширения: в них отслеживаются изменённые объекты метаданных. */
    public boolean hasMetadataObjects() {
        return this == CONFIGURATION || this == EXTENSION;
    }

    private static boolean hasNature(IProject project, String natureId) {
        try {
            return project.hasNature(natureId);
        } catch (CoreException e) {
            // Проект закрыт или недоступен — считаем, что nature нет
            return false;
        }
    }
}
