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

        public TransactionViewHolder(@NonNull View itemView) {
            super(itemView);
            dateText = itemView.findViewById(R.id.dateText);
            bankText = itemView.findViewById(R.id.bankText);
            typeChip = itemView.findViewById(R.id.typeChip);
            amountText = itemView.findViewById(R.id.amountText);
            descriptionText = itemView.findViewById(R.id.descriptionText);
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

            amountText.setText(String.format(Locale.getDefault(), "₹%.2f", transaction.getAmount()));
            descriptionText.setText(transaction.getDescription());
        }
    }
}
