package com.appliedrec.mrtdreader;

import static org.jmrtd.PassportService.DEFAULT_MAX_BLOCKSIZE;
import static org.jmrtd.PassportService.NORMAL_MAX_TRANCEIVE_LENGTH;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.nfc.tech.IsoDep;

import net.sf.scuba.smartcards.CardFileInputStream;
import net.sf.scuba.smartcards.CardService;

import org.jmrtd.BACKey;
import org.jmrtd.PassportService;
import org.jmrtd.lds.CardSecurityFile;
import org.jmrtd.lds.LDSFileUtil;
import org.jmrtd.lds.PACEInfo;
import org.jmrtd.lds.SODFile;
import org.jmrtd.lds.SecurityInfo;
import org.jmrtd.lds.icao.COMFile;
import org.jmrtd.lds.icao.DG1File;
import org.jmrtd.lds.icao.DG2File;
import org.jmrtd.lds.icao.MRZInfo;
import org.jmrtd.lds.iso19794.FaceImageInfo;
import org.jmrtd.lds.iso19794.FaceInfo;
import org.spongycastle.jce.provider.BouncyCastleProvider;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.reactivex.rxjava3.core.Observable;
import jj2000.JJ2000Frontend;

public class MRTDReader {

    @FunctionalInterface
    interface Transformer<T, R> {
        R apply(T t) throws Exception;
    }

    private static <T> T readFile(PassportService passportService, short fid) throws Exception {
        try (CardFileInputStream inputStream = passportService.getInputStream(fid)) {
            return (T) LDSFileUtil.getLDSFile(fid, inputStream);
        }
    }

    public static Observable<MRTDReaderProgress> createProgressObservable(IsoDep isoDep, BACSpec bacSpec) {
        BACKey bacKey = new BACKey(bacSpec.getDocumentNumber(), bacSpec.getDateOfBirth(), bacSpec.getDateOfExpiry());
        return Observable.create(emitter -> {
            short fid = PassportService.EF_COM;
            CardService cardService = null;
            PassportService service = null;
            try {

                Security.insertProviderAt(new BouncyCastleProvider(), 1);
                Security.addProvider(new SecurityProvider("MRTDSecurityProvider", 1, "null"));

                cardService = CardService.getInstance(isoDep);
                cardService.open();

                service = new PassportService(cardService, NORMAL_MAX_TRANCEIVE_LENGTH, DEFAULT_MAX_BLOCKSIZE, true, false);
                service.open();

                boolean paceSucceeded = false;
                try (InputStream cardServiceInputStream = service.getInputStream(PassportService.EF_CARD_SECURITY, DEFAULT_MAX_BLOCKSIZE)) {
                    CardSecurityFile cardAccessFile = new CardSecurityFile(cardServiceInputStream);
                    Collection<SecurityInfo> securityInfos = cardAccessFile.getSecurityInfos();
                    for (SecurityInfo securityInfo : securityInfos) {
                        if (securityInfo instanceof PACEInfo) {
                            PACEInfo paceInfo = (PACEInfo)securityInfo;
                            service.doPACE(bacKey, paceInfo.getObjectIdentifier(), PACEInfo.toParameterSpec(paceInfo.getParameterId()), null);
                            paceSucceeded = true;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                service.sendSelectApplet(paceSucceeded);

                if (!paceSucceeded) {
                    try (InputStream inputStream = service.getInputStream(PassportService.EF_COM, DEFAULT_MAX_BLOCKSIZE)) {
                        inputStream.read();
                    } catch (Exception e) {
                        service.doBAC(bacKey);
                    }
                }

                MRTDReaderProgress progress = new MRTDReaderProgress();

                COMFile comFile = readFile(service, fid);
                progress.setFileId(fid);
                progress.setProgress(0.25);
                if (!emitter.isDisposed()) {
                    emitter.onNext(progress);
                }

                fid = PassportService.EF_SOD;
                SODFile sodFile = readFile(service, fid);
                progress.setFileId(fid);
                progress.setProgress(0.5);
                if (!emitter.isDisposed()) {
                    emitter.onNext(progress);
                }

                fid = PassportService.EF_DG1;
                DG1File dg1File = readFile(service, fid);
                MRZInfo mrzInfo = dg1File.getMRZInfo();
                progress.getResult().setDateOfBirth(mrzInfo.getDateOfBirth());
                progress.getResult().setDateOfExpiry(mrzInfo.getDateOfExpiry());
                progress.getResult().setDocumentNumber(mrzInfo.getDocumentNumber());
                progress.getResult().setDocumentCode(mrzInfo.getDocumentCode());
                progress.getResult().setGender(mrzInfo.getGender().name());
                progress.getResult().setIssuingState(mrzInfo.getIssuingState());
                progress.getResult().setNationality(mrzInfo.getNationality());
                progress.getResult().setPersonalNumber(mrzInfo.getPersonalNumber());
                progress.getResult().setPrimaryIdentifier(mrzInfo.getPrimaryIdentifier());
                progress.getResult().setSecondaryIdentifiers(mrzInfo.getSecondaryIdentifierComponents());
                progress.setFileId(fid);
                progress.setProgress(0.75);
                if (!emitter.isDisposed()) {
                    emitter.onNext(progress);
                }

                fid = PassportService.EF_DG2;
                DG2File dg2File = readFile(service, fid);
                progress.setFileId(fid);
                if (!emitter.isDisposed()) {
                    emitter.onNext(progress);
                }

                List<FaceImageInfo> allFaceImageInfos = new ArrayList<>();
                List<FaceInfo> faceInfos = dg2File.getFaceInfos();
                for (FaceInfo faceInfo : faceInfos) {
                    allFaceImageInfos.addAll(faceInfo.getFaceImageInfos());
                }

                if (!allFaceImageInfos.isEmpty()) {
                    FaceImageInfo faceImageInfo = allFaceImageInfos.iterator().next();

                    int imageLength = faceImageInfo.getImageLength();
                    try (InputStream inputStream = faceImageInfo.getImageInputStream()) {
                        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                            int read;
                            byte[] buffer = new byte[128];
                            int totalRead = 0;
                            while ((read = inputStream.read(buffer, 0, buffer.length)) > 0) {
                                outputStream.write(buffer, 0, read);
                                totalRead += read;
                                if (!emitter.isDisposed()) {
                                    progress.setProgress(0.75 + (double)totalRead / (double)imageLength * 0.25);
                                    emitter.onNext(progress);
                                }
                            }
                            outputStream.flush();
                            byte[] imageData = outputStream.toByteArray();
                            Bitmap faceBitmap = JJ2000Frontend.decode(imageData);

                            if (faceBitmap != null) {
                                progress.getResult().setFaceImage(faceBitmap);
                            } else {
                                faceBitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
                                if (faceBitmap != null) {
                                    progress.getResult().setFaceImage(faceBitmap);
                                }
                            }
                        }
                    }
//                    DataInputStream dataInputStream = new DataInputStream(faceImageInfo.getImageInputStream());
//                    byte[] imgData = new byte[imageLength];
//                    dataInputStream.readFully(imgData, 0, imageLength);
//
//                    Bitmap faceBitmap = JJ2000Frontend.decode(imgData);
//
//                    if (faceBitmap != null) {
//                        progress.getResult().setFaceImage(faceBitmap);
//                    } else {
//                        faceBitmap = BitmapFactory.decodeByteArray(imgData, 0, imgData.length);
//                        if (faceBitmap != null) {
//                            progress.getResult().setFaceImage(faceBitmap);
//                        }
//                    }
                }
                if (!emitter.isDisposed()) {
                    progress.setProgress(1.0);
                    emitter.onNext(progress);
                    emitter.onComplete();
                }
            } catch (Exception e) {
                if (!emitter.isDisposed()) {
                    emitter.onError(e);
                }
            } finally {
                if (service != null) {
                    service.close();
                }
                if (cardService != null) {
                    cardService.close();
                }
            }
        });
    }

//    public static BiFunction<MRTDScanResult, ? super MRTDReaderProgress, MRTDScanResult> createResultBiFunction() {
//        return (result, progress) -> {
//            Object file = progress.getFile();
//            if (file instanceof DG1File) {
//                MRZInfo mrzInfo = ((DG1File)file).getMRZInfo();
//                result.setDateOfBirth(mrzInfo.getDateOfBirth());
//                result.setDateOfExpiry(mrzInfo.getDateOfExpiry());
//                result.setDocumentNumber(mrzInfo.getDocumentNumber());
//                result.setDocumentCode(mrzInfo.getDocumentCode());
//                result.setGender(mrzInfo.getGender().name());
//                result.setIssuingState(mrzInfo.getIssuingState());
//                result.setNationality(mrzInfo.getNationality());
//                result.setPersonalNumber(mrzInfo.getPersonalNumber());
//                result.setPrimaryIdentifier(mrzInfo.getPrimaryIdentifier());
//                result.setSecondaryIdentifiers(mrzInfo.getSecondaryIdentifierComponents());
//            } else if (file instanceof DG2File) {
//                List<FaceImageInfo> allFaceImageInfos = new ArrayList<>();
//                List<FaceInfo> faceInfos = ((DG2File)file).getFaceInfos();
//                for (FaceInfo faceInfo : faceInfos) {
//                    allFaceImageInfos.addAll(faceInfo.getFaceImageInfos());
//                }
//
//                if (!allFaceImageInfos.isEmpty()) {
//                    FaceImageInfo faceImageInfo = allFaceImageInfos.iterator().next();
//
//                    int imageLength = faceImageInfo.getImageLength();
//                    DataInputStream dataInputStream = new DataInputStream(faceImageInfo.getImageInputStream());
//                    byte[] imgData = new byte[imageLength];
//                    dataInputStream.readFully(imgData, 0, imageLength);
//
//                    Bitmap faceBitmap = JJ2000Frontend.decode(imgData);
//
//                    if (faceBitmap != null) {
//                        result.setFaceImage(faceBitmap);
//                    } else {
//                        faceBitmap = BitmapFactory.decodeByteArray(imgData, 0, imgData.length);
//                        if (faceBitmap != null) {
//                            result.setFaceImage(faceBitmap);
//                        }
//                    }
//                }
//            }
//            return result;
//        };
//    }
}
