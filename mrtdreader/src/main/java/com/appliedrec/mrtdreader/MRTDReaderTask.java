/*
 * aJMRTD - An Android Client for JMRTD, a Java API for accessing machine readable travel documents.
 *
 * Max Guenther, max.math.guenther@googlemail.com
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 *
 */


package com.appliedrec.mrtdreader;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.nfc.tech.IsoDep;
import android.os.AsyncTask;
import android.util.Log;

import net.sf.scuba.smartcards.CardFileInputStream;
import net.sf.scuba.smartcards.CardService;

import org.jmrtd.BACKey;
import org.jmrtd.BACKeySpec;
import org.jmrtd.PassportService;
import org.jmrtd.lds.CardAccessFile;
import org.jmrtd.lds.PACEInfo;
import org.jmrtd.lds.SODFile;
import org.jmrtd.lds.icao.COMFile;
import org.jmrtd.lds.icao.DG1File;
import org.jmrtd.lds.icao.DG2File;
import org.jmrtd.lds.icao.LDS;
import org.jmrtd.lds.icao.MRZInfo;
import org.jmrtd.lds.iso19794.FaceImageInfo;
import org.jmrtd.lds.iso19794.FaceInfo;
import org.spongycastle.jce.provider.BouncyCastleProvider;

import java.io.DataInputStream;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import jj2000.JJ2000Frontend;

/**
 * Created by pauldriegen on 2017-01-23.
 */


public class MRTDReaderTask extends AsyncTask<Void, MRTDReaderTask.MRTDReaderTaskProgress, MRTDConnectionResult> {

    private static final String TAG = "MRTDReaderTask";

    private final Context context;
    private final BACKeySpec bacKey;
    private final IsoDep isoDep;
    private final IMRTDReaderTaskListener listener;

    public class MRTDReaderTaskProgress {
        public short fileId;
        public boolean success;
        public String message;
        public Float subProgress = null;
    }

    public interface IMRTDReaderTaskListener {
        void onProgress(MRTDReaderTask.MRTDReaderTaskProgress progress);
        void onCancelled();
        void onCompleted(MRTDConnectionResult result);
    }


    public MRTDReaderTask(Context context, IMRTDReaderTaskListener listener, IsoDep isodep, BACSpec bacSpec) {
        super();

        this.context = context;
        this.listener = listener;
        this.isoDep = isodep;
        String docNumber = bacSpec.getDocumentNumber();
        docNumber = docNumber + ("<<<<<<<<<".substring(docNumber.length()));
        this.bacKey = new BACKey(docNumber, bacSpec.getDateOfBirth(), bacSpec.getDateOfExpiry());

    }

    private MRTDReaderTaskProgress makeTaskProgress(String message) {
        MRTDReaderTaskProgress progress = new MRTDReaderTaskProgress();
        progress.message = message;
        progress.fileId = -1;
        progress.success = true;
        return progress;
    }

    private MRTDReaderTaskProgress makeTaskProgress(short fileId, boolean success, String message) {
        return makeTaskProgress(fileId, success, message, null);
    }

    private MRTDReaderTaskProgress makeTaskProgress(short fileId, boolean success, String message, Float subProgress) {
        MRTDReaderTaskProgress progress = new MRTDReaderTaskProgress();
        progress.message = message;
        progress.fileId = fileId;
        progress.success = success;
        progress.subProgress = subProgress;
        return progress;
    }

