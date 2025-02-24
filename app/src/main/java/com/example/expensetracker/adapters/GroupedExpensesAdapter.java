package com.example.expensetracker.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.AutoTransition;
import androidx.transition.TransitionManager;

import com.example.expensetracker.R;
import com.example.expensetracker.viewmodel.GroupedExpensesViewModel;
import com.example.expensetracker.viewmodel.GroupedExpensesViewModel.ExpenseGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GroupedExpensesAdapter extends RecyclerView.Adapter<GroupedExpensesAdapter.ViewHolder> {
    private List<ExpenseGroup> groups = new ArrayList<>();
    private OnGroupClickListener listener;

    // Track expanded groups
    private List<Boolean> expandedStates = new ArrayList<>();

    public interface OnGroupClickListener {
        void onGroupClick(int position);
    }

    public void setOnGroupClickListener(OnGroupClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_expense_group, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ExpenseGroup group = groups.get(position);

        // Get expansion state
        boolean isExpanded = false;
        if (position < expandedStates.size()) {
            isExpanded = expandedStates.get(position);
        }

        holder.bind(group, position, isExpanded);
    }

    @Override
    public int getItemCount() {
        return groups.size();
    }

    public void setGroups(List<ExpenseGroup> groups) {
        this.groups = groups;

        // Initialize expanded states if needed
        if (expandedStates.size() < groups.size()) {
            while (expandedStates.size() < groups.size()) {
                expandedStates.add(false);
            }
        }

        notifyDataSetChanged();
    }

    // Update expanded state for a specific position
    public void toggleExpansion(int position) {
        if (position < expandedStates.size()) {
            expandedStates.set(position, !expandedStates.get(position));
            notifyItemChanged(position);
        }
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView groupNameText;
        private final TextView transactionCountText;
        private final TextView totalAmountText;
        private final ImageView categoryIcon;
        private final ImageView expandIcon;
        private final View groupHeader;
        private final RecyclerView transactionsList;
        private final TransactionAdapter transactionAdapter;
        private final View expandableContent;

        ViewHolder(View itemView) {
            super(itemView);
            groupNameText = itemView.findViewById(R.id.groupNameText);
            transactionCountText = itemView.findViewById(R.id.transactionCountText);
            totalAmountText = itemView.findViewById(R.id.totalAmountText);
            categoryIcon = itemView.findViewById(R.id.categoryIcon);
            expandIcon = itemView.findViewById(R.id.expandIcon);
            groupHeader = itemView.findViewById(R.id.groupHeader);
            transactionsList = itemView.findViewById(R.id.transactionsList);
            expandableContent = itemView.findViewById(R.id.expandableContent);

            // Setup nested RecyclerView
            transactionsList.setLayoutManager(new LinearLayoutManager(itemView.getContext()));
            transactionAdapter = new TransactionAdapter();
            transactionsList.setAdapter(transactionAdapter);

            // Set click listener for group header
            groupHeader.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    // Toggle expansion
                    toggleExpansion(position);

                    // Also notify the activity
                    listener.onGroupClick(position);
                }
            });
        }

        void bind(ExpenseGroup group, int position, boolean isExpanded) {
            groupNameText.setText(group.getName());
            transactionCountText.setText(String.format(Locale.getDefault(),
                    "%d transactions", group.getTransactionCount()));
            totalAmountText.setText(String.format(Locale.getDefault(),
                    "₹%.2f", group.getTotalAmount()));

            // Set category icon based on group name
            setCategoryIcon(group.getName());

            // Handle expansion state
            TransitionManager.beginDelayedTransition((ViewGroup) itemView, new AutoTransition());
            expandableContent.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
            expandIcon.setRotation(isExpanded ? 180 : 0);

            if (isExpanded) {
                transactionAdapter.setTransactions(group.getTransactions());
            }
        }

        private void setCategoryIcon(String groupName) {
            int iconResId;

            // Add null check for groupName
            if (groupName == null) {
                iconResId = R.drawable.ic_expense;
            } else {
                switch (groupName.toLowerCase()) {
                    case "food & dining":
                        iconResId = R.drawable.ic_food;
                        break;
                    case "shopping":
                        iconResId = R.drawable.ic_shopping;
                        break;
                    case "transportation":
                        iconResId = R.drawable.ic_transport;
                        break;
                    case "entertainment":
                        iconResId = R.drawable.ic_entertainment;
                        break;
                    case "bills & utilities":
                        iconResId = R.drawable.ic_bills;
                        break;
                    default:
                        iconResId = R.drawable.ic_expense;
                        break;
                }
            }

            categoryIcon.setImageResource(iconResId);
        }
    }
}