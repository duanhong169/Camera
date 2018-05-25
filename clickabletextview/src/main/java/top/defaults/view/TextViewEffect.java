package top.defaults.view;

import android.content.res.ColorStateList;

public interface TextViewEffect {

    int EFFECT_DEFAULT = 0;
    int EFFECT_ANIMATE_TEXT_COLOR = 1;
    int EFFECT_ANIMATE_TEXT_SIZE = 2;
    int EFFECT_ANIMATE_TEXT_COLOR_AND_SIZE = 3;

    void init(TextButton textButton);

    void actionDown();

    void actionUp();

    class Factory {

        static TextViewEffect create(int type) {
            switch (type) {
                case EFFECT_ANIMATE_TEXT_COLOR:
                    return new AnimateTextColorEffect();
                case EFFECT_ANIMATE_TEXT_SIZE:
                    return new AnimateTextSizeEffect();
                case EFFECT_ANIMATE_TEXT_COLOR_AND_SIZE:
                    return new TextViewEffect() {
                        AnimateTextColorEffect colorEffect = new AnimateTextColorEffect();
                        AnimateTextSizeEffect sizeEffect = new AnimateTextSizeEffect();

                        @Override
                        public void init(TextButton textButton) {
                            colorEffect.init(textButton);
                            sizeEffect.init(textButton);
                        }

                        @Override
                        public void actionDown() {
                            colorEffect.actionDown();
                            sizeEffect.actionDown();
                        }

                        @Override
                        public void actionUp() {
                            colorEffect.actionUp();
                            sizeEffect.actionUp();
                        }
                    };
                default:
                    return new DefaultEffect();
            }
        }
    }

    class DefaultEffect implements TextViewEffect {

        @Override
        public void init(TextButton textButton) {
            ColorStateList colorStateList = new ColorStateList(
                    new int[][]{
                            new int[]{ android.R.attr.state_pressed },
                            new int[]{ -android.R.attr.state_enabled },
                            new int[]{} // this should be empty to make default color as we want
                    },
                    new int[]{
                            textButton.pressedTextColor,
                            textButton.disabledTextColor,
                            textButton.defaultTextColor
                    }
            );
            textButton.setTextColor(colorStateList);
        }

        @Override
        public void actionDown() {

        }

        @Override
        public void actionUp() {

        }
    }
}
