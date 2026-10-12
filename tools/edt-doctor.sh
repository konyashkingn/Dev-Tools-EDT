#!/bin/bash
# Проверка и исправление установок 1C:EDT, созданных через 1C:EDT Start на macOS.
#
# Находит три известные проблемы:
#   1. Установщик пишет список модулей не туда, откуда его читает EDT
#      (в профиле p2 не задана папка конфигурации) — плагины «ставятся», но не загружаются.
#   2. EDT загружает модули из устаревшего кэша: в bundles.info новая версия плагина,
#      а работает старая (так было с MNT DevTools и 1С:Напарником).
#   3. После сбоя запуска EDT ищет конфигурацию по пути с «%20» вместо пробела.
#
# Использование (EDT должен быть закрыт для --fix):
#   bash edt-doctor.sh          — только проверка, ничего не меняет
#   bash edt-doctor.sh --fix    — исправить найденное (с резервными копиями)
#
# Переменные для проверки на другой машине/копии: INSTALLS, REGISTRY.

set -u
INSTALLS="${INSTALLS:-$HOME/Library/Application Support/1C/1cedtstart/installations}"
REGISTRY="${REGISTRY:-$HOME/.p2/org.eclipse.equinox.p2.engine/profileRegistry}"
STAMP=$(date +%Y%m%d-%H%M%S)
FIX=false
[ "${1:-}" = "--fix" ] && FIX=true
PROBLEMS=0

say() { printf '%s\n' "$*"; }
problem() { say "  ✗ $*"; PROBLEMS=$((PROBLEMS + 1)); }
ok() { say "  ✓ $*"; }

[ -d "$INSTALLS" ] || { say "Нет установок EDT Start: $INSTALLS"; exit 1; }

if $FIX && ps -axo command | grep -F "$INSTALLS" | grep -F "/Contents/MacOS/1cedt" | grep -vq grep; then
  say "EDT запущен. Закрой его полностью (Cmd+Q) и запусти скрипт ещё раз."
  exit 1
fi

# --- 1. Папка конфигурации в профиле p2 -------------------------------------------------
check_profile() {
  local eclipse="$1" conf="$2"
  local profile_dir="$REGISTRY/$(printf '%s' "$eclipse" | tr '/' '_').profile"
  if [ ! -d "$profile_dir" ]; then
    ok "профиль установщика не найден — пропускаю ($profile_dir)"
    return
  fi
  local latest
  latest=$(ls -t "$profile_dir"/*.profile.gz 2>/dev/null | head -1)
  [ -n "$latest" ] || { ok "нет состояний профиля — пропускаю"; return; }

  local install_folder current
  install_folder=$(gzip -dc "$latest" | grep -o "name='org.eclipse.equinox.p2.installFolder' value='[^']*'" | head -1 | sed "s/.*value='//; s/'$//")
  current=$(gzip -dc "$latest" | grep -o "name='org.eclipse.equinox.p2.configurationFolder' value='[^']*'" | head -1 | sed "s/.*value='//; s/'$//")

  if [ -n "$current" ]; then
    ok "установщик пишет конфигурацию в правильную папку"
    return
  fi
  case "$install_folder" in
    */Contents/MacOS) ;;
    *) ok "установка устроена иначе (installFolder: $install_folder) — пропускаю"; return ;;
  esac
  problem "установщик пишет конфигурацию в Contents/MacOS, а EDT читает из Contents/Eclipse"
  $FIX || return

  local install_name
  install_name=$(basename "$(dirname "$(dirname "$(dirname "$eclipse")")")" | tr ' ' '_')
  local backup="$HOME/edt-p2-profile-backup-$STAMP-$install_name.tgz"
  tar -czf "$backup" -C "$REGISTRY" "$(basename "$profile_dir")" || { say "    не удалось сделать резервную копию профиля"; return; }
  local ts new
  ts=$(( $(date +%s) * 1000 ))
  new="$profile_dir/$ts.profile.gz"
  gzip -dc "$latest" | CONF="$conf" TS="$ts" perl -0pe '
    my $c = $ENV{CONF}; $c =~ s/&/&amp;/g; $c =~ s/\x27/&apos;/g;
    s{(<profile\b[^>]*\btimestamp=\x27)\d+(\x27)}{$1$ENV{TS}$2};
    s{(<profile\b[^>]*>\s*<properties size=\x27)(\d+)(\x27>)}
     {$1 . ($2 + 1) . $3 . "\n    <property name=\x27org.eclipse.equinox.p2.configurationFolder\x27 value=\x27$c\x27/>"}e;
  ' | gzip -c > "$new"
  if gzip -dc "$new" | grep -q "org.eclipse.equinox.p2.configurationFolder"; then
    say "    исправлено (резервная копия профиля: $backup)"
  else
    rm -f "$new"
    say "    не удалось изменить профиль, ничего не записано"
  fi
}

