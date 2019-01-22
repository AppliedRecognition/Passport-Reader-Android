package com.appliedrec.mrtdreader;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Date;

/**
 * Basic Access Control (BAC) specification
 * @version 1.0.0
 */
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

    /**
     * @version 1.0.0
     * @return Travel document number
     */
    public String getDocumentNumber() {
        return documentNumber;
    }

    /**
     * @version 1.0.0
     * @return Date of birth of the travel document holder
     */
    public Date getDateOfBirth() {
        return dateOfBirth;
    }

    /**
     * @version 1.0.0
     * @return Travel document expiry date
     */
    public Date getDateOfExpiry() {
        return dateOfExpiry;
    }

    /**
     *
     * @param documentNumber
     * @param dateOfBirth
     * @param dateOfExpiry
     */
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