    @Override
    protected MRTDConnectionResult doInBackground(Void... voids) {

        short fid = PassportService.EF_COM;
        try {

            Security.insertProviderAt(new BouncyCastleProvider(), 1);
            Security.addProvider(new SecurityProvider("MRTDSecurityProvider", 1, "null"));

            CardService cardService = CardService.getInstance(isoDep);
            cardService.open();

            PassportService service = new PassportService(cardService);
            service.open();

            boolean paceSucceeded = false;
            try {
                CardAccessFile cardAccessFile = new CardAccessFile(service.getInputStream(PassportService.EF_CARD_ACCESS));
                Collection<PACEInfo> paceInfos = cardAccessFile.getPACEInfos();
                if (paceInfos != null && paceInfos.size() > 0) {
                    PACEInfo paceInfo = paceInfos.iterator().next();
                    service.doPACE(bacKey, paceInfo.getObjectIdentifier(), PACEInfo.toParameterSpec(paceInfo.getParameterId()));
                    paceSucceeded = true;
                } else {
                    paceSucceeded = true;
                }
            } catch (Exception e) {
                Log.w(TAG, e);
            }

            service.sendSelectApplet(paceSucceeded);

            if (!paceSucceeded) {
                try {
                    service.getInputStream(PassportService.EF_COM).read();
                } catch (Exception e) {
                    service.doBAC(bacKey);
                }
            }

            MRTDConnectionResult res = new MRTDConnectionResult();

            LDS lds = new LDS();


            CardFileInputStream comIn = service.getInputStream(PassportService.EF_COM);
            lds.add(PassportService.EF_COM, comIn, comIn.getLength());
            COMFile comFile = lds.getCOMFile();
            publishProgress(makeTaskProgress(PassportService.EF_COM, true, "Success"));


            fid = PassportService.EF_SOD;
            CardFileInputStream sodIn = service.getInputStream(PassportService.EF_SOD);
            lds.add(PassportService.EF_SOD, sodIn, sodIn.getLength());
            SODFile sodFile = lds.getSODFile();

            fid = PassportService.EF_DG1;
            CardFileInputStream dg1In = service.getInputStream(PassportService.EF_DG1);
            lds.add(PassportService.EF_DG1, dg1In, dg1In.getLength());
            DG1File dg1File = lds.getDG1File();
            MRZInfo mrzInfo = dg1File.getMRZInfo();
            res.setMRZInfo(mrzInfo);

            fid = PassportService.EF_DG2;
            CardFileInputStream dg2In = service.getInputStream(PassportService.EF_DG2);
            lds.add(PassportService.EF_DG2, dg2In, dg2In.getLength());
            DG2File dg2File = lds.getDG2File();

            List<FaceImageInfo> allFaceImageInfos = new ArrayList<>();
            List<FaceInfo> faceInfos = dg2File.getFaceInfos();
            for (FaceInfo faceInfo : faceInfos) {
                allFaceImageInfos.addAll(faceInfo.getFaceImageInfos());
            }

            if (!allFaceImageInfos.isEmpty()) {
                FaceImageInfo faceImageInfo = allFaceImageInfos.iterator().next();

                int imageLength = faceImageInfo.getImageLength();
                DataInputStream dataInputStream = new DataInputStream(faceImageInfo.getImageInputStream());
                byte[] imgData = new byte[imageLength];
                dataInputStream.readFully(imgData, 0, imageLength);

                Bitmap faceBitmap = JJ2000Frontend.decode(imgData);

                if (faceBitmap != null) {
                    Log.d(TAG, "Face Img w: " + faceBitmap.getWidth() + "; h: " + faceBitmap.getHeight());
                    res.setFaceBitmap(faceBitmap);
                } else {
                    faceBitmap = BitmapFactory.decodeByteArray(imgData, 0, imgData.length);
                    if (faceBitmap != null) {
                        Log.d(TAG, "Face Img w: " + faceBitmap.getWidth() + "; h: " + faceBitmap.getHeight());
                        res.setFaceBitmap(faceBitmap);
                    }
                }

                publishProgress(makeTaskProgress(fid, true, null));

                return res;

            }

        } catch (Exception e) {
            String errorMessage = "Exception reading file " + Integer.toHexString(fid) + ": \n"
                    + e.getClass().getSimpleName() + "\n" + e.getMessage() + "\n";
            e.printStackTrace();

            publishProgress(makeTaskProgress(fid, false, "Error! " + e.getMessage()));
        }
        return null;

    }

    @Override
    protected void onProgressUpdate(MRTDReaderTaskProgress... values) {
        if (listener != null && values[0] != null){
            listener.onProgress(values[0]);
        }
    }

    @Override
    protected void onCancelled(MRTDConnectionResult mrtdConnectionResult) {
        if (listener != null){
            listener.onCancelled();
        }
    }

