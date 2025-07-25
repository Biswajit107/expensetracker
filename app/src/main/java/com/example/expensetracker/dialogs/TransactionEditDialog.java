package com.example.expensetracker.dialogs;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.expensetracker.R;
import com.example.expensetracker.models.CustomCategory;
import com.example.expensetracker.models.Transaction;
import com.example.expensetracker.ocr.CameraCaptureActivity;
import com.example.expensetracker.ocr.OCRResultsActivity;
import com.example.expensetracker.viewmodel.CategoryViewModel;
import com.example.expensetracker.viewmodel.ExclusionPatternViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TransactionEditDialog extends DialogFragment {
    private Transaction transaction;
    private OnTransactionEditListener listener;

    // View models
    private CategoryViewModel categoryViewModel;

    // Input views
    private TextInputEditText descriptionInput;
    private TextInputEditText noteInput;
    private MaterialButton scanReceiptButton;
    private AutoCompleteTextView categoryInput;
    private SwitchMaterial excludeSwitch;
    private LinearLayout patternOptionLayout;
    private SwitchMaterial createPatternSwitch;

    // Custom category views
    private LinearLayout customCategoryLayout;
    private EditText customCategoryInput;
    private String selectedColor = "#4CAF50"; // Default color
    
    // OCR related constants
    private static final int REQUEST_CAMERA_CAPTURE = 1001;
    private static final int REQUEST_OCR_RESULTS = 1002;

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
        noteInput = view.findViewById(R.id.noteInput);
        scanReceiptButton = view.findViewById(R.id.scanReceiptButton);
        categoryInput = view.findViewById(R.id.categoryInput);
        excludeSwitch = view.findViewById(R.id.excludeSwitch);

        MaterialButton cancelButton = view.findViewById(R.id.cancelButton);
        MaterialButton saveButton = view.findViewById(R.id.saveButton);

        // Setup touch handling for text inputs to prevent scroll conflicts
        setupTextInputTouchHandling();
        
        // Setup dialog root view touch handling
        view.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                clearFocusFromTextInputs();
            }
            return false;
        });

        // NEW: Add pattern option layout and switch
        patternOptionLayout = view.findViewById(R.id.patternOptionLayout);
        createPatternSwitch = view.findViewById(R.id.createPatternSwitch);

        // Initialize custom category views
        customCategoryLayout = view.findViewById(R.id.customCategoryLayout);
        customCategoryInput = view.findViewById(R.id.customCategoryInput);
        ViewGroup colorSelectionContainer = view.findViewById(R.id.colorSelectionContainer);

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

        // Initialize category ViewModel
        categoryViewModel = new ViewModelProvider(requireActivity()).get(CategoryViewModel.class);

        // Setup category dropdown with custom categories
        setupCategoryDropdownWithCustom();

        // Setup category change listener
        categoryInput.setOnItemClickListener((parent, clickedView, position, id) -> {
            String selectedCategory = parent.getItemAtPosition(position).toString();
            if ("Others".equals(selectedCategory)) {
                // Show custom category input
                customCategoryLayout.setVisibility(View.VISIBLE);
            } else {
                // Hide custom category input
                customCategoryLayout.setVisibility(View.GONE);
            }
        });

        // Setup color selection
        setupColorSelection(colorSelectionContainer);
        
        // Setup scan receipt button
        setupScanReceiptButton();

        // Populate data
        populateTransactionData();

        // NEW: Set up exclusion pattern option visibility based on exclude switch
        excludeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updatePatternOptionVisibility(isChecked);
        });

        // Setup buttons
        cancelButton.setOnClickListener(v -> dismiss());
        saveButton.setOnClickListener(v -> {
            String selectedCategory = categoryInput.getText().toString();

            if ("Others".equals(selectedCategory) &&
                    customCategoryInput.getText().toString().trim().length() > 0) {
                // Create a custom category
                String customCategoryName = customCategoryInput.getText().toString().trim();
                CustomCategory newCategory = new CustomCategory(customCategoryName, selectedColor);
                categoryViewModel.insertCategory(newCategory);

                // Use the custom category name for the transaction
                transaction.setCategory(customCategoryName);
            } else {
                transaction.setCategory(selectedCategory);
            }

            // Update other transaction fields
            transaction.setDescription(descriptionInput.getText().toString());
            transaction.setNote(noteInput.getText().toString());

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

            // NEW: Create exclusion pattern if option is selected
            if (excludeSwitch.isChecked() && createPatternSwitch.isChecked()) {
                createExclusionPattern();
            }

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

    // New method to set up category dropdown with custom categories
    private void setupTextInputTouchHandling() {
        // Only set up touch handling for the text inputs, skip dialog root view setup
        // since getDialog() might be null at this point
        
        if (noteInput != null) {
            // Allow text input to scroll internally when focused
            noteInput.setOnTouchListener((v, event) -> {
                v.getParent().requestDisallowInterceptTouchEvent(true);
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                }
                return false;
            });
        }
        
        if (descriptionInput != null) {
            // Also handle description input
            descriptionInput.setOnTouchListener((v, event) -> {
                v.getParent().requestDisallowInterceptTouchEvent(true);
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                }
                return false;
            });
        }
    }
    
    private void clearFocusFromTextInputs() {
        if (noteInput != null && noteInput.hasFocus()) {
            noteInput.clearFocus();
            // Hide keyboard
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(noteInput.getWindowToken(), 0);
            }
        }
        if (descriptionInput != null && descriptionInput.hasFocus()) {
            descriptionInput.clearFocus();
            // Hide keyboard
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(descriptionInput.getWindowToken(), 0);
            }
        }
    }

    private void setupCategoryDropdownWithCustom() {
        // Get predefined categories
        List<String> categories = new ArrayList<>(Arrays.asList(Transaction.Categories.getAllCategories()));

        // Add "Others" option at the end
        if (!categories.contains("Others")) {
            categories.add("Others");
        }

        // Get custom categories
        categoryViewModel.getAllCustomCategories().observe(this, customCategories -> {
            // Add custom categories to list
            for (CustomCategory customCategory : customCategories) {
                if (!categories.contains(customCategory.getName())) {
                    categories.add(customCategory.getName());
                }
            }

            // Create adapter
            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    requireContext(),
                    android.R.layout.simple_dropdown_item_1line,
                    categories
            );

            // Set adapter and current selection
            categoryInput.setAdapter(adapter);
            categoryInput.setText(transaction.getCategory(), false);

            // Check if current category is "Others"
            if ("Others".equals(transaction.getCategory())) {
                customCategoryLayout.setVisibility(View.VISIBLE);
            } else {
                customCategoryLayout.setVisibility(View.GONE);
            }
        });
    }

    // Method to set up color selection
    private void setupColorSelection(ViewGroup colorContainer) {
        // Define available colors
        String[] colors = {"#F44336", "#2196F3", "#4CAF50", "#FFC107", "#9C27B0", "#795548"};

        // Clear existing views
        colorContainer.removeAllViews();

        // Create a color circle for each color
        for (String color : colors) {
            View colorView = requireActivity().getLayoutInflater()
                    .inflate(R.layout.item_color_selection, colorContainer, false);

            View colorCircle = colorView.findViewById(R.id.colorCircle);
            colorCircle.setBackgroundColor(android.graphics.Color.parseColor(color));

            // Selection indicator
            View checkIcon = colorView.findViewById(R.id.checkIcon);

            // Set click listener
            colorView.setOnClickListener(v -> {
                // Update selected color
                selectedColor = color;

                // Update UI to show selection
                for (int i = 0; i < colorContainer.getChildCount(); i++) {
                    View child = colorContainer.getChildAt(i);
                    child.findViewById(R.id.checkIcon).setVisibility(View.GONE);
                }

                checkIcon.setVisibility(View.VISIBLE);
            });

            colorContainer.addView(colorView);
        }

        // Select the first color by default
        if (colorContainer.getChildCount() > 0) {
            colorContainer.getChildAt(0).findViewById(R.id.checkIcon).setVisibility(View.VISIBLE);
        }
    }

    private void setupScanReceiptButton() {
        scanReceiptButton.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), CameraCaptureActivity.class);
            startActivityForResult(intent, REQUEST_CAMERA_CAPTURE);
        });
    }

    private void populateTransactionData() {
        if (transaction != null) {
            descriptionInput.setText(transaction.getDescription());
            noteInput.setText(transaction.getNote());
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

    /**
     * Create an exclusion pattern from the current transaction
     */
    private void createExclusionPattern() {
        if (transaction == null) return;

        // Get the ExclusionPatternViewModel from the parent activity
        ExclusionPatternViewModel patternViewModel = new ViewModelProvider(requireActivity())
                .get(ExclusionPatternViewModel.class);

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

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (resultCode == getActivity().RESULT_OK && data != null) {
            switch (requestCode) {
                case REQUEST_CAMERA_CAPTURE:
                    // Image captured, now process with OCR
                    String imageUri = data.getStringExtra(CameraCaptureActivity.EXTRA_IMAGE_URI);
                    if (imageUri != null) {
                        Intent ocrIntent = new Intent(getActivity(), OCRResultsActivity.class);
                        ocrIntent.putExtra(OCRResultsActivity.EXTRA_IMAGE_URI, imageUri);
                        startActivityForResult(ocrIntent, REQUEST_OCR_RESULTS);
                    }
                    break;
                    
                case REQUEST_OCR_RESULTS:
                    // OCR completed, get extracted text
                    String extractedText = data.getStringExtra(OCRResultsActivity.EXTRA_EXTRACTED_TEXT);
                    boolean appendMode = data.getBooleanExtra(OCRResultsActivity.EXTRA_APPEND_MODE, false);
                    
                    if (extractedText != null && !extractedText.trim().isEmpty()) {
                        String currentText = noteInput.getText().toString();
                        
                        if (appendMode && !currentText.trim().isEmpty()) {
                            // Append to existing text
                            noteInput.setText(currentText + "\n\n" + extractedText);
                        } else {
                            // Replace existing text
                            noteInput.setText(extractedText);
                        }
                        
                        // Clear focus from text input to allow dialog scrolling
                        clearFocusFromTextInputs();
                        
                        android.widget.Toast.makeText(requireContext(), "Receipt text added successfully!", android.widget.Toast.LENGTH_SHORT).show();
                    }
                    break;
            }
        }
    }
}