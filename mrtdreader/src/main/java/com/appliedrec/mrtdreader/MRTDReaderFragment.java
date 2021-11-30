package com.appliedrec.mrtdreader;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.nfc.tech.IsoDep;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.jmrtd.PassportService;
import org.jmrtd.lds.icao.MRZInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Fragment that maintains the lifecycle of the MRTD Reader process
 * Activities that contain this fragment must implement the
 * {@link MRTDReaderFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link MRTDReaderFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class MRTDReaderFragment extends Fragment implements MRTDReaderTask.IMRTDReaderTaskListener {

    private static final String TAG = "MRTDReaderFragment";

    private MRTDReaderFragmentInteractionListener mListener;

    public static final String ARG_BAC_SPEC = "com.appliedrec.ARG_BAC_SPEC";

    private static final int VIEW_MODE_WAITING = 0;
    private static final int VIEW_MODE_READING = 1;
    private static final int VIEW_MODE_NONFC = 2;

    private BACSpec bacSpec;

    private View pnlWaiting;
    private View pnlReading;
    private View pnlNoNFC;
    private ListView lstReaderEvents;

    private MRTDReaderTask readerTask;
    private List<MRTDReaderEvent> readerEvents;
    private MRTDReaderEventListAdapter adapter;

    public MRTDReaderFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment MRTDReaderFragment.
     */
    public static MRTDReaderFragment newInstance(BACSpec bacSpec) {
        MRTDReaderFragment fragment = new MRTDReaderFragment();
        Bundle args = new Bundle();
        if (bacSpec != null) {
            args.putParcelable(ARG_BAC_SPEC, bacSpec);
        }
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null && getArguments().containsKey(ARG_BAC_SPEC)) {
            bacSpec = getArguments().getParcelable(ARG_BAC_SPEC);
        }

        //init reader events list
        readerEvents = new ArrayList<>();
        buildEventList();

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View v = inflater.inflate(R.layout.fragment_mrtdreader, container, false);
        pnlWaiting = v.findViewById(R.id.pnl_waiting_for_document);
        pnlReading = v.findViewById(R.id.pnl_read_document);
        pnlNoNFC = v.findViewById(R.id.pnl_no_nfc);

        Button btnSettings = (Button)v.findViewById(R.id.btn_nfc_settings);
        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS));
            }
        });

        lstReaderEvents = (ListView) v.findViewById(android.R.id.list);
        adapter = new MRTDReaderEventListAdapter();
        lstReaderEvents.setAdapter(adapter);

        toggleViewMode(VIEW_MODE_WAITING);

        return v;
    }


    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof MRTDReaderFragmentInteractionListener) {
            mListener = (MRTDReaderFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement MRTDReaderFragmentInteractionListener");
        }
    }


    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    @Override
    public void onPause() {
        super.onPause();
        //kill read task if we're waiting
        if (readerTask != null && !readerTask.isCancelled()) {
            readerTask.cancel(true);
            readerTask = null;
        }
    }

    @Override
    public void onProgress(MRTDReaderTask.MRTDReaderTaskProgress progress) {
        Log.d(TAG, "onProgress. Fid: " + Integer.toHexString(progress.fileId) + "; Success: " + progress.success + "; Message: " + progress.message);
        if (!progress.success) {
            Log.d(TAG, "Cancelling task due to non-successful progress indication");
            //cancel task
            readerTask.cancel(true);
            readerTask = null;

            if (mListener != null) {
                mListener.onMRTDReadFailed(progress.message);
                mListener = null;
            }
            //set all events to failed

        } else {
            if (progress.fileId >= 0) {
                boolean bEventFound = false;
                for (int i = 0; i < readerEvents.size(); i++) {
                    MRTDReaderEvent evt = readerEvents.get(i);
                    if (evt.fileId == progress.fileId) {
                        if (progress.subProgress == null || progress.subProgress.floatValue() >= 1f) {
                            evt.state = MRTDReaderEvent.STATE_FINISH_SUCCESS;
                            if (i < readerEvents.size() - 1) {
                                readerEvents.get(i + 1).state = MRTDReaderEvent.STATE_IN_PROGRESS;
                                readerEvents.get(i + 1).progress = 0f;
                            }
                        } else {
                            evt.state = MRTDReaderEvent.STATE_IN_PROGRESS;
                            evt.progress = progress.subProgress.floatValue();
                        }
                        bEventFound = true;
                        break;
                    }
                }

                if (bEventFound) {
                    adapter.notifyDataSetChanged();
                }
            }
        }

    }

    @Override
    public void onCancelled() {
        Log.d(TAG, "received Task Cancelled");
        if (mListener != null)
            mListener.onMRTDReadFailed("Failed to read passport chip");
    }

    @Override
    public void onCompleted(MRTDConnectionResult result) {

        MRZInfo mrzInfo = result.getMRZInfo();
        String gender;
        switch (mrzInfo.getGender()){
            case FEMALE:
                gender = getResources().getString(R.string.gender_code_female);
                break;
            case MALE:
                gender = getResources().getString(R.string.gender_code_male);
                break;
            case UNSPECIFIED:
                gender = getResources().getString(R.string.gender_code_unspecified);
                break;
            default:
                gender = getResources().getString(R.string.gender_code_unknown);
        }
        String imageFilePath = null;
        Bitmap faceImage = result.getFaceBitmap();
        if (faceImage != null) {
            try {
                File file = File.createTempFile("image", ".jpg");
                FileOutputStream outputStream = new FileOutputStream(file);
                faceImage.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
                outputStream.close();
                imageFilePath = file.getPath();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

//        MRTDScanResult scanResult = new MRTDScanResult(mrzInfo.getDocumentCode(), mrzInfo.getIssuingState(), mrzInfo.getPrimaryIdentifier(), mrzInfo.getSecondaryIdentifierComponents(), mrzInfo.getNationality(), mrzInfo.getDocumentNumber(), mrzInfo.getPersonalNumber(), mrzInfo.getDateOfBirth(), gender, mrzInfo.getDateOfExpiry(), imageFilePath);
//
//        if (mListener != null)
//            mListener.onMRTDReadCompleted(scanResult);

    }


    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     */
    public interface MRTDReaderFragmentInteractionListener {
        void onMRTDReadStarted();
        void onMRTDReadFailed(String errorDescription);
        void onMRTDReadCompleted(MRTDScanResult result);
    }

    public void onIsoDepReceived(IsoDep isoDep) {

        isoDep.setTimeout(30000);

        if (readerTask == null) {

            if (mListener != null)
                mListener.onMRTDReadStarted();
            toggleViewMode(VIEW_MODE_READING);

            readerTask = new MRTDReaderTask(this.getActivity(), this, isoDep, bacSpec);
            readerTask.execute();
        }

    }

    public void notifyNFCNotEnabled(){
        Log.d(TAG, "notifyNFCNotEnabled()");
        toggleViewMode(VIEW_MODE_NONFC);
    }

    public void notifyNFCWaiting(){
        Log.d(TAG, "notifyNFCWaiting()");
        toggleViewMode(VIEW_MODE_WAITING);
    }

    private void toggleViewMode(int viewMode) {
        switch (viewMode) {
            case VIEW_MODE_READING:

                Log.d(TAG, "toggleViewMode: READING");
                //reset the state of event items
                buildEventList();
                readerEvents.get(0).state = MRTDReaderEvent.STATE_IN_PROGRESS;
                adapter.notifyDataSetChanged();

                pnlWaiting.setVisibility(View.GONE);
                pnlNoNFC.setVisibility(View.GONE);
                pnlReading.setVisibility(View.VISIBLE);

                break;
            case VIEW_MODE_WAITING:

                Log.d(TAG, "toggleViewMode: WAITING");
                pnlReading.setVisibility(View.GONE);
                pnlNoNFC.setVisibility(View.GONE);
                pnlWaiting.setVisibility(View.VISIBLE);

                break;

            case VIEW_MODE_NONFC:

                Log.d(TAG, "toggleViewMode: NO NFC");
                pnlNoNFC.setVisibility(View.VISIBLE);
                pnlReading.setVisibility(View.GONE);
                pnlWaiting.setVisibility(View.GONE);


                break;
        }
    }

    private class MRTDReaderEvent {

        public static final int STATE_NOT_STARTED = 0;
        public static final int STATE_IN_PROGRESS = 1;
        public static final int STATE_FINISH_SUCCESS = 2;
        public static final int STATE_FINISH_FAILED = 3;


        public short fileId;
        public int state;
        public String title;
        public String subTitle;
        public float progress;
    }

    private MRTDReaderEvent makeReaderEvent(short fieldId, String title) {
        MRTDReaderEvent evt = new MRTDReaderEvent();
        evt.fileId = fieldId;
        evt.title = title;
        evt.state = MRTDReaderEvent.STATE_NOT_STARTED;
        evt.progress = 0f;
        return evt;
    }

    private void buildEventList() {
        readerEvents.clear();
        readerEvents.add(makeReaderEvent(PassportService.EF_COM, getText(R.string.mrtd_evt_bac).toString()));
        readerEvents.add(makeReaderEvent(PassportService.EF_DG2, getText(R.string.mrtd_evt_photo).toString()));
        readerEvents.add(makeReaderEvent(PassportService.EF_DG15, getText(R.string.mrtd_evt_mrz).toString()));
    }

    private class MRTDReaderEventListAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return readerEvents.size();
        }

        @Override
        public Object getItem(int i) {
            return readerEvents.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            View v = view;
            if (v == null) {
                v = getActivity().getLayoutInflater().inflate(R.layout.mrtd_progress_list_item, viewGroup, false);
            }

            ProgressBar prg = (ProgressBar) v.findViewById(R.id.prg_working);
            ImageView iv = (ImageView) v.findViewById(R.id.img_step_status);

            iv.setVisibility(View.INVISIBLE);
            prg.setVisibility(View.INVISIBLE);

            MRTDReaderEvent event = (MRTDReaderEvent) getItem(i);
            switch (event.state) {
                case MRTDReaderEvent.STATE_IN_PROGRESS:
                    prg.setVisibility(View.VISIBLE);
                    if (event.progress > 0f) {
                        prg.setIndeterminate(false);
                        prg.setMax(100);
                        prg.setProgress((int)(event.progress * 100f));
                    }
                    break;
                case MRTDReaderEvent.STATE_FINISH_FAILED:
                case MRTDReaderEvent.STATE_FINISH_SUCCESS:
                    iv.setVisibility(View.VISIBLE);
                    break;
            }

            TextView tvTitle = (TextView) v.findViewById(R.id.tv_event_title);
            tvTitle.setText(event.title);

            return v;
        }
    }



}
