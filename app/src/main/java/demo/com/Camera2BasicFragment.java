package demo.com;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import demo.com.util.AutoFitTextureView;
import demo.com.util.CompareSizesByArea;
import demo.com.util.ImageSaver;
import demo.com.util.MLog;

/**
 * 相机
 */

public class Camera2BasicFragment extends Fragment implements View.OnClickListener {
    private Button btn;

    //==========================================常量===================================================

    /**
     * 图片旋转方向数组
     */
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private static final String TAG = "Camera2BasicFragment";

    /**
     * 相机状态：展示相机预览
     */
    private static final int STATE_PREVIEW = 0;

    /**
     * 相机状态： 等待焦点被锁定.
     */
    private static final int STATE_WAITING_LOCK = 1;

    /**
     * 相机状态: Waiting for the exposure to be precapture state.
     */
    private static final int STATE_WAITING_PRECAPTURE = 2;

    /**
     * 相机状态: Waiting for the exposure state to be something other than precapture.
     */
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;

    /**
     * 相机状态:Picture was taken.
     */
    private static final int STATE_PICTURE_TAKEN = 4;

    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_WIDTH = 1080;

    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_HEIGHT = 1920;


    //==========================================变量===================================================
    public File mFile;
    /**
     * 相机id
     */
    private String mCameraId;
    /**
     * 相机预览使用的布局
     */
    private AutoFitTextureView mTextureView;

    /**
     * 相机流程控制类
     */
    private CameraCaptureSession mCaptureSession;

    /**
     * 相机类
     */
    private CameraDevice mCameraDevice;
    /**
     * 相机参数类
     */

    private CaptureRequest mPreviewRequest;
    private CaptureRequest.Builder mPreviewRequestBuilder;

    /**
     * 相机的当前状态
     */
    private int mState = STATE_PREVIEW;

    /**
     * The {@link Size} of camera preview.
     */
    private Size mPreviewSize;


    /**
     * 处理静态图片拍照
     */

    private ImageReader mImageReader;

    /**
     * 并发控制
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    /**
     * 相机是否支持 flash
     */
    private boolean mFlashSupported;

    /**
     * 相机的方向
     */
    private int mSensorOrientation;

    /**
     * 子线程
     */
    private HandlerThread mBackgroundThread;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mBackgroundHandler;


    //==========================================含重写的变量（监听等内部类变量）===================================================

