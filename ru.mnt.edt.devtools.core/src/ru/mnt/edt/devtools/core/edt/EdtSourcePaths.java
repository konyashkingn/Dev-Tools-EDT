package ru.mnt.edt.devtools.core.edt;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import ru.mnt.edt.devtools.core.changes.MdObjectRef;

/**
 * Структура исходников проекта EDT: src/&lt;Папка типа&gt;/&lt;Имя объекта&gt;/...
 * Сопоставляет путь файла с объектом метаданных верхнего уровня.
 * Новый тип метаданных добавляется одной строкой в таблицу ниже.
 */
public final class EdtSourcePaths {

    private static final String SRC = "src"; //$NON-NLS-1$
    private static final String CONFIGURATION_FOLDER = "Configuration"; //$NON-NLS-1$

    /** Папка в src → тип объекта (имя класса модели EDT, как в навигаторе). */
    private static final Map<String, String> FOLDER_TO_TYPE = new HashMap<>();

    static {
        type("AccountingRegisters", "AccountingRegister"); //$NON-NLS-1$ //$NON-NLS-2$
        type("AccumulationRegisters", "AccumulationRegister"); //$NON-NLS-1$ //$NON-NLS-2$
        type("Bots", "Bot"); //$NON-NLS-1$ //$NON-NLS-2$
        type("BusinessProcesses", "BusinessProcess"); //$NON-NLS-1$ //$NON-NLS-2$
        type("CalculationRegisters", "CalculationRegister"); //$NON-NLS-1$ //$NON-NLS-2$
        type("Catalogs", "Catalog"); //$NON-NLS-1$ //$NON-NLS-2$
        type("ChartsOfAccounts", "ChartOfAccounts"); //$NON-NLS-1$ //$NON-NLS-2$
        type("ChartsOfCalculationTypes", "ChartOfCalculationTypes"); //$NON-NLS-1$ //$NON-NLS-2$
        type("ChartsOfCharacteristicTypes", "ChartOfCharacteristicTypes"); //$NON-NLS-1$ //$NON-NLS-2$
        type("CommandGroups", "CommandGroup"); //$NON-NLS-1$ //$NON-NLS-2$
        type("CommonAttributes", "CommonAttribute"); //$NON-NLS-1$ //$NON-NLS-2$
        type("CommonCommands", "CommonCommand"); //$NON-NLS-1$ //$NON-NLS-2$
        type("CommonForms", "CommonForm"); //$NON-NLS-1$ //$NON-NLS-2$
        type("CommonModules", "CommonModule"); //$NON-NLS-1$ //$NON-NLS-2$
        type("CommonPictures", "CommonPicture"); //$NON-NLS-1$ //$NON-NLS-2$
        type("CommonTemplates", "CommonTemplate"); //$NON-NLS-1$ //$NON-NLS-2$
        type("Constants", "Constant"); //$NON-NLS-1$ //$NON-NLS-2$
        type("DataProcessors", "DataProcessor"); //$NON-NLS-1$ //$NON-NLS-2$
        type("DefinedTypes", "DefinedType"); //$NON-NLS-1$ //$NON-NLS-2$
        type("DocumentJournals", "DocumentJournal"); //$NON-NLS-1$ //$NON-NLS-2$
        type("DocumentNumerators", "DocumentNumerator"); //$NON-NLS-1$ //$NON-NLS-2$
        type("Documents", "Document"); //$NON-NLS-1$ //$NON-NLS-2$
        type("Enums", "Enum"); //$NON-NLS-1$ //$NON-NLS-2$
        type("EventSubscriptions", "EventSubscription"); //$NON-NLS-1$ //$NON-NLS-2$
        type("ExchangePlans", "ExchangePlan"); //$NON-NLS-1$ //$NON-NLS-2$
        type("ExternalDataSources", "ExternalDataSource"); //$NON-NLS-1$ //$NON-NLS-2$
        type("FilterCriteria", "FilterCriterion"); //$NON-NLS-1$ //$NON-NLS-2$
        type("FunctionalOptions", "FunctionalOption"); //$NON-NLS-1$ //$NON-NLS-2$
        type("FunctionalOptionsParameters", "FunctionalOptionsParameter"); //$NON-NLS-1$ //$NON-NLS-2$
        type("HTTPServices", "HTTPService"); //$NON-NLS-1$ //$NON-NLS-2$
        type("InformationRegisters", "InformationRegister"); //$NON-NLS-1$ //$NON-NLS-2$
        type("IntegrationServices", "IntegrationService"); //$NON-NLS-1$ //$NON-NLS-2$
        type("Languages", "Language"); //$NON-NLS-1$ //$NON-NLS-2$
        type("PaletteColors", "PaletteColor"); //$NON-NLS-1$ //$NON-NLS-2$
        type("Reports", "Report"); //$NON-NLS-1$ //$NON-NLS-2$
        type("Roles", "Role"); //$NON-NLS-1$ //$NON-NLS-2$
        type("ScheduledJobs", "ScheduledJob"); //$NON-NLS-1$ //$NON-NLS-2$
        type("Sequences", "Sequence"); //$NON-NLS-1$ //$NON-NLS-2$
        type("SessionParameters", "SessionParameter"); //$NON-NLS-1$ //$NON-NLS-2$
        type("SettingsStorages", "SettingsStorage"); //$NON-NLS-1$ //$NON-NLS-2$
        type("StyleItems", "StyleItem"); //$NON-NLS-1$ //$NON-NLS-2$
        type("Styles", "Style"); //$NON-NLS-1$ //$NON-NLS-2$
        type("Subsystems", "Subsystem"); //$NON-NLS-1$ //$NON-NLS-2$
        type("Tasks", "Task"); //$NON-NLS-1$ //$NON-NLS-2$
        type("WebServices", "WebService"); //$NON-NLS-1$ //$NON-NLS-2$
        type("WebSocketClients", "WebSocketClient"); //$NON-NLS-1$ //$NON-NLS-2$
        type("WSReferences", "WSReference"); //$NON-NLS-1$ //$NON-NLS-2$
        type("XDTOPackages", "XDTOPackage"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static final Set<String> TOP_LEVEL_TYPES = new HashSet<>(FOLDER_TO_TYPE.values());

    private EdtSourcePaths() {
    }

    private static void type(String folder, String type) {
        FOLDER_TO_TYPE.put(folder, type);
    }

    /**
     * Объект, к которому относится файл. Например:
     * src/Catalogs/Товары/Forms/ФормаЭлемента/Module.bsl → Catalog.Товары,
     * src/Configuration/Configuration.mdo → Configuration.
     *
     * @param projectRelativePath путь относительно проекта, разделитель «/»
     */
    public static Optional<MdObjectRef> toObject(String projectRelativePath) {
        String[] parts = projectRelativePath.split("/"); //$NON-NLS-1$
        if (parts.length < 2 || !SRC.equals(parts[0])) {
            return Optional.empty();
        }
        if (CONFIGURATION_FOLDER.equals(parts[1])) {
            return Optional.of(MdObjectRef.CONFIGURATION);
        }
        String type = FOLDER_TO_TYPE.get(parts[1]);
        if (type == null || parts.length < 3 || parts[2].isEmpty()) {
            return Optional.empty();
        }
        // Git на macOS может отдавать имена в разложенной форме (NFD: «й» = «и» + знак),
        // а модель EDT хранит их в составной (NFC) — приводим к одной форме
        return Optional.of(new MdObjectRef(type, Normalizer.normalize(parts[2], Normalizer.Form.NFC)));
    }

    /** Тип объекта верхнего уровня (справочник, документ…), а не подчинённого (реквизит, форма…). */
    public static boolean isTopLevelType(String type) {
        return TOP_LEVEL_TYPES.contains(type);
    }
}