    @Override
    protected void onPostExecute(MRTDConnectionResult mrtdConnectionResult) {
        if (listener != null){
            listener.onCompleted(mrtdConnectionResult);
        }
    }
//
//    private MRTDConnectionResult getPassInfos(Passport passport) {
//
//
//        List<Short> fileList = passport.getFileList();
//        for (short fid : fileList) {
//
//            if (isCancelled()) {
//                Log.d(TAG, "task was cancelled. exiting...");
//                break;
//            }
//
//            try {
//                InputStream in = passport.getInputStream(fid);
//                if (in == null) {
//                    Log.w(TAG, "Got null inputstream while trying to display " + Integer.toHexString(fid & 0xFFFF));
//                }
//                switch (fid) {
//                    case PassportService.EF_COM:
//                        /* NOTE: Already processed this one. */
//                        break;
//                    case PassportService.EF_DG1:
//                        InputStream dg1In = passport.getInputStream(PassportService.EF_DG1);
//                        DG1File dg1 = new DG1File(dg1In);
//                        MRZInfo mrzInfo = dg1.getMRZInfo();
//
//                        res.setMRZInfo(mrzInfo);
//                        break;
//                    case PassportService.EF_DG2:
//                        DG2File dg2 = new DG2File(in);
//                        int biometricTemplateCount = dg2.getBiometricTemplateCount();
//
//                        Log.d(TAG, "DG2: template Count:" + biometricTemplateCount);
//
//                        for(FaceInfo fi : dg2.getBiometricTemplates()){
//                            //fi.getImage();
//                            Log.d(TAG, fi.toString());
//                        }
//
//                        Integer totalInt = passport.getFileLength(fid);
//                        if (totalInt != null) {
//                            float total = totalInt.floatValue();
//                            float totalRead = 0f;
//                            byte[] buffer = new byte[1024];
//                            int read;
//                            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
//                            while ((read = in.read(buffer, 0, buffer.length)) > 0) {
//                                outputStream.write(buffer, 0, read);
//                                totalRead += (float) read;
//                                publishProgress(makeTaskProgress(fid, true, null, totalRead / total));
//                            }
//                            byte[] imgData = outputStream.toByteArray();
//                            outputStream.close();
//                            Bitmap faceBitmap = JJ2000Frontend.decode(imgData);
//
//                            if (faceBitmap != null) {
//                                Log.d(TAG, "Face Img w: " + faceBitmap.getWidth() + "; h: " + faceBitmap.getHeight());
//                                res.setFaceBitmap(faceBitmap);
//                            } else {
//                                faceBitmap = BitmapFactory.decodeByteArray(imgData, 0, imgData.length);
//                                if (faceBitmap != null) {
//                                    Log.d(TAG, "Face Img w: " + faceBitmap.getWidth() + "; h: " + faceBitmap.getHeight());
//                                    res.setFaceBitmap(faceBitmap);
//                                }
//                            }
//                        } else {
//                            publishProgress(makeTaskProgress(fid, false, "Failed to read image"));
//                        }
//                        break;
//                    case PassportService.EF_DG3:
//                        DG3File dg3 = new DG3File(in);
//                        //					if (eacEvent == null || !eacEvent.isSuccess())
//                        //					{
//                        //						Log.w(TAG,"Starting to read DG3, but eacEvent = " + eacEvent);
//                        //					}
//                        List<FingerInfo> fingers = dg3.getBiometricTemplates();
//                        for (FingerInfo finger : fingers) {
//                            //						displayPreviewPanel.addDisplayedImage(finger, isProgressiveMode);
//                        }
//                        break;
//                    case PassportService.EF_DG4:
//                        DG4File dg4 = new DG4File(in);
//                        break;
//                    case PassportService.EF_DG5:
//                        DG5File dg5 = new DG5File(in);
//                        break;
//                    case PassportService.EF_DG6:
//                        DG6File dg6 = new DG6File(in);
//                        break;
//                    case PassportService.EF_DG7:
//                        DG7File dg7 = new DG7File(in);
////					List<DisplayedImageInfo> infos = dg7.getImages();
////					for (DisplayedImageInfo info: infos) { displayPreviewPanel.addDisplayedImage(info, isProgressiveMode); }
//                        break;
//                    case PassportService.EF_DG11:
//                        DG11File dg11 = new DG11File(in);
//                        break;
//                    case PassportService.EF_DG12:
//                        DG12File dg12 = new DG12File(in);
//                        break;
//                    case PassportService.EF_DG14:
//                        DG14File dg14 = new DG14File(in);
//                        break;
//                    case PassportService.EF_DG15:
//                        DG15File dg15 = new DG15File(in);
//                        break;
//                    case PassportService.EF_SOD:
//                    /* NOTE: Already processed this one above. */
//                        break;
//                    case PassportService.EF_CVCA:
//                        CVCAFile cvca = new CVCAFile(in);
//                        break;
//                    default:
//                        String message = "Displaying of file " + Integer.toHexString(fid) + " not supported!";
//                        if ((fid & 0x010F) == fid) {
//                            int tag = PassportFile.lookupTagByFID(fid);
//                            int dgNumber = PassportFile.lookupDataGroupNumberByTag(tag);
//                            message = "Displaying of DG" + dgNumber + " not supported!";
//                        }
//                }
//
//                publishProgress(makeTaskProgress(fid, true, null));
//
//            } catch (TagLostException tle) {
//                publishProgress(makeTaskProgress(fid, false, "Unable to read from the document's NFC tag"));
//
//            } catch (Exception ioe) {
//                String errorMessage = "Exception reading file " + Integer.toHexString(fid) + ": \n"
//                        + ioe.getClass().getSimpleName() + "\n" + ioe.getMessage() + "\n";
//                ioe.printStackTrace();
//
//                publishProgress(makeTaskProgress(fid, false, "Error! " + ioe.getMessage()));
//                continue;
//            }
//
//        }
//
//        return res;
//    }


}