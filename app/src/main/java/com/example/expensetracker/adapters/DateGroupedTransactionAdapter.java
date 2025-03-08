package com.example.expensetracker.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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

/**
 * Adapter for displaying transactions grouped by date
 */
public class DateGroupedTransactionAdapter extends RecyclerView.Adapter<DateGroupedTransactionAdapter.DateGroupViewHolder> {

    private List<DateGroup> dateGroups = new ArrayList<>();
    private TransactionAdapter.OnTransactionClickListener transactionClickListener;
    private Context context;

    // Limit of transactions to show per group before "Show More" option
    private static final int INITIAL_TRANSACTION_LIMIT = 3;

    /**
     * Constructor requiring context for formatting
     */
    public DateGroupedTransactionAdapter(Context context) {
        this.context = context;
    }

    /**
     * Set transaction click listener to pass through to child adapters
     */
    public void setOnTransactionClickListener(TransactionAdapter.OnTransactionClickListener listener) {
        this.transactionClickListener = listener;

        // Update existing child adapters
        for (DateGroup group : dateGroups) {
            if (group.adapter != null) {
                group.adapter.setOnTransactionClickListener(listener);
            }
        }
    }

    /**
     * Group transactions by date and set them to this adapter
     */
    public void setTransactions(List<Transaction> transactions) {
        // Group transactions by date
        Map<Long, List<Transaction>> groupedByDate = groupTransactionsByDate(transactions);

        // Convert to adapter's data structure
        List<DateGroup> newDateGroups = new ArrayList<>();

        for (Map.Entry<Long, List<Transaction>> entry : groupedByDate.entrySet()) {
            List<Transaction> dayTransactions = entry.getValue();

            // Calculate totals for this date
            double totalDebit = 0;
            double totalCredit = 0;

            for (Transaction transaction : dayTransactions) {
                if (!transaction.isExcludedFromTotal()) {
                    if (transaction.isDebit()) {
                        totalDebit += transaction.getAmount();
                    } else {
                        totalCredit += transaction.getAmount();
                    }
                }
            }

            // Create date group
            DateGroup group = new DateGroup(
                    entry.getKey(),
                    dayTransactions,
                    totalDebit,
                    totalCredit,
                    dayTransactions.size()
            );

            newDateGroups.add(group);
        }

        // Sort by date (newest first)
        Collections.sort(newDateGroups, (a, b) -> Long.compare(b.dateTimestamp, a.dateTimestamp));

        // Update data and notify
        this.dateGroups = newDateGroups;
        notifyDataSetChanged();
    }

    /**
     * Update a single transaction within the grouped structure
     * @param updatedTransaction The updated transaction
     */
    public void updateTransaction(Transaction updatedTransaction) {
        if (updatedTransaction == null) return;

        boolean found = false;

        // We need to find which date group contains this transaction
        for (DateGroup group : dateGroups) {
            for (int i = 0; i < group.transactions.size(); i++) {
                Transaction transaction = group.transactions.get(i);
                if (transaction.getId() == updatedTransaction.getId()) {
                    // Replace the transaction
                    group.transactions.set(i, updatedTransaction);
                    found = true;

                    // Recalculate group totals
                    double totalDebit = 0;
                    double totalCredit = 0;

                    for (Transaction t : group.transactions) {
                        if (!t.isExcludedFromTotal()) {
                            if (t.isDebit()) {
                                totalDebit += t.getAmount();
                            } else {
                                totalCredit += t.getAmount();
                            }
                        }
                    }

                    // Update group data
                    group.totalDebit = totalDebit;
                    group.totalCredit = totalCredit;

                    // Update the adapter
                    if (group.adapter != null) {
                        group.adapter.setTransactions(group.transactions);
                    }

                    // Notify data changed for this group
                    int groupIndex = dateGroups.indexOf(group);
                    if (groupIndex >= 0) {
                        notifyItemChanged(groupIndex);
                    }

                    break;
                }
            }
            if (found) break;
        }

        // If transaction wasn't found in any group, it might belong to a different date range
        // In this case, a full reload might be necessary, but that's handled by the caller
    }

    /**
     * Group transactions by date (day)
     */
    private Map<Long, List<Transaction>> groupTransactionsByDate(List<Transaction> transactions) {
        // TreeMap to have entries naturally sorted by key (timestamp)
        Map<Long, List<Transaction>> groupedTransactions = new TreeMap<>(Collections.reverseOrder());

        if (transactions == null || transactions.isEmpty()) {
            return groupedTransactions;
        }

        Calendar calendar = Calendar.getInstance();

        for (Transaction transaction : transactions) {
            // Get transaction date and normalize to start of day
            calendar.setTimeInMillis(transaction.getDate());
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);

            long dayTimestamp = calendar.getTimeInMillis();

            // Add transaction to its day group
            if (!groupedTransactions.containsKey(dayTimestamp)) {
                groupedTransactions.put(dayTimestamp, new ArrayList<>());
            }

            groupedTransactions.get(dayTimestamp).add(transaction);
        }

