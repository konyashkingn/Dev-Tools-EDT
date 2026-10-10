package ru.mnt.edt.devtools.core.edt;

/**
 * Идентификаторы типов (natures) проектов 1C:EDT.
 * Источник: plugin.xml бандла com._1c.g5.v8.dt.core (EDT 2026.1).
 */
public final class EdtNatures {

    /** Проект конфигурации. */
    public static final String CONFIGURATION = "com._1c.g5.v8.dt.core.V8ConfigurationNature"; //$NON-NLS-1$

    /** Проект расширения конфигурации. */
    public static final String EXTENSION = "com._1c.g5.v8.dt.core.V8ExtensionNature"; //$NON-NLS-1$

    /** Проект внешних отчётов и обработок. */
    public static final String EXTERNAL_OBJECTS = "com._1c.g5.v8.dt.core.V8ExternalObjectsNature"; //$NON-NLS-1$

    private EdtNatures() {
    }
}
