# MNT DevTools для 1C:EDT

Набор инструментов разработчика для 1C:EDT. Плагин развивается постепенно: каждая крупная возможность добавляется отдельным модулем или пакетом.

**Сейчас в работе:** кнопка в Навигаторе «Только изменённые объекты» — версия 0.2.7: отбор по незакоммиченным изменениям Git работает. Подробности: [docs/architecture.md](docs/architecture.md).

## Окружение

| Что | Версия |
|---|---|
| 1C:EDT | 2026.1.1 (Eclipse 4.30 / 2023-12, EGit 6.8.12) |
| Java в EDT | Axiom JDK 17.0.16, x86_64 |
| Уровень компиляции | Java 17 |
| Сборка | Maven 3.9+ и Tycho 4 (или экспорт из Eclipse) |

## Структура

```
ru.mnt.edt.devtools.target      — целевая платформа: плагины установленного EDT
ru.mnt.edt.devtools.core        — логика без UI (проекты, изменения, обёртки над API EDT)
ru.mnt.edt.devtools.ui          — интерфейс: кнопки, фильтры, иконки
ru.mnt.edt.devtools.feature     — устанавливаемая единица
ru.mnt.edt.devtools.repository  — сайт обновлений (p2), из него плагин ставится в EDT
```

## Правила разработки

1. **Поддерживаемость и расширяемость.** Новая крупная возможность — отдельный модуль или пакет; общее (проекты, изменения, настройки) — в `core`.
2. **Изоляция от EDT.** Всё, что завязано на внутреннее устройство EDT (ID видов, natures, классы узлов навигатора, внутренний API EGit), лежит только в пакетах `*.edt`. При переходе на новую версию EDT правки ищем там.
3. **Комментарии** короткие, на русском, объясняют «зачем», а не пересказывают код.
4. **Внешний вид** (размещение кнопок, названия, иконки, тексты) согласуется с Германом до реализации.
5. **Строки интерфейса** только в `.properties` (кириллица хранится как `\uXXXX`), не в коде.
6. Версия плагина в `MANIFEST.MF`/`feature.xml` (`0.1.0.qualifier`) и в `pom.xml` (`0.1.0-SNAPSHOT`) меняется синхронно.

## Сборка

### Вариант 1: Maven (рекомендуется)

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn clean verify
```

Сайт обновлений появится в `ru.mnt.edt.devtools.repository/target/repository`.

Если Maven не установлен: `brew install maven`.

### Вариант 2: Eclipse

1. **File → Import → Existing Projects into Workspace**, выбрать корень репозитория, включить **Search for nested projects**.
2. Открыть `ru.mnt.edt.devtools.target/ru.mnt.edt.devtools.target.target` и нажать **Set as Active Target Platform**.
3. **File → Export → Plug-in Development → Deployable features**, выбрать `ru.mnt.edt.devtools.feature`, указать папку. На вкладке **Options** должно быть включено создание p2-репозитория (Generate p2 repository / metadata).

## Установка в EDT

> **macOS + 1C:EDT Start:** после установки или обновления плагина закройте EDT и выполните `bash tools/edt-doctor.sh --fix` (подробности: [docs/install-macos-edt-start.md](docs/install-macos-edt-start.md)).

**Справка → Установить новое ПО → Добавить → Локальный…** → выбрать папку сайта обновлений → отметить «MNT DevTools для 1C:EDT» → Далее → перезапустить EDT.

Обновление: то же самое, EDT предложит обновить установленную версию. Если не предлагает, повысьте версию плагина или удалите старую через **Справка → О программе → Сведения об установке**.

## Смена версии EDT

1. Поменять путь в `ru.mnt.edt.devtools.target/ru.mnt.edt.devtools.target.target`.
2. Собрать. Если сломалось, смотреть пакеты `*.edt`.
