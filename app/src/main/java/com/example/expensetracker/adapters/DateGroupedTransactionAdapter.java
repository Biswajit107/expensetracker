package com.example.expensetracker.adapters;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;
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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Adapter for displaying transactions grouped by date with configurable grouping level
 */
public class DateGroupedTransactionAdapter extends RecyclerView.Adapter<DateGroupedTransactionAdapter.GroupViewHolder> {
    private static final String TAG = "DateGroupedAdapter";

    // Grouping mode constants
    public static final int GROUP_BY_DAY = 0;
    public static final int GROUP_BY_WEEK = 1;
    public static final int GROUP_BY_MONTH = 2;

    private final Context context;
    private List<DateGroup> dateGroups = new ArrayList<>();
    private TransactionAdapter.OnTransactionClickListener listener;
    private int currentGroupingMode = GROUP_BY_DAY; // Default to day grouping

    public DateGroupedTransactionAdapter(Context context) {
        this.context = context;
    }

    /**
     * Set the transaction click listener that will be passed to the nested TransactionAdapter
     */
    public void setOnTransactionClickListener(TransactionAdapter.OnTransactionClickListener listener) {
        this.listener = listener;

        // Update listener in any existing nested adapters
        notifyDataSetChanged();
    }

    /**
     * Set the transactions and group them according to the specified mode
     */
    public void setTransactions(List<Transaction> transactions, int groupingMode) {
        this.currentGroupingMode = groupingMode;

        if (transactions == null || transactions.isEmpty()) {
            dateGroups = new ArrayList<>();
            notifyDataSetChanged();
            return;
        }

        // Create groups based on the grouping mode
        switch (groupingMode) {
            case GROUP_BY_WEEK:
                dateGroups = groupTransactionsByWeek(transactions);
                break;
            case GROUP_BY_MONTH:
                dateGroups = groupTransactionsByMonth(transactions);
                break;
            case GROUP_BY_DAY:
            default:
                dateGroups = groupTransactionsByDay(transactions);
                break;
        }

        notifyDataSetChanged();
    }

    /**
     * Update a single transaction in the adapter
     * This finds the transaction by ID and updates it in the appropriate group
     */
    public void updateTransaction(Transaction updatedTransaction) {
        boolean found = false;

        // Search each group for the transaction to update
        for (DateGroup group : dateGroups) {
            List<Transaction> transactions = group.getTransactions();
            for (int i = 0; i < transactions.size(); i++) {
                if (transactions.get(i).getId() == updatedTransaction.getId()) {
                    // Found the transaction to update
                    transactions.set(i, updatedTransaction);
                    found = true;
                    break;
                }
            }

            if (found) break;
        }

        if (found) {
            // Refresh the view to show updated data
            notifyDataSetChanged();
        }
    }

    /**
     * Group transactions by day
     */
    private List<DateGroup> groupTransactionsByDay(List<Transaction> transactions) {
        // Sort transactions by date (newest first)
        Collections.sort(transactions, (a, b) -> Long.compare(b.getDate(), a.getDate()));

        // Group by day
        Map<String, List<Transaction>> groupedMap = new HashMap<>();
        SimpleDateFormat dateKeyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        for (Transaction transaction : transactions) {
            String dateKey = dateKeyFormat.format(new Date(transaction.getDate()));

            if (!groupedMap.containsKey(dateKey)) {
                groupedMap.put(dateKey, new ArrayList<>());
            }

            groupedMap.get(dateKey).add(transaction);
        }

        // Create date groups
        List<DateGroup> groups = new ArrayList<>();
        SimpleDateFormat dateLabelFormat = new SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault());

        for (Map.Entry<String, List<Transaction>> entry : groupedMap.entrySet()) {
            try {
                Date groupDate = dateKeyFormat.parse(entry.getKey());
                String dateLabel = dateLabelFormat.format(groupDate);

                groups.add(new DateGroup(
                        dateLabel,
                        entry.getValue(),
                        groupDate.getTime()
                ));
            } catch (ParseException e) {
                Log.e(TAG, "Error parsing date: " + e.getMessage());
            }
        }

        // Sort groups by date (newest first)
        Collections.sort(groups, (a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));

