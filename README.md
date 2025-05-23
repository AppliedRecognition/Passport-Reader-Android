# NFC Passport Reader for Android

Read information and face image from passports equipped with an NFC (near field communication) chip using an Android device.

## Installation

Add the following dependency in your module's build.gradle file:

```groovy
dependencies {
    implementation 'com.appliedrec.mrtdreader:mrtdreader:2.0.0'
}
```
## Usage

To read a passport you will first need to obtain the following:

- Document number
- Date of birth of the passport holder
- The passport's date of expiry

You can use a library like [Microblink's BlinkID](https://github.com/BlinkID/blinkid-android) to read the machine-readable zone (MRZ) on the passport or you can let the user enter the above information.

Once you have the document number, date of birth and date of expiry, construct an instance of [`BACSpec`](https://appliedrecognition.github.io/Passport-Reader-Android/-m-r-t-d%20-reader/com.appliedrec.mrtdreader/-b-a-c-spec/index.html) that you're going to pass to the scan session:

```kotlin
val documentNumber: String // Document (passport) number
val dateOfBirth: Date // Passport holder's date of birth
val dateOfExpiry: Date // Passport's expiry date

val bacSpec = BACSpec(documentNumber, dateOfBirth, dateOfExpiry)
```
Having the `BACSpec`, you can now start a passport scan session from your activity:

```kotlin
class MyActivity : AppCompatActivity(), MRTDScanSessionListener {

    fun scanPassport(bacSpec: BACSpec) {
        val session = MRTDScanSession(this, bacSpec)
        session.setListener(this)
        session.start()
    }
    
    override fun onMRTDScanSucceeded(bacSpec: BACSpec, result: MRTDScanResult) {
        TODO("Use the result")
    }
    
    override fun onMRTDScanFailed(bacSpec: BACSpec, throwable: Throwable) {
        TODO("Handle session error")
    }
}
```

## Sample application

The project contains a sample application in the module called `testapp`. The application shows how to use [BlinkID](https://github.com/BlinkID/blinkid-android) to read the machine-readable zone (MRZ) on the passport and our [Ver-ID SDK](https://github.com/AppliedRecognition/Ver-ID-UI-Android) to capture a selfie and compare it to the face from the passport's NFC chip.

## Documentation

API documentation is available in the [docs folder](https://appliedrecognition.github.io/Passport-Reader-Android/-m-r-t-d%20-reader/com.appliedrec.mrtdreader/index.html).