/*
Copyright 2008-2011 Gephi
Authors : Vojtech Bardiovsky <vojtech.bardiovsky@gmail.com>
Website : http://www.gephi.org

This file is part of Gephi.

Gephi is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as
published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version.

Gephi is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with Gephi.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.gephi.visualization.controller;

import java.awt.Dimension;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import javax.swing.SwingUtilities;
import org.gephi.graph.api.Node;
import org.gephi.lib.gleem.linalg.Vec3f;
import org.gephi.visualization.api.MotionManager;
import org.gephi.visualization.api.config.VizConfig;
import org.gephi.visualization.api.selection.SelectionManager;
import org.gephi.visualization.api.selection.SelectionType;
import org.gephi.visualization.api.selection.Shape;
import org.gephi.visualization.api.selection.Shape.SelectionModifier;
import org.gephi.visualization.apiimpl.shape.ShapeUtils;
import org.openide.util.Lookup;

public class MotionManager3D implements MotionManager {

    protected int[] mousePosition = new int[2];
    protected int[] mouseDrag = new int[2];

    protected Shape selectionShape;

    protected static float MOVE_FACTOR = 5.0f;
    protected static float ZOOM_FACTOR = 0.008f;
    protected static float ORBIT_FACTOR = 0.005f;

    @Override
    public Shape getSelectionShape() {
        return selectionShape;
    }

    @Override
    public float[] getDragDisplacement() {
        return new float[] {mouseDrag[0], mouseDrag[1]};
    }

    @Override
    public float[] getMousePosition() {
        return new float[] {mousePosition[0], mousePosition[1]};
    }

    @Override
    public float[] getMousePosition3d() {
        // FIXME
        return new float[] {0f, 0f};
    }

    @Override
    public void mousePressed(MouseEvent e) {
        mousePosition[0] = e.getX();
        mousePosition[1] = e.getY();

        SelectionModifier modifier = extractSelectionModifier(e);
        VizConfig vizConfig = Lookup.getDefault().lookup(VizConfig.class);
        SelectionManager selectionManager = Lookup.getDefault().lookup(SelectionManager.class);

        if (vizConfig.isDirectMouseSelection()) {
            if (SwingUtilities.isLeftMouseButton(e)) {
                if (modifier == SelectionModifier.DEFAULT) {
                    selectionManager.clearSelection();
                }
                selectionManager.selectSingle(e.getPoint(), modifier.isPositive());
            }
        } else if (vizConfig.getSelectionType() != SelectionType.NONE) {
            if (modifier == SelectionModifier.DEFAULT) {
                selectionManager.clearSelection();
            }

            // Initialize selections
            if (selectionShape == null || selectionShape.getSelectionType() != vizConfig.getSelectionType()) {
                selectionShape = ShapeUtils.initShape(vizConfig.getSelectionType(), modifier, e.getX(), e.getY());
            // And also update discrete type selections for better responsiveness
            } else {
                selectionShape = ShapeUtils.singleUpdate(selectionShape, e.getX(), e.getY());

                if (selectionShape.isDiscretelyUpdated()) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        selectionManager.applyContinuousSelection(selectionShape);
                    }
                }
            }
        } else if (vizConfig.isMovementEnabled()) {
            if (SwingUtilities.isLeftMouseButton(e)) {
                Controller.getInstance().getCamera().startTranslation();
            } else if (SwingUtilities.isRightMouseButton(e)) {
                Dimension viewDimension = Controller.getInstance().getViewDimensions();
                int dx = e.getX() - viewDimension.width / 2;
                int dy = e.getY() - viewDimension.height / 2;
                float orbitModifier = (float) (Math.sqrt(dx * dx + dy * dy) / Math.sqrt(viewDimension.width * viewDimension.width / 4 + viewDimension.height * viewDimension.height / 4));
                Controller.getInstance().getCamera().startOrbit(orbitModifier);
            }
        } else if (vizConfig.isDraggingEnabled()) {
            // TODO cleanup - just a proposal - orbit tool working with node dragging
            if (SwingUtilities.isRightMouseButton(e)) {
                Dimension viewDimension = Controller.getInstance().getViewDimensions();
                int dx = e.getX() - viewDimension.width / 2;
                int dy = e.getY() - viewDimension.height / 2;
                float orbitModifier = (float) (Math.sqrt(dx * dx + dy * dy) / Math.sqrt(viewDimension.width * viewDimension.width / 4 + viewDimension.height * viewDimension.height / 4));
                Controller.getInstance().getCamera().startOrbit(orbitModifier);
            }
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {}

    @Override
    public void mouseDragged(MouseEvent e) {
        mouseDrag[0] = e.getX() - mousePosition[0];
        mouseDrag[1] = e.getY() - mousePosition[1];
        mousePosition[0] = e.getX();
        mousePosition[1] = e.getY();

        VizConfig vizConfig = Lookup.getDefault().lookup(VizConfig.class);
        SelectionManager selectionManager = Lookup.getDefault().lookup(SelectionManager.class);

        if (vizConfig.getSelectionType() != SelectionType.NONE && selectionShape != null) {
            selectionShape = ShapeUtils.continuousUpdate(selectionShape, e.getX(), e.getY());
            selectionManager.cancelContinuousSelection();

            if (SwingUtilities.isLeftMouseButton(e)) {
                selectionManager.applyContinuousSelection(selectionShape);
            }
        } else if (vizConfig.isMovementEnabled()) {
            if (SwingUtilities.isLeftMouseButton(e)) {
                Controller.getInstance().getCamera().updateTranslation(MOVE_FACTOR * mouseDrag[0], -MOVE_FACTOR * mouseDrag[1]);
            } else if (SwingUtilities.isRightMouseButton(e)) {
                Controller.getInstance().getCamera().updateOrbit(ORBIT_FACTOR * mouseDrag[0], ORBIT_FACTOR * mouseDrag[1]);
            }
        } else if (vizConfig.isDraggingEnabled()) {
            // TODO cleanup - just a proposal - orbit tool working with node dragging
            if (SwingUtilities.isLeftMouseButton(e)) {
                Vec3f translation = Controller.getInstance().getCamera().projectVectorInverse(MOVE_FACTOR * mouseDrag[0], -MOVE_FACTOR * mouseDrag[1]);
                for (Node node : selectionManager.getSelectedNodes()) {
                    node.getNodeData().setPosition(node.getNodeData().x() - translation.x(), node.getNodeData().y() - translation.y(), node.getNodeData().z() - translation.z());
                }
            } else if (SwingUtilities.isRightMouseButton(e)) {
                Controller.getInstance().getCamera().updateOrbit(ORBIT_FACTOR * mouseDrag[0], ORBIT_FACTOR * mouseDrag[1]);
            }
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        VizConfig vizConfig = Lookup.getDefault().lookup(VizConfig.class);
        SelectionManager selectionManager = Lookup.getDefault().lookup(SelectionManager.class);

        if (vizConfig.getSelectionType() != SelectionType.NONE && selectionShape != null) {
            selectionShape = ShapeUtils.continuousUpdate(selectionShape, e.getX(), e.getY());

            if (!selectionShape.isDiscretelyUpdated() && SwingUtilities.isLeftMouseButton(e) ||
                selectionShape.isDiscretelyUpdated() && SwingUtilities.isRightMouseButton(e)) {
                selectionManager.cancelContinuousSelection();
                selectionManager.applySelection(selectionShape);
            }

        }
        
        if (selectionShape != null && (!selectionShape.isDiscretelyUpdated() ||
            selectionShape.isDiscretelyUpdated() && SwingUtilities.isRightMouseButton(e))) {
            selectionShape = null;
        }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        mousePosition[0] = e.getX();
        mousePosition[1] = e.getY();

        VizConfig vizConfig = Lookup.getDefault().lookup(VizConfig.class);
        SelectionManager selectionManager = Lookup.getDefault().lookup(SelectionManager.class);

        // Only discretely updated shapes
        if (vizConfig.getSelectionType() != SelectionType.NONE && selectionShape != null && selectionShape.isDiscretelyUpdated()) {
            selectionShape = ShapeUtils.continuousUpdate(selectionShape, e.getX(), e.getY());
            selectionManager.cancelContinuousSelection();
            selectionManager.applyContinuousSelection(selectionShape);
        }
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        Controller.getInstance().getCamera().zoom(ZOOM_FACTOR * e.getUnitsToScroll());
    }

    private SelectionModifier extractSelectionModifier(MouseEvent e) {
        return (e.getModifiersEx() & MouseEvent.SHIFT_DOWN_MASK) > 0 ? SelectionModifier.INCREMENTAL :
               (e.getModifiersEx() & MouseEvent.CTRL_DOWN_MASK) > 0 ? SelectionModifier.DECREMENTAL :
               SelectionModifier.DEFAULT;
    }

}