        return groups;
    }

    /**
     * Group transactions by week
     */
    private List<DateGroup> groupTransactionsByWeek(List<Transaction> transactions) {
        // Sort transactions by date (newest first)
        Collections.sort(transactions, (a, b) -> Long.compare(b.getDate(), a.getDate()));

        // Group by week
        Map<String, List<Transaction>> groupedMap = new HashMap<>();
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat weekLabelFormat = new SimpleDateFormat("MMM d", Locale.getDefault());

        for (Transaction transaction : transactions) {
            calendar.setTimeInMillis(transaction.getDate());

            // Get week of year and year
            int year = calendar.get(Calendar.YEAR);
            int weekOfYear = calendar.get(Calendar.WEEK_OF_YEAR);

            // Create week key
            String weekKey = year + "-" + weekOfYear;

            if (!groupedMap.containsKey(weekKey)) {
                groupedMap.put(weekKey, new ArrayList<>());
            }

            groupedMap.get(weekKey).add(transaction);
        }

        // Create week groups
        List<DateGroup> groups = new ArrayList<>();

        for (Map.Entry<String, List<Transaction>> entry : groupedMap.entrySet()) {
            List<Transaction> weekTransactions = entry.getValue();

            // Sort week transactions to find first and last day
            Collections.sort(weekTransactions, (a, b) -> Long.compare(a.getDate(), b.getDate()));

            // Get first and last transaction dates
            long firstDay = weekTransactions.get(0).getDate();
            long lastDay = weekTransactions.get(weekTransactions.size() - 1).getDate();

            // Format week label: "May 1 - May 7, 2023"
            Calendar firstCal = Calendar.getInstance();
            firstCal.setTimeInMillis(firstDay);
            Calendar lastCal = Calendar.getInstance();
            lastCal.setTimeInMillis(lastDay);

            String startDayStr = weekLabelFormat.format(firstCal.getTime());
            String endDayStr = weekLabelFormat.format(lastCal.getTime());

            // Add year only if the week spans different years or if it's end of the range
            if (firstCal.get(Calendar.YEAR) != lastCal.get(Calendar.YEAR)) {
                startDayStr += ", " + firstCal.get(Calendar.YEAR);
                endDayStr += ", " + lastCal.get(Calendar.YEAR);
            } else {
                endDayStr += ", " + lastCal.get(Calendar.YEAR);
            }

            String weekLabel = "Week of " + startDayStr + " - " + endDayStr;

            groups.add(new DateGroup(
                    weekLabel,
                    weekTransactions,
                    firstDay
            ));
        }

        // Sort groups by date (newest first)
        Collections.sort(groups, (a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));

        return groups;
    }

    /**
     * Group transactions by month
     */
    private List<DateGroup> groupTransactionsByMonth(List<Transaction> transactions) {
        // Sort transactions by date (newest first)
        Collections.sort(transactions, (a, b) -> Long.compare(b.getDate(), a.getDate()));

        // Group by month
        Map<String, List<Transaction>> groupedMap = new HashMap<>();
        Calendar calendar = Calendar.getInstance();

        for (Transaction transaction : transactions) {
            calendar.setTimeInMillis(transaction.getDate());

            // Get month and year
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH);

            // Create month key
            String monthKey = year + "-" + month;

            if (!groupedMap.containsKey(monthKey)) {
                groupedMap.put(monthKey, new ArrayList<>());
            }

            groupedMap.get(monthKey).add(transaction);
        }

        // Create month groups
        List<DateGroup> groups = new ArrayList<>();
        SimpleDateFormat monthLabelFormat = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());

        for (Map.Entry<String, List<Transaction>> entry : groupedMap.entrySet()) {
            String[] keyParts = entry.getKey().split("-");
            int year = Integer.parseInt(keyParts[0]);
            int month = Integer.parseInt(keyParts[1]);

            // Create calendar for this month
            Calendar monthCal = Calendar.getInstance();
            monthCal.set(year, month, 1, 0, 0, 0);
            monthCal.set(Calendar.MILLISECOND, 0);

            String monthLabel = monthLabelFormat.format(monthCal.getTime());

            groups.add(new DateGroup(
                    monthLabel,
                    entry.getValue(),
                    monthCal.getTimeInMillis()
            ));
        }

        // Sort groups by date (newest first)
        Collections.sort(groups, (a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));

        return groups;
    }

    @NonNull
    @Override
    public GroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_date_group, parent, false);
        return new GroupViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GroupViewHolder holder, int position) {
        DateGroup group = dateGroups.get(position);
        holder.bind(group, position);
    }

    @Override
    public int getItemCount() {
        return dateGroups.size();
    }

    /**
     * ViewHolder for date group items
     */
    class GroupViewHolder extends RecyclerView.ViewHolder {
        private final TextView dateGroupText;
        private final TextView transactionCountText;
        private final TextView totalAmountText;
        private final ImageView expansionIndicator;
        private final RecyclerView nestedTransactionList;
        private final View dateGroupHeader;

        public GroupViewHolder(@NonNull View itemView) {
            super(itemView);
            dateGroupText = itemView.findViewById(R.id.dateGroupText);
            transactionCountText = itemView.findViewById(R.id.transactionCountText);
            totalAmountText = itemView.findViewById(R.id.totalAmountText);
            expansionIndicator = itemView.findViewById(R.id.expansionIndicator);
            nestedTransactionList = itemView.findViewById(R.id.nestedTransactionList);
            dateGroupHeader = itemView.findViewById(R.id.dateGroupHeader);

            // Set up nested RecyclerView
            nestedTransactionList.setLayoutManager(new LinearLayoutManager(context));
            nestedTransactionList.setNestedScrollingEnabled(false);

            // Set click listener for group header
            dateGroupHeader.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    DateGroup group = dateGroups.get(position);
                    toggleGroupExpansion(group, position);
                }
            });
        }

        /**
         * Toggle group expansion state
         */
        private void toggleGroupExpansion(DateGroup group, int position) {
            group.setExpanded(!group.isExpanded());

            // Update expansion state in the UI
            updateExpansionState(group);

            // Notify adapter of the change for smooth animation
            notifyItemChanged(position);
        }

        /**
         * Bind data to the view
         */
        public void bind(DateGroup group, int position) {
            // Set group title and details
            dateGroupText.setText(group.getLabel());
            int count = group.getTransactionCount();
            transactionCountText.setText(count == 1
                    ? "1 transaction"
                    : count + " transactions");

            // Format and set total amount
            double totalAmount = group.getTotalAmount();
            totalAmountText.setText(String.format(Locale.getDefault(), "₹%.2f", totalAmount));

            // Set amount text color based on value
            if (totalAmount > 0) {
                totalAmountText.setTextColor(context.getColor(R.color.red));
            } else if (totalAmount < 0) {
                totalAmountText.setTextColor(context.getColor(R.color.green));
            } else {
                totalAmountText.setTextColor(context.getColor(R.color.text_primary));
            }

            // Set up nested transaction list if expanded
            updateExpansionState(group);

            // If expanded, set up the nested adapter with the group's transactions
            if (group.isExpanded()) {
                TransactionAdapter nestedAdapter = new TransactionAdapter();
                if (listener != null) {
                    nestedAdapter.setOnTransactionClickListener(listener);
                }
                nestedAdapter.setTransactions(group.getTransactions());
                nestedTransactionList.setAdapter(nestedAdapter);
            }
        }

        /**
         * Update the expansion state UI (expand/collapse indicators)
         */
        private void updateExpansionState(DateGroup group) {
            if (group.isExpanded()) {
                // Expanded state
                expansionIndicator.setImageResource(R.drawable.ic_expand_less);
                nestedTransactionList.setVisibility(View.VISIBLE);
            } else {
                // Collapsed state
                expansionIndicator.setImageResource(R.drawable.ic_expand_more);
                nestedTransactionList.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Data class for a date group
     */
    public static class DateGroup {
        private final String label;
        private final List<Transaction> transactions;
        private final long timestamp;
        private boolean isExpanded = false;

        public DateGroup(String label, List<Transaction> transactions, long timestamp) {
            this.label = label;
            this.transactions = transactions;
            this.timestamp = timestamp;
        }

        public String getLabel() { return label; }
        public List<Transaction> getTransactions() { return transactions; }
        public int getTransactionCount() { return transactions.size(); }
        public long getTimestamp() { return timestamp; }
        public boolean isExpanded() { return isExpanded; }
        public void setExpanded(boolean expanded) { isExpanded = expanded; }

        /**
         * Calculate total amount for this group
         */
        public double getTotalAmount() {
            double totalAmount = 0;
            for (Transaction transaction : transactions) {
                if (transaction.isDebit()) {
                    totalAmount += transaction.getAmount();
                } else {
                    totalAmount -= transaction.getAmount();
                }
            }
            return totalAmount;
        }
    }
}