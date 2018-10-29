package com.appliedrec.mrtdreader;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

/**
 * Created by jakub on 02/05/2017.
 */

public class DatePickerFragment extends DialogFragment implements AbsListView.OnScrollListener {

    public interface DatePickerFragmentListener {
        void onDateSelected(int identifier, Date date);
        void onDateCommitted(int identifier);
        void onDateEntryCancelled(int identifier);
    }

    private DatePickerFragmentListener listener;
    private ListView yearView;
    private ListView monthView;
    private ListView dayView;

    private GregorianCalendar minDate;
    private GregorianCalendar maxDate;
    private GregorianCalendar selectedDate;
    private GregorianCalendar originalDate;

    private int identifier = 0;
    private boolean isDialog = false;
    private String dialogTitle = "Select date";

    public static DatePickerFragment newInstance(int identifier, Date minDate, Date maxDate, Date selectedDate, String dialogTitle) {
        Bundle args = new Bundle();
        args.putInt("identifier", identifier);
        if (minDate != null)
            args.putLong("minDate", minDate.getTime());
        if (maxDate != null)
            args.putLong("maxDate", maxDate.getTime());
        if (selectedDate != null)
            args.putLong("selectedDate", selectedDate.getTime());
        if (dialogTitle != null) {
            args.putString("dialogTitle", dialogTitle);
        }
        DatePickerFragment fragment = new DatePickerFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Fragment parentFragment = getParentFragment();
        if (parentFragment != null && parentFragment instanceof DatePickerFragmentListener) {
            listener = (DatePickerFragmentListener) parentFragment;
        }
        long minTime = new GregorianCalendar(1900, 0, 1).getTimeInMillis();
        long maxTime = new GregorianCalendar(Calendar.getInstance().get(Calendar.YEAR) + 10, 0, 1).getTimeInMillis();
        long selectedTime = System.currentTimeMillis();
        Bundle args = getArguments();
        if (args != null) {
            minTime = args.getLong("minDate", minTime);
            maxTime = args.getLong("maxDate", maxTime);
            selectedTime = args.getLong("selectedDate", selectedTime);
            identifier = args.getInt("identifier");
            dialogTitle = args.getString("dialogTitle", dialogTitle);
        }
        if (minTime > maxTime) {
            long newMinDate = maxTime;
            maxTime = minTime;
            minTime = newMinDate;
        }
        minDate = new GregorianCalendar();
        minDate.setTimeInMillis(minTime);
        maxDate = new GregorianCalendar();
        maxDate.setTimeInMillis(maxTime);
        selectedDate = new GregorianCalendar();
        selectedDate.setTimeInMillis(selectedTime);
        originalDate = new GregorianCalendar();
        originalDate.setTimeInMillis(selectedTime);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (activity instanceof DatePickerFragmentListener) {
            listener = (DatePickerFragmentListener) activity;
        }
    }

    public Date getSelectedDate() {
        return selectedDate.getTime();
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (!isDialog) {
            return createView(inflater, container);
        } else {
            return null;
        }
    }

    View createView(LayoutInflater inflater, ViewGroup container) {
        int startYear = minDate.get(Calendar.YEAR);
        int endYear = maxDate.get(Calendar.YEAR);
        String[] years = new String[endYear-startYear+3];
        int i = 1;
        int selectedYear = selectedDate.get(Calendar.YEAR);
        int selectedYearIndex = 0;
        years[0] = "";
        for (int y=startYear; y<=endYear; y++) {
            years[i] = Integer.toString(y);
            if (y == selectedYear) {
                selectedYearIndex = i - 1;
            }
            i++;
        }
        years[i] = "";
        String[] months = new String[]{
                "", "January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December",""
        };
        int selectedMonthIndex = selectedDate.get(Calendar.MONTH);
        int selectedDayIndex = selectedDate.get(Calendar.DATE) - 1;
        View view = inflater.inflate(R.layout.fragment_datepicker, container, false);
        yearView = (ListView) view.findViewById(R.id.year);
        monthView = (ListView) view.findViewById(R.id.month);
        dayView = (ListView) view.findViewById(R.id.date);
        yearView.setAdapter(new ArrayAdapter<String>(view.getContext(), R.layout.fragment_string, R.id.textView, years));
        monthView.setAdapter(new ArrayAdapter<String>(view.getContext(), R.layout.fragment_string, R.id.textView, months));
        dayView.setAdapter(new DayListAdapter(getContext(), selectedDate.get(Calendar.MONTH), selectedDate.get(Calendar.YEAR)));
        yearView.setOnScrollListener(this);
        monthView.setOnScrollListener(this);
        dayView.setOnScrollListener(this);
        yearView.setSelection(selectedYearIndex);
        monthView.setSelection(selectedMonthIndex);
        dayView.setSelection(selectedDayIndex);
        return view;
    }



    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (listener instanceof  DatePickerFragmentListener) {
            listener = (DatePickerFragmentListener) context;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    public Date getDate() {
        int year = (int) yearView.getSelectedItem();
        int month = monthView.getSelectedItemPosition();
        int day = (int) dayView.getSelectedItem();
        Calendar calendar = Calendar.getInstance();
        calendar.set(year, month, day, 0, 0, 0);
        return calendar.getTime();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        isDialog = true;
        View view = createView(LayoutInflater.from(getContext()), null);
        return new AlertDialog.Builder(getContext()).setTitle(dialogTitle).setView(view).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                selectedDate = new GregorianCalendar();
                selectedDate.setTimeInMillis(originalDate.getTimeInMillis());
                if (listener != null) {
                    listener.onDateSelected(identifier, selectedDate.getTime());
                    listener.onDateEntryCancelled(identifier);
                }
            }
        }).setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (listener != null) {
                    listener.onDateSelected(identifier, selectedDate.getTime());
                    listener.onDateCommitted(identifier);
                }
            }
        }).create();
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        if (scrollState == SCROLL_STATE_IDLE) {
            View itemView = view.getChildAt(0);
            int top = Math.abs(itemView.getTop());
            int bottom = Math.abs(itemView.getBottom());
            int scrollBy = top >= bottom ? bottom : -top;
            if (scrollBy == 0) {
                return;
            }
            smoothScroll(scrollBy, (ListView) view);
        }
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        Rect viewRect = new Rect(location[0], location[1], location[0]+view.getWidth(), location[1]+view.getHeight());
        for (int i=0; i<visibleItemCount; i++) {
            TextView child = (TextView) view.getChildAt(i);
            child.getLocationOnScreen(location);
            Rect childRect = new Rect(location[0], location[1], location[0]+child.getWidth(), location[1]+child.getHeight());
            if (childRect.contains(viewRect.centerX(), viewRect.centerY())) {
                child.setTextColor(Color.BLACK);
                child.setTypeface(null, Typeface.BOLD);
                Date previousSelectedDate = getSelectedDate();
                if (view.getId() == yearView.getId()) {
                    selectedDate.set(Calendar.YEAR, Integer.parseInt(child.getText().toString()));
                } else if (view.getId() == monthView.getId()) {
                    selectedDate.set(Calendar.MONTH, firstVisibleItem + i - 1);
                } else if (view.getId() == dayView.getId()) {
                    selectedDate.set(Calendar.DATE, Integer.parseInt(child.getText().toString()));
                }
                if (listener != null && previousSelectedDate.compareTo(selectedDate.getTime()) != 0) {
                    listener.onDateSelected(identifier, getSelectedDate());
                }
            } else {
                child.setTextColor(Color.LTGRAY);
                child.setTypeface(null, Typeface.NORMAL);
            }
        }
    }

    private void smoothScroll(final int scrollBy, final ListView listView) {
        listView.post(new Runnable() {
            @Override
            public void run() {
                listView.smoothScrollBy(scrollBy, 100);
                if (listView.getId() != dayView.getId()) {
                    listView.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            ((DayListAdapter)dayView.getAdapter()).setMonthAndYear(selectedDate.get(Calendar.MONTH), selectedDate.get(Calendar.YEAR));
                        }
                    }, 100);
                }
            }
        });
    }

    private static class DayListAdapter extends ArrayAdapter<String> {

        private ArrayList<String> days = new ArrayList<>();

        public DayListAdapter(Context context, int month, int year) {
            super(context, R.layout.fragment_string, R.id.textView);
            setMonthAndYear(month, year);
        }

        public void setMonthAndYear(int month, int year) {
            Calendar calendar = new GregorianCalendar(year, month, 1);
            int dayCount = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
            days.clear();
            days.add("");
            for (int i=1; i<=dayCount; i++) {
                days.add(Integer.toString(i));
            }
            days.add("");
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return days.size();
        }

        @Nullable
        @Override
        public String getItem(int position) {
            return days.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }
    }
}
