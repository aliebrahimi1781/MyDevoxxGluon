/**
 * Copyright (c) 2016, Gluon Software
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation and/or other materials provided
 *    with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse
 *    or promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.devoxx.control.skin;


import com.devoxx.control.CircularSelector;
import javafx.animation.RotateTransition;
import javafx.beans.InvalidationListener;
import javafx.geometry.Bounds;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.SkinBase;
import javafx.scene.shape.Circle;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;

import java.util.concurrent.atomic.AtomicReference;

public class CircularSelectorSkin<T> extends SkinBase<CircularSelector<T>> {

    private Group group = new Group();
    private Circle mainCircle = new Circle();

    public CircularSelectorSkin(CircularSelector control) {
        super(control);

        mainCircle.radiusProperty().bind(getSkinnable().mainCircleRadiusProperty());
        mainCircle.getStyleClass().add("main-circle");

        getChildren().add(group);

        rebuild();

        getSkinnable().selectorCircleRadiusProperty().addListener((o, ov, nv) -> rebuild());
        getSkinnable().getItems().addListener((InvalidationListener) o -> rebuild());
        getSkinnable().transitionDurationProperty().addListener((o, ov, nv) -> rebuild());

    }


    private void rebuild() {

        group.getChildren().clear();
        group.getChildren().add(mainCircle);


        // central circle decoration
        Node centerDecoration = getSkinnable().getCellFactory().apply(null);

        if ( centerDecoration != null ) {
            Bounds b = centerDecoration.getBoundsInParent();
            Translate t = new Translate();
            t.xProperty().bind(mainCircle.centerXProperty().subtract(b.getWidth()/2));
            t.yProperty().bind(mainCircle.centerYProperty().subtract(b.getHeight()/2));
            centerDecoration.getTransforms().add(t);
            group.getChildren().add(centerDecoration);

        }

        int itemCount = getSkinnable().getItems().size();
        if (itemCount < 1) return; // nothing to do

        getSkinnable().setSelectedItem(getSkinnable().getItems().get(0));

        double itemAngle = 360 / itemCount;
        AtomicReference<Double> absoluteAngle = new AtomicReference<>(0d);

        final double selectorRadius = getSkinnable().getSelectorCircleRadius();

        for (int i = 0; i < itemCount; i++) {

            final T item = getSkinnable().getItems().get(i);
            final Node itemGraphic = getSkinnable().getCellFactory().apply(item);
            if (itemGraphic == null) continue;

            Label itemLabel = new Label( null, itemGraphic);

            // translate selector to the edge of the main circle
            Translate t = new Translate(-selectorRadius, -getSkinnable().getMainCircleRadius()-selectorRadius);

            // Rotate selector to appropriate angle
            Rotate r = new Rotate(i * itemAngle);
            r.setPivotX(selectorRadius);
            r.pivotYProperty().bind( mainCircle.radiusProperty().add(selectorRadius));
            r.axisProperty().setValue(Rotate.Z_AXIS);

            itemLabel.getTransforms().addAll(t, r);

            final double cangle = i * itemAngle;

            itemLabel.setOnMouseClicked(e -> {
                // rotate selected item to the top of the control
                RotateTransition transition = new RotateTransition(getSkinnable().getTransitionDuration(), group);
                transition.setByAngle((-absoluteAngle.get() - cangle) % 360);
                absoluteAngle.set(-cangle);
                transition.setOnFinished(ae -> getSkinnable().setSelectedItem(item));
                transition.play();
            });

            group.getChildren().add(itemLabel);

        }

    }

}