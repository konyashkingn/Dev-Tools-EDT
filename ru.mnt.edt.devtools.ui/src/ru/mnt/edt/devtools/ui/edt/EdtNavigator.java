package ru.mnt.edt.devtools.ui.edt;

import java.util.Optional;

import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.navigator.CommonNavigator;
import org.eclipse.ui.navigator.CommonViewer;

/**
 * Доступ к виду «Навигатор» EDT.
 * Всё знание об устройстве навигатора EDT собирается в пакете {@code ui.edt},
 * чтобы при переходе на новую версию EDT правки были в одном месте.
 */
public final class EdtNavigator {

    /** ID вида и его дерева (Common Navigator), из plugin.xml бандла com._1c.g5.v8.dt.navigator.ui. */
    public static final String VIEW_ID = "com._1c.g5.v8.dt.ui2.navigator"; //$NON-NLS-1$

    private EdtNavigator() {
    }

    /** Дерево навигатора на странице, если вид открыт. */
    public static Optional<CommonViewer> findViewer(IWorkbenchPage page) {
        if (page == null) {
            return Optional.empty();
        }
        IViewPart view = page.findView(VIEW_ID);
        return viewerOf(view);
    }

    /** Дерево навигатора, если переданная часть — это навигатор EDT. */
    public static Optional<CommonViewer> viewerOf(IWorkbenchPart part) {
        if (part instanceof CommonNavigator navigator) {
            CommonViewer viewer = navigator.getCommonViewer();
            if (viewer != null && viewer.getControl() != null && !viewer.getControl().isDisposed()) {
                return Optional.of(viewer);
            }
        }
        return Optional.empty();
    }
}
