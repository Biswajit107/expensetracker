package com.example.expensetracker.adapters;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import com.example.expensetracker.R;
import com.example.expensetracker.models.Transaction;
import com.example.expensetracker.viewmodel.CategoryViewModel;
import com.google.android.material.chip.Chip;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder> {
    private List<Transaction> transactions = new ArrayList<>();
    private OnTransactionClickListener listener;
    // Add a field for the category click listener
    private OnCategoryClickListener categoryClickListener;

    // Interface for click handling
    public interface OnTransactionClickListener {
        void onTransactionClick(Transaction transaction);
    }

    // Interface for long-click handling
    public interface OnTransactionLongClickListener {
        void onTransactionLongClick(Transaction transaction);
    }

    // Set click listener
    public void setOnTransactionClickListener(OnTransactionClickListener listener) {
        this.listener = listener;
    }

    private OnTransactionLongClickListener longClickListener;

    // Set long-click listener
    public void setOnTransactionLongClickListener(OnTransactionLongClickListener listener) {
        this.longClickListener = listener;
    }

    // Add a new listener interface for category clicks
    public interface OnCategoryClickListener {
        void onCategoryClick(Transaction transaction, View categoryView);
    }

    @NonNull
    @Override
    public TransactionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transaction, parent, false);
        return new TransactionViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull TransactionViewHolder holder, int position) {
        Transaction current = transactions.get(position);
        holder.bind(current);
    }

    @Override
    public int getItemCount() {
        return transactions.size();
    }

    public void setTransactions(List<Transaction> transactions) {
        this.transactions = transactions;
        notifyDataSetChanged();
    }

    // Add a method to set the category click listener
    public void setOnCategoryClickListener(OnCategoryClickListener listener) {
        this.categoryClickListener = listener;
    }

    public class TransactionViewHolder extends RecyclerView.ViewHolder {
        private TextView dateText;
        private TextView timeText;
        private TextView bankText;
        private Chip typeChip;
        private TextView amountText;
        private TextView descriptionText;
        private TextView categoryIndicator;
        private TextView otherBankIndicator;
        private TextView excludedHintText;
        private TextView noteText;
        private TextView noteExpandToggle;

        public TransactionViewHolder(@NonNull View itemView) {
            super(itemView);
            dateText = itemView.findViewById(R.id.dateText);
            timeText = itemView.findViewById(R.id.timeText);
            bankText = itemView.findViewById(R.id.bankText);
            typeChip = itemView.findViewById(R.id.typeChip);
            amountText = itemView.findViewById(R.id.amountText);
            descriptionText = itemView.findViewById(R.id.descriptionText);
            categoryIndicator = itemView.findViewById(R.id.categoryIndicator);

            // Add these new fields to the ViewHolder class
            otherBankIndicator = itemView.findViewById(R.id.otherBankIndicator);
            excludedHintText = itemView.findViewById(R.id.excludedHintText);
            noteText = itemView.findViewById(R.id.noteText);
            noteExpandToggle = itemView.findViewById(R.id.noteExpandToggle);

            // Set click listener
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onTransactionClick(transactions.get(position));
                }
            });

            // Set long-click listener
            itemView.setOnLongClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && longClickListener != null) {
                    longClickListener.onTransactionLongClick(transactions.get(position));
                    return true;
                }
                return false;
            });
        }

        // In TransactionAdapter.java, update the bind method to add visual indicators for manually excluded transactions

        void bind(Transaction transaction) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
            
            Date transactionDate = new Date(transaction.getDate());
            dateText.setText(dateFormat.format(transactionDate));
            timeText.setText(timeFormat.format(transactionDate));
            
            bankText.setText(transaction.getBank());

            typeChip.setText(transaction.getType());
            if ("CREDIT".equals(transaction.getType())) {
                typeChip.setChipBackgroundColorResource(R.color.green_light);
                typeChip.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.green_dark));
                amountText.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.green));
            } else {
                typeChip.setChipBackgroundColorResource(R.color.red_light);
                typeChip.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.red_dark));
                amountText.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.red));
            }

            // Set description
            descriptionText.setText(transaction.getDescription());

            // Apply category coloring
            if (transaction.getCategory() != null && !transaction.getCategory().isEmpty()) {
                categoryIndicator.setVisibility(View.VISIBLE);
                categoryIndicator.setText(transaction.getCategory());

                // Apply category-specific styling
                int categoryColor = getCategoryColor(transaction.getCategory());
                View categoryColorIndicator = itemView.findViewById(R.id.categoryColorIndicator);
                if (categoryColorIndicator != null) {
                    categoryColorIndicator.setBackgroundColor(categoryColor);
                    categoryColorIndicator.setVisibility(View.VISIBLE);
                }

                categoryIndicator.setTextColor(categoryColor);
            } else {
                categoryIndicator.setVisibility(View.GONE);
                View categoryColorIndicator = itemView.findViewById(R.id.categoryColorIndicator);
                if (categoryColorIndicator != null) {
                    categoryColorIndicator.setVisibility(View.INVISIBLE);
                }
            }

            // Handle excluded transactions - differentiate between manually excluded and auto-excluded
            if (transaction.isExcludedFromTotal()) {
                if (!transaction.isOtherDebit()) {
                    // This is a manually excluded transaction - show special indicator
                    if (excludedHintText != null) {
                        excludedHintText.setVisibility(View.VISIBLE);
                        excludedHintText.setText("Manually excluded");
                        excludedHintText.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.purple));
                    }

                    // Add strikethrough to the amount
                    amountText.setPaintFlags(amountText.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);

                    // Change color to indicate excluded status but with a different color than auto-excluded
                    amountText.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.purple_light));

                    // Reset background - don't use the gray background that's used for auto-excluded
                    itemView.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), R.color.white));

                    // Hide the "other bank" indicator that's used for auto-excluded
                    if (otherBankIndicator != null) {
                        otherBankIndicator.setVisibility(View.GONE);
                    }

                    // No need for italic style that's used for auto-excluded
                    descriptionText.setTypeface(descriptionText.getTypeface(), Typeface.NORMAL);
                } else {
                    // This is an auto-excluded transaction (from OTHER bank)
                    // Apply the existing styling for auto-excluded transactions
                    amountText.setPaintFlags(amountText.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
                    amountText.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.text_secondary));

                    // Apply background tint
                    itemView.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), R.color.background_light_grey));

                    // Show the "other bank" indicator
                    if (otherBankIndicator != null) {
                        otherBankIndicator.setVisibility(View.VISIBLE);
                    }

                    // Add italic style
                    descriptionText.setTypeface(descriptionText.getTypeface(), Typeface.ITALIC);

                    // Update excluded hint
                    if (excludedHintText != null) {
                        excludedHintText.setVisibility(View.VISIBLE);
                        excludedHintText.setText("Auto-excluded (unknown source)");
                        excludedHintText.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.text_secondary));
                    }
                }
            } else {
                // Non-excluded transaction - use the existing styling
                amountText.setPaintFlags(amountText.getPaintFlags() & (~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG));
                itemView.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), R.color.white));

                if (otherBankIndicator != null) {
                    otherBankIndicator.setVisibility(View.GONE);
                }

                if (excludedHintText != null) {
                    excludedHintText.setVisibility(View.GONE);
                }

                descriptionText.setTypeface(descriptionText.getTypeface(), Typeface.NORMAL);

                // Set color based on transaction type
                if ("CREDIT".equals(transaction.getType())) {
                    amountText.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.green));
                } else {
                    amountText.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.red));
                }
            }

            amountText.setText(String.format(Locale.getDefault(), "₹%.2f", transaction.getAmount()));

            // Handle note display
            if (transaction.getNote() != null && !transaction.getNote().trim().isEmpty()) {
                noteText.setVisibility(View.VISIBLE);
                String noteContent = transaction.getNote().trim();
                
                // Check if note is long and needs expand/collapse functionality
                if (noteContent.length() > 80) {
                    // Show truncated note initially
                    noteText.setText(noteContent.substring(0, 80) + "...");
                    noteExpandToggle.setVisibility(View.VISIBLE);
                    
                    // Set up expand/collapse click listener
                    final boolean[] isExpanded = {false};
                    noteExpandToggle.setOnClickListener(v -> {
                        if (isExpanded[0]) {
                            noteText.setText(noteContent.substring(0, 80) + "...");
                            noteExpandToggle.setText("Tap to expand");
                            noteText.setMaxLines(2);
                            isExpanded[0] = false;
                        } else {
                            noteText.setText(noteContent);
                            noteExpandToggle.setText("Tap to collapse");
                            noteText.setMaxLines(Integer.MAX_VALUE);
                            isExpanded[0] = true;
                        }
                    });
                } else {
                    // Show full note if it's short
                    noteText.setText(noteContent);
                    noteExpandToggle.setVisibility(View.GONE);
                }
            } else {
                noteText.setVisibility(View.GONE);
                noteExpandToggle.setVisibility(View.GONE);
            }
        }

        // Helper method to get category color
        private int getCategoryColor(String categoryName) {
            Context context = itemView.getContext();

            // Handle predefined categories first with immediate return
            switch (categoryName) {
                case Transaction.Categories.FOOD:
                    return ContextCompat.getColor(context, R.color.category_food);
                case Transaction.Categories.SHOPPING:
                    return ContextCompat.getColor(context, R.color.category_shopping);
                case Transaction.Categories.BILLS:
                    return ContextCompat.getColor(context, R.color.category_bills);
                case Transaction.Categories.ENTERTAINMENT:
                    return ContextCompat.getColor(context, R.color.category_entertainment);
                case Transaction.Categories.TRANSPORT:
                    return ContextCompat.getColor(context, R.color.category_transport);
                case Transaction.Categories.HEALTH:
                    return ContextCompat.getColor(context, R.color.category_health);
                case Transaction.Categories.EDUCATION:
                    return ContextCompat.getColor(context, R.color.category_education);
                default:
                    // Return default color immediately
                    int defaultColor = ContextCompat.getColor(context, R.color.text_secondary);

                    // Start async lookup that will update the UI element later
                    FragmentActivity activity = (FragmentActivity) context;
                    CategoryViewModel viewModel = new ViewModelProvider(activity).get(CategoryViewModel.class);

                    viewModel.getCategoryByNameAsync(categoryName, category -> {
                        if (category != null && category.getColor() != null) {
                            try {
                                int customColor = Color.parseColor(category.getColor());
                                // Update the UI element directly with the new color when it's available
                                updateCategoryIndicatorColor(categoryName, customColor);
                            } catch (Exception e) {
                                Log.e("TransactionAdapter", "Error parsing color", e);
                            }
                        }
                    });

                    // Return default color for now
                    return defaultColor;
            }
        }

        // Add a method to update the UI when color becomes available
        private void updateCategoryIndicatorColor(String categoryName, int color) {
            // Find the view that needs updating
            // This depends on your specific implementation

            // Example:
            if (categoryIndicator != null && categoryName.equals(categoryName)) {
                categoryIndicator.setTextColor(color);

                View categoryColorIndicator = itemView.findViewById(R.id.categoryColorIndicator);
                if (categoryColorIndicator != null) {
                    categoryColorIndicator.setBackgroundColor(color);
                }
            }
        }
    }

    public List<Transaction> getTransactions() {
        return new ArrayList<>(transactions); // Return a copy to avoid modification
    }

    public void addTransactions(List<Transaction> newTransactions) {
        int startPosition = this.transactions.size();
        this.transactions.addAll(newTransactions);
        notifyItemRangeInserted(startPosition, newTransactions.size());
    }

    public void clearTransactions() {
        int size = this.transactions.size();
        this.transactions.clear();
        notifyItemRangeRemoved(0, size);
    }

    /**
     * Get the current transaction click listener
     * @return The OnTransactionClickListener
     */
    public OnTransactionClickListener getOnTransactionClickListener() {
        return this.listener;
    }
}