        // Sort transactions within each group (by amount, highest first)
        for (List<Transaction> group : groupedTransactions.values()) {
            Collections.sort(group, (a, b) -> Double.compare(b.getAmount(), a.getAmount()));
        }

        return groupedTransactions;
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
        DateGroup dateGroup = dateGroups.get(position);
        holder.bind(dateGroup);
    }

    @Override
    public int getItemCount() {
        return dateGroups.size();
    }

    /**
     * ViewHolder for date groups
     */
    class DateGroupViewHolder extends RecyclerView.ViewHolder {
        private TextView dateGroupText;
        private TextView transactionCountText;
        private TextView totalAmountText;
        private RecyclerView nestedTransactionList;

        // Child adapter for this group's transactions
        private TransactionAdapter adapter;

        public DateGroupViewHolder(@NonNull View itemView) {
            super(itemView);
            dateGroupText = itemView.findViewById(R.id.dateGroupText);
            transactionCountText = itemView.findViewById(R.id.transactionCountText);
            totalAmountText = itemView.findViewById(R.id.totalAmountText);
            nestedTransactionList = itemView.findViewById(R.id.nestedTransactionList);

            // Setup nested RecyclerView
            nestedTransactionList.setLayoutManager(new LinearLayoutManager(itemView.getContext()));
            adapter = new TransactionAdapter();
            nestedTransactionList.setAdapter(adapter);

            // Pass through click listener if already set
            if (transactionClickListener != null) {
                adapter.setOnTransactionClickListener(transactionClickListener);
            }
        }

        void bind(DateGroup dateGroup) {
            // Set date header
            SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault());
            dateGroupText.setText(dateFormat.format(new Date(dateGroup.dateTimestamp)));

            // Set transaction count
            transactionCountText.setText(String.format(Locale.getDefault(),
                    "%d transaction(s)", dateGroup.transactionCount));

            // Set total amount
            double netAmount = dateGroup.totalCredit - dateGroup.totalDebit;
            totalAmountText.setText(String.format(Locale.getDefault(), "₹%.2f", netAmount));

            // Set text color based on net amount
            if (netAmount > 0) {
                totalAmountText.setTextColor(context.getColor(R.color.green));
            } else if (netAmount < 0) {
                totalAmountText.setTextColor(context.getColor(R.color.red));
            } else {
                totalAmountText.setTextColor(context.getColor(R.color.text_primary));
            }

            // Store reference to adapter in date group for later updates
            dateGroup.adapter = adapter;

            // Control transaction list visibility based on expanded state
            if (dateGroup.expanded) {
                // Show all transactions if expanded
                adapter.setTransactions(dateGroup.transactions);
                nestedTransactionList.setVisibility(View.VISIBLE);
            } else {
                // Hide all transactions if collapsed - show only summary
                adapter.setTransactions(new ArrayList<>());
                nestedTransactionList.setVisibility(View.GONE);
            }

            // Look for expansion indicator if it exists in the layout
            ImageView expansionIndicator = itemView.findViewById(R.id.expansionIndicator);
            if (expansionIndicator != null) {
                // Set the appropriate icon based on expanded state
                expansionIndicator.setImageResource(
                        dateGroup.expanded ? R.drawable.ic_expand_less : R.drawable.ic_expand_more);
            }

            // Setup click listener for the group header to expand/collapse
            View groupHeader = itemView.findViewById(R.id.dateGroupHeader);
            if (groupHeader != null) {
                groupHeader.setOnClickListener(v -> {
                    // Toggle expanded state
                    dateGroup.expanded = !dateGroup.expanded;

                    // Update visibility and content based on new state
                    if (dateGroup.expanded) {
                        adapter.setTransactions(dateGroup.transactions);
                        nestedTransactionList.setVisibility(View.VISIBLE);

                        // Update indicator if it exists
                        if (expansionIndicator != null) {
                            expansionIndicator.setImageResource(R.drawable.ic_expand_less);
                        }
                    } else {
                        nestedTransactionList.setVisibility(View.GONE);

                        // Update indicator if it exists
                        if (expansionIndicator != null) {
                            expansionIndicator.setImageResource(R.drawable.ic_expand_more);
                        }
                    }
                });
            }
        }

    }

    /**
     * Data class to hold information about a date group
     */
    public static class DateGroup {
        final long dateTimestamp;
        final List<Transaction> transactions;
        double totalDebit;
        double totalCredit;
        final int transactionCount;
        boolean expanded = false;
        TransactionAdapter adapter = null;

        public DateGroup(long dateTimestamp, List<Transaction> transactions,
                         double totalDebit, double totalCredit, int transactionCount) {
            this.dateTimestamp = dateTimestamp;
            this.transactions = transactions;
            this.totalDebit = totalDebit;
            this.totalCredit = totalCredit;
            this.transactionCount = transactionCount;
        }
    }
}