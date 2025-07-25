package com.example.expensetracker.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import android.content.DialogInterface;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.expensetracker.R;
import com.example.expensetracker.adapters.CategoryTileAdapter;
import com.example.expensetracker.models.Category;
import com.example.expensetracker.models.Transaction;
import com.example.expensetracker.ocr.CameraCaptureActivity;
import com.example.expensetracker.ocr.OCRResultsActivity;
import com.example.expensetracker.utils.PreferencesManager;
import com.example.expensetracker.viewmodel.QuickEntryViewModel;
import com.example.expensetracker.viewmodel.TransactionViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class QuickEntryFragment extends BottomSheetDialogFragment {

    private QuickEntryViewModel viewModel;
    private TransactionViewModel transactionViewModel;
    private CategoryTileAdapter categoryAdapter;
    private RecyclerView categoryRecyclerView;
    private TextView amountDisplay;
    private MaterialButton increaseButton;
    private MaterialButton decreaseButton;
    private ChipGroup amountChipGroup;
    private TextInputEditText descriptionInput;
    private MaterialButton scanReceiptButton;
    private MaterialButton saveButton;
    private TextView addAnotherLink;

    private double currentAmount = 0.0;
    private Category selectedCategory = null;
    private static final double AMOUNT_INCREMENT = 10.0; // Amount to increase/decrease by

    private PreferencesManager preferencesManager;
    private OnTransactionAddedListener transactionAddedListener;
    
    // OCR related constants
    private static final int REQUEST_CAMERA_CAPTURE = 1001;
    private static final int REQUEST_OCR_RESULTS = 1002;

    // Interface for callbacks to the activity
    public interface OnTransactionAddedListener {
        void onTransactionAdded(Transaction transaction);
        void onMultipleTransactionsAdded();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnTransactionAddedListener) {
            transactionAddedListener = (OnTransactionAddedListener) context;
        } else {
            throw new RuntimeException(context.toString() + " must implement OnTransactionAddedListener");
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        
        dialog.setOnShowListener(dialogInterface -> {
            BottomSheetDialog d = (BottomSheetDialog) dialogInterface;
            View bottomSheetInternal = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheetInternal != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheetInternal);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
                behavior.setHideable(false); // Prevent dismissal by dragging
                behavior.setDraggable(true); // Allow dragging for scrolling
                
                // Set peek height to match screen height to keep it expanded
                behavior.setPeekHeight(getResources().getDisplayMetrics().heightPixels);
            }
        });
        
        return dialog;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_quick_entry, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize preferences manager
        preferencesManager = new PreferencesManager(requireContext());

        // Initialize ViewModels
        viewModel = new ViewModelProvider(this).get(QuickEntryViewModel.class);
        transactionViewModel = new ViewModelProvider(requireActivity()).get(TransactionViewModel.class);

        // Find views
        initializeViews(view);

        // Setup category recycler view
        setupCategoryRecyclerView();

        // Setup amount controls
        setupAmountControls();

        // Setup OCR scan button
        setupScanReceiptButton();

        // Setup save button
        setupSaveButton();

        // Setup "Add Another" link
        setupAddAnotherLink();

        // Observe changes in the ViewModel
        observeViewModel();
    }

    private void initializeViews(View view) {
        categoryRecyclerView = view.findViewById(R.id.categoryRecyclerView);
        amountDisplay = view.findViewById(R.id.amountDisplay);
        increaseButton = view.findViewById(R.id.increaseButton);
        decreaseButton = view.findViewById(R.id.decreaseButton);
        amountChipGroup = view.findViewById(R.id.amountChipGroup);
        descriptionInput = view.findViewById(R.id.descriptionInput);
        scanReceiptButton = view.findViewById(R.id.scanReceiptButton);
        saveButton = view.findViewById(R.id.saveButton);
        addAnotherLink = view.findViewById(R.id.addAnotherLink);

        // Setup touch handling for description input to prevent scroll conflicts
        setupTextInputTouchHandling();

        // Initialize amount display
        updateAmountDisplay();
    }

    private void setupTextInputTouchHandling() {
        // Find the ScrollView in the layout
        View rootView = getView();
        if (rootView instanceof android.widget.ScrollView) {
            android.widget.ScrollView scrollView = (android.widget.ScrollView) rootView;
            
            // Ensure proper nested scrolling
            scrollView.setNestedScrollingEnabled(true);
            scrollView.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    clearFocusFromTextInputs();
                }
                return false;
            });
        }
        
        if (descriptionInput != null) {
            // Allow text input to scroll internally when focused
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
        if (descriptionInput != null && descriptionInput.hasFocus()) {
            descriptionInput.clearFocus();
            // Hide keyboard
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(descriptionInput.getWindowToken(), 0);
            }
        }
    }

    private void setupCategoryRecyclerView() {
        // Create sample categories (you should load these from your ViewModel)
        List<Category> categories = createSampleCategories();

        // Setup adapter
        categoryAdapter = new CategoryTileAdapter(requireContext(), categories);
        categoryAdapter.setOnCategoryClickListener((category, position) -> {
            selectedCategory = category;

            // Update suggested amount based on category
            viewModel.suggestAmountForCategory(category.getName());
        });

        // Setup RecyclerView
        categoryRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 4));
        categoryRecyclerView.setAdapter(categoryAdapter);
    }

    private void setupAmountControls() {
        // Setup increase/decrease buttons
        increaseButton.setOnClickListener(v -> {
            currentAmount += AMOUNT_INCREMENT;
            updateAmountDisplay();

            // Clear any selected chip when manually changing amount
            amountChipGroup.clearCheck();
        });

        decreaseButton.setOnClickListener(v -> {
            if (currentAmount >= AMOUNT_INCREMENT) {
                currentAmount -= AMOUNT_INCREMENT;
                updateAmountDisplay();

                // Clear any selected chip when manually changing amount
                amountChipGroup.clearCheck();
            }
        });

        // Setup amount chips
        setupAmountChips();
    }

    private void setupAmountChips() {
        // Find all amount chips
        Chip chip50 = amountChipGroup.findViewById(R.id.chip50);
        Chip chip100 = amountChipGroup.findViewById(R.id.chip100);
        Chip chip200 = amountChipGroup.findViewById(R.id.chip200);
        Chip chip500 = amountChipGroup.findViewById(R.id.chip500);
        Chip chip1000 = amountChipGroup.findViewById(R.id.chip1000);
        Chip chipCustom = amountChipGroup.findViewById(R.id.chipCustom);

        // Set click listeners for fixed amount chips
        if (chip50 != null) {
            chip50.setOnClickListener(v -> {
                currentAmount = 50.0;
                updateAmountDisplay();
            });
        }

        if (chip100 != null) {
            chip100.setOnClickListener(v -> {
                currentAmount = 100.0;
                updateAmountDisplay();
            });
        }

        if (chip200 != null) {
            chip200.setOnClickListener(v -> {
                currentAmount = 200.0;
                updateAmountDisplay();
            });
        }

        if (chip500 != null) {
            chip500.setOnClickListener(v -> {
                currentAmount = 500.0;
                updateAmountDisplay();
            });
        }

        if (chip1000 != null) {
            chip1000.setOnClickListener(v -> {
                currentAmount = 1000.0;
                updateAmountDisplay();
            });
        }

        // Set click listener for custom amount
        if (chipCustom != null) {
            chipCustom.setOnClickListener(v -> {
                showCustomAmountDialog();
            });
        }
    }

    private void showCustomAmountDialog() {
        // Inflate the dialog layout
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_custom_amount, null);

        // Find views in the dialog
        EditText customAmountInput = dialogView.findViewById(R.id.customAmountInput);
        Button cancelButton = dialogView.findViewById(R.id.cancelButton);
        Button confirmButton = dialogView.findViewById(R.id.confirmButton);

        // Set up the numpad buttons
        setupNumpadButtons(dialogView, customAmountInput);

        // Create dialog
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();

        // Set current amount in the input field
        if (currentAmount > 0) {
            customAmountInput.setText(String.valueOf(currentAmount));
        }

        // Set up button click listeners
        cancelButton.setOnClickListener(v -> dialog.dismiss());

        confirmButton.setOnClickListener(v -> {
            // Parse the input
            try {
                String amountStr = customAmountInput.getText().toString();
                if (!amountStr.isEmpty()) {
                    currentAmount = Double.parseDouble(amountStr);
                    updateAmountDisplay();

                    // Clear chip selection
                    amountChipGroup.clearCheck();
                }
                dialog.dismiss();
            } catch (NumberFormatException e) {
                Toast.makeText(requireContext(), "Please enter a valid amount", Toast.LENGTH_SHORT).show();
            }
        });

        // Show the dialog
        dialog.show();
    }

    private void setupNumpadButtons(View dialogView, EditText customAmountInput) {
        // Find all numpad buttons
        Button button0 = dialogView.findViewById(R.id.button0);
        Button button1 = dialogView.findViewById(R.id.button1);
        Button button2 = dialogView.findViewById(R.id.button2);
        Button button3 = dialogView.findViewById(R.id.button3);
        Button button4 = dialogView.findViewById(R.id.button4);
        Button button5 = dialogView.findViewById(R.id.button5);
        Button button6 = dialogView.findViewById(R.id.button6);
        Button button7 = dialogView.findViewById(R.id.button7);
        Button button8 = dialogView.findViewById(R.id.button8);
        Button button9 = dialogView.findViewById(R.id.button9);
        Button buttonDecimal = dialogView.findViewById(R.id.buttonDecimal);
        Button buttonDelete = dialogView.findViewById(R.id.buttonDelete);

        // Set up click listeners for number buttons
        if (button0 != null) button0.setOnClickListener(v -> appendToInput(customAmountInput, "0"));
        if (button1 != null) button1.setOnClickListener(v -> appendToInput(customAmountInput, "1"));
        if (button2 != null) button2.setOnClickListener(v -> appendToInput(customAmountInput, "2"));
        if (button3 != null) button3.setOnClickListener(v -> appendToInput(customAmountInput, "3"));
        if (button4 != null) button4.setOnClickListener(v -> appendToInput(customAmountInput, "4"));
        if (button5 != null) button5.setOnClickListener(v -> appendToInput(customAmountInput, "5"));
        if (button6 != null) button6.setOnClickListener(v -> appendToInput(customAmountInput, "6"));
        if (button7 != null) button7.setOnClickListener(v -> appendToInput(customAmountInput, "7"));
        if (button8 != null) button8.setOnClickListener(v -> appendToInput(customAmountInput, "8"));
        if (button9 != null) button9.setOnClickListener(v -> appendToInput(customAmountInput, "9"));

        // Set up decimal point button
        if (buttonDecimal != null) {
            buttonDecimal.setOnClickListener(v -> {
                String currentText = customAmountInput.getText().toString();
                if (!currentText.contains(".")) {
                    appendToInput(customAmountInput, ".");
                }
            });
        }

        // Set up delete button
        if (buttonDelete != null) {
            buttonDelete.setOnClickListener(v -> {
                String currentText = customAmountInput.getText().toString();
                if (!currentText.isEmpty()) {
                    customAmountInput.setText(currentText.substring(0, currentText.length() - 1));
                }
            });
        }
    }

    private void appendToInput(EditText input, String text) {
        String currentText = input.getText().toString();
        input.setText(currentText + text);
    }

    private void setupScanReceiptButton() {
        scanReceiptButton.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), CameraCaptureActivity.class);
            startActivityForResult(intent, REQUEST_CAMERA_CAPTURE);
        });
    }

    private void setupSaveButton() {
        saveButton.setOnClickListener(v -> {
            // Validate input
            if (currentAmount <= 0) {
                Toast.makeText(requireContext(), "Please enter an amount", Toast.LENGTH_SHORT).show();
                return;
            }

            if (selectedCategory == null) {
                Toast.makeText(requireContext(), "Please select a category", Toast.LENGTH_SHORT).show();
                return;
            }

            // Create and save the transaction
            saveTransaction();
        });
    }

    private void setupAddAnotherLink() {
        addAnotherLink.setOnClickListener(v -> {
            // Save the current transaction
            if (currentAmount > 0 && selectedCategory != null) {
                saveTransaction();
            }

            // Reset the form for a new transaction
            resetForm();

            // Show a toast
            Toast.makeText(requireContext(), "Ready for next transaction", Toast.LENGTH_SHORT).show();
        });
    }

    private void saveTransaction() {
        // Get current timestamp
        long timestamp = System.currentTimeMillis();

        // Get the user input from description/notes field
        String userInput = "";
        if (descriptionInput != null && descriptionInput.getText() != null) {
            userInput = descriptionInput.getText().toString().trim();
        }
        
        // Create a description and note from the user input
        String description;
        String note = null;
        
        if (!userInput.isEmpty()) {
            // If user provided input, use it as both description and note
            description = userInput;
            note = userInput;
        } else {
            // If no user input, use default description
            description = selectedCategory.getName() + " Expense";
        }

        // Create the transaction
        Transaction transaction = new Transaction(
                "CASH", // Bank is "CASH" for manual entries
                "DEBIT", // Type is always DEBIT for expenses
                currentAmount,
                timestamp,
                description
        );

        // Set the category
        transaction.setCategory(selectedCategory.getName());
        
        // Set the note if provided
        if (note != null) {
            transaction.setNote(note);
        }

        // Save the transaction
        transactionViewModel.insert(transaction);

        // Update category usage statistics
        selectedCategory.incrementUseCount();

        // Save category preferences (this would depend on your implementation)
        // viewModel.updateCategoryUsage(selectedCategory);

        // Notify the listener
        if (transactionAddedListener != null) {
            transactionAddedListener.onTransactionAdded(transaction);
        }

        // Show success message
        Toast.makeText(requireContext(), "Transaction saved", Toast.LENGTH_SHORT).show();

        // Reset form if not adding another
        resetForm();

        // Dismiss the bottom sheet after saving
        dismiss();
    }

    private void resetForm() {
        // Reset amount
        currentAmount = 0.0;
        updateAmountDisplay();

        // Clear category selection
        categoryAdapter.setSelectedCategory(-1);
        selectedCategory = null;

        // Clear amount chip selection
        amountChipGroup.clearCheck();

        // Clear description
        descriptionInput.setText("");

        // Hide keyboard
        hideKeyboard();
    }

    private void hideKeyboard() {
        Activity activity = getActivity();
        if (activity != null) {
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            View currentFocus = activity.getCurrentFocus();
            if (currentFocus != null) {
                imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
            }
        }
    }

    private void updateAmountDisplay() {
        amountDisplay.setText(String.format(Locale.getDefault(), "₹%.2f", currentAmount));
    }

    private void observeViewModel() {
        // Observe any changes in the ViewModel that might affect the UI
        // For example, recently used categories or suggested amounts
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        // Reset the visibility state in MainActivity when bottom sheet is dismissed
        if (getActivity() != null && getActivity().getClass().getName().contains("MainActivity")) {
            try {
                getActivity().getClass().getMethod("onQuickEntryDismissed").invoke(getActivity());
            } catch (Exception e) {
                // Ignore if method doesn't exist
            }
        }
    }

    private List<Category> createSampleCategories() {
        // In a real implementation, you would load these from your ViewModel or repository
        List<Category> categories = new ArrayList<>();

        // Add standard categories
        categories.add(new Category("Food", R.drawable.ic_food, R.color.category_food));
        categories.add(new Category("Transport", R.drawable.ic_transport, R.color.category_transport));
        categories.add(new Category("Shopping", R.drawable.ic_shopping, R.color.category_shopping));
        categories.add(new Category("Entertainment", R.drawable.ic_entertainment, R.color.category_entertainment));
        categories.add(new Category("Bills", R.drawable.ic_bills, R.color.category_bills));
        categories.add(new Category("Health", R.drawable.ic_health, R.color.category_health));
        categories.add(new Category("Education", R.drawable.ic_education, R.color.category_education));
        categories.add(new Category("Others", R.drawable.ic_others, R.color.category_others));

        // Add a sample recent category
        // In a real app, you'd get this from user history
        categories.add(new Category("Coffee", R.drawable.ic_coffee, R.color.category_food, System.currentTimeMillis()));

        // Add a custom category option
        categories.add(new Category("Add New", R.drawable.ic_add, R.color.category_others, true));

        return categories;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (resultCode == Activity.RESULT_OK && data != null) {
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
                        String currentText = descriptionInput.getText().toString();
                        
                        if (appendMode && !currentText.trim().isEmpty()) {
                            // Append to existing text
                            descriptionInput.setText(currentText + "\n\n" + extractedText);
                        } else {
                            // Replace existing text
                            descriptionInput.setText(extractedText);
                        }
                        
                        // Clear focus from text input to allow page scrolling
                        clearFocusFromTextInputs();
                        
                        Toast.makeText(requireContext(), "Receipt text added successfully!", Toast.LENGTH_SHORT).show();
                    }
                    break;
            }
        }
    }
}