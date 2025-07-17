package com.example.expensetracker.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.example.expensetracker.R;
import com.example.expensetracker.adapters.TransactionAdapter;
import com.example.expensetracker.models.Transaction;

public class SwipeToExcludeCallback extends ItemTouchHelper.SimpleCallback {

    private final TransactionAdapter adapter;
    private final Drawable icon;
    private final ColorDrawable background;
    private final int iconMargin;
    private final Paint textPaint;
    private final String swipeText;
    private final SwipeActionListener listener;
    private final SwipeActionType actionType;

    public enum SwipeActionType {
        EXCLUDE
    }

    public interface SwipeActionListener {
        void onSwipeToExclude(Transaction transaction);
    }

    public SwipeToExcludeCallback(Context context, TransactionAdapter adapter, SwipeActionListener listener) {
        this(context, adapter, listener, SwipeActionType.EXCLUDE);
    }

    public SwipeToExcludeCallback(Context context, TransactionAdapter adapter, SwipeActionListener listener, SwipeActionType actionType) {
        // Only enable right swipe, disable drag
        super(0, ItemTouchHelper.RIGHT);

        this.adapter = adapter;
        this.listener = listener;
        this.actionType = actionType;
        
        android.util.Log.d("SwipeToExcludeCallback", "Created SwipeToExcludeCallback with actionType: " + actionType);

        // Set up styling for exclude action only
        background = new ColorDrawable(ContextCompat.getColor(context, R.color.purple_light));
        icon = ContextCompat.getDrawable(context, R.drawable.ic_exclude);
        swipeText = "Exclude";

        // Margin for the icon
        iconMargin = context.getResources().getDimensionPixelSize(R.dimen.swipe_icon_margin);

        // Set up text to display during swipe
        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(context.getResources().getDimensionPixelSize(R.dimen.swipe_text_size));
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    @Override
    public int getSwipeDirs(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
        // Only allow swiping on transaction item viewholders, not group headers
        if (viewHolder instanceof TransactionAdapter.TransactionViewHolder) {
            return super.getSwipeDirs(recyclerView, viewHolder);
        }
        return 0; // No swiping for other viewholder types
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder,
                          @NonNull RecyclerView.ViewHolder target) {
        // We don't support moving items, so return false
        return false;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        int position = viewHolder.getAdapterPosition();

        // Get the transaction that was swiped
        Transaction transaction = adapter.getTransactions().get(position);

        // Notify the listener for exclude action
        if (listener != null) {
            listener.onSwipeToExclude(transaction);
        }
    }

    @Override
    public void onChildDraw(
            @NonNull Canvas c,
            @NonNull RecyclerView recyclerView,
            @NonNull RecyclerView.ViewHolder viewHolder,
            float dX, float dY, int actionState, boolean isCurrentlyActive) {

        View itemView = viewHolder.itemView;

        // Don't draw if the swipe amount is 0 or less (no movement or left swipe)
        if (dX <= 0) {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            return;
        }

        // Draw the purple background
        background.setBounds(itemView.getLeft(), itemView.getTop(),
                itemView.getLeft() + ((int) dX), itemView.getBottom());
        background.draw(c);

        // Calculate position for the icon
        int iconLeft = itemView.getLeft() + iconMargin;
        int iconTop = itemView.getTop() + (itemView.getHeight() - icon.getIntrinsicHeight()) / 2;
        int iconRight = iconLeft + icon.getIntrinsicWidth();
        int iconBottom = iconTop + icon.getIntrinsicHeight();

        // Draw the icon
        icon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
        icon.draw(c);

        // Draw the text "Exclude" after the icon
        int textX = iconRight + iconMargin;
        int textY = itemView.getTop() + (itemView.getHeight() / 2) +
                ((int) (textPaint.getTextSize() / 2));

        c.drawText(swipeText, textX, textY, textPaint);

        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
    }
}