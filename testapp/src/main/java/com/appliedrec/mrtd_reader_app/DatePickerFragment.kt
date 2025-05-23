package com.appliedrec.mrtd_reader_app

import android.app.DatePickerDialog
import android.app.DatePickerDialog.OnDateSetListener
import android.app.Dialog
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.DatePicker
import androidx.fragment.app.DialogFragment
import java.util.Calendar
import java.util.Date

class DatePickerFragment : DialogFragment(), OnDateSetListener {

    interface Listener {
        fun onPickDate(identifier: String?, date: Date?)
    }

    private var listener: Listener? = null
    private var identifier: String? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val calendar = Calendar.getInstance()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arguments?.getSerializable(DATE_KEY, Date::class.java)?.let {
                calendar.time = it
            }
        } else {
            (arguments?.getSerializable(DATE_KEY) as? Date)?.let {
                calendar.time = it
            }
        }
        arguments?.getString(IDENTIFIER_KEY)?.let {
            identifier = it
        }
        return DatePickerDialog(
            requireActivity(),
            this,
            calendar[Calendar.YEAR],
            calendar[Calendar.MONTH],
            calendar[Calendar.DATE]
        )
    }

    override fun onDateSet(view: DatePicker, year: Int, month: Int, dayOfMonth: Int) {
        val calendar = Calendar.getInstance()
        calendar[year, month] = dayOfMonth
        listener?.onPickDate(identifier, calendar.time)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (parentFragment != null && parentFragment is Listener) {
            listener = parentFragment as Listener
        }
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    companion object {
        private const val DATE_KEY = "date"
        private const val IDENTIFIER_KEY = "identifier"
        fun newInstance(identifier: String, date: Date?): DatePickerFragment {
            val args = Bundle()
            if (date != null) {
                args.putSerializable(DATE_KEY, date)
            }
            args.putString(IDENTIFIER_KEY, identifier)
            val fragment = DatePickerFragment()
            fragment.arguments = args
            return fragment
        }
    }
}