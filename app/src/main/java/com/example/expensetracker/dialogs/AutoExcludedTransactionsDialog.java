package com.example.expensetracker.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.example.expensetracker.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Dialog to inform the user about auto-excluded transactions
 * and offer options to include them or review them
 */
public class AutoExcludedTransactionsDialog extends DialogFragment {

    private int transactionCount;
    private OnActionSelectedListener listener;

    public interface OnActionSelectedListener {
        void onIncludeAllSelected();
        void onReviewSelected();
    }

    public AutoExcludedTransactionsDialog(int transactionCount) {
        this.transactionCount = transactionCount;
    }

    public void setListener(OnActionSelectedListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());

        // Inflate the custom layout
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_auto_excluded_transactions, null);

        // Set up the message
        TextView messageText = view.findViewById(R.id.messageText);
        String message = String.format(
                "%d transaction(s) from unrecognized sources have been automatically excluded. " +
                        "These might be promotional messages or non-transaction SMS incorrectly detected " +
                        "as transactions.", transactionCount);
        messageText.setText(message);

        builder.setTitle("Auto-Excluded Transactions")
                .setView(view)
                .setPositiveButton("Review Transactions", (dialog, which) -> {
                    if (listener != null) {
                        listener.onReviewSelected();
                    }
                })
                .setNegativeButton("Ignore", null)
                .setNeutralButton("Include All", (dialog, which) -> {
                    if (listener != null) {
                        listener.onIncludeAllSelected();
                    }
                });

        return builder.create();
    }
}