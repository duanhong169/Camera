package top.defaults.view;

import android.content.res.ColorStateList;

public interface TextViewEffect {

    int EFFECT_DEFAULT = 0;
    int EFFECT_ANIMATE_TEXT_COLOR = 1;

    void init(ClickableTextView clickableTextView);

    void actionDown();

    void actionUp();

    class Factory {

        static TextViewEffect create(int type) {
            switch (type) {
                case EFFECT_ANIMATE_TEXT_COLOR:
                    return new AnimateTextColorEffect();
                default:
                    return new DefaultEffect();
            }
        }
    }

    class DefaultEffect implements TextViewEffect {

        @Override
        public void init(ClickableTextView clickableTextView) {
            ColorStateList colorStateList = new ColorStateList(
                    new int[][]{
                            new int[]{ android.R.attr.state_pressed },
                            new int[]{ -android.R.attr.state_enabled },
                            new int[]{} // this should be empty to make default color as we want
                    },
                    new int[]{
                            clickableTextView.pressedTextColor,
                            clickableTextView.disabledTextColor,
                            clickableTextView.defaultTextColor
                    }
            );
            clickableTextView.setTextColor(colorStateList);
        }

        @Override
        public void actionDown() {

        }

        @Override
        public void actionUp() {

        }
    }
}
