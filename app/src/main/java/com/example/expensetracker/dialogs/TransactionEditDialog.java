package com.example.expensetracker.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.expensetracker.R;
import com.example.expensetracker.models.Transaction;
import com.example.expensetracker.viewmodel.ExclusionPatternViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

public class TransactionEditDialog extends DialogFragment {
    private Transaction transaction;
    private OnTransactionEditListener listener;
    private ExclusionPatternViewModel patternViewModel;

    // Input views
    private TextInputEditText descriptionInput;
    private AutoCompleteTextView categoryInput;
    private SwitchMaterial excludeSwitch;
    private LinearLayout patternOptionLayout;
    private SwitchMaterial createPatternSwitch;

    public interface OnTransactionEditListener {
        void onTransactionEdited(Transaction transaction);
    }

    public TransactionEditDialog(Transaction transaction) {
        this.transaction = transaction;
    }

    public void setOnTransactionEditListener(OnTransactionEditListener listener) {
        this.listener = listener;
    }

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

        // NEW: Add pattern option layout and switch
        patternOptionLayout = view.findViewById(R.id.patternOptionLayout);
        createPatternSwitch = view.findViewById(R.id.createPatternSwitch);

        // Add a button to view original SMS
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

        // NEW: Set up exclusion pattern option visibility based on exclude switch
        excludeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updatePatternOptionVisibility(isChecked);
        });

        // Setup buttons
        cancelButton.setOnClickListener(v -> dismiss());
        saveButton.setOnClickListener(v -> {
            updateTransaction();

            // NEW: Create exclusion pattern if option is selected
            if (excludeSwitch.isChecked() && createPatternSwitch.isChecked()) {
                createExclusionPattern();
            }

            if (listener != null) {
                listener.onTransactionEdited(transaction);
            }
            dismiss();
        });

        // Initialize pattern ViewModel
        patternViewModel = new ViewModelProvider(requireActivity()).get(ExclusionPatternViewModel.class);

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

            // Update pattern option visibility
            updatePatternOptionVisibility(transaction.isExcludedFromTotal());
        }
    }

    /**
     * Update visibility of pattern creation option based on exclude state
     */
    private void updatePatternOptionVisibility(boolean excludeChecked) {
        if (patternOptionLayout != null) {
            if (excludeChecked && !transaction.isOtherDebit()) {
                patternOptionLayout.setVisibility(View.VISIBLE);

                // Default to checked
                if (createPatternSwitch != null) {
                    createPatternSwitch.setChecked(true);
                }
            } else {
                patternOptionLayout.setVisibility(View.GONE);

                // Reset switch
                if (createPatternSwitch != null) {
                    createPatternSwitch.setChecked(false);
                }
            }
        }
    }

    private void updateTransaction() {
        if (transaction != null) {
            transaction.setDescription(descriptionInput.getText().toString());
            transaction.setCategory(categoryInput.getText().toString());

            // Store previous excluded state to detect changes
            boolean wasExcluded = transaction.isExcludedFromTotal();
            boolean nowExcluded = excludeSwitch.isChecked();

            transaction.setExcludedFromTotal(nowExcluded);

            // Set exclusion source if changed
            if (!wasExcluded && nowExcluded) {
                // Newly excluded - mark as manual
                transaction.setExclusionSource("MANUAL");
            } else if (wasExcluded && !nowExcluded) {
                // No longer excluded - reset source
                transaction.setExclusionSource("NONE");
            }
        }
    }

    /**
     * Create an exclusion pattern from the current transaction
     */
    private void createExclusionPattern() {
        if (transaction == null) return;

        // Create pattern in ViewModel
        patternViewModel.createPatternFromTransaction(transaction);

        // Observe result
        patternViewModel.getPatternCreationResult().observe(this, result -> {
            if (result != null && result) {
                Toast.makeText(requireContext(),
                        "Exclusion pattern created. Similar transactions will be auto-excluded.",
                        Toast.LENGTH_LONG).show();
            } else if (result != null) {
                Toast.makeText(requireContext(),
                        "Failed to create exclusion pattern",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }
}