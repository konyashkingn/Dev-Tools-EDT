package ru.mnt.edt.devtools.core.changes;

/**
 * Ссылка на объект метаданных верхнего уровня: тип (имя класса модели EDT) и имя.
 * Например, {@code Catalog / Номенклатура}. Корень конфигурации — {@link #CONFIGURATION}.
 */
public record MdObjectRef(String type, String name) {

    /** Корень конфигурации (свойства конфигурации, командный интерфейс и т.п.). */
    public static final MdObjectRef CONFIGURATION = new MdObjectRef("Configuration", ""); //$NON-NLS-1$ //$NON-NLS-2$

    public boolean isConfiguration() {
        return CONFIGURATION.equals(this);
    }

    @Override
    public String toString() {
        return isConfiguration() ? type : type + '.' + name;
    }
}
