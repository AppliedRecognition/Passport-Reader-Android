package com.appliedrec.mrtdreader;

import android.annotation.SuppressLint;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class BACSpecJsonAdapter implements JsonSerializer<BACSpec>, JsonDeserializer<BACSpec> {

    private static final String DOC_NUMBER = "doc_number";
    private static final String DOB = "dob";
    private static final String DOE = "doe";

    @SuppressLint("SimpleDateFormat")
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    @Override
    public BACSpec deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        try {
            JsonObject jsonObject = json.getAsJsonObject();
            String docNumber = jsonObject.get(DOC_NUMBER).getAsString();
            Date dob = dateFormat.parse(jsonObject.get(DOB).getAsString());
            Date doe = dateFormat.parse(jsonObject.get(DOE).getAsString());
            return new BACSpec(docNumber, dob, doe);
        } catch (ParseException e) {
            throw new JsonParseException(e);
        }
    }

    @Override
    public JsonElement serialize(BACSpec src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty(DOC_NUMBER, src.getDocumentNumber());
        jsonObject.addProperty(DOB, dateFormat.format(src.getDateOfBirth()));
        jsonObject.addProperty(DOE, dateFormat.format(src.getDateOfExpiry()));
        return jsonObject;
    }
}
