# NFC Passport Reader for Android

Read information and face image from passports equipped with an NFC (near field communication) chip using an Android device.

## Installation

Add the following dependency in your module's build.gradle file:

```groovy
dependencies {
    implementation 'com.appliedrec:mrtd-reader:3.0.0'
}
```
## Usage

To read a passport you will first need to obtain the following:

- Document number
- Date of birth of the passport holder
- The passport's date of expiry

You can use a library like [Microblink's BlinkID](https://github.com/BlinkID/blinkid-android) to read the machine-readable zone (MRZ) on the passport or you can let the user enter the above information.

Once you have the document number, date of birth and date of expiry, you can start a passport scan session from your activity:

```kotlin
class MyActivity : AppCompatActivity() {

    fun scanPassport(
        documentNumber: String, // Document (passport) number
        dateOfBirth: Date, // Passport holder's date of birth
        dateOfExpiry: Date // Passport's expiry date
    ) {
        val bacSpec = BACSpec(documentNumber, dateOfBirth, dateOfExpiry)
        passportScanner.launch(MRTDScanSettings(bacSpec))
    }

    private val passportScanner = registerForActivityResult(MRTDReaderActivityResultContract()) { result ->
        when (result) {
            is MRTDScanResult.Success -> {
                TODO("Handle success result")
            }
            is MRTDScanResult.Failure -> {
                TODO("Handle error")
            }
            else -> {}
        }
    }
}
```

## Sample application

The project contains a sample application in the module called `testapp`. The application shows how to use the r[Ver-ID SDK](https://github.com/AppliedRecognition/Face-Capture-Android) to capture a selfie and compare it to the face from the passport's NFC chip.

## Documentation

API documentation is available in the [docs folder](https://appliedrecognition.github.io/Passport-Reader-Android/-m-r-t-d%20-reader/com.appliedrec.mrtdreader/index.html).