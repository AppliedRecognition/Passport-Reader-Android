package com.appliedrec.mrtd_reader_app

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Parcel
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.appliedrec.mrtd_reader_app.databinding.BacEntryBinding
import com.appliedrec.mrtdreader.BACSpec
import com.microblink.blinkid.entities.recognizers.Recognizer
import com.microblink.blinkid.entities.recognizers.RecognizerBundle
import com.microblink.blinkid.entities.recognizers.blinkid.generic.BlinkIdSingleSideRecognizer
import com.microblink.blinkid.entities.recognizers.blinkid.generic.ClassFilter
import com.microblink.blinkid.entities.recognizers.blinkid.generic.classinfo.ClassInfo
import com.microblink.blinkid.entities.recognizers.blinkid.generic.classinfo.Type
import com.microblink.blinkid.uisettings.ActivityRunner
import com.microblink.blinkid.uisettings.BlinkIdUISettings
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.regex.Pattern

class BACEntryFragment : Fragment(), DatePickerFragment.Listener {
    interface Listener {
        fun onRequestCapture(bacSpec: BACSpec)
    }

    private var listener: Listener? = null
    private val dateFormat = SimpleDateFormat.getDateInstance(SimpleDateFormat.MEDIUM)
    private var viewBinding: BacEntryBinding? = null
    private var passportRecognizer: BlinkIdSingleSideRecognizer? = null
    private var recognizerBundle: RecognizerBundle? = null
    private var viewModel: BACSpecModel? = null
    private var application: MRTDReaderApplication? = null
    private val microblinkKeyDownloadBroadcastReceiver: BroadcastReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (viewBinding == null || application == null) {
                    return
                }
                viewBinding!!.cameraButton.isEnabled = application!!.isMicroblinkEnabled
                if (application!!.isMicroblinkEnabled) {
                    createBlinkIdRecognizer()
                    LocalBroadcastManager.getInstance(application!!).unregisterReceiver(this)
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewBinding = BacEntryBinding.inflate(inflater, container, false)
        viewBinding!!.dateOfBirth.text = dateFormat.format(Date())
        viewBinding!!.dateOfExpiry.text = dateFormat.format(Date())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arguments?.getParcelable(BAC_KEY, BACSpec::class.java)?.let { updateFromBACSpec(it) }
        } else {
            arguments?.getParcelable<BACSpec>(BAC_KEY)?.let { updateFromBACSpec(it) }
        }
        viewBinding!!.documentNumber.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                updateCaptureButton()
                updateModel()
            }

            override fun afterTextChanged(s: Editable) {}
        })
        viewBinding!!.dateOfBirth.setOnClickListener { v: View? ->
            selectDate(
                viewBinding!!.dateOfBirth, BUTTON_DOB
            )
        }
        viewBinding!!.dateOfExpiry.setOnClickListener { v: View? ->
            selectDate(
                viewBinding!!.dateOfExpiry, BUTTON_DOE
            )
        }
        viewBinding!!.captureButton.setOnClickListener { v: View? ->
            if (listener != null) {
                if (viewBinding!!.documentNumber.text.toString().trim { it <= ' ' }.isEmpty()) {
                    return@setOnClickListener
                }
                try {
                    listener!!.onRequestCapture(bacSpec)
                } catch (e: ParseException) {
                    e.printStackTrace()
                }
            }
        }
        viewBinding!!.cameraButton.isEnabled = false
        viewBinding!!.cameraButton.setOnClickListener { v: View? -> scanMRZ() }
        updateCaptureButton()
        return viewBinding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateCameraButton()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewBinding = null
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is Listener) {
            listener = context
        }
        application = requireActivity().application as MRTDReaderApplication
        viewModel = ViewModelProvider(requireActivity()).get(
            BACSpecModel::class.java
        )
        viewModel!!.setSharedPreferences(PreferenceManager.getDefaultSharedPreferences(context))
        updateCameraButton()
    }

    private fun updateCameraButton() {
        if (viewBinding == null || application == null) {
            return
        }
        viewBinding!!.cameraButton.isEnabled = application!!.isMicroblinkEnabled
        if (application!!.isMicroblinkEnabled) {
            createBlinkIdRecognizer()
        } else {
            LocalBroadcastManager.getInstance(application!!).registerReceiver(
                microblinkKeyDownloadBroadcastReceiver,
                IntentFilter(MRTDReaderApplication.INTENT_ACTION_MICROBLINK_ENABLED)
            )
        }
    }

    private fun createBlinkIdRecognizer() {
        passportRecognizer = BlinkIdSingleSideRecognizer()
        passportRecognizer!!.setClassFilter(object : ClassFilter {
            override fun classFilter(classInfo: ClassInfo): Boolean {
                return classInfo.type == Type.PASSPORT
            }

            override fun describeContents(): Int {
                return 0
            }

            override fun writeToParcel(dest: Parcel, flags: Int) {}
        })
        recognizerBundle = RecognizerBundle(passportRecognizer)
    }

    override fun onDetach() {
        super.onDetach()
        LocalBroadcastManager.getInstance(application!!).unregisterReceiver(
            microblinkKeyDownloadBroadcastReceiver
        )
        application = null
        listener = null
        viewModel = null
    }

    override fun onResume() {
        super.onResume()
        if (viewBinding == null) {
            return
        }
        viewBinding!!.documentNumber.requestFocus()
        viewBinding!!.documentNumber.postDelayed({
            if (viewBinding == null || context == null) {
                return@postDelayed
            }
            val imm =
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(viewBinding!!.documentNumber, 0)
        }, 300)
    }

    private fun selectDate(button: Button, identifier: String) {
        var date: Date? = null
        try {
            date = dateFormat.parse(button.text as String)
        } catch (e: ParseException) {
            e.printStackTrace()
        }
        DatePickerFragment.newInstance(identifier, date).show(childFragmentManager, "date")
    }

    private fun updateFromBACSpec(bacSpec: BACSpec) {
        if (viewBinding == null) {
            return
        }
        viewBinding!!.dateOfBirth.text = dateFormat.format(bacSpec.dateOfBirth)
        viewBinding!!.dateOfExpiry.text = dateFormat.format(bacSpec.dateOfExpiry)
        var docNumber = bacSpec.documentNumber
        val matcher = Pattern.compile("([^<]+)<*$").matcher(docNumber)
        if (matcher.find()) {
            docNumber = matcher.group(1)!!
        }
        viewBinding!!.documentNumber.setText(docNumber)
    }

    private fun updateModel() {
        try {
            viewModel?.bacSpec = bacSpec
        } catch (e: ParseException) {
            e.printStackTrace()
        }
    }

    @get:Throws(ParseException::class)
    private val bacSpec: BACSpec
        get() {
            val docNumber = viewBinding?.documentNumber?.text?.toString()?.trim { it <= ' ' }?.uppercase() ?: ""
            val dob = dateFormat.parse(viewBinding?.dateOfBirth?.text?.toString() ?: dateFormat.format(Date())) ?: Date()
            val doe = dateFormat.parse(viewBinding?.dateOfExpiry?.text?.toString() ?: dateFormat.format(Date())) ?: Date()
            return BACSpec(docNumber, dob, doe)
        }

    private fun updateCaptureButton() {
        viewBinding?.captureButton?.isEnabled =
            !(viewBinding?.documentNumber?.text?.toString()?.trim { it <= ' ' }?.isBlank() ?: false)
    }

    override fun onPickDate(identifier: String?, date: Date?) {
        if (date == null) {
            return
        }
        if (BUTTON_DOB == identifier) {
            viewBinding?.dateOfBirth?.text = dateFormat.format(date)
        } else if (BUTTON_DOE == identifier) {
            viewBinding?.dateOfExpiry?.text = dateFormat.format(date)
        }
        updateModel()
    }

    private val blinkIdLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { resultIntent ->
        if (resultIntent.resultCode == Activity.RESULT_OK && resultIntent.data != null) {
            recognizerBundle!!.loadFromIntent(resultIntent.data!!)
            val result = passportRecognizer!!.result
            if (result.resultState == Recognizer.Result.State.Valid) {
                val mrzResult = result.mrzResult
                val dob = mrzResult.dateOfBirth.date
                val doe = mrzResult.dateOfExpiry.date
                if (dob == null || doe == null) {
                    return@registerForActivityResult
                }
                val dobCal = Calendar.getInstance()
                dobCal.clear()
                dobCal[dob.year, dob.month - 1] = dob.day
                val doeCal = Calendar.getInstance()
                doeCal.clear()
                doeCal[doe.year, doe.month - 1] = doe.day
                val bacSpec = BACSpec(mrzResult.documentNumber, dobCal.time, doeCal.time)
                updateFromBACSpec(bacSpec)
                if (viewModel != null) {
                    viewModel!!.bacSpec = bacSpec
                }
            }
        }
    }

    private fun scanMRZ() {
        val settings = BlinkIdUISettings(recognizerBundle)
        val intent = Intent(this.activity, settings.targetActivity)
        settings.saveToIntent(intent)
        blinkIdLauncher.launch(intent)
    }

    companion object {
        private const val BAC_KEY = "BAC"
        private const val BUTTON_DOB = "dob"
        private const val BUTTON_DOE = "doe"
        fun newInstance(bacSpec: BACSpec?): BACEntryFragment {
            val args = Bundle()
            args.putParcelable(BAC_KEY, bacSpec)
            val fragment = BACEntryFragment()
            fragment.arguments = args
            return fragment
        }
    }
}