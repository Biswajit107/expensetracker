package com.example.expensetracker.adapters;

import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.example.expensetracker.R;
import com.example.expensetracker.models.Transaction;
import com.google.android.material.chip.Chip;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder> {
    private List<Transaction> transactions = new ArrayList<>();
    private OnTransactionClickListener listener;

    // Interface for click handling
    public interface OnTransactionClickListener {
        void onTransactionClick(Transaction transaction);
    }

    // Set click listener
    public void setOnTransactionClickListener(OnTransactionClickListener listener) {
        this.listener = listener;
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

    class TransactionViewHolder extends RecyclerView.ViewHolder {
        private TextView dateText;
        private TextView bankText;
        private Chip typeChip;
        private TextView amountText;
        private TextView descriptionText;
        private TextView categoryIndicator;
        private TextView otherBankIndicator;
        private TextView excludedHintText;

        public TransactionViewHolder(@NonNull View itemView) {
            super(itemView);
            dateText = itemView.findViewById(R.id.dateText);
            bankText = itemView.findViewById(R.id.bankText);
            typeChip = itemView.findViewById(R.id.typeChip);
            amountText = itemView.findViewById(R.id.amountText);
            descriptionText = itemView.findViewById(R.id.descriptionText);
            categoryIndicator = itemView.findViewById(R.id.categoryIndicator);

            // Add these new fields to the ViewHolder class
            otherBankIndicator = itemView.findViewById(R.id.otherBankIndicator);
            excludedHintText = itemView.findViewById(R.id.excludedHintText);

            // Set click listener
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onTransactionClick(transactions.get(position));
                }
            });
        }

        void bind(Transaction transaction) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            dateText.setText(dateFormat.format(new Date(transaction.getDate())));
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

            // Special handling for excluded transactions
            if (transaction.isExcludedFromTotal()) {
                // Add strikethrough
                amountText.setPaintFlags(amountText.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);

                // Change text color to indicate excluded status
                amountText.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.text_secondary));

                // If it's from an unrecognized bank (OTHER), apply additional styling
                if ("OTHER".equals(transaction.getBank()) || transaction.isOtherDebit()) {
                    // Apply a background tint to the entire item to make it distinct
                    itemView.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), R.color.background_light_grey));

                    // Add an indicator icon or text
                    if (otherBankIndicator != null) {
                        otherBankIndicator.setVisibility(View.VISIBLE);
                    }

                    // Add italic style to description
                    descriptionText.setTypeface(descriptionText.getTypeface(), Typeface.ITALIC);

                    // Add a hint about excluded status
                    if (excludedHintText != null) {
                        excludedHintText.setVisibility(View.VISIBLE);
                        excludedHintText.setText("Auto-excluded (unknown source)");
                    }
                } else {
                    // Reset background for regular excluded transactions
                    itemView.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), R.color.white));

                    // Hide the indicators for regular transactions
                    if (otherBankIndicator != null) {
                        otherBankIndicator.setVisibility(View.GONE);
                    }

                    // Reset text style
                    descriptionText.setTypeface(descriptionText.getTypeface(), Typeface.NORMAL);

                    // Update excluded hint
                    if (excludedHintText != null) {
                        excludedHintText.setVisibility(View.VISIBLE);
                        excludedHintText.setText("Excluded from totals");
                    }
                }
            } else {
                // Remove strikethrough for non-excluded transactions
                amountText.setPaintFlags(amountText.getPaintFlags() & (~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG));

                // Reset background
                itemView.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), R.color.white));

                // Hide indicators
                if (otherBankIndicator != null) {
                    otherBankIndicator.setVisibility(View.GONE);
                }

                // Hide excluded hint
                if (excludedHintText != null) {
                    excludedHintText.setVisibility(View.GONE);
                }

                // Reset text style
                descriptionText.setTypeface(descriptionText.getTypeface(), Typeface.NORMAL);

                // Set color based on transaction type
                if ("CREDIT".equals(transaction.getType())) {
                    amountText.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.green));
                } else {
                    amountText.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.red));
                }
            }

            amountText.setText(String.format(Locale.getDefault(), "₹%.2f", transaction.getAmount()));
        }

        // Helper method to get category color
        private int getCategoryColor(String category) {
            Context context = itemView.getContext();

            switch (category) {
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
                    return ContextCompat.getColor(context, R.color.text_secondary);
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
}