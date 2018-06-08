package top.defaults.cameraapp.dialog;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import top.defaults.view.PickerView;

public abstract class PickerDialog<T extends PickerView.PickerItem> extends DialogFragment {

    public abstract T getSelectedItem(Class<T> cls);

    public interface ActionListener<T extends PickerView.PickerItem> {

        void onCancelClick(PickerDialog<T> dialog);

        void onDoneClick(PickerDialog<T> dialog);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            View decorView = window.getDecorView();
            decorView.setSystemUiVisibility(getActivity().getWindow().getDecorView().getSystemUiVisibility());
            dialog.setOnShowListener(dialog1 -> window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE));
        }
        return dialog;
    }
}
