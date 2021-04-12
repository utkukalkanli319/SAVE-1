package CameraApp;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;


public class FrontCameraService extends Service {
    private static final String TAG = "FRONT-CAMERA SERVICE";
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private static final int REQUEST_PERMISSION = 200;
    public IBinder mBinder = new FrontCameraService.LocalBinder();

    // by elif
    private String frontCameraID;
    private String backCameraID;
    private ImageReader frontImageReader;
    private ImageReader backImageReader;
    private CameraDevice frontCameraDevice;
    private CameraDevice backCameraDevice;
    private CameraCaptureSession frontCameraCaptureSession;
    private CameraCaptureSession backCameraCaptureSession;

    private WindowManager windowManager;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private Handler frontHandler;
    private HandlerThread frontThread;
    private int frontCounter = 0;
    private int backCounter = 0;
    CaptureRequest.Builder mPreviewRequestBuilder;
    static Bitmap picture;
    public static byte[][] yuvBytes = new byte[3][];

    //public static BlockingQueue<Bitmap> queue = new ArrayBlockingQueue<Bitmap>(10);
    public static BlockingQueue<byte[]> queue = new ArrayBlockingQueue<byte[]>(10);


    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
    public class LocalBinder extends Binder {
        public FrontCameraService getServerInstance(){return FrontCameraService.this;}
    }
    @Override
    public void onCreate() {
        Toast.makeText(getApplicationContext(),TAG + " onCreate", Toast.LENGTH_SHORT).show();

        super.onCreate();
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, " onDestroy...");
        super.onDestroy();
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, " onStartCommand...");
        Thread frontThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    producer();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        frontThread.start();
        setCamera();
        openCamera();

        return super.onStartCommand(intent, flags, startId);
    }

    private void setCamera() {
        Log.i("camera", "set camera icindeyiz");
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraID : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraID);
                // getting two cameras here
                if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    this.frontCameraID = cameraID;
                } else {
                    continue;
                }
            }
            // WARNING - THIS PART SHOULD CHANGE, MUST NOT BE CONSTANT?
            int pictureWidth = 640;
            int pictureHeight = 480;
            // END OF THE WARNING

            frontImageReader = ImageReader.newInstance(pictureWidth, pictureHeight, ImageFormat.JPEG, 10);
            frontImageReader.setOnImageAvailableListener(onImageAvailableListener, frontHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        if ( frontCameraID == null ){ Log.e("camera", "cannot find camera"); }
        else
            Log.i("camera", "set camera success");

    }

    private static void producer() throws InterruptedException {
        Random random = new Random();
        while (true){
            Thread.sleep(500);
           // queue.put(picture);
            Log.i(TAG, "Inserting value: " + "front picture" + "; Queue size is: " + queue.size());
        }
    }


    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            manager.openCamera(frontCameraID, frontCameraStateCallback, frontHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    private final CameraDevice.StateCallback frontCameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice device) {
            frontCameraDevice = device;
            createCaptureSession();
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
        }

        @Override
        public void onError(CameraDevice cameraDevice, int error) {
        }
    };

    private final ImageReader.OnImageAvailableListener onImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = frontImageReader.acquireLatestImage();
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            try {
                queue.put(bytes);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            // yuvBytes[0] = bytes;




            /*
           // ByteBuffer buffer2 = ByteBuffer.wrap(bytes);
            picture = Bitmap.createBitmap(1280, 960, Bitmap.Config.ARGB_8888);
            buffer.rewind();
            picture.copyPixelsFromBuffer(buffer);
            try {
                queue.put(picture);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            /*
            final Image.Plane[] planes = image.getPlanes();
            final ByteBuffer buffer = planes[0].getBuffer();

            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * image.getWidth();
            // create bitmap
            picture = Bitmap.createBitmap(image.getWidth() + rowPadding / pixelStride, image.getHeight(), Bitmap.Config.ARGB_8888);
            picture.copyPixelsFromBuffer(buffer);
            try {
                queue.put(picture);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            image.close();
            // this part for saving to local
            /*
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            String fname = getExternalFilesDir(Environment.DIRECTORY_PICTURES) + "/pic" + frontCameraID + "_" + frontCounter + ".jpg";
            Log.d(TAG, "Saving:" + fname);
            File file = new File(fname);
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            try {
                //save(bytes, file); // save image here
                OutputStream output = null;
                output = new FileOutputStream(file);
                output.write(bytes);
                frontCounter++;

            } catch (IOException e) {
                e.printStackTrace();
            }
            image.close();
            // frontImageReader.close();
             */
        }
    };

    private void createCaptureSession() {
        List<Surface> outputSurfaces = new LinkedList<>();
        outputSurfaces.add(frontImageReader.getSurface());

        try {

            frontCameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    frontCameraCaptureSession = session;
                    createCaptureRequest();
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                }
            }, frontHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    private void createCaptureRequest() {
        try {

            CaptureRequest.Builder requestBuilder = frontCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            requestBuilder.addTarget(frontImageReader.getSurface());

            // Focus
            requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            // Orientation
           // int rotation = windowManager.getDefaultDisplay().getRotation();
            //int rotation = getWindowManager().getDefaultDisplay().getRotation();
           // int rotation = 270;
            requestBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(270));

            mFrontCaptureCallback.setState(PictureCaptureCallback.STATE_LOCKING);
            frontCameraCaptureSession.capture(requestBuilder.build(), mFrontCaptureCallback, null);
          //  frontCameraCaptureSession.setRepeatingRequest(requestBuilder.build(), mFrontCaptureCallback, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    PictureCaptureCallback mFrontCaptureCallback = new PictureCaptureCallback() {

        @Override
        public void onPrecaptureRequired() {
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            setState(STATE_PRECAPTURE);
            try {
                frontCameraCaptureSession.capture(mPreviewRequestBuilder.build(), this, frontHandler);
               // frontCameraCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), this, frontHandler);
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                        CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);
            } catch (CameraAccessException e) {
                Log.e(TAG, "Failed to run precapture sequence.", e);
            }
        }

        @Override
        public void onReady() {
            //captureStillPicture();
        }

    };
}

