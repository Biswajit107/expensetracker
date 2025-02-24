package com.example.expensetracker.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.NumberPicker;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.example.expensetracker.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Calendar;

public class MonthYearPickerDialog extends DialogFragment {
    private static final int MIN_YEAR = 2000;
    private static final int MAX_YEAR = 2100;

    private DatePickerListener listener;
    private Calendar calendar;

    public void setListener(DatePickerListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        calendar = Calendar.getInstance();

        View view = LayoutInflater.from(getActivity())
                .inflate(R.layout.dialog_month_year_picker, null);

        NumberPicker monthPicker = view.findViewById(R.id.monthPicker);
        NumberPicker yearPicker = view.findViewById(R.id.yearPicker);

        // Setup MonthPicker
        String[] months = new String[]{
                "January", "February", "March", "April",
                "May", "June", "July", "August",
                "September", "October", "November", "December"
        };
        monthPicker.setMinValue(0);
        monthPicker.setMaxValue(11);
        monthPicker.setDisplayedValues(months);
        monthPicker.setValue(calendar.get(Calendar.MONTH));

        // Setup YearPicker
        yearPicker.setMinValue(MIN_YEAR);
        yearPicker.setMaxValue(MAX_YEAR);
        yearPicker.setValue(calendar.get(Calendar.YEAR));

        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Select Month & Year")
                .setView(view)
                .setPositiveButton("OK", (dialog, which) -> {
                    if (listener != null) {
                        listener.onDateSet(
                                null,
                                yearPicker.getValue(),
                                monthPicker.getValue(),
                                1
                        );
                    }
                })
                .setNegativeButton("Cancel", null)
                .create();
    }

    public interface DatePickerListener {
        void onDateSet(View view, int year, int month, int dayOfMonth);
    }
}