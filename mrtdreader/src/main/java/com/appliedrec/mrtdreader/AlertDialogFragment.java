package com.appliedrec.mrtdreader;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.view.ContextThemeWrapper;

/**
 * Created by pauldriegen on 2017-01-26.
 */


public class AlertDialogFragment extends DialogFragment {

    public interface AlertDialogListener {
        void onDialogOkClick(DialogFragment dialog);
    }

    private static final String TAG = "AlertDialogFragment";

    private final static String KEY_MSG = "msg";
    private final static String KEY_TITLE = "title";

    private AlertDialogListener mListener;

    public static AlertDialogFragment getInstance(CharSequence msg, CharSequence title) {

        AlertDialogFragment frag = new AlertDialogFragment();
        Bundle b = new Bundle();
        b.putString(KEY_MSG, msg.toString());
        b.putString(KEY_TITLE, title.toString());
        frag.setArguments(b);
        return frag;
    }

    @Override
    public void onAttach(Context context) {

        super.onAttach(context);

        try {
            mListener = (AlertDialogListener) context;
        } catch (ClassCastException cce) {
        }
    }


    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        final String msg = getArguments().getString(KEY_MSG, null);
        final String title = getArguments().getString(KEY_TITLE, null);

        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setMessage(msg)
                .setTitle(title)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        if (mListener != null) {
                            mListener.onDialogOkClick(AlertDialogFragment.this);
                        }
                    }
                });
        // Create the AlertDialog object and return it
        return builder.create();
    }
}