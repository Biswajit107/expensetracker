package com.example.expensetracker.adapters;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.expensetracker.MainActivity;
import com.example.expensetracker.R;
import com.example.expensetracker.models.Transaction;
import com.example.expensetracker.utils.SwipeToExcludeCallback;

import java.text.NumberFormat;
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
import java.util.TreeMap;

/**
 * Adapter for displaying transactions grouped by various criteria with configurable grouping level
 */
public class DateGroupedTransactionAdapter extends RecyclerView.Adapter<DateGroupedTransactionAdapter.GroupViewHolder> {
    private static final String TAG = "DateGroupedAdapter";

    // Grouping mode constants
    public static final int GROUP_BY_DAY = 0;
    public static final int GROUP_BY_WEEK = 1;
    public static final int GROUP_BY_MONTH = 2;
    public static final int GROUP_BY_CATEGORY = 3;
    public static final int GROUP_BY_MERCHANT = 4;
    public static final int GROUP_BY_AMOUNT_RANGE = 5;
    public static final int GROUP_BY_BANK = 6;

    // Amount range boundaries for GROUP_BY_AMOUNT_RANGE
    private static final double[] AMOUNT_RANGES = {0, 100, 500, 1000, 5000, 10000, Double.MAX_VALUE};
    private static final String[] AMOUNT_RANGE_LABELS = {
            "₹0-₹100", "₹100-₹500", "₹500-₹1,000",
            "₹1,000-₹5,000", "₹5,000-₹10,000", "₹10,000+"
    };

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

    public void setTransactions(List<Transaction> transactions, int groupingMode, int sortOption) {
        this.currentGroupingMode = groupingMode;

        if (transactions == null || transactions.isEmpty()) {
            dateGroups = new ArrayList<>();
            notifyDataSetChanged();
            return;
        }

        // Create groups based on the grouping mode
        List<DateGroup> newGroups;
        switch (groupingMode) {
            case GROUP_BY_WEEK:
                newGroups = groupTransactionsByWeek(transactions);
                break;
            case GROUP_BY_MONTH:
                newGroups = groupTransactionsByMonth(transactions);
                break;
            case GROUP_BY_CATEGORY:
                newGroups = groupTransactionsByCategory(transactions);
                break;
            case GROUP_BY_MERCHANT:
                newGroups = groupTransactionsByMerchant(transactions);
                break;
            case GROUP_BY_AMOUNT_RANGE:
                newGroups = groupTransactionsByAmountRange(transactions);
                break;
            case GROUP_BY_BANK:
                newGroups = groupTransactionsByBank(transactions);
                break;
            case GROUP_BY_DAY:
            default:
                newGroups = groupTransactionsByDay(transactions);
                break;
        }

        // For each group, apply sorting to its transactions
        for (DateGroup group : newGroups) {
            sortTransactionsInGroup(group, sortOption);
        }

        // Now sort the groups themselves if needed (based on sort option)
        if (sortOption != 0) { // If not default sort (newest date first)
            sortGroups(newGroups, sortOption);
        }

        dateGroups = newGroups;
        notifyDataSetChanged();
    }

    // Add this helper method to sort transactions within a group
    private void sortTransactionsInGroup(DateGroup group, int sortOption) {
        List<Transaction> transactions = group.getTransactions();

        switch (sortOption) {
            case 0: // Date (newest first)
                Collections.sort(transactions, (a, b) -> Long.compare(b.getDate(), a.getDate()));
                break;
            case 1: // Date (oldest first)
                Collections.sort(transactions, (a, b) -> Long.compare(a.getDate(), b.getDate()));
                break;
            case 2: // Amount (highest first)
                Collections.sort(transactions, (a, b) -> Double.compare(b.getAmount(), a.getAmount()));
                break;
            case 3: // Amount (lowest first)
                Collections.sort(transactions, (a, b) -> Double.compare(a.getAmount(), b.getAmount()));
                break;
            case 4: // Description (A-Z)
                Collections.sort(transactions, (a, b) -> {
                    String descA = a.getDescription() != null ? a.getDescription() : "";
                    String descB = b.getDescription() != null ? b.getDescription() : "";
                    return descA.compareToIgnoreCase(descB);
                });
                break;
            case 5: // Description (Z-A)
                Collections.sort(transactions, (a, b) -> {
                    String descA = a.getDescription() != null ? a.getDescription() : "";
                    String descB = b.getDescription() != null ? b.getDescription() : "";
                    return descB.compareToIgnoreCase(descA);
                });
                break;
        }
    }

