package com.appliedrec.mrtdreader;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Date;

public class MRTDScanResult implements Parcelable {

    private String documentCode;
    private String issuingState;
    private String primaryIdentifier;
    private String[] secondaryIdentifiers;
    private String nationality;
    private String documentNumber;
    private String personalNumber;
    private String dateOfBirth;
    private String gender;
    private String dateOfExpiry;
    private String imageFilePath;

    MRTDScanResult(
            String documentCode,
            String issuingState,
            String primaryIdentifier,
            String[] secondaryIdentifiers,
            String nationality,
            String documentNumber,
            String personalNumber,
            String dateOfBirth,
            String gender,
            String dateOfExpiry,
            String imageFilePath
    ) {
        this.documentCode = documentCode;
        this.issuingState = issuingState;
        this.primaryIdentifier = primaryIdentifier;
        this.secondaryIdentifiers = secondaryIdentifiers;
        this.nationality = nationality;
        this.documentNumber = documentNumber;
        this.personalNumber = personalNumber;
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
        this.dateOfExpiry = dateOfExpiry;
        this.imageFilePath = imageFilePath;
    }

    protected MRTDScanResult(Parcel in) {
        documentCode = in.readString();
        issuingState = in.readString();
        primaryIdentifier = in.readString();
        secondaryIdentifiers = in.createStringArray();
        nationality = in.readString();
        documentNumber = in.readString();
        personalNumber = in.readString();
        dateOfBirth = in.readString();
        gender = in.readString();
        dateOfExpiry = in.readString();
        imageFilePath = in.readString();
    }

    public static final Creator<MRTDScanResult> CREATOR = new Creator<MRTDScanResult>() {
        @Override
        public MRTDScanResult createFromParcel(Parcel in) {
            return new MRTDScanResult(in);
        }

        @Override
        public MRTDScanResult[] newArray(int size) {
            return new MRTDScanResult[size];
        }
    };

    public String getDocumentCode() {
        return documentCode;
    }

    public String getIssuingState() {
        return issuingState;
    }

    public String getPrimaryIdentifier() {
        return primaryIdentifier;
    }

    public String[] getSecondaryIdentifiers() {
        return secondaryIdentifiers;
    }

    public String getNationality() {
        return nationality;
    }

    public String getDocumentNumber() {
        return documentNumber;
    }

    public String getPersonalNumber() {
        return personalNumber;
    }

    public String getDateOfBirth() {
        return dateOfBirth;
    }

    public String getGender() {
        return gender;
    }

    public String getDateOfExpiry() {
        return dateOfExpiry;
    }

    public String getImageFilePath() {
        return imageFilePath;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(documentCode);
        parcel.writeString(issuingState);
        parcel.writeString(primaryIdentifier);
        parcel.writeStringArray(secondaryIdentifiers);
        parcel.writeString(nationality);
        parcel.writeString(documentNumber);
        parcel.writeString(personalNumber);
        parcel.writeString(dateOfBirth);
        parcel.writeString(gender);
        parcel.writeString(dateOfExpiry);
        parcel.writeString(imageFilePath);
    }
}
