package com.example.expensetracker.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.expensetracker.R;
import com.example.expensetracker.models.Transaction;
import com.example.expensetracker.models.CustomCategory;
import com.example.expensetracker.viewmodel.CategoryViewModel;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CategorySelectionDialog extends DialogFragment {
    private Transaction transaction;
    private OnCategorySelectedListener listener;
    private CategoryViewModel viewModel;
    private View customCategoryView;
    private String selectedColor = "#4CAF50"; // Default color

    public interface OnCategorySelectedListener {
        void onCategorySelected(Transaction transaction, String category, boolean isCustom);
    }

    public CategorySelectionDialog(Transaction transaction) {
        this.transaction = transaction;
    }

    public void setOnCategorySelectedListener(OnCategorySelectedListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        View view = requireActivity().getLayoutInflater().inflate(R.layout.dialog_category_selection, null);

        builder.setTitle("Select Category");
        builder.setView(view);

        // Initialize the category view model
        viewModel = new ViewModelProvider(requireActivity()).get(CategoryViewModel.class);

        // Set up the predefined categories grid
        setupPredefinedCategories(view);

        // Set up the custom categories section
        setupCustomCategories(view);

        // Set up the "Add New" button
        Button addNewButton = view.findViewById(R.id.addNewCategoryButton);
        customCategoryView = view.findViewById(R.id.customCategoryInputContainer);

        addNewButton.setOnClickListener(v -> {
            // Show the custom category input section
            customCategoryView.setVisibility(View.VISIBLE);
        });

        // Set up color selection
        setupColorSelection(view);

        // Set up Save button for custom category
        Button saveCustomButton = view.findViewById(R.id.saveCustomCategoryButton);
        saveCustomButton.setOnClickListener(v -> {
            EditText categoryNameInput = view.findViewById(R.id.customCategoryNameInput);
            String categoryName = categoryNameInput.getText().toString().trim();

            if (categoryName.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a category name", Toast.LENGTH_SHORT).show();
                return;
            }

            // Create and save the custom category
            CustomCategory newCategory = new CustomCategory(categoryName, selectedColor);
            viewModel.insertCategory(newCategory);

            // Apply the category to the transaction and notify listener
            if (listener != null) {
                listener.onCategorySelected(transaction, categoryName, true);
            }

            dismiss();
        });

        return builder.create();
    }

    private void setupPredefinedCategories(View view) {
        ViewGroup categoriesContainer = view.findViewById(R.id.predefinedCategoriesContainer);
        String[] categories = Transaction.Categories.getAllCategories();

        for (String category : categories) {
            View categoryView = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_category_selection, categoriesContainer, false);

            TextView categoryText = categoryView.findViewById(R.id.categoryText);
            categoryText.setText(category);

            // Set background tint based on category
            MaterialCardView cardView = (MaterialCardView) categoryView;
            cardView.setCardBackgroundColor(getCategoryColor(category));

            // Set click listener
            categoryView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCategorySelected(transaction, category, false);
                }
                dismiss();
            });

            categoriesContainer.addView(categoryView);
        }
    }

    private void setupCustomCategories(View view) {
        RecyclerView customCategoriesRecyclerView = view.findViewById(R.id.customCategoriesRecyclerView);
        customCategoriesRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));

        // Create adapter for custom categories
        CustomCategoryAdapter adapter = new CustomCategoryAdapter();
        customCategoriesRecyclerView.setAdapter(adapter);

        // Observe custom categories
        viewModel.getAllCustomCategories().observe(this, customCategories -> {
            adapter.setCategories(customCategories);
        });
    }

    private void setupColorSelection(View view) {
        ViewGroup colorContainer = view.findViewById(R.id.colorSelectionContainer);

        // Define available colors
        String[] colors = {"#F44336", "#2196F3", "#4CAF50", "#FFC107", "#9C27B0", "#795548"};

        for (String color : colors) {
            View colorView = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_color_selection, colorContainer, false);

            View colorCircle = colorView.findViewById(R.id.colorCircle);
            colorCircle.setBackgroundColor(android.graphics.Color.parseColor(color));

            colorView.setOnClickListener(v -> {
                // Update selected color
                selectedColor = color;

                // Update UI to show selection
                for (int i = 0; i < colorContainer.getChildCount(); i++) {
                    View child = colorContainer.getChildAt(i);
                    child.setSelected(false);
                }
                colorView.setSelected(true);
            });

            colorContainer.addView(colorView);
        }
    }

    // Helper class for custom categories adapter
    private class CustomCategoryAdapter extends RecyclerView.Adapter<CustomCategoryAdapter.ViewHolder> {
        private List<CustomCategory> categories = new ArrayList<>();

        void setCategories(List<CustomCategory> categories) {
            this.categories = categories;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_category_selection, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            CustomCategory category = categories.get(position);
            holder.bind(category);
        }

        @Override
        public int getItemCount() {
            return categories.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            MaterialCardView cardView;
            TextView categoryText;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                cardView = (MaterialCardView) itemView;
                categoryText = itemView.findViewById(R.id.categoryText);

                itemView.setOnClickListener(v -> {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION && listener != null) {
                        CustomCategory category = categories.get(position);
                        listener.onCategorySelected(transaction, category.getName(), true);

                        // Increment use count
                        viewModel.incrementCategoryUseCount(category.getId());

                        dismiss();
                    }
                });
            }

            void bind(CustomCategory category) {
                categoryText.setText(category.getName());

                // Set card background color
                try {
                    cardView.setCardBackgroundColor(android.graphics.Color.parseColor(category.getColor()));

                    // Set text color (white for dark backgrounds, black for light backgrounds)
                    int color = android.graphics.Color.parseColor(category.getColor());
                    boolean isDarkColor = isDarkColor(color);
                    categoryText.setTextColor(isDarkColor ?
                            android.graphics.Color.WHITE : android.graphics.Color.BLACK);
                } catch (Exception e) {
                    // Fallback for invalid colors
                    cardView.setCardBackgroundColor(android.graphics.Color.LTGRAY);
                }
            }
        }
    }

    // Helper function to get category color
    private int getCategoryColor(String category) {
        // Implementation similar to your existing code in TransactionAdapter
        switch (category) {
            case Transaction.Categories.FOOD:
                return getResources().getColor(R.color.category_food, null);
            case Transaction.Categories.SHOPPING:
                return getResources().getColor(R.color.category_shopping, null);
            case Transaction.Categories.BILLS:
                return getResources().getColor(R.color.category_bills, null);
            case Transaction.Categories.ENTERTAINMENT:
                return getResources().getColor(R.color.category_entertainment, null);
            case Transaction.Categories.TRANSPORT:
                return getResources().getColor(R.color.category_transport, null);
            case Transaction.Categories.HEALTH:
                return getResources().getColor(R.color.category_health, null);
            case Transaction.Categories.EDUCATION:
                return getResources().getColor(R.color.category_education, null);
            default:
                return getResources().getColor(R.color.text_secondary, null);
        }
    }

    // Helper to determine if a color is dark (for determining text color)
    private boolean isDarkColor(int color) {
        double darkness = 1 - (0.299 * android.graphics.Color.red(color)
                + 0.587 * android.graphics.Color.green(color)
                + 0.114 * android.graphics.Color.blue(color)) / 255;
        return darkness >= 0.5;
    }
}