/*
 * Copyright (C) 2010-2012 Serge Rieder
 * serge@jkiss.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.jkiss.dbeaver.ui.views.navigator.database;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.widgets.Display;
import org.jkiss.dbeaver.model.navigator.DBNDatabaseNode;
import org.jkiss.dbeaver.model.navigator.DBNNode;
import org.jkiss.dbeaver.runtime.VoidProgressMonitor;
import org.jkiss.dbeaver.ui.UIUtils;
import org.jkiss.dbeaver.ui.views.navigator.database.load.TreeLoadNode;
import org.jkiss.dbeaver.ui.views.navigator.database.load.TreeLoadService;
import org.jkiss.dbeaver.ui.views.navigator.database.load.TreeLoadVisualizer;
import org.jkiss.utils.CommonUtils;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

/**
 * DatabaseNavigatorContentProvider
*/
class DatabaseNavigatorContentProvider implements IStructuredContentProvider, ITreeContentProvider {
    static final Log log = LogFactory.getLog(DatabaseNavigatorContentProvider.class);

    private static final Object[] EMPTY_CHILDREN = new Object[0];

    private TreeViewer viewer;
    private boolean showRoot;

    DatabaseNavigatorContentProvider(TreeViewer viewer, boolean showRoot)
    {
        this.viewer = viewer;
        this.showRoot = showRoot;
    }

    @Override
    public void inputChanged(Viewer v, Object oldInput, Object newInput)
    {
    }

    @Override
    public void dispose()
    {
    }

    @Override
    public Object[] getElements(Object parent)
    {
        if (parent instanceof DatabaseNavigatorContent) {
            if (showRoot) {
                return new Object[] { ((DatabaseNavigatorContent) parent).getRootNode() };
            } else {
                return getChildren(((DatabaseNavigatorContent) parent).getRootNode());
            }
        } else {
            return getChildren(parent);
        }
    }

    @Override
    public Object getParent(Object child)
    {
        if (child instanceof DBNNode) {
            return ((DBNNode)child).getParentNode();
        } else if (child instanceof TreeLoadNode) {
            return ((TreeLoadNode)child).getParent();
        } else {
            log.warn("Unknown node type: " + child);
            return null;
        }
    }

    @Override
    public Object[] getChildren(final Object parent)
    {
        if (parent instanceof TreeLoadNode) {
            return null;
        }
        if (!(parent instanceof DBNNode)) {
            log.error("Bad parent type: " + parent);
            return null;
        }
        final DBNNode parentNode = (DBNNode)parent;//view.getNavigatorModel().findNode(parent);
/*
        if (parentNode == null) {
            log.error("Can't find parent node '" + ((DBSObject) parent).getName() + "' in model");
            return EMPTY_CHILDREN;
        }
*/
        if (!parentNode.allowsNavigableChildren()) {
            return EMPTY_CHILDREN;
        }
        if (parentNode instanceof DBNDatabaseNode && ((DBNDatabaseNode)parentNode).isLazyNode()) {
            return TreeLoadVisualizer.expandChildren(
                viewer,
                new TreeLoadService("Loading", ((DBNDatabaseNode)parentNode)));
        } else {
            try {
                // Read children with null monitor cos' it's not a lazy node
                // and no blocking process will occur
                List<? extends DBNNode> children = TreeLoadService.filterNavigableChildren(
                    parentNode.getChildren(VoidProgressMonitor.INSTANCE));
                if (CommonUtils.isEmpty(children)) {
                    return EMPTY_CHILDREN;
                } else {
                    return children.toArray(new Object[children.size()]);
                }
                //return DBNNode.convertNodesToObjects(children);
            }
            catch (Throwable ex) {
                if (ex instanceof InvocationTargetException) {
                    ex = ((InvocationTargetException)ex).getTargetException();
                }
                UIUtils.showErrorDialog(
                    viewer.getControl().getShell(),
                    "Navigator error",
                    ex.getMessage(),
                    ex);
                // Collapse this item
                Display.getDefault().asyncExec(new Runnable() {
                    @Override
                    public void run()
                    {
                        viewer.collapseToLevel(parent, 1);
                        viewer.refresh(parent);
                    }
                });
                return EMPTY_CHILDREN;
            }
        }
    }

    @Override
    public boolean hasChildren(Object parent)
    {
        return parent instanceof DBNNode && ((DBNNode) parent).allowsNavigableChildren();
    }

/*
    public void cancelLoading(Object parent)
    {
        if (!(parent instanceof DBSObject)) {
            log.error("Bad parent type: " + parent);
        }
        DBSObject object = (DBSObject)parent;
        object.getDataSource().cancelCurrentOperation();
    }
*/

}
