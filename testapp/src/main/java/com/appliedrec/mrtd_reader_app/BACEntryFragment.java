package com.appliedrec.mrtd_reader_app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcel;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;

import com.appliedrec.mrtd_reader_sample.databinding.BacEntryBinding;
import com.appliedrec.mrtdreader.BACSpec;
import com.microblink.entities.recognizers.Recognizer;
import com.microblink.entities.recognizers.RecognizerBundle;
import com.microblink.entities.recognizers.blinkid.generic.BlinkIdRecognizer;
import com.microblink.entities.recognizers.blinkid.generic.ClassFilter;
import com.microblink.entities.recognizers.blinkid.generic.classinfo.ClassInfo;
import com.microblink.entities.recognizers.blinkid.generic.classinfo.Type;
import com.microblink.entities.recognizers.blinkid.mrtd.MrzResult;
import com.microblink.uisettings.ActivityRunner;
import com.microblink.uisettings.BlinkIdUISettings;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BACEntryFragment extends Fragment implements DatePickerFragment.Listener {

    public interface Listener {
        void onRequestCapture(BACSpec bacSpec);
    }

    private static final String BAC_KEY = "BAC";
    private static final String BUTTON_DOB = "dob";
    private static final String BUTTON_DOE = "doe";
    private Listener listener;
    private final DateFormat dateFormat = SimpleDateFormat.getDateInstance(SimpleDateFormat.MEDIUM);
    private BacEntryBinding viewBinding;
    private static final int REQUEST_CODE_MRZ_SCAN = 1;
    private BlinkIdRecognizer passportRecognizer;
    private RecognizerBundle recognizerBundle;
    private BACSpecModel viewModel;

    public static BACEntryFragment newInstance(BACSpec bacSpec) {

        Bundle args = new Bundle();
        args.putParcelable(BAC_KEY, bacSpec);

        BACEntryFragment fragment = new BACEntryFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable @org.jetbrains.annotations.Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        passportRecognizer = new BlinkIdRecognizer();
        passportRecognizer.setClassFilter(new ClassFilter() {
            @Override
            public int describeContents() {
                return 0;
            }

            @Override
            public void writeToParcel(Parcel dest, int flags) {

            }

            @Override
            public boolean classFilter(@NonNull ClassInfo classInfo) {
                return classInfo.getType() == Type.PASSPORT;
            }
        });
        recognizerBundle = new RecognizerBundle(passportRecognizer);
    }

    @Nullable
    @org.jetbrains.annotations.Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable @org.jetbrains.annotations.Nullable ViewGroup container, @Nullable @org.jetbrains.annotations.Nullable Bundle savedInstanceState) {
        viewBinding = BacEntryBinding.inflate(inflater, container, false);
        viewBinding.dateOfBirth.setText(dateFormat.format(new Date()));
        viewBinding.dateOfExpiry.setText(dateFormat.format(new Date()));
        if (getArguments() != null) {
            BACSpec bacSpec = getArguments().getParcelable(BAC_KEY);
            if (bacSpec != null) {
                updateFromBACSpec(bacSpec);
            }
        }
        viewBinding.documentNumber.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateCaptureButton();
                updateModel();
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
        viewBinding.dateOfBirth.setOnClickListener(v -> selectDate(viewBinding.dateOfBirth, BUTTON_DOB));
        viewBinding.dateOfExpiry.setOnClickListener(v -> selectDate(viewBinding.dateOfExpiry, BUTTON_DOE));
        viewBinding.captureButton.setOnClickListener(v -> {
            if (listener != null) {
                if (viewBinding.documentNumber.getText().toString().trim().isEmpty()) {
                    return;
                }
                try {
                    listener.onRequestCapture(getBACSpec());
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            }
        });
        viewBinding.cameraButton.setOnClickListener(v -> scanMRZ());
        updateCaptureButton();
        return viewBinding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        viewBinding = null;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof Listener) {
            this.listener = (Listener) context;
        }
        viewModel = new ViewModelProvider(requireActivity()).get(BACSpecModel.class);
        viewModel.setSharedPreferences(PreferenceManager.getDefaultSharedPreferences(context));
    }

    @Override
    public void onDetach() {
        super.onDetach();
        this.listener = null;
        this.viewModel = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (viewBinding == null) {
            return;
        }
        viewBinding.documentNumber.requestFocus();
        viewBinding.documentNumber.postDelayed(() -> {
            if (viewBinding == null || getContext() == null) {
                return;
            }
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(viewBinding.documentNumber, 0);
        }, 300);
    }

    private void selectDate(Button button, String identifier) {
        Date date = null;
        try {
            date = dateFormat.parse((String) button.getText());
        } catch (ParseException e) {
            e.printStackTrace();
        }
        DatePickerFragment.newInstance(identifier, date).show(getChildFragmentManager(), "date");
    }

    private void updateFromBACSpec(BACSpec bacSpec) {
        if (viewBinding == null) {
            return;
        }
        viewBinding.dateOfBirth.setText(dateFormat.format(bacSpec.getDateOfBirth()));
        viewBinding.dateOfExpiry.setText(dateFormat.format(bacSpec.getDateOfExpiry()));
        String docNumber = bacSpec.getDocumentNumber();
        Matcher matcher = Pattern.compile("([^\\<]+)\\<*$").matcher(docNumber);
        if (matcher.find()) {
            docNumber = matcher.group(1);
        }
        viewBinding.documentNumber.setText(docNumber);
    }

    private void updateModel() {
        if (viewModel == null) {
            return;
        }
        try {
            viewModel.setBACSpec(getBACSpec());
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    private BACSpec getBACSpec() throws ParseException {
        String docNumber = viewBinding.documentNumber.getText().toString().trim().toUpperCase(Locale.ROOT);
        Date dob = dateFormat.parse(viewBinding.dateOfBirth.getText().toString());
        Date doe = dateFormat.parse(viewBinding.dateOfExpiry.getText().toString());
        return new BACSpec(docNumber, dob, doe);
    }

    private void updateCaptureButton() {
        if (viewBinding == null) {
            return;
        }
        viewBinding.captureButton.setEnabled(!viewBinding.documentNumber.getText().toString().trim().isEmpty());
    }

    @Override
    public void onPickDate(String identifier, Date date) {
        if (BUTTON_DOB.equals(identifier)) {
            viewBinding.dateOfBirth.setText(dateFormat.format(date));
        } else if (BUTTON_DOE.equals(identifier)) {
            viewBinding.dateOfExpiry.setText(dateFormat.format(date));
        }
        updateModel();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable @org.jetbrains.annotations.Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_MRZ_SCAN && resultCode == Activity.RESULT_OK && data != null) {
            recognizerBundle.loadFromIntent(data);
            BlinkIdRecognizer.Result result = passportRecognizer.getResult();
            if (result.getResultState() == Recognizer.Result.State.Valid) {
                MrzResult mrzResult = result.getMrzResult();
                com.microblink.results.date.Date dob = mrzResult.getDateOfBirth().getDate();
                com.microblink.results.date.Date doe = mrzResult.getDateOfExpiry().getDate();
                if (doe == null || dob == null) {
                    return;
                }
                Calendar dobCal = Calendar.getInstance();
                dobCal.clear();
                dobCal.set(dob.getYear(), dob.getMonth()-1, dob.getDay());
                Calendar doeCal = Calendar.getInstance();
                doeCal.clear();
                doeCal.set(doe.getYear(), doe.getMonth()-1, doe.getDay());

                BACSpec bacSpec = new BACSpec(mrzResult.getDocumentNumber(), dobCal.getTime(), doeCal.getTime());
                updateFromBACSpec(bacSpec);
                if (viewModel != null) {
                    viewModel.setBACSpec(bacSpec);
                }
            }
        }
    }

    private void scanMRZ() {
        BlinkIdUISettings settings = new BlinkIdUISettings(recognizerBundle);
        ActivityRunner.startActivityForResult(this, REQUEST_CODE_MRZ_SCAN, settings);
    }
}
