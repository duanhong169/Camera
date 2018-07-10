/*
 * Copyright 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package top.defaults.camera;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.TextureView;

import java.util.LinkedList;
import java.util.List;

/**
 * A {@link TextureView} that can be adjusted to a specified aspect ratio.
 */
class AutoFitTextureView extends TextureView {

    private int ratioWidth = 0;
    private int ratioHeight = 0;
    private boolean fillSpace = false;
    private int displayOrientation;

    public AutoFitTextureView(Context context) {
        this(context, null);
    }

    public AutoFitTextureView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AutoFitTextureView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {

            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                configureTransform();
                dispatchSurfaceChanged();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                configureTransform();
                dispatchSurfaceChanged();
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            }
        });
    }

    /**
     * Sets the aspect ratio for this view. The size of the view will be measured based on the ratio
     * calculated from the parameters. Note that the actual sizes of parameters don't matter, that
     * is, calling setAspectRatio(2, 3) and setAspectRatio(4, 6) make the same result.
     *
     * @param width  Relative horizontal size
     * @param height Relative vertical size
     */
    public void setAspectRatio(int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Size cannot be negative.");
        }
        ratioWidth = width;
        ratioHeight = height;
        requestLayout();
    }

    public boolean isFillSpace() {
        return fillSpace;
    }

    public void setFillSpace(boolean fillSpace) {
        this.fillSpace = fillSpace;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (0 == ratioWidth || 0 == ratioHeight) {
            setMeasuredDimension(width, height);
        } else {
            // is filling space by default
            boolean isFillSpaceWithoutScale = width == height * ratioWidth / ratioHeight;
            if (isFillSpaceWithoutScale) {
                setMeasuredDimension(width, height);
                return;
            }

            if (fillSpace) {
                if (width < height * ratioWidth / ratioHeight) {
                    setMeasuredDimension(height * ratioWidth / ratioHeight, height);
                } else {
                    setMeasuredDimension(width, width * ratioHeight / ratioWidth);
                }
            } else {
                if (width < height * ratioWidth / ratioHeight) {
                    setMeasuredDimension(width, width * ratioHeight / ratioWidth);
                } else {
                    setMeasuredDimension(height * ratioWidth / ratioHeight, height);
                }
            }
        }
    }

    Surface getSurface() {
        return new Surface(getSurfaceTexture());
    }

    void setBufferSize(int width, int height) {
        getSurfaceTexture().setDefaultBufferSize(width, height);
    }

    int getDisplayOrientation() {
        return displayOrientation;
    }

    void setDisplayOrientation(int displayOrientation) {
        this.displayOrientation = displayOrientation;
        configureTransform();
    }

    private void configureTransform() {
        Matrix matrix = new Matrix();
        if (displayOrientation % 180 == 90) {
            final int width = getWidth();
            final int height = getHeight();
            // Rotate the camera preview when the screen is landscape.
            matrix.setPolyToPoly(
                    new float[]{
                            0.f, 0.f, // top left
                            width, 0.f, // top right
                            0.f, height, // bottom left
                            width, height, // bottom right
                    }, 0,
                    displayOrientation == 90 ?
                            // Clockwise
                            new float[]{
                                    0.f, height, // top left
                                    0.f, 0.f, // top right
                                    width, height, // bottom left
                                    width, 0.f, // bottom right
                            } : // mDisplayOrientation == 270
                            // Counter-clockwise
                            new float[]{
                                    width, 0.f, // top left
                                    width, height, // top right
                                    0.f, 0.f, // bottom left
                                    0.f, height, // bottom right
                            }, 0,
                    4);
        } else if (displayOrientation == 180) {
            matrix.postRotate(180, getWidth() / 2, getHeight() / 2);
        }
        setTransform(matrix);
    }

    interface Callback {
        void onSurfaceChanged();
    }

    private List<Callback> callbacks = new LinkedList<>();

    public void addCallback(Callback callback) {
        if (callback != null) {
            callbacks.add(callback);
        }
    }

    private void dispatchSurfaceChanged() {
        for (Callback callback : callbacks) {
            callback.onSurfaceChanged();
        }
    }
}
