package com.example.expensetracker.adapters;

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

        public TransactionViewHolder(@NonNull View itemView) {
            super(itemView);
            dateText = itemView.findViewById(R.id.dateText);
            bankText = itemView.findViewById(R.id.bankText);
            typeChip = itemView.findViewById(R.id.typeChip);
            amountText = itemView.findViewById(R.id.amountText);
            descriptionText = itemView.findViewById(R.id.descriptionText);
            categoryIndicator = itemView.findViewById(R.id.categoryIndicator);

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

            // Strikethrough text if excluded from totals
            if (transaction.isExcludedFromTotal()) {
                amountText.setPaintFlags(amountText.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
                // Add italics to indicate excluded status
                amountText.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.text_secondary));
            } else {
                amountText.setPaintFlags(amountText.getPaintFlags() & (~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG));
                // Set color based on transaction type
                if ("CREDIT".equals(transaction.getType())) {
                    amountText.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.green));
                } else {
                    amountText.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.red));
                }
            }

            amountText.setText(String.format(Locale.getDefault(), "₹%.2f", transaction.getAmount()));

            // Set description
            descriptionText.setText(transaction.getDescription());

            // Display category indicator if available
            if (transaction.getCategory() != null && !transaction.getCategory().isEmpty()) {
                categoryIndicator.setVisibility(View.VISIBLE);
                categoryIndicator.setText(transaction.getCategory());

                // Set category indicator color based on category
                int categoryColor;
                switch (transaction.getCategory()) {
                    case Transaction.Categories.FOOD:
                        categoryColor = ContextCompat.getColor(itemView.getContext(), R.color.green);
                        break;
                    case Transaction.Categories.SHOPPING:
                        categoryColor = ContextCompat.getColor(itemView.getContext(), R.color.primary);
                        break;
                    case Transaction.Categories.BILLS:
                        categoryColor = ContextCompat.getColor(itemView.getContext(), R.color.red);
                        break;
                    case Transaction.Categories.ENTERTAINMENT:
                        categoryColor = ContextCompat.getColor(itemView.getContext(), R.color.secondary);
                        break;
                    case Transaction.Categories.TRANSPORT:
                        categoryColor = ContextCompat.getColor(itemView.getContext(), R.color.yellow);
                        break;
                    default:
                        categoryColor = ContextCompat.getColor(itemView.getContext(), R.color.text_secondary);
                }
                categoryIndicator.setTextColor(categoryColor);
            } else {
                categoryIndicator.setVisibility(View.GONE);
            }
        }
    }
}