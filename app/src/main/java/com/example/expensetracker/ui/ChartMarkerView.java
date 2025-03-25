package com.example.expensetracker.ui;

import android.content.Context;
import android.widget.TextView;

import com.example.expensetracker.R;
import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.utils.MPPointF;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Custom marker view that displays date and amount when a chart point is clicked
 */
public class ChartMarkerView extends MarkerView {
    private TextView dateText;
    private TextView amountText;
    private List<String> dateLabels;

    public ChartMarkerView(Context context, int layoutResource, List<String> dateLabels) {
        super(context, layoutResource);
        this.dateLabels = dateLabels;

        // Find TextViews from the marker layout
        dateText = findViewById(R.id.markerDateText);
        amountText = findViewById(R.id.markerAmountText);
    }

    @Override
    public void refreshContent(Entry e, Highlight highlight) {
        // Get the date from our stored labels based on the entry's x index
        int index = (int) e.getX();
        String date = index < dateLabels.size() ? dateLabels.get(index) : "";

        // Format the amount
        String amount = String.format(Locale.getDefault(), "₹%.2f", e.getY());

        // Set the text for the marker's TextViews
        dateText.setText(date);
        amountText.setText(amount);

        super.refreshContent(e, highlight);
    }

    @Override
    public MPPointF getOffset() {
        // Center the marker on top of the selected value
        return new MPPointF(-(getWidth() / 2), -getHeight() - 10);
    }
}