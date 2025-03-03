// Create a new adapter for date-grouped transactions
package com.example.expensetracker.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.expensetracker.R;
import com.example.expensetracker.models.Transaction;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class DateGroupedTransactionAdapter extends RecyclerView.Adapter<DateGroupedTransactionAdapter.DateGroupViewHolder> {

    private List<DateGroup> dateGroups = new ArrayList<>();
    private TransactionAdapter.OnTransactionClickListener listener;

    public static class DateGroup {
        private Date date;
        private List<Transaction> transactions;
        private double totalDebit = 0;
        private double totalCredit = 0;

        public DateGroup(Date date, List<Transaction> transactions) {
            this.date = date;
            this.transactions = transactions;
            calculateTotals();
        }

        private void calculateTotals() {
            for (Transaction transaction : transactions) {
                if ("DEBIT".equals(transaction.getType()) && !transaction.isExcludedFromTotal()) {
                    totalDebit += transaction.getAmount();
                } else if ("CREDIT".equals(transaction.getType()) && !transaction.isExcludedFromTotal()) {
                    totalCredit += transaction.getAmount();
                }
            }
        }

        public Date getDate() { return date; }
        public List<Transaction> getTransactions() { return transactions; }
        public double getTotalDebit() { return totalDebit; }
        public double getTotalCredit() { return totalCredit; }
        public int getTransactionCount() { return transactions.size(); }
        public double getNetAmount() { return totalCredit - totalDebit; }
    }

    public void setOnTransactionClickListener(TransactionAdapter.OnTransactionClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public DateGroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_date_group, parent, false);
        return new DateGroupViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DateGroupViewHolder holder, int position) {
        DateGroup group = dateGroups.get(position);
        holder.bind(group);
    }

    @Override
    public int getItemCount() {
        return dateGroups.size();
    }

    public void setTransactions(List<Transaction> transactions) {
        // Group transactions by date
        Map<Long, List<Transaction>> groupedByDate = new TreeMap<>(Collections.reverseOrder());

        for (Transaction transaction : transactions) {
            // Truncate time to get just the date
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(transaction.getDate());
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long dateKey = cal.getTimeInMillis();

            if (!groupedByDate.containsKey(dateKey)) {
                groupedByDate.put(dateKey, new ArrayList<>());
            }
            groupedByDate.get(dateKey).add(transaction);
        }

        // Convert to list of DateGroup objects
        List<DateGroup> newGroups = new ArrayList<>();
        for (Map.Entry<Long, List<Transaction>> entry : groupedByDate.entrySet()) {
            newGroups.add(new DateGroup(new Date(entry.getKey()), entry.getValue()));
        }

        this.dateGroups = newGroups;
        notifyDataSetChanged();
    }

    public void addTransactions(List<Transaction> newTransactions) {
        // Group new transactions by date
        Map<Long, List<Transaction>> groupedByDate = new HashMap<>();

        for (Transaction transaction : newTransactions) {
            // Truncate time to get just the date
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(transaction.getDate());
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long dateKey = cal.getTimeInMillis();

            if (!groupedByDate.containsKey(dateKey)) {
                groupedByDate.put(dateKey, new ArrayList<>());
            }
            groupedByDate.get(dateKey).add(transaction);
        }

        // Merge with existing groups or add new ones
        Map<Long, Integer> existingGroupIndexes = new HashMap<>();
        for (int i = 0; i < dateGroups.size(); i++) {
            DateGroup group = dateGroups.get(i);
            existingGroupIndexes.put(group.getDate().getTime(), i);
        }

        for (Map.Entry<Long, List<Transaction>> entry : groupedByDate.entrySet()) {
            if (existingGroupIndexes.containsKey(entry.getKey())) {
                // Update existing group
                int index = existingGroupIndexes.get(entry.getKey());
                DateGroup group = dateGroups.get(index);
                group.getTransactions().addAll(entry.getValue());
                notifyItemChanged(index);
            } else {
                // Add new group
                DateGroup newGroup = new DateGroup(new Date(entry.getKey()), entry.getValue());
                dateGroups.add(newGroup);
            }
        }

        // Sort groups by date (newest first)
        Collections.sort(dateGroups, (a, b) -> Long.compare(b.getDate().getTime(), a.getDate().getTime()));
        notifyDataSetChanged();
    }

    public void clearTransactions() {
        int size = dateGroups.size();
        dateGroups.clear();
        notifyItemRangeRemoved(0, size);
    }

    class DateGroupViewHolder extends RecyclerView.ViewHolder {
        private final TextView dateText;
        private final TextView countText;
        private final TextView totalText;
        private final RecyclerView transactionsRecyclerView;
        private final TransactionAdapter transactionAdapter;

        DateGroupViewHolder(View itemView) {
            super(itemView);
            dateText = itemView.findViewById(R.id.dateGroupText);
            countText = itemView.findViewById(R.id.transactionCountText);
            totalText = itemView.findViewById(R.id.totalAmountText);
            transactionsRecyclerView = itemView.findViewById(R.id.nestedTransactionList);

            // Set up nested RecyclerView
            transactionAdapter = new TransactionAdapter();
            transactionsRecyclerView.setLayoutManager(new LinearLayoutManager(itemView.getContext()));
            transactionsRecyclerView.setAdapter(transactionAdapter);

            // Pass through click events
            if (listener != null) {
                transactionAdapter.setOnTransactionClickListener(listener);
            }
        }

        void bind(DateGroup group) {
            // Format date as day of week, month day (e.g., "Monday, Jan 15")
            SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault());
            dateText.setText(dateFormat.format(group.getDate()));

            // Show transaction count
            countText.setText(String.format(Locale.getDefault(), "%d transactions", group.getTransactionCount()));

            // Calculate net amount for the day
            double netAmount = group.getNetAmount();
            String formattedAmount = String.format(Locale.getDefault(), "₹%.2f", Math.abs(netAmount));
            if (netAmount > 0) {
                totalText.setText("+" + formattedAmount);
                totalText.setTextColor(itemView.getContext().getColor(R.color.green));
            } else if (netAmount < 0) {
                totalText.setText("-" + formattedAmount);
                totalText.setTextColor(itemView.getContext().getColor(R.color.red));
            } else {
                totalText.setText(formattedAmount);
                totalText.setTextColor(itemView.getContext().getColor(R.color.text_primary));
            }

            // Set transactions in nested adapter
            transactionAdapter.setTransactions(group.getTransactions());
        }
    }
}