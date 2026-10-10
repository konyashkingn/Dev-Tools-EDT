#!/bin/bash
# Исправление установок 1C:EDT Start на macOS, в которых «Установить новое ПО» не работает.
#
# Проблема: в профиле p2 установки указан installFolder = <EDT>.app/Contents/MacOS, а папка
# конфигурации не указана. Поэтому установщик пишет список модулей в Contents/MacOS/configuration,
# а EDT запускается с -configuration ../Eclipse/configuration и этот список не видит.
# Итог: любые доустановленные плагины числятся в «Установленном ПО», но не загружаются.
#
# Исправление: добавить в профиль свойство org.eclipse.equinox.p2.configurationFolder,
# указывающее на Contents/Eclipse/configuration (туда же EDT Start записал конфигурацию
# при создании установки). Добавляется новое состояние профиля, старые не трогаются.
#
# Запускать при ЗАКРЫТОМ EDT:
#   bash fix-edt-start-p2-config.sh           — исправить все установки EDT Start
#   bash fix-edt-start-p2-config.sh --check   — только показать состояние
#
# Откат: удалить файл состояния профиля, который покажет скрипт (или восстановить из архива).

set -u
INSTALLS="$HOME/Library/Application Support/1C/1cedtstart/installations"
REGISTRY="$HOME/.p2/org.eclipse.equinox.p2.engine/profileRegistry"
PROP="org.eclipse.equinox.p2.configurationFolder"
CHECK_ONLY=false
[ "${1:-}" = "--check" ] && CHECK_ONLY=true

[ -d "$INSTALLS" ] || { echo "Нет установок EDT Start: $INSTALLS"; exit 1; }

if ! $CHECK_ONLY && ps -axo command | grep -F "$INSTALLS" | grep -vq grep; then
  echo "EDT запущен. Закрой его полностью (Cmd+Q) и запусти скрипт ещё раз."
  exit 1
fi

for app in "$INSTALLS"/*/1cedt.app; do
  [ -d "$app" ] || continue
  eclipse="$app/Contents/Eclipse"
  conf="$eclipse/configuration"
  # Имя профиля p2 — путь к Contents/Eclipse с заменой «/» на «_» (так его создаёт EDT Start)
  profile_dir="$REGISTRY/$(echo "$eclipse" | tr '/' '_').profile"
  echo "=== $(basename "$(dirname "$app")")"

  if [ ! -d "$profile_dir" ]; then
    echo "  профиль не найден: $profile_dir"
    continue
  fi
  latest=$(ls -t "$profile_dir"/*.profile.gz 2>/dev/null | head -1)
  [ -n "$latest" ] || { echo "  нет состояний профиля"; continue; }

  install_folder=$(gzcat "$latest" | grep -o "name='org.eclipse.equinox.p2.installFolder' value='[^']*'" | head -1 | sed "s/.*value='//; s/'$//")
  current=$(gzcat "$latest" | grep -o "name='$PROP' value='[^']*'" | head -1 | sed "s/.*value='//; s/'$//")
  echo "  installFolder:       $install_folder"
  echo "  configurationFolder: ${current:-(не задан)}"
  echo "  EDT читает из:       $conf"

  if [ -n "$current" ]; then
    echo "  исправление не требуется"
    continue
  fi
  case "$install_folder" in
    */Contents/MacOS) ;;
    *) echo "  installFolder не указывает на Contents/MacOS — установка устроена иначе, пропускаю"; continue ;;
  esac
  [ -f "$conf/org.eclipse.equinox.simpleconfigurator/bundles.info" ] \
    || { echo "  нет $conf/.../bundles.info — пропускаю"; continue; }
  $CHECK_ONLY && { echo "  требуется исправление"; continue; }

  # Резервная копия всего профиля
  backup="$HOME/edt-p2-profile-backup-$(date +%Y%m%d-%H%M%S)-$(basename "$(dirname "$app")" | tr ' ' '_').tgz"
  tar -czf "$backup" -C "$REGISTRY" "$(basename "$profile_dir")" || { echo "  не удалось сделать резервную копию"; exit 1; }
  echo "  резервная копия: $backup"

  # Новое состояние профиля: +1 свойство, новый timestamp (p2 берёт самое свежее состояние)
  ts=$(( $(date +%s) * 1000 ))
  new="$profile_dir/$ts.profile.gz"
  gzcat "$latest" | CONF="$conf" TS="$ts" perl -0pe '
    my $conf = $ENV{CONF}; $conf =~ s/&/&amp;/g; $conf =~ s/\x27/&apos;/g;
    s{(<profile\b[^>]*\btimestamp=\x27)\d+(\x27)}{$1$ENV{TS}$2};
    s{(<profile\b[^>]*>\s*<properties size=\x27)(\d+)(\x27>)}
     {$1 . ($2 + 1) . $3 . "\n    <property name=\x27org.eclipse.equinox.p2.configurationFolder\x27 value=\x27$conf\x27/>"}e;
  ' | gzip -c > "$new"

  if gzcat "$new" | grep -q "name='$PROP'"; then
    echo "  исправлено, новое состояние профиля: $new"
  else
    rm -f "$new"
    echo "  не удалось изменить профиль, ничего не записано"
  fi
done
