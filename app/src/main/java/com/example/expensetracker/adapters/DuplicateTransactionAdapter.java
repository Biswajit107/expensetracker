//package com.example.expensetracker.adapters;
//
//import android.graphics.Typeface;
//import android.view.LayoutInflater;
//import android.view.View;
//import android.view.ViewGroup;
//import android.widget.Button;
//import android.widget.TextView;
//import androidx.annotation.NonNull;
//import androidx.core.content.ContextCompat;
//import androidx.recyclerview.widget.RecyclerView;
//
//import com.example.expensetracker.R;
//import com.example.expensetracker.models.Transaction;
//import com.google.android.material.chip.Chip;
//
//import java.text.SimpleDateFormat;
//import java.util.ArrayList;
//import java.util.Date;
//import java.util.List;
//import java.util.Locale;
//
///**
// * Adapter for displaying and managing duplicate transactions
// */
//public class DuplicateTransactionAdapter extends RecyclerView.Adapter<DuplicateTransactionAdapter.DuplicateViewHolder> {
//
//    private List<List<Transaction>> duplicateSets = new ArrayList<>();
//    private OnDuplicateActionListener listener;
//
//    // Interface for handling duplicate transaction actions
//    public interface OnDuplicateActionListener {
//        void onKeepTransaction(Transaction transaction, List<Transaction> duplicateSet);
//        void onRemoveTransaction(Transaction transaction);
//        void onViewDetails(Transaction transaction);
//    }
//
//    public DuplicateTransactionAdapter(OnDuplicateActionListener listener) {
//        this.listener = listener;
//    }
//
//    public void setDuplicateSets(List<List<Transaction>> duplicateSets) {
//        this.duplicateSets = duplicateSets;
//        notifyDataSetChanged();
//    }
//
//    @NonNull
//    @Override
//    public DuplicateViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
//        View view = LayoutInflater.from(parent.getContext())
//                .inflate(R.layout.item_duplicate_set, parent, false);
//        return new DuplicateViewHolder(view);
//    }
//
//    @Override
//    public void onBindViewHolder(@NonNull DuplicateViewHolder holder, int position) {
//        List<Transaction> duplicateSet = duplicateSets.get(position);
//        holder.bind(duplicateSet);
//    }
//
//    @Override
//    public int getItemCount() {
//        return duplicateSets.size();
//    }
//
//    class DuplicateViewHolder extends RecyclerView.ViewHolder {
//        private TextView setTitleText;
//        private ViewGroup duplicateContainer;
//
//        DuplicateViewHolder(@NonNull View itemView) {
//            super(itemView);
//            setTitleText = itemView.findViewById(R.id.duplicateSetTitle);
//            duplicateContainer = itemView.findViewById(R.id.duplicateContainer);
//        }
//
//        void bind(List<Transaction> duplicateSet) {
//            if (duplicateSet == null || duplicateSet.isEmpty()) {
//                return;
//            }
//
//            // Set the title for this duplicate set
//            setTitleText.setText(String.format(Locale.getDefault(),
//                    "Potential Duplicate Set (%d transactions)", duplicateSet.size()));
//
//            // Clear previous items
//            duplicateContainer.removeAllViews();
//
//            // Inflate and add transaction items
//            LayoutInflater inflater = LayoutInflater.from(itemView.getContext());
//
//            for (Transaction transaction : duplicateSet) {
//                View transactionView = inflater.inflate(
//                        R.layout.item_duplicate_transaction, duplicateContainer, false);
//
//                // Fill in transaction details
//                TextView dateText = transactionView.findViewById(R.id.duplicateDateText);
//                TextView bankText = transactionView.findViewById(R.id.duplicateBankText);
//                TextView amountText = transactionView.findViewById(R.id.duplicateAmountText);
//                TextView descriptionText = transactionView.findViewById(R.id.duplicateDescriptionText);
//                Button keepButton = transactionView.findViewById(R.id.keepButton);
//                Button removeButton = transactionView.findViewById(R.id.removeButton);
//                Button detailsButton = transactionView.findViewById(R.id.detailsButton);
//
//                // Set transaction data
//                SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
//                dateText.setText(dateFormat.format(new Date(transaction.getDate())));
//                bankText.setText(transaction.getBank());
//
//                amountText.setText(String.format(Locale.getDefault(),
//                        "₹%.2f (%s)", transaction.getAmount(), transaction.getType()));
//
//                // Format description - remove [DUPLICATE] tag for display
//                String description = transaction.getDescription();
//                if (description != null && description.startsWith("[DUPLICATE]")) {
//                    description = description.replace("[DUPLICATE]", "").trim();
//                }
//                descriptionText.setText(description);
//
//                // Apply special styling for excluded transactions
//                if (transaction.isExcludedFromTotal()) {
//                    amountText.setPaintFlags(amountText.getPaintFlags()
//                            | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
//                    amountText.setTextColor(ContextCompat.getColor(
//                            amountText.getContext(), R.color.text_secondary));
//                }
//
//                // Set click listeners
//                keepButton.setOnClickListener(v -> {
//                    if (listener != null) {
//                        listener.onKeepTransaction(transaction, duplicateSet);
//                    }
//                });
//
//                removeButton.setOnClickListener(v -> {
//                    if (listener != null) {
//                        listener.onRemoveTransaction(transaction);
//                    }
//                });
//
//                detailsButton.setOnClickListener(v -> {
//                    if (listener != null) {
//                        listener.onViewDetails(transaction);
//                    }
//                });
//
//                duplicateContainer.addView(transactionView);
//            }
//        }
//    }
//}