package com.example.expensetracker.viewmodel;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.expensetracker.adapters.GroupedExpensesAdapter;
import com.example.expensetracker.models.Transaction;
import com.example.expensetracker.repository.TransactionRepository;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GroupedExpensesViewModel extends AndroidViewModel implements GroupedExpensesAdapter.OnGroupClickListener {
    private final TransactionRepository repository;
    private final ExecutorService executorService;
    private final MutableLiveData<String> timeframe;
    private final MutableLiveData<String> searchQuery;
    private final Set<Integer> expandedGroups;
    private final MutableLiveData<List<ExpenseGroup>> expenseGroups;

    // Add this to force reload data when groups are expanded
    private final MutableLiveData<Boolean> dataRefreshTrigger = new MutableLiveData<>(false);

    public GroupedExpensesViewModel(Application application) {
        super(application);
        repository = new TransactionRepository(application);
        executorService = Executors.newSingleThreadExecutor();
        timeframe = new MutableLiveData<>("This Month");
        searchQuery = new MutableLiveData<>("");
        expandedGroups = new HashSet<>();
        expenseGroups = new MutableLiveData<>(new ArrayList<>());

        // Initialize data
        loadTransactions();
    }

    public LiveData<List<ExpenseGroup>> getExpenseGroups() {
        return Transformations.switchMap(dataRefreshTrigger, refresh ->
                Transformations.switchMap(timeframe, tf ->
                        Transformations.map(searchQuery, query -> {
                            List<ExpenseGroup> filteredGroups = filterGroups(expenseGroups.getValue(), query);
                            return filteredGroups;
                        })
                )
        );
    }

    public void setTimeframe(String newTimeframe) {
        if (!newTimeframe.equals(timeframe.getValue())) {
            timeframe.setValue(newTimeframe);
            loadTransactions();
        }
    }

    public void setSearchQuery(String query) {
        searchQuery.setValue(query);
    }

    public void toggleGroupExpansion(int position) {
        if (expandedGroups.contains(position)) {
            expandedGroups.remove(position);
        } else {
            expandedGroups.add(position);
        }

        // This will force the LiveData observers to update
        dataRefreshTrigger.setValue(!dataRefreshTrigger.getValue());
    }

    public boolean isGroupExpanded(int position) {
        return expandedGroups.contains(position);
    }

    private void loadTransactions() {
        executorService.execute(() -> {
            // Get date range based on timeframe
            long[] dateRange = getDateRange(timeframe.getValue());
            List<Transaction> transactions = repository
                    .getTransactionsBetweenDatesSync(dateRange[0], dateRange[1]);

            List<ExpenseGroup> groups = createExpenseGroups(transactions);
            expenseGroups.postValue(groups);

            // Force UI update
            dataRefreshTrigger.postValue(!dataRefreshTrigger.getValue());
        });
    }

    private long[] getDateRange(String timeframe) {
        Calendar cal = Calendar.getInstance();
        long endDate = cal.getTimeInMillis();

        // Set end date to end of current day (23:59:59)
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        endDate = cal.getTimeInMillis();

        // Now calculate start date based on timeframe
        switch (timeframe) {
            case "This Week":
                // Go to beginning of current week (Sunday is first day of week in many locales)
                cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
                break;
            case "This Month":
                // Go to beginning of current month
                cal.set(Calendar.DAY_OF_MONTH, 1);
                break;
            case "Last 3 Months":
                // Go back 3 months from current date
                cal.add(Calendar.MONTH, -3);
                break;
            case "Last 6 Months":
                // Go back 6 months from current date
                cal.add(Calendar.MONTH, -6);
                break;
            case "This Year":
                // Go to beginning of current year
                cal.set(Calendar.DAY_OF_YEAR, 1);
                break;
            default:
                // Default to "This Month" if timeframe isn't recognized
                cal.set(Calendar.DAY_OF_MONTH, 1);
                break;
        }

        // Set start time to beginning of day (00:00:00)
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        long startDate = cal.getTimeInMillis();

        return new long[]{startDate, endDate};
    }

    private List<ExpenseGroup> createExpenseGroups(List<Transaction> transactions) {
        Map<String, ExpenseGroup> groupMap = new HashMap<>();

        // Group transactions
        for (Transaction transaction : transactions) {
            if (transaction.isDebit() && !transaction.isExcludedFromTotal()) {
                String groupKey = determineGroupKey(transaction);
                if (groupKey == null) {
                    groupKey = "Other"; // Set a default group name if null
                }

                ExpenseGroup group = groupMap.computeIfAbsent(groupKey,
                        k -> new ExpenseGroup(k, new ArrayList<>()));
                group.addTransaction(transaction);
            }
        }

        // Convert to list and sort by total amount
        List<ExpenseGroup> groups = new ArrayList<>(groupMap.values());
        groups.sort((a, b) -> Double.compare(b.getTotalAmount(), a.getTotalAmount()));
        return groups;
    }

    private String determineGroupKey(Transaction transaction) {
        // Try to determine group from transaction data
        String description = transaction.getDescription() != null ?
                transaction.getDescription().toLowerCase() : "";
        String merchantName = transaction.getMerchantName();

        // Common merchants/categories mapping
        if (merchantName != null && !merchantName.isEmpty()) {
            return merchantName;
        } else if (description.contains("food") ||
                description.contains("swiggy") ||
                description.contains("zomato")) {
            return "Food & Dining";
        } else if (description.contains("shopping") ||
                description.contains("amazon") ||
                description.contains("flipkart")) {
            return "Shopping";
        } else if (description.contains("uber") ||
                description.contains("ola") ||
                description.contains("metro")) {
            return "Transportation";
        } else if (description.contains("movie") ||
                description.contains("netflix") ||
                description.contains("entertainment")) {
            return "Entertainment";
        } else if (description.contains("bill") ||
                description.contains("recharge") ||
                description.contains("utility")) {
            return "Bills & Utilities";
        }

        // Default to transaction category if no specific group found
        return transaction.getCategory() != null ? transaction.getCategory() : "Other";
    }

    private List<ExpenseGroup> filterGroups(List<ExpenseGroup> groups, String query) {
        if (query == null || query.isEmpty() || groups == null) {
            return groups;
        }

        List<ExpenseGroup> filteredGroups = new ArrayList<>();
        String lowerQuery = query.toLowerCase();

        for (ExpenseGroup group : groups) {
            // Check if group name matches
            if (group.getName() != null && group.getName().toLowerCase().contains(lowerQuery)) {
                filteredGroups.add(group);
                continue;
            }

            // Check if any transaction in group matches
            List<Transaction> matchingTransactions = new ArrayList<>();
            for (Transaction transaction : group.getTransactions()) {
                if ((transaction.getDescription() != null &&
                        transaction.getDescription().toLowerCase().contains(lowerQuery)) ||
                        (transaction.getBank() != null &&
                                transaction.getBank().toLowerCase().contains(lowerQuery))) {
                    matchingTransactions.add(transaction);
                }
            }

            if (!matchingTransactions.isEmpty()) {
                // Create new group with only matching transactions
                ExpenseGroup filteredGroup = new ExpenseGroup(
                        group.getName(), matchingTransactions);
                filteredGroups.add(filteredGroup);
            }
        }

        return filteredGroups;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }

    @Override
    public void onGroupClick(int position) {
        toggleGroupExpansion(position);
    }

    // Data class for expense groups
    public static class ExpenseGroup {
        private final String name;
        private final List<Transaction> transactions;
        private double totalAmount;

        public ExpenseGroup(String name, List<Transaction> transactions) {
            this.name = name;
            this.transactions = transactions;
            this.totalAmount = calculateTotal();
        }

        public void addTransaction(Transaction transaction) {
            transactions.add(transaction);
            totalAmount += transaction.getAmount();
        }

        private double calculateTotal() {
            double sum = 0;
            for (Transaction transaction : transactions) {
                sum += transaction.getAmount();
            }
            return sum;
        }

        public String getName() { return name; }
        public List<Transaction> getTransactions() { return transactions; }
        public double getTotalAmount() { return totalAmount; }
        public int getTransactionCount() { return transactions.size(); }
    }
}