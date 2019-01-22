package com.appliedrec.mrtdreader;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

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

    private EditText txtDocNumber;
    private TextView tvExpiryDate;
    private TextView tvBirthDate;

    private Date dateOfBirth;
    private Date dateOfExpiry;

    private OnBACInputListener mListener;
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
     * @version 1.0.0
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
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View v = inflater.inflate(R.layout.fragment_bacmanual_input, container, false);
        txtDocNumber = v.findViewById(R.id.txt_doc_number);
        txtDocNumber.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                selectDateOfBirth();
                return true;
            }
        });

        final Calendar selectedDOEDate = Calendar.getInstance();
        selectedDOEDate.set(Calendar.YEAR, selectedDOEDate.get(Calendar.YEAR) + 2);
        dateOfExpiry = selectedDOEDate.getTime();

        tvExpiryDate = v.findViewById(R.id.tv_expiry);
        tvExpiryDate.setText(dateFormat.format(dateOfExpiry));
        tvExpiryDate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectDateOfExpiry();
            }
        });

        final Calendar selectedDOBDate = Calendar.getInstance();
        selectedDOBDate.set(Calendar.YEAR, selectedDOBDate.get(Calendar.YEAR) - 35);
        dateOfBirth = selectedDOBDate.getTime();

        tvBirthDate = v.findViewById(R.id.tv_dob);
        tvBirthDate.setText(dateFormat.format(selectedDOBDate.getTime()));
        tvBirthDate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectDateOfBirth();
            }
        });

        if (getArguments() != null) {
            BACSpec bacSpec = getArguments().getParcelable("bacSpec");
            if (bacSpec != null) {
                txtDocNumber.setText(bacSpec.getDocumentNumber());
                dateOfBirth = bacSpec.getDateOfBirth();
                dateOfExpiry = bacSpec.getDateOfExpiry();
                tvBirthDate.setText(dateFormat.format(dateOfBirth));
                tvExpiryDate.setText(dateFormat.format(dateOfExpiry));
            }
        }
        txtDocNumber.addTextChangedListener(new TextWatcher() {
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
        return v;
    }

    private void onBACSpecChanged() {
        if (isFormValid()) {
            String docNumber = txtDocNumber.getText().toString().trim();
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
        String docNumber = txtDocNumber.getText().toString().trim();
        boolean valid = docNumber.length() > 0 && docNumber.length() <= 9;
        if (valid) {
            valid &= dateOfBirth.before(new Date());
        }
        return valid;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null && txtDocNumber != null) {
            txtDocNumber.requestFocus();
            InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(txtDocNumber, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    @Override
    public void onAttach(Context context) {
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
            tvBirthDate.setText(dateFormat.format(date));
        } else {
            tvExpiryDate.setText(dateFormat.format(date));
        }
    }

    @Override
    public void onDateCommitted(int identifier) {
        try {
            if (identifier == ID_DOB) {
                dateOfBirth = dateFormat.parse(tvBirthDate.getText().toString());
                selectDateOfExpiry();
            } else {
                dateOfExpiry = dateFormat.parse(tvExpiryDate.getText().toString());
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
         * @version 1.0.0
         */
        void onBACChanged(BACSpec bacSpec);
    }
}
