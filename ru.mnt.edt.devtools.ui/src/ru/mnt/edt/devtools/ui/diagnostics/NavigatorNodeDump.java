package ru.mnt.edt.devtools.ui.diagnostics;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Adapters;
import org.eclipse.core.runtime.ILog;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.Viewer;

/**
 * ВРЕМЕННАЯ диагностика (шаг 1). Удалить после того, как фильтр научится распознавать узлы.
 *
 * Записывает в ~/edt_navigator_nodes.txt, из каких классов состоит дерево навигатора EDT:
 * иерархию, интерфейсы, геттеры, привязку к файлу. По 3 примера на каждую пару «узел ← родитель».
 * Любые ошибки здесь глушатся, чтобы диагностика не могла сломать навигатор.
 */
public final class NavigatorNodeDump {

    private static final int SAMPLES_PER_KEY = 3;
    private static final int MAX_GETTERS = 40;
    private static final int MAX_TEXT = 200;
    private static final Path FILE = Path.of(System.getProperty("user.home"), "edt_navigator_nodes.txt"); //$NON-NLS-1$

    private static final Map<String, Integer> SAMPLES = new HashMap<>();
    private static boolean headerWritten;

    private NavigatorNodeDump() {
    }

    /** Записывает описание узла дерева (вызывается из фильтра в UI-потоке). */
    public static void record(Viewer viewer, Object parent, Object element) {
        try {
            String key = className(element) + " <- " + className(parent); //$NON-NLS-1$
            int sample = SAMPLES.merge(key, 1, Integer::sum);
            if (sample > SAMPLES_PER_KEY) {
                return;
            }
            StringBuilder out = new StringBuilder();
            if (!headerWritten) {
                headerWritten = true;
                out.append("##### EDT navigator dump ").append(LocalDateTime.now()).append('\n'); //$NON-NLS-1$
                ILog.of(NavigatorNodeDump.class).info("Диагностика навигатора пишется в " + FILE); //$NON-NLS-1$
            }
            out.append("\n=== ").append(key).append("  #").append(sample).append('\n'); //$NON-NLS-1$ //$NON-NLS-2$
            out.append("  label:  ").append(label(viewer, element)).append('\n'); //$NON-NLS-1$
            out.append("  eClass: ").append(eClassName(element)).append('\n'); //$NON-NLS-1$
            out.append("  file:   ").append(resourcePath(element)).append('\n'); //$NON-NLS-1$
            if (sample == 1 && element != null) {
                try {
                    describeClass(element.getClass(), out);
                } catch (Throwable e) {
                    out.append("  describe failed: ").append(e).append('\n'); //$NON-NLS-1$
                }
            }
            Files.writeString(FILE, out, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Throwable e) {
            // диагностика не должна мешать работе навигатора
        }
    }

    private static String className(Object o) {
        return o == null ? "null" : o.getClass().getName(); //$NON-NLS-1$
    }

    /** Текст узла так, как его показывает навигатор. */
    private static String label(Viewer viewer, Object element) {
        try {
            if (viewer instanceof StructuredViewer structured
                && structured.getLabelProvider() instanceof ILabelProvider provider) {
                return truncate(provider.getText(element));
            }
        } catch (Throwable e) {
            // ниже — запасной вариант
        }
        return truncate(String.valueOf(element));
    }

    /** Имя EMF-класса, если узел — объект модели EDT (без зависимости от EMF). */
    private static String eClassName(Object element) {
        try {
            Method eClass = element.getClass().getMethod("eClass"); //$NON-NLS-1$
            Object cls = eClass.invoke(element);
            return String.valueOf(cls.getClass().getMethod("getName").invoke(cls)); //$NON-NLS-1$
        } catch (Throwable e) {
            return "-"; //$NON-NLS-1$
        }
    }

    /** Файл или папка, к которым привязан узел. */
    private static String resourcePath(Object element) {
        try {
            IResource resource = Adapters.adapt(element, IResource.class, true);
            return resource == null ? "-" : resource.getFullPath().toString(); //$NON-NLS-1$
        } catch (Throwable e) {
            return "-"; //$NON-NLS-1$
        }
    }

    /** Иерархия классов, интерфейсы и публичные геттеры — для поиска нужных методов. */
    private static void describeClass(Class<?> cls, StringBuilder out) {
        out.append("  extends:"); //$NON-NLS-1$
        for (Class<?> c = cls.getSuperclass(); c != null && c != Object.class; c = c.getSuperclass()) {
            out.append(' ').append(c.getName());
        }
        out.append('\n');

        Set<String> interfaces = new LinkedHashSet<>();
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            collectInterfaces(c, interfaces);
        }
        out.append("  implements: ").append(String.join(", ", interfaces)).append('\n'); //$NON-NLS-1$ //$NON-NLS-2$

        List<String> getters = new ArrayList<>();
        for (Method m : cls.getMethods()) {
            String name = m.getName();
            boolean getter = (name.startsWith("get") || name.startsWith("is")) //$NON-NLS-1$ //$NON-NLS-2$
                && m.getParameterCount() == 0
                && !Modifier.isStatic(m.getModifiers())
                && m.getDeclaringClass() != Object.class;
            if (getter) {
                getters.add(name + "(): " + m.getReturnType().getSimpleName()); //$NON-NLS-1$
            }
        }
        getters.sort(null);
        if (getters.size() > MAX_GETTERS) {
            getters = getters.subList(0, MAX_GETTERS);
        }
        out.append("  getters: ").append(String.join(", ", getters)).append('\n'); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static void collectInterfaces(Class<?> cls, Set<String> result) {
        for (Class<?> i : cls.getInterfaces()) {
            if (result.add(i.getName())) {
                collectInterfaces(i, result);
            }
        }
    }

    private static String truncate(String s) {
        if (s == null) {
            return "null"; //$NON-NLS-1$
        }
        return s.length() <= MAX_TEXT ? s : s.substring(0, MAX_TEXT) + "…"; //$NON-NLS-1$
    }
}
