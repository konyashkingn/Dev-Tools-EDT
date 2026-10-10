package ru.mnt.edt.devtools.ui;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ResourceLocator;

/**
 * Иконки плагина. Версии @2x для Retina подхватываются автоматически по имени файла.
 */
public final class DevToolsImages {

    /** Кнопка «Только изменённые»: отбор выключен. */
    public static final ImageDescriptor CHANGED_ONLY_OFF = load("icons/etool16/changed_only_off.png"); //$NON-NLS-1$

    /** Кнопка «Только изменённые»: отбор включён. */
    public static final ImageDescriptor CHANGED_ONLY_ON = load("icons/etool16/changed_only_on.png"); //$NON-NLS-1$

    private DevToolsImages() {
    }

    private static ImageDescriptor load(String path) {
        return ResourceLocator.imageDescriptorFromBundle(DevToolsImages.class, path)
            .orElseGet(ImageDescriptor::getMissingImageDescriptor);
    }
}
