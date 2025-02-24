package com.example.expensetracker.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.expensetracker.R;
import com.example.expensetracker.viewmodel.PredictionViewModel.RecurringExpense;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RecurringExpensesAdapter extends RecyclerView.Adapter<RecurringExpensesAdapter.ViewHolder> {
    private List<RecurringExpense> expenses = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recurring_expense, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RecurringExpense expense = expenses.get(position);
        holder.bind(expense);
    }

    @Override
    public int getItemCount() {
        return expenses.size();
    }

    public void setRecurringExpenses(List<RecurringExpense> expenses) {
        this.expenses = expenses;
        notifyDataSetChanged();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView descriptionText;
        private final TextView amountText;
        private final TextView frequencyText;
        private final TextView nextDateText;

        ViewHolder(View itemView) {
            super(itemView);
            descriptionText = itemView.findViewById(R.id.descriptionText);
            amountText = itemView.findViewById(R.id.amountText);
            frequencyText = itemView.findViewById(R.id.frequencyText);
            nextDateText = itemView.findViewById(R.id.nextDateText);
        }

        void bind(RecurringExpense expense) {
            descriptionText.setText(expense.getDescription());
            amountText.setText(String.format(Locale.getDefault(), "₹%.2f", expense.getAmount()));

            // Format frequency text
            String frequencyStr;
            if (expense.getFrequencyDays() == 30) {
                frequencyStr = "Monthly";
            } else if (expense.getFrequencyDays() == 7) {
                frequencyStr = "Weekly";
            } else {
                frequencyStr = String.format(Locale.getDefault(),
                        "Every %d days", expense.getFrequencyDays());
            }
            frequencyText.setText(String.format(Locale.getDefault(),
                    "%s (%d occurrences)", frequencyStr, expense.getOccurrences()));

            // Format next expected date
            nextDateText.setText(String.format("Next: %s",
                    dateFormat.format(new Date(expense.getNextExpectedDate()))));
        }
    }
}