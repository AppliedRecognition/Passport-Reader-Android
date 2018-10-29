package com.appliedrec.mrtdreader;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Date;

public class BACSpec implements Parcelable {

    private String documentNumber;
    private Date dateOfBirth;
    private Date dateOfExpiry;

    protected BACSpec(Parcel in) {
        documentNumber = in.readString();
        if (in.readByte() == 1) {
            dateOfBirth = new Date(in.readLong());
        }
        if (in.readByte() == 1) {
            dateOfExpiry = new Date(in.readLong());
        }
    }

    public static final Creator<BACSpec> CREATOR = new Creator<BACSpec>() {
        @Override
        public BACSpec createFromParcel(Parcel in) {
            return new BACSpec(in);
        }

        @Override
        public BACSpec[] newArray(int size) {
            return new BACSpec[size];
        }
    };

    public String getDocumentNumber() {
        return documentNumber;
    }

    public Date getDateOfBirth() {
        return dateOfBirth;
    }

    public Date getDateOfExpiry() {
        return dateOfExpiry;
    }

    public BACSpec(String documentNumber, Date dateOfBirth, Date dateOfExpiry) {
        this.documentNumber = documentNumber;
        this.dateOfBirth = dateOfBirth;
        this.dateOfExpiry = dateOfExpiry;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(documentNumber);
        if (dateOfBirth != null) {
            parcel.writeByte((byte)1);
            parcel.writeLong(dateOfBirth.getTime());
        } else {
            parcel.writeByte((byte)0);
        }
        if (dateOfExpiry != null) {
            parcel.writeByte((byte)1);
            parcel.writeLong(dateOfExpiry.getTime());
        } else {
            parcel.writeByte((byte)0);
        }
    }
}