    // Add this helper method to sort the groups themselves
    private void sortGroups(List<DateGroup> groups, int sortOption) {
        switch (sortOption) {
            case 1: // Date (oldest first) - reverse the default group order
                Collections.reverse(groups);
                break;
            case 2: // Amount (highest first)
                Collections.sort(groups, (a, b) -> Double.compare(b.getTotalAmount(), a.getTotalAmount()));
                break;
            case 3: // Amount (lowest first)
                Collections.sort(groups, (a, b) -> Double.compare(a.getTotalAmount(), b.getTotalAmount()));
                break;
            case 4: // Description (A-Z) - sort by group name
                Collections.sort(groups, (a, b) -> a.getLabel().compareToIgnoreCase(b.getLabel()));
                break;
            case 5: // Description (Z-A) - sort by group name reversed
                Collections.sort(groups, (a, b) -> b.getLabel().compareToIgnoreCase(a.getLabel()));
                break;
            // Default case (0) is already sorted by date newest first
        }
    }

    // Add this method to maintain compatibility with existing calls
    public void setTransactions(List<Transaction> transactions, int groupingMode) {
        // Default to sort option 0 (Date newest first)
        setTransactions(transactions, groupingMode, 0);
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

    /**
     * Group transactions by category
     */
    private List<DateGroup> groupTransactionsByCategory(List<Transaction> transactions) {
        // Group by category
        Map<String, List<Transaction>> groupedMap = new HashMap<>();

        for (Transaction transaction : transactions) {
            // Get category or use "Uncategorized" if not set
            String category = transaction.getCategory();
            if (category == null || category.isEmpty()) {
                category = "Uncategorized";
            }

            if (!groupedMap.containsKey(category)) {
                groupedMap.put(category, new ArrayList<>());
            }

            groupedMap.get(category).add(transaction);
        }

        // Create category groups
        List<DateGroup> groups = new ArrayList<>();

        for (Map.Entry<String, List<Transaction>> entry : groupedMap.entrySet()) {
            // For timestamp, use the most recent transaction date in this category
            long mostRecentTimestamp = 0;
            for (Transaction t : entry.getValue()) {
                if (t.getDate() > mostRecentTimestamp) {
                    mostRecentTimestamp = t.getDate();
                }
            }

            groups.add(new DateGroup(
                    entry.getKey(), // Category name as label
                    entry.getValue(),
                    mostRecentTimestamp
            ));
        }

        // Sort groups by total amount (highest first)
        Collections.sort(groups, (a, b) -> Double.compare(b.getTotalAmount(), a.getTotalAmount()));

        return groups;
    }

    /**
     * Group transactions by merchant name
     */
    private List<DateGroup> groupTransactionsByMerchant(List<Transaction> transactions) {
        // Group by merchant
        Map<String, List<Transaction>> groupedMap = new HashMap<>();

        for (Transaction transaction : transactions) {
            // Get merchant name or use description if not available
            String merchant = transaction.getMerchantName();
            if (merchant == null || merchant.isEmpty()) {
                // Extract a merchant-like name from description
                merchant = extractMerchantFromDescription(transaction.getDescription());
            }

            if (!groupedMap.containsKey(merchant)) {
                groupedMap.put(merchant, new ArrayList<>());
            }

            groupedMap.get(merchant).add(transaction);
        }

        // Create merchant groups
        List<DateGroup> groups = new ArrayList<>();

        for (Map.Entry<String, List<Transaction>> entry : groupedMap.entrySet()) {
            // For timestamp, use the most recent transaction date for this merchant
            long mostRecentTimestamp = 0;
            for (Transaction t : entry.getValue()) {
                if (t.getDate() > mostRecentTimestamp) {
                    mostRecentTimestamp = t.getDate();
                }
            }

            groups.add(new DateGroup(
                    entry.getKey(), // Merchant name as label
                    entry.getValue(),
                    mostRecentTimestamp
            ));
        }

        // Sort groups by total amount (highest first)
        Collections.sort(groups, (a, b) -> Double.compare(b.getTotalAmount(), a.getTotalAmount()));

        return groups;
    }

    /**
     * Helper to extract a merchant name from a transaction description
     */
    private String extractMerchantFromDescription(String description) {
        if (description == null || description.isEmpty()) {
            return "Unknown Merchant";
        }

        // Try to extract first few words as merchant name
        String[] words = description.split("\\s+");
        if (words.length > 0) {
            StringBuilder merchantBuilder = new StringBuilder();
            // Take up to first 3 words
            int wordCount = Math.min(3, words.length);
            for (int i = 0; i < wordCount; i++) {
                if (merchantBuilder.length() > 0) {
                    merchantBuilder.append(" ");
                }
                merchantBuilder.append(words[i]);
            }
            return merchantBuilder.toString();
        } else {
            return "Unknown Merchant";
        }
    }

    /**
     * Group transactions by amount range
     */
    private List<DateGroup> groupTransactionsByAmountRange(List<Transaction> transactions) {
        // Create a map to hold transactions for each amount range
        Map<Integer, List<Transaction>> groupedMap = new HashMap<>();

        // Initialize all range groups
        for (int i = 0; i < AMOUNT_RANGES.length - 1; i++) {
            groupedMap.put(i, new ArrayList<>());
        }

        // Group transactions by amount range
        for (Transaction transaction : transactions) {
            double amount = transaction.getAmount();

            // Find which range this transaction belongs to
            for (int i = 0; i < AMOUNT_RANGES.length - 1; i++) {
                if (amount >= AMOUNT_RANGES[i] && amount < AMOUNT_RANGES[i + 1]) {
                    groupedMap.get(i).add(transaction);
                    break;
                }
            }
        }

        // Create amount range groups
        List<DateGroup> groups = new ArrayList<>();

        for (int i = 0; i < AMOUNT_RANGES.length - 1; i++) {
            List<Transaction> rangeTransactions = groupedMap.get(i);

            // Skip empty ranges
            if (rangeTransactions.isEmpty()) {
                continue;
            }

            // For timestamp, use the most recent transaction date in this range
            long mostRecentTimestamp = 0;
            for (Transaction t : rangeTransactions) {
                if (t.getDate() > mostRecentTimestamp) {
                    mostRecentTimestamp = t.getDate();
                }
            }

            groups.add(new DateGroup(
                    AMOUNT_RANGE_LABELS[i], // Range label
                    rangeTransactions,
                    mostRecentTimestamp
            ));
        }

        // Sort groups by amount range (ascending)
        Collections.sort(groups, (a, b) -> {
            // Find index of the range labels
            int indexA = -1;
            int indexB = -1;
            for (int i = 0; i < AMOUNT_RANGE_LABELS.length; i++) {
                if (AMOUNT_RANGE_LABELS[i].equals(a.getLabel())) {
                    indexA = i;
                }
                if (AMOUNT_RANGE_LABELS[i].equals(b.getLabel())) {
                    indexB = i;
                }
            }
            return Integer.compare(indexA, indexB);
        });

        return groups;
    }

    /**
     * Group transactions by bank
     */
    private List<DateGroup> groupTransactionsByBank(List<Transaction> transactions) {
        // Group by bank
        Map<String, List<Transaction>> groupedMap = new HashMap<>();

        for (Transaction transaction : transactions) {
            String bank = transaction.getBank();
            if (bank == null || bank.isEmpty()) {
                bank = "Unknown Bank";
            }

            if (!groupedMap.containsKey(bank)) {
                groupedMap.put(bank, new ArrayList<>());
            }

            groupedMap.get(bank).add(transaction);
        }

        // Create bank groups
        List<DateGroup> groups = new ArrayList<>();

        for (Map.Entry<String, List<Transaction>> entry : groupedMap.entrySet()) {
            // For timestamp, use the most recent transaction date for this bank
            long mostRecentTimestamp = 0;
            for (Transaction t : entry.getValue()) {
                if (t.getDate() > mostRecentTimestamp) {
                    mostRecentTimestamp = t.getDate();
                }
            }

            groups.add(new DateGroup(
                    entry.getKey(), // Bank name as label
                    entry.getValue(),
                    mostRecentTimestamp
            ));
        }

        // Sort groups by total amount (highest first)
        Collections.sort(groups, (a, b) -> Double.compare(b.getTotalAmount(), a.getTotalAmount()));

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

                if (context instanceof MainActivity) {
                    SwipeToExcludeCallback swipeCallback = new SwipeToExcludeCallback(
                            context,
                            nestedAdapter,
                            transaction -> ((MainActivity) context).excludeTransactionManually(transaction)
                    );
                    ItemTouchHelper itemTouchHelper = new ItemTouchHelper(swipeCallback);
                    itemTouchHelper.attachToRecyclerView(nestedTransactionList);
                }
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
                if (!transaction.isExcludedFromTotal()) {
                    if (transaction.isDebit()) {
                        totalAmount += transaction.getAmount();
                    } else {
                        totalAmount -= transaction.getAmount();
                    }
                }
            }
            return totalAmount;
        }
    }
}