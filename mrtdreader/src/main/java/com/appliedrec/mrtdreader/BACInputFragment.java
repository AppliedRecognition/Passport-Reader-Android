package com.appliedrec.mrtdreader;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.appliedrec.mrtdreader.databinding.FragmentBacmanualInputBinding;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link OnBACInputListener} interface
 * to handle interaction events.
 * Use the {@link BACInputFragment#newInstance} factory method to
 * create an instance of this fragment.
 * @version 1.0.0
 */
public class BACInputFragment extends Fragment implements DatePickerFragment.DatePickerFragmentListener {

    private FragmentBacmanualInputBinding viewBinding;

    private Date dateOfBirth;
    private Date dateOfExpiry;

    private OnBACInputListener mListener;
    @SuppressLint("SimpleDateFormat")
    private SimpleDateFormat dateFormat = new SimpleDateFormat("d MMM, yyyy");

    public static final int ID_DOB = 0;
    public static final int ID_DOE = 1;

    public BACInputFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.

     * @return A new instance of fragment BACInputFragment.
     * @since 1.0.0
     */
    public static BACInputFragment newInstance(BACSpec bacSpec) {
        BACInputFragment fragment = new BACInputFragment();
        if (bacSpec != null) {
            Bundle args = new Bundle();
            args.putParcelable("bacSpec", bacSpec);
            fragment.setArguments(args);
        }
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        viewBinding = FragmentBacmanualInputBinding.inflate(inflater, container, false);
        viewBinding.txtDocNumber.setOnEditorActionListener((v, actionId, event) -> {
            selectDateOfBirth();
            return true;
        });

        final Calendar selectedDOEDate = Calendar.getInstance();
        selectedDOEDate.set(Calendar.YEAR, selectedDOEDate.get(Calendar.YEAR) + 2);
        dateOfExpiry = selectedDOEDate.getTime();

        viewBinding.tvExpiry.setText(dateFormat.format(dateOfExpiry));
        viewBinding.tvExpiry.setOnClickListener(v -> selectDateOfExpiry());

        final Calendar selectedDOBDate = Calendar.getInstance();
        selectedDOBDate.set(Calendar.YEAR, selectedDOBDate.get(Calendar.YEAR) - 35);
        dateOfBirth = selectedDOBDate.getTime();

        viewBinding.tvDob.setText(dateFormat.format(selectedDOBDate.getTime()));
        viewBinding.tvDob.setOnClickListener(v -> selectDateOfBirth());

        if (getArguments() != null) {
            BACSpec bacSpec = getArguments().getParcelable("bacSpec");
            if (bacSpec != null) {
                viewBinding.txtDocNumber.setText(bacSpec.getDocumentNumber());
                dateOfBirth = bacSpec.getDateOfBirth();
                dateOfExpiry = bacSpec.getDateOfExpiry();
                viewBinding.tvDob.setText(dateFormat.format(dateOfBirth));
                viewBinding.tvExpiry.setText(dateFormat.format(dateOfExpiry));
            }
        }
        viewBinding.txtDocNumber.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                onBACSpecChanged();
            }
        });
        return viewBinding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        viewBinding = null;
    }

    private void onBACSpecChanged() {
        if (isFormValid()) {
            String docNumber = viewBinding.txtDocNumber.getText().toString().trim();
            BACSpec bacSpec = new BACSpec(docNumber, dateOfBirth, dateOfExpiry);
            mListener.onBACChanged(bacSpec);
        } else {
            mListener.onBACChanged(null);
        }
    }

    private void selectDateOfBirth() {
        Calendar minDate = Calendar.getInstance();
        minDate.set(Calendar.YEAR, 1900);
        Calendar maxDate = Calendar.getInstance();
        maxDate.set(Calendar.YEAR, maxDate.get(Calendar.YEAR) - 6);
        DatePickerFragment datePickerFragment = DatePickerFragment.newInstance(ID_DOB, minDate.getTime(), maxDate.getTime(), dateOfBirth, "Date of birth");
        datePickerFragment.show(getChildFragmentManager(), "DatePicker");
    }

    private void selectDateOfExpiry() {
        Calendar minDate = Calendar.getInstance();
        Calendar maxDate = Calendar.getInstance();
        maxDate.set(Calendar.YEAR, maxDate.get(Calendar.YEAR) + 10);
        DatePickerFragment datePickerFragment = DatePickerFragment.newInstance(ID_DOE, minDate.getTime(), maxDate.getTime(), dateOfExpiry, "Date of expiry");
        datePickerFragment.show(getChildFragmentManager(), "DatePicker");
    }

    private boolean isFormValid() {
        String docNumber = viewBinding.txtDocNumber.getText().toString().trim();
        boolean valid = docNumber.length() > 0 && docNumber.length() <= 9;
        if (valid) {
            valid = dateOfBirth.before(new Date());
        }
        return valid;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (viewBinding != null) {
            viewBinding.txtDocNumber.requestFocus();
            InputMethodManager imm = (InputMethodManager) requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(viewBinding.txtDocNumber, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnBACInputListener) {
            mListener = (OnBACInputListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnBACInputListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    @Override
    public void onDateSelected(int identifier, Date date) {
        if (identifier == ID_DOB) {
            viewBinding.tvDob.setText(dateFormat.format(date));
        } else {
            viewBinding.tvExpiry.setText(dateFormat.format(date));
        }
    }

    @Override
    public void onDateCommitted(int identifier) {
        try {
            if (identifier == ID_DOB) {
                dateOfBirth = dateFormat.parse(viewBinding.tvDob.getText().toString());
                selectDateOfExpiry();
            } else {
                dateOfExpiry = dateFormat.parse(viewBinding.tvExpiry.getText().toString());
            }
            onBACSpecChanged();
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDateEntryCancelled(int identifier) {

    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     * @version 1.0.0
     */
    public interface OnBACInputListener {

        /**
         * Called when the BAC specification changes as a result of user input
         * @param bacSpec The new BAC specification
         * @since 1.0.0
         */
        void onBACChanged(BACSpec bacSpec);
    }
}
