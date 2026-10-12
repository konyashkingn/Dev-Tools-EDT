#!/bin/bash
# Проверка и исправление установок 1C:EDT, созданных через 1C:EDT Start на macOS.
#
# Находит три известные проблемы:
#   1. Установщик пишет список модулей не туда, откуда его читает EDT
#      (в профиле p2 не задана папка конфигурации) — плагины «ставятся», но не загружаются.
#   2. EDT загружает модули из устаревшего кэша: в bundles.info новая версия плагина,
#      а работает старая (так было с MNT DevTools и 1С:Напарником).
#   3. После сбоя запуска EDT ищет конфигурацию по пути с «%20» вместо пробела.
#   4. Список модулей (bundles.info) отстаёт от установленного: плагины, поставленные до
#      исправления п.1, числятся установленными, но загружается старая версия или ничего.
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

# --- 4. Список модулей против профиля установщика --------------------------------------
# Печатает действия: «REPLACE имя старая новая файл» и «ADD имя версия файл».
# Учитываются только модули (osgi.bundle), чей файл есть в пуле; исходники (*.source) пропускаются.
bundles_diff() {
  local profile_gz="$1" info="$2" pool="$3"
  gzip -dc "$profile_gz" | perl -e '
    my ($info, $pool) = @ARGV;
    my $xml = do { local $/; <STDIN> };
    my %prof;
    while ($xml =~ m{<unit id=\x27([^\x27]+)\x27 version=\x27([^\x27]+)\x27(.*?)</unit>}gs) {
      my ($id, $ver, $body) = ($1, $2, $3);
      next unless $body =~ /classifier=\x27osgi\.bundle\x27/;
      next if $id =~ /\.source$/;
      $prof{$id} = $ver;
    }
    my %have;
    open(my $fh, "<", $info) or die "нет $info\n";
    while (<$fh>) {
      next if /^#/;
      my @f = split /,/;
      next unless @f >= 3;
      $have{$f[0]}{$f[1]} = 1;
    }
    for my $id (sort keys %prof) {
      my $ver = $prof{$id};
      next if $have{$id} && $have{$id}{$ver};
      my $file = -e "$pool/${id}_$ver.jar" ? "${id}_$ver.jar" : -d "$pool/${id}_$ver" ? "${id}_$ver/" : undef;
      next unless defined $file;
      if ($have{$id}) {
        print "REPLACE $id ", join("+", sort keys %{$have{$id}}), " $ver $file\n";
      } else {
        print "ADD $id $ver $file\n";
      }
    }
  ' "$info" "$pool"
}

# Применяет действия bundles_diff к bundles.info (новый файл пишется рядом и подменяет старый)
bundles_apply() {
  local info="$1" actions="$2"
  ACTIONS="$actions" perl -e '
    my ($info) = @ARGV;
    my (%replace, @add);
    for (split /\n/, $ENV{ACTIONS}) {
      my @a = split / /;
      if ($a[0] eq "REPLACE") { $replace{$a[1]} = [$a[3], $a[4]]; }
      elsif ($a[0] eq "ADD") { push @add, [$a[1], $a[2], $a[3]]; }
    }
    open(my $in, "<", $info) or die;
    my (@out, $prefix, %done);
    while (my $line = <$in>) {
      chomp $line;
      my @f = split /,/, $line, -1;
      if ($line !~ /^#/ && @f >= 5) {
        $prefix //= $1 if $f[2] =~ m{^(.*/plugins/)};
        if (my $r = $replace{$f[0]}) {
          next if $done{$f[0]}++;      # несколько старых версий — оставляем одну строку
          (my $dir = $f[2]) =~ s{[^/]+/?$}{};
          $line = join(",", $f[0], $r->[0], $dir . $r->[1], @f[3 .. $#f]);
        }
      }
      push @out, $line;
    }
    close $in;
    die "не найден путь к пулу в bundles.info\n" if @add && !defined $prefix;
    push @out, join(",", $_->[0], $_->[1], $prefix . $_->[2], 4, "false") for @add;
    open(my $o, ">", "$info.new") or die;
    print $o "$_\n" for @out;
    close $o;
    rename("$info.new", $info) or die;
  ' "$info"
}

check_bundles() {
  local eclipse="$1" conf="$2"
  local info="$conf/org.eclipse.equinox.simpleconfigurator/bundles.info"
  local profile_dir="$REGISTRY/$(printf '%s' "$eclipse" | tr '/' '_').profile"
  local latest pool
  latest=$(ls -t "$profile_dir"/*.profile.gz 2>/dev/null | head -1)
  [ -n "$latest" ] && [ -f "$info" ] || { ok "нет профиля или bundles.info — сверку списка модулей пропускаю"; return 1; }
  pool=$(gzip -dc "$latest" | grep -o "name='org.eclipse.equinox.p2.cache' value='[^']*'" | head -1 | sed "s/.*value='//; s/'$//")
  [ -n "$pool" ] || pool="$HOME/.p2/pool"
  pool="$pool/plugins"

  local actions
  actions=$(bundles_diff "$latest" "$info" "$pool") || { say "    не удалось сравнить список модулей с профилем"; return 1; }
  if [ -z "$actions" ]; then
    ok "список модулей совпадает с установленным"
    return 1
  fi
  problem "список модулей отстаёт от установленного (загружаются старые версии или не загружаются вовсе):"
  printf '%s\n' "$actions" | awk '$1=="REPLACE"{print "      " $2 ": " $3 " → " $4} $1=="ADD"{print "      " $2 ": не загружается → " $3}'
  $FIX || return 1

  cp "$info" "$info.mnt-backup-$STAMP" || return 1
  if bundles_apply "$info" "$actions"; then
    say "    список модулей исправлен (резервная копия: $info.mnt-backup-$STAMP)"
    return 0
  fi
  say "    не удалось исправить список модулей, файл не изменён"
  return 1
}

# --- 2. Устаревший кэш модулей ----------------------------------------------------------
check_cache() {
  local conf="$1" force="${2:-false}"
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

  if [ -z "$stale" ] && ! $force; then
    ok "кэш модулей соответствует списку модулей"
    return
  fi
  if [ -n "$stale" ]; then
    problem "EDT загружает старые версии модулей из кэша. Должны загружаться:"
    printf '%s\n' "$stale" | head -20 | sed 's/^/      /'
  else
    say "  • список модулей изменён — кэш нужно пересобрать"
  fi
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
  if check_bundles "$eclipse" "$conf"; then
    check_cache "$conf" true
  else
    check_cache "$conf"
  fi
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
