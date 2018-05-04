package top.defaults.videoapp.dialog;

import android.app.DialogFragment;

import top.defaults.view.PickerView;

public abstract class PickerDialog<T extends PickerView.PickerItem> extends DialogFragment {

    public abstract T getSelectedItem(Class<T> cls);

    public interface ActionListener<T extends PickerView.PickerItem> {

        void onCancelClick(PickerDialog<T> dialog);

        void onDoneClick(PickerDialog<T> dialog);
    }
}
