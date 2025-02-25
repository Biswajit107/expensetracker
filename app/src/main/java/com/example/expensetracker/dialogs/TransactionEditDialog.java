package com.example.expensetracker.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.example.expensetracker.R;
import com.example.expensetracker.models.Transaction;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

public class TransactionEditDialog extends DialogFragment {
    private Transaction transaction;
    private OnTransactionEditListener listener;

    // Input views
    private TextInputEditText descriptionInput;
    private AutoCompleteTextView categoryInput;
    private SwitchMaterial excludeSwitch;

    public interface OnTransactionEditListener {
        void onTransactionEdited(Transaction transaction);
    }

    public TransactionEditDialog(Transaction transaction) {
        this.transaction = transaction;
    }

    public void setOnTransactionEditListener(OnTransactionEditListener listener) {
        this.listener = listener;
    }

    // In TransactionEditDialog.java

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());

        // Inflate and get instance of layout
        View view = requireActivity().getLayoutInflater()
                .inflate(R.layout.dialog_edit_transaction, null);

        // Initialize views
        descriptionInput = view.findViewById(R.id.descriptionInput);
        categoryInput = view.findViewById(R.id.categoryInput);
        excludeSwitch = view.findViewById(R.id.excludeSwitch);
        MaterialButton cancelButton = view.findViewById(R.id.cancelButton);
        MaterialButton saveButton = view.findViewById(R.id.saveButton);

        // Add a new button to view original SMS
        MaterialButton viewSmsButton = view.findViewById(R.id.viewSmsButton);

        // Show or hide button based on whether original SMS is available
        if (transaction.getOriginalSms() != null && !transaction.getOriginalSms().isEmpty()) {
            viewSmsButton.setVisibility(View.VISIBLE);
            viewSmsButton.setOnClickListener(v -> {
                showOriginalSmsDialog(transaction.getOriginalSms());
            });
        } else {
            viewSmsButton.setVisibility(View.GONE);
        }

        // Setup category dropdown
        setupCategoryDropdown();

        // Populate data
        populateTransactionData();

        // Setup buttons
        cancelButton.setOnClickListener(v -> dismiss());
        saveButton.setOnClickListener(v -> {
            updateTransaction();
            if (listener != null) {
                listener.onTransactionEdited(transaction);
            }
            dismiss();
        });

        // Build the dialog
        builder.setView(view);
        return builder.create();
    }

    private void showOriginalSmsDialog(String smsText) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        builder.setTitle("Original SMS Message");

        // Create a text view with scrolling for long messages
        TextView textView = new TextView(requireActivity());
        textView.setText(smsText);
        textView.setTextIsSelectable(true); // Allow copying
        textView.setPadding(48, 24, 48, 8);

        ScrollView scrollView = new ScrollView(requireActivity());
        scrollView.addView(textView);

        builder.setView(scrollView);
        builder.setPositiveButton("Close", null);
        builder.show();
    }

    private void setupCategoryDropdown() {
        String[] categories = Transaction.Categories.getAllCategories();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                categories
        );
        categoryInput.setAdapter(adapter);
    }

    private void populateTransactionData() {
        if (transaction != null) {
            descriptionInput.setText(transaction.getDescription());
            categoryInput.setText(transaction.getCategory(), false);
            excludeSwitch.setChecked(transaction.isExcludedFromTotal());
        }
    }

    private void updateTransaction() {
        if (transaction != null) {
            transaction.setDescription(descriptionInput.getText().toString());
            transaction.setCategory(categoryInput.getText().toString());
            transaction.setExcludedFromTotal(excludeSwitch.isChecked());
        }
    }
}