package com.appliedrec.mrtd_reader_app;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.widget.DatePicker;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import java.util.Calendar;
import java.util.Date;

public class DatePickerFragment extends DialogFragment implements DatePickerDialog.OnDateSetListener {

    public interface Listener {
        void onPickDate(String identifier, Date date);
    }

    private static final String DATE_KEY = "date";
    private static final String IDENTIFIER_KEY = "identifier";
    private Listener listener;
    private String identifier;

    public static DatePickerFragment newInstance(@NonNull String identifier, @Nullable Date date) {
        Bundle args = new Bundle();
        if (date != null) {
            args.putSerializable(DATE_KEY, date);
        }
        args.putString(IDENTIFIER_KEY, identifier);
        DatePickerFragment fragment = new DatePickerFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable @org.jetbrains.annotations.Nullable Bundle savedInstanceState) {
        Calendar calendar = Calendar.getInstance();
        if (getArguments() != null) {
            Date date = (Date) getArguments().getSerializable(DATE_KEY);
            if (date != null) {
                calendar.setTime(date);
            }
            identifier = getArguments().getString(IDENTIFIER_KEY);
        }
        return new DatePickerDialog(requireActivity(), this, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DATE));
    }

    @Override
    public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
        if (listener != null) {
            Calendar calendar = Calendar.getInstance();
            calendar.set(year, month, dayOfMonth);
            listener.onPickDate(this.identifier, calendar.getTime());
        }
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (getParentFragment() != null && getParentFragment() instanceof Listener) {
            listener = (Listener) getParentFragment();
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }
}