    /**
     * This a callback object for the ImageReader. "onImageAvailable" will be called when a still image is ready to be saved.
     * ImageReader类的回调，当拍照完成时，会生成ImageReader类，就会回调此类，一般用于保存操作
     */
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            //保存图片
            mBackgroundHandler.post(new ImageSaver(getActivity(), reader.acquireNextImage(), mFile));
        }

    };

    /**
     * 布局视图的监听，用户处理视图生命周期的事件
     */
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    /**
     * 摄像头切换的回调
     */
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        //相机打开就执行该方法，用于预览
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCameraOpenCloseLock.release();
            mCameraDevice = camera;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            mCameraOpenCloseLock.release();
            camera.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            mCameraOpenCloseLock.release();
            camera.close();
            mCameraDevice = null;
            Activity activity = getActivity();
            if (activity != null) {
                activity.finish();
            }


        }
    };
    /**
     * 拍照回调
     * 多个回调重写，只需要俩
     */
    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            process(result);
        }


        /**
         * 根据回调 设置result
         *
         * CONTROL_AF_STATE 自动对焦算法的当前状态
         * CONTROL_AE_STATE 自动曝光算法的当前状态
         * CONTROL_AF_STATE_FOCUSED_LOCKED  AF认为它是正确的并且锁定了焦点
         * CONTROL_AF_STATE_NOT_FOCUSED_LOCKED AF未能成功地集中注意力，并且锁定了焦点。
         * CONTROL_AE_STATE_CONVERGED AE对当前场景有很好的控制值。
         * CONTROL_AE_STATE_PRECAPTURE AE被要求做一个预捕获序列，目前正在执行它。
         * CONTROL_AE_STATE_FLASH_REQUIRED AE有一组很好的控制值，但是flash需要被触发，因为它的质量仍然很好。
         *
         * @param result
         */
        private void process(CaptureResult result) {
            switch (mState) {
                case STATE_PREVIEW:
                    //预览状态下的操作
                    break;
                case STATE_WAITING_LOCK: {
                    //某些设备上CONTROL_AF_STATE不存在
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);

                    if (afState == null) {
                        captureStillPicture();
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (aeState == null ||
                                aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {

                            mState = STATE_PICTURE_TAKEN;
                            captureStillPicture();
                        } else {
                            runPrecaptureSequence();
                        }

                    }
                    break;
                }
                case STATE_WAITING_PRECAPTURE: {

                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mState = STATE_WAITING_NON_PRECAPTURE;
                    }
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null ||
                            aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        mState = STATE_PICTURE_TAKEN;
                        captureStillPicture();
                    }
                    break;
                }
            }
        }
    };

    //==========================================frg重写===================================================

    /**
     * Shows a {@link Toast} on the UI thread.
     *
     * @param text The message to show
     */
    private void showToast(final String text) {
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    /**
     * @return
     */
    public static Camera2BasicFragment newInstance() {
        return new Camera2BasicFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_camera2, container, false);

    }

    //初始化控件
    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        btn = view.findViewById(R.id.picture);
        btn.setOnClickListener(this);
        mTextureView = (AutoFitTextureView) view.findViewById(R.id.texture);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mFile = new File(getActivity().getExternalFilesDir(null), "pic.jpg");
    }

    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();
        /**
         *当屏幕关闭再打开，SurfaceTexture已经存在并可用，onSurfaceTextureAvailable不会被调用。
         * 这时，我们可以打开相机从这里预览，否则，我们需要在监听mSurfaceTextureListener中一直等待surface准备好
         */
        if (mTextureView.isAvailable()) {
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    public void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }


    /**
     * 创建相机流程控制类
     */
    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;//断言
            //设置图片流的尺寸
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            //输出使用的Surface
            Surface surface = new Surface(texture);

            //参数类设置
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);

            //创建流程类
            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        //当摄像机设备完成配置时，这个方法就会被调用，session可以开始处理捕获请求。
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            // 关闭
                            if (null == mCameraDevice) {
                                return;
                            }
                            // session已经准备好，可以开始预览
                            mCaptureSession = session;

                            //参数类设置特定属性
                            try {
                                //设置自动对焦
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE
                                        , CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

                                //设置flash
                                if (mFlashSupported) {
                                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                                            CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                                }

                                //启动预览
                                mPreviewRequest = mPreviewRequestBuilder.build();

                                //流程类 集成
                                mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback, mBackgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        //如果session不能按照请求配置，则调用此方法。
                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            showToast("启动相机失败，CameraCaptureSession的配置失败");
                        }
                    },
                    null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }


    }

    /**
     * 设置相机的成员变量
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    @SuppressWarnings("SuspiciousNameCombination")
    private void setUpCameraOutputs(int width, int height) {
        Activity activity = getActivity();
        //获取相机管理
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {

                //通过相机管理 获取 相机属性
                CameraCharacteristics cameraCharacteristics = manager.getCameraCharacteristics(cameraId);

                // 不用前置摄像头演示demo
                Integer facing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    //不使用前置
                    continue;
                }

                //获取配置属性
                StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }
                //相机拍照，选择最大的Size
                Size largestSize = Collections.max(
                        Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new CompareSizesByArea());

                //初始化指定的ImageReader
                mImageReader = ImageReader.newInstance(largestSize.getWidth(),
                        largestSize.getHeight(),
                        ImageFormat.JPEG,
                        2);

                //设置回调监听
                mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);

                //找出我们是否需要交换尺寸来获得相对于传感器的预览尺寸
                int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
                MLog.d("根据手机方向获取的角度displayRotation：", displayRotation);
                mSensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                MLog.d("默认的角度mSensorOrientation：", mSensorOrientation);
                boolean isSwapDimension = false;//交换尺寸标记
                // TODO: 2017/11/30 最好换成范围，而不是固定的四个值
                switch (displayRotation) {
                    case Surface.ROTATION_0:
                    case Surface.ROTATION_180:
                        if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                            isSwapDimension = true;
                        }
                        break;
                    case Surface.ROTATION_90:
                    case Surface.ROTATION_270:
                        if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                            isSwapDimension = true;
                        }
                        break;
                    default:
                        MLog.d("代码无法解决的角度displayRotation=" + displayRotation);
                        break;
                }

                //根据旋转角度 做修正
                Point displaySize = new Point();
                activity.getWindowManager().getDefaultDisplay().getSize(displaySize);

                int rotatedPreviewWidth = width;
                int rotatedPreviewHeight = height;
                int maxPreviewWidth = displaySize.x;//最大预览尺寸
                int maxPreviewHeight = displaySize.y;

                if (isSwapDimension) {
                    rotatedPreviewWidth = height;
                    rotatedPreviewHeight = width;
                    maxPreviewWidth = displaySize.y;//最大预览尺寸
                    maxPreviewHeight = displaySize.x;
                }

                //选默认设置的尺寸
                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH;
                }

                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT;
                }

                //尝试使用太大的预览尺寸可能会超过摄像头总线的带宽限制
                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        rotatedPreviewWidth,
                        rotatedPreviewHeight,
                        maxPreviewWidth,
                        maxPreviewHeight,
                        largestSize
                );

                //将选择的预览的Size和视图的比率相适应
                int orientation = getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
                } else {
                    mTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
                }

                //是否支持flash
                Boolean isFlashSupport = cameraCharacteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                mFlashSupported = isFlashSupport == null ? false : isFlashSupport;

                //获取当前的相机id
                mCameraId = cameraId;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            e.printStackTrace();
            showToast(e.toString());
        }
    }

    /**
     * 打开相机
     */
    private void openCamera(int width, int height) {
        if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            MLog.d("之前一个界面已经处理权限，此处不会出现");
            return;
        }
        setUpCameraOutputs(width, height);
        configureTransform(width, height);
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }

            //打开相机
            manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    /**
     * 关闭相机
     */
    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            if (null != mCaptureSession) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mImageReader) {
                mImageReader.close();
                mImageReader = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    /**
     * 为TextureView配置必要的Matrix,这个方法应该在摄像机预览size确定且TextureView的尺寸修复后被调用
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = getActivity();
        if (mTextureView == null || mPreviewSize == null || activity == null) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    /**
     * 如果有一个摄像头所支持Size的选择，那么选择最小的一个
     * 至少和纹理视图的Size一样大，最多也就是这个Size
     * 各自的最大Size，以及其方面的比率与指定的值相匹配。如果这样的Size
     * 不存在，选择最大的最大值和最大的最大值，
     * 它的纵横比与指定的值相匹配。
     *
     * @param choices           The list of sizes that the camera supports for the intended output class
     * @param textureViewWidth  The width of the texture view relative to sensor coordinate
     * @param textureViewHeight The height of the texture view relative to sensor coordinate
     * @param maxWidth          The maximum width that can be chosen
     * @param maxHeight         The maximum height that can be chosen
     * @param aspectRatio       The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
                                          int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        //选择最大中的最小的Size,如果没有，就选择不是最大中的最大Size
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    /**
     * 启动一个静态图像捕获。
     */
    private void takePicture() {

        lockFocus();
    }

    /**
     * 将焦点锁定为静态图像捕获的第一步。
     */
    private void lockFocus() {
        try {
            //设置自动对焦
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START);
            //告诉回调mCaptureCallback，等待锁定
            mState = STATE_WAITING_LOCK;
            //
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取静态照片，当从Capturecallback或者lockFucs中获取响应时，需要调用这个方法
     * TEMPLATE_STILL_CAPTURE 创建一个适合于静态图像捕获的请求。
     */
    private void captureStillPicture() {
        final Activity activity = getActivity();
        if (null == activity || mCameraDevice == null) {
            return;
        }
        try {
            final CaptureRequest.Builder captrueRequestBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);

            //设置自动对焦
            captrueRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE
                    , CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            //设置flash
            if (mFlashSupported) {
                captrueRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            }

            int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();

            //设置图片方向
            captrueRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));
            CameraCaptureSession.CaptureCallback captureCallback =
                    new CameraCaptureSession.CaptureCallback() {
                        //拍照完成操作
                        @Override
                        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                            Toast.makeText(getActivity(), "保存路径：" + (getActivity().getExternalFilesDir(null) + "/pic.jpg").toString(), Toast.LENGTH_SHORT).show();
                            unlockFocus();
                        }
                    };

            mCaptureSession.stopRepeating();//取消继续捕捉
            mCaptureSession.abortCaptures();//丢弃当前正在等待和正在进行中的所有捕获，并尽可能快地完成。
            mCaptureSession.capture(captrueRequestBuilder.build(), captureCallback, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    /**
     * 解锁焦距，拍照完成后执行该方法
     */

    private void unlockFocus() {
        try {
            //重新设置自动对焦的触发
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);

            //设置自动对焦
            setAutoFlash(mPreviewRequestBuilder);

            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);

            // 拍照后，重新设置成预览状态
            mState = STATE_PREVIEW;
            //
            mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback, mBackgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setAutoFlash(CaptureRequest.Builder requestBuilder) {
        if (mFlashSupported) {
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        }
    }
    /**
     * 从指定的屏幕旋转中检索JPEG朝向。
     *
     * @param rotation
     */
    private int getOrientation(int rotation) {
        /**
         *
         *   Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
         We have to take that into account and rotate JPEG properly.
         For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
         For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
         对于大多数设备来说，传感器的定位是90度，对于某些设备来说是270度(例如。Nexus5 x)
         我们必须把它考虑进去，并正确地旋转JPEG。
         对于有90度的设备，我们只是简单地从方向返回我们的映射。
         对于270度的设备，我们需要旋转JPEG 180度。
         */
        return (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;

    }

    /**
     * 运行预捕捉序列 抓捕静态图片，当从lockFoucs()方法获取在mCaptureCallback的响应，应该调用该方法
     */
    private void runPrecaptureSequence() {
        try {
            //重新设置自动对焦的触发
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            //            //设置flash
            //            if (mFlashSupported) {
            //                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
            //                        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            //            }
            mState = STATE_WAITING_PRECAPTURE;

            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.picture:
                Toast.makeText(getActivity(), "拍照", Toast.LENGTH_SHORT).show();
                takePicture();
                break;
        }

    }


}
