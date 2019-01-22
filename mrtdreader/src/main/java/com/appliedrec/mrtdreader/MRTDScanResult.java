package com.appliedrec.mrtdreader;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Base64;
import android.util.Base64OutputStream;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.annotations.JsonAdapter;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;

/**
 * Result of a MRTD scan
 * @version 1.0.0
 */
@JsonAdapter(MRTDScanResult.class)
public class MRTDScanResult implements Parcelable, JsonSerializer<MRTDScanResult>, JsonDeserializer<MRTDScanResult> {

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
    private String faceImageFilePath;

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
            String faceImageFilePath
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
        this.faceImageFilePath = faceImageFilePath;
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
        faceImageFilePath = in.readString();
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

    /**
     * @version 1.0.0
     * @return Document code
     */
    public String getDocumentCode() {
        return documentCode;
    }

    /**
     * @version 1.0.0
     * @return Document issuing state
     */
    public String getIssuingState() {
        return issuingState;
    }

    /**
     * @version 1.0.0
     * @return Primary identifier
     */
    public String getPrimaryIdentifier() {
        return primaryIdentifier;
    }

    /**
     * @version 1.0.0
     * @return Secondary identifiers
     */
    public String[] getSecondaryIdentifiers() {
        return secondaryIdentifiers;
    }

    /**
     * @version 1.0.0
     * @return Nationality of the document holder
     */
    public String getNationality() {
        return nationality;
    }

    /**
     * @version 1.0.0
     * @return Document number
     */
    public String getDocumentNumber() {
        return documentNumber;
    }

    /**
     * @version 1.0.0
     * @return Personal number
     */
    public String getPersonalNumber() {
        return personalNumber;
    }

    /**
     * @version 1.0.0
     * @return Document holder's date of birth
     */
    public String getDateOfBirth() {
        return dateOfBirth;
    }

    /**
     * @version 1.0.0
     * @return Document holder's gender
     */
    public String getGender() {
        return gender;
    }

    /**
     * @version 1.0.0
     * @return Document expiry date
     */
    public String getDateOfExpiry() {
        return dateOfExpiry;
    }

    /**
     * @version 1.0.0
     * @return Path to the image of the documen't holder face
     */
    public String getFaceImageFilePath() {
        return faceImageFilePath;
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
        parcel.writeString(faceImageFilePath);
    }

    @Override
    public MRTDScanResult deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        String documentCode = jsonObject.has("documentCode") ? jsonObject.get("documentCode").getAsString() : null;
        String issuingState = jsonObject.has("issuingState") ? jsonObject.get("documentCode").getAsString() : null;
        String primaryIdentifier = jsonObject.has("primaryIdentifier") ? jsonObject.get("primaryIdentifier").getAsString() : null;
        String[] secondaryIdentifiers;
        if (jsonObject.has("secondaryIdentifiers") && jsonObject.get("secondaryIdentifiers").isJsonArray()) {
            JsonArray secondaryIdentifiersJson = jsonObject.getAsJsonArray("secondaryIdentifiers");
            secondaryIdentifiers = new String[secondaryIdentifiersJson.size()];
            int i=0;
            for (JsonElement identifier : secondaryIdentifiersJson) {
                secondaryIdentifiers[i++] = identifier.getAsString();
            }
        } else {
            secondaryIdentifiers = new String[0];
        }
        String nationality = jsonObject.has("nationality") ? jsonObject.get("nationality").getAsString() : null;
        String documentNumber = jsonObject.has("documentNumber") ? jsonObject.get("documentNumber").getAsString() : null;
        String personalNumber = jsonObject.has("personalNumber") ? jsonObject.get("personalNumber").getAsString() : null;
        String dateOfBirth = jsonObject.has("dateOfBirth") ? jsonObject.get("dateOfBirth").getAsString() : null;
        String gender = jsonObject.has("gender") ? jsonObject.get("gender").getAsString() : null;
        String dateOfExpiry = jsonObject.has("dateOfExpiry") ? jsonObject.get("dateOfExpiry").getAsString() : null;
        if (jsonObject.has("image") && !jsonObject.get("image").isJsonNull()) {

        }
        String faceImageFilePath = jsonObject.has("faceImageFilePath") ? jsonObject.get("faceImageFilePath").getAsString() : null;
        return new MRTDScanResult(documentCode, issuingState, primaryIdentifier, secondaryIdentifiers, nationality, documentNumber, personalNumber, dateOfBirth, gender, dateOfExpiry, faceImageFilePath);
    }

    @Override
    public JsonElement serialize(MRTDScanResult src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("documentCode", src.documentCode);
        jsonObject.addProperty("issuingState", src.issuingState);
        jsonObject.addProperty("primaryIdentifier", src.primaryIdentifier);
        if (src.secondaryIdentifiers != null && src.secondaryIdentifiers.length > 0) {
            JsonArray secondaryIdentifiers = new JsonArray();
            for (String identifier : src.secondaryIdentifiers) {
                secondaryIdentifiers.add(identifier);
            }
            jsonObject.add("secondaryIdentifiers", secondaryIdentifiers);
        }
        jsonObject.addProperty("nationality", src.nationality);
        jsonObject.addProperty("documentNumber", src.documentNumber);
        jsonObject.addProperty("personalNumber", src.personalNumber);
        jsonObject.addProperty("dateOfBirth", src.dateOfBirth);
        jsonObject.addProperty("gender", src.gender);
        jsonObject.addProperty("dateOfExpiry", src.dateOfExpiry);
        if (src.faceImageFilePath != null) {
            try {
                Bitmap bitmap = BitmapFactory.decodeFile(src.faceImageFilePath);
                if (bitmap != null) {
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    outputStream.write("data:image/jpeg;base64,".getBytes("UTF-8"));
                    Base64OutputStream base64OutputStream = new Base64OutputStream(outputStream, Base64.NO_WRAP);
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, base64OutputStream);
                    jsonObject.addProperty("image", outputStream.toString("UTF-8"));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return jsonObject;
    }
}