# --- 2. Устаревший кэш модулей ----------------------------------------------------------
check_cache() {
  local conf="$1"
  local info="$conf/org.eclipse.equinox.simpleconfigurator/bundles.info"
  local cache="$conf/org.eclipse.osgi"
  [ -f "$info" ] || { ok "нет bundles.info — пропускаю"; return; }
  local fw
  fw=$(ls -t "$cache"/framework.info.* 2>/dev/null | head -1)
  if [ -z "$fw" ]; then
    ok "кэш модулей пуст — EDT соберёт его при запуске"
    return
  fi

  # Имена файлов модулей: что должно загружаться (bundles.info) и что лежит в кэше
  local want have stale
  want=$(grep -v '^#' "$info" | cut -d, -f3 | sed 's|/$||; s|.*/||' | sort -u)
  have=$(grep -a -o 'plugins/[A-Za-z0-9._+-]*' "$fw" | sed 's|^plugins/||' | sort -u)
  # Устаревшие: модуль есть в кэше, но другой версии, чем в bundles.info
  stale=$(comm -23 <(printf '%s\n' "$want") <(printf '%s\n' "$have") | while IFS= read -r file; do
    name=${file%%_[0-9]*}
    printf '%s\n' "$have" | grep -q "^${name//./\\.}_[0-9]" && printf '%s\n' "$file"
  done)

  if [ -z "$stale" ]; then
    ok "кэш модулей соответствует списку модулей"
    return
  fi
  problem "EDT загружает старые версии модулей из кэша. Должны загружаться:"
  printf '%s\n' "$stale" | head -20 | sed 's/^/      /'
  $FIX || return

  # То же, что запуск с -clean, но без правки параметров запуска: кэш переносится в резервную
  # папку, и EDT при следующем запуске собирает его заново из bundles.info
  local backup="$conf/.mnt-cache-backup-$STAMP"
  mkdir -p "$backup" &&
    mv "$cache" "$backup/" &&
    { [ ! -d "$conf/org.eclipse.core.runtime" ] || mv "$conf/org.eclipse.core.runtime" "$backup/"; } &&
    say "    кэш перенесён в $backup — EDT соберёт новый при запуске (первый запуск дольше)"
}

# --- 3. Путь конфигурации с %20 ----------------------------------------------------------
check_encoded_path() {
  local conf="$1"
  local encoded="${conf// /%20}"
  [ "$encoded" = "$conf" ] && { ok "в пути нет пробелов — проверка не нужна"; return; }

  if [ -L "$encoded" ]; then
    ok "путь с %20 перенаправлен на настоящую конфигурацию"
    return
  fi
  if [ -e "$encoded" ]; then
    problem "есть ошибочная папка конфигурации с %20 (EDT мог запускаться с пустой конфигурацией)"
  else
    problem "нет защитной ссылки для пути с %20 (при сбое EDT создаст пустую конфигурацию)"
  fi
  $FIX || return

  if [ -e "$encoded" ]; then
    mv "$encoded" "$HOME/edt-config-20-backup-$STAMP" || return
  fi
  mkdir -p "$(dirname "$encoded")" && ln -s "$conf" "$encoded" &&
    say "    путь с %20 теперь ведёт на настоящую конфигурацию"
}

for app in "$INSTALLS"/*/1cedt.app; do
  [ -d "$app" ] || continue
  eclipse="$app/Contents/Eclipse"
  conf="$eclipse/configuration"
  say ""
  say "=== $(basename "$(dirname "$app")") ($(grep -h '^product.version=' "$conf/config.ini" 2>/dev/null | cut -d= -f2))"
  check_profile "$eclipse" "$conf"
  check_cache "$conf"
  check_encoded_path "$conf"
done

say ""
if [ "$PROBLEMS" -eq 0 ]; then
  say "Проблем не найдено."
elif $FIX; then
  say "Исправлено проблем: $PROBLEMS. Запусти EDT через 1C:EDT Start."
else
  say "Найдено проблем: $PROBLEMS. Для исправления закрой EDT и запусти: bash $0 --fix"
fi
