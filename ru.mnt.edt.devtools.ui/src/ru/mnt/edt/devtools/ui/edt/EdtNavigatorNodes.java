package ru.mnt.edt.devtools.ui.edt;

import java.lang.reflect.Method;
import java.text.Normalizer;
import java.util.Optional;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.Adapters;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.jface.viewers.TreePath;

import ru.mnt.edt.devtools.core.changes.MdObjectRef;

/**
 * Распознавание узлов дерева навигатора EDT (проверено на EDT 2026.1):
 * <ul>
 * <li>проект — IProject, первый сегмент пути к любому узлу;</li>
 * <li>объект метаданных — EMF-объект модели EDT (CatalogImpl и т.п.), тип = имя EClass;</li>
 * <li>папка («Справочники») — адаптер *NavigatorAdapter$Folder с методом getContentFeature():
 * ссылка модели, тип её элементов = тип объектов в папке;</li>
 * <li>прочие группы («Общие») — виртуальные узлы без собственного типа.</li>
 * </ul>
 * Классы адаптеров — внутренности EDT, поэтому к ним обращаемся через reflection:
 * если метод исчезнет в новой версии, папка просто будет считаться прочей группой.
 */
public final class EdtNavigatorNodes {

    private static final String CONTENT_FEATURE_METHOD = "getContentFeature"; //$NON-NLS-1$
    private static final String NAME_FEATURE = "name"; //$NON-NLS-1$

    /** Кэш поиска метода getContentFeature по классу узла. */
    private static final ClassValue<Optional<Method>> CONTENT_FEATURE = new ClassValue<>() {
        @Override
        protected Optional<Method> computeValue(Class<?> type) {
            try {
                Method method = type.getMethod(CONTENT_FEATURE_METHOD);
                return EReference.class.isAssignableFrom(method.getReturnType()) ? Optional.of(method)
                    : Optional.empty();
            } catch (NoSuchMethodException | SecurityException e) {
                return Optional.empty();
            }
        }
    };

    private EdtNavigatorNodes() {
    }

    /** Проект, которому принадлежит узел с этим путём (null, если путь не начинается с проекта). */
    public static IProject project(TreePath path) {
        if (path == null || path.getSegmentCount() == 0) {
            return null;
        }
        return Adapters.adapt(path.getFirstSegment(), IProject.class, true);
    }

    /** Объект метаданных, если узел — объект модели EDT; иначе null. */
    public static MdObjectRef mdObject(Object node) {
        if (!(node instanceof EObject object)) {
            return null;
        }
        EClass eClass = object.eClass();
        if (MdObjectRef.CONFIGURATION.type().equals(eClass.getName())) {
            return MdObjectRef.CONFIGURATION;
        }
        EStructuralFeature nameFeature = eClass.getEStructuralFeature(NAME_FEATURE);
        Object name = nameFeature == null ? null : object.eGet(nameFeature, true);
        return name instanceof String text
            ? new MdObjectRef(eClass.getName(), Normalizer.normalize(text, Normalizer.Form.NFC)) : null;
    }

    /** Тип объектов в папке навигатора («Справочники» → Catalog); null, если узел не такая папка. */
    public static String folderType(Object node) {
        if (node == null) {
            return null;
        }
        Optional<Method> method = CONTENT_FEATURE.get(node.getClass());
        if (method.isEmpty()) {
            return null;
        }
        try {
            Object feature = method.get().invoke(node);
            return feature instanceof EReference reference ? reference.getEReferenceType().getName() : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
