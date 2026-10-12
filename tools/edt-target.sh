#!/bin/bash
# Готовит целевую платформу для сборки плагина из установки 1C:EDT Start.
#
# В установках EDT Start модули лежат не в папке EDT, а в общем пуле ~/.p2/pool вперемешку
# с другими версиями EDT. Скрипт берёт ровно те модули, которые загружает выбранная
# установка (её bundles.info), и собирает из ссылок на них папку ~/.mnt-edt-target/plugins.
# Целевая платформа (ru.mnt.edt.devtools.target) указывает на эту папку.
#
# Использование:
#   bash edt-target.sh                    — установка «1C_EDT 2026.1»
#   bash edt-target.sh "1C_EDT 2026.2"    — другая установка EDT Start
#
# Повторять после обновления EDT через EDT Start.

set -u
NAME="${1:-1C_EDT 2026.1}"
INSTALLS="${INSTALLS:-$HOME/Library/Application Support/1C/1cedtstart/installations}"
TARGET="${TARGET:-$HOME/.mnt-edt-target}"

APP="$INSTALLS/$NAME/1cedt.app/Contents"
INFO="$APP/Eclipse/configuration/org.eclipse.equinox.simpleconfigurator/bundles.info"
# Пути в bundles.info заданы относительно папки установки (osgi.install.area = Contents/MacOS)
BASE="$APP/MacOS"

[ -f "$INFO" ] || { echo "Не найден список модулей: $INFO"; exit 1; }
[ -d "$BASE" ] || { echo "Не найдена папка установки: $BASE"; exit 1; }

# Старые ссылки удаляются, новые создаются заново
mkdir -p "$TARGET/plugins" || exit 1
find "$TARGET/plugins" -maxdepth 1 -type l -delete

result=$(perl -MCwd=abs_path -e '
  my ($info, $base, $out) = @ARGV;
  open(my $fh, "<", $info) or die "нет $info\n";
  my ($ok, $missing) = (0, 0);
  while (<$fh>) {
    chomp; s/\r$//;
    next if /^#/ || $_ eq "";
    my @f = split /,/;
    next unless @f >= 3;
    my $loc = $f[2];
    $loc =~ s{^file:}{};
    $loc =~ s{/$}{};
    my $path = $loc =~ m{^/} ? $loc : "$base/$loc";
    my $abs = abs_path($path);
    if (!defined $abs || !-e $abs) { $missing++; print STDERR "  нет файла: $loc\n"; next; }
    (my $name = $abs) =~ s{.*/}{};
    symlink($abs, "$out/$name") and $ok++;
  }
  print "$ok $missing\n";
' "$INFO" "$BASE" "$TARGET/plugins") || exit 1

linked=${result% *}
missing=${result#* }
version=$(grep -h '^product.version=' "$APP/Eclipse/configuration/config.ini" 2>/dev/null | cut -d= -f2)
printf 'installation=%s\nproduct.version=%s\nbundles=%s\n' "$NAME" "$version" "$linked" > "$TARGET/edt-target.properties"

echo "Целевая платформа: $TARGET"
echo "Установка: $NAME (EDT $version), модулей: $linked"
[ "$missing" -eq 0 ] || echo "Не найдено файлов: $missing (см. выше)"
