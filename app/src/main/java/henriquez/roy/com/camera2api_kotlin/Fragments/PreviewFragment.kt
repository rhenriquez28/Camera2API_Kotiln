package henriquez.roy.com.camera2api_kotlin.Fragments

import android.Manifest
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.MediaRecorder
import android.media.ThumbnailUtils
import android.os.*
import android.provider.MediaStore
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v4.graphics.drawable.RoundedBitmapDrawable
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory
import android.util.Log
import android.util.SparseIntArray
import android.view.*
import henriquez.roy.com.camera2api_kotlin.R
import kotlinx.android.synthetic.main.fragment_preview.*
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class PreviewFragment : Fragment() {

    //Equivalent to static in Java
    companion object {
        //ID for request code
        const val REQUEST_CAMERA_PERMISSION = 100
        //Tag recieves the qualified name of the class to display it
        private val TAG = PreviewFragment::class.qualifiedName
        /*Calls the constructor of the PreviewFragement
        and returns the object back to us*/
        @JvmStatic fun newInstance() = PreviewFragment()
        //Setting up the default orientations to calibrate the offsets of the camera
        private val SENSOR_DEFAULT_ORIENTATION_DEGREES = 90
        private val SENSOR_INVERSE_ORIENTATION_DEGREES = 270
        //Sparse int arrays for the different offsets
        private val DEFAULT_ORIENTATION = SparseIntArray().apply{
            //Apply all the functions of this object to the object itself
            append(Surface.ROTATION_0, 90)
            append(Surface.ROTATION_90, 0)
            append(Surface.ROTATION_180, 270)
            append(Surface.ROTATION_270, 180)
        }
            private val INVERSE_ORIENTATION = SparseIntArray().apply{
            //Apply all the functions of this object to the object itself
            append(Surface.ROTATION_0, 270)
            append(Surface.ROTATION_90, 180)
            append(Surface.ROTATION_180, 90)
            append(Surface.ROTATION_270, 0)
        }
    }

    //Attatches the fragment to the fragement_first_image layout and inflates it
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_preview, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        activity?.window?.decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }

    private val surfaceListener = object : TextureView.SurfaceTextureListener{
        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {

        }

        //Unit is Kotlin's default return type for a function
        override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) = Unit

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?) = true

        override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
            Log.d(TAG, "textureSurface width: $width height: $height")
            openCamera()
        }

    }

    private fun openCamera(){
        checkCameraPermission()
    }

    //Passing the permissions request to the Easy Permissions library
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    //Helper function to save us from going back
    // to the initial call of requesting permissions
    @AfterPermissionGranted(REQUEST_CAMERA_PERMISSION)
    private fun checkCameraPermission(){
        if (EasyPermissions.hasPermissions(activity!!, Manifest.permission.CAMERA)){
            Log.d(TAG, "App has camera permission")
            connectCamera()
        }else{
            EasyPermissions.requestPermissions(activity!!,
                    getString(R.string.camera_request_rationale),
                    REQUEST_CAMERA_PERMISSION,
                    Manifest.permission.CAMERA)
        }
    }

    //Simply, lazy creates an instance that performs initialization
    // at the first access to the property value, stores the result, and returns the stored value.
    // For reference: https://medium.com/til-kotlin/how-kotlins-delegated-properties-and-lazy-initialization-work-552cbad8be60
    private val cameraManager by lazy {
        activity?.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    //Helper function to support different camera "characteristics"
    private fun <T> cameraCharacteristics(cameraId: String, key: CameraCharacteristics.Key<T>) : T{
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        return when(key){
            CameraCharacteristics.LENS_FACING -> characteristics.get(key)
            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP -> characteristics.get(key)
            CameraCharacteristics.SENSOR_ORIENTATION -> characteristics.get(key)
            else -> throw IllegalArgumentException("Key not recognized")
        }
    }

    //Function that returns a specific camera ID to a front- or rear-facing lens
    private fun cameraId(lens: Int): String{
        var deviceId = listOf<String>()
        try {
            val cameraIdList = cameraManager.cameraIdList
            //"it" is each element of cameraIdList
            deviceId = cameraIdList.filter {
                lens == cameraCharacteristics(it, CameraCharacteristics.LENS_FACING)
            }
        }catch (e: CameraAccessException){
            Log.e(TAG, e.toString())
        }
        return deviceId.first()
    }

    private lateinit var cameraDevice: CameraDevice
    //Informs us whether or not we've a camera device object
    private val deviceStateCallback = object: CameraDevice.StateCallback(){
        override fun onOpened(camera: CameraDevice?) {
            Log.d(TAG, "camera device opened")
            if (camera != null){
                cameraDevice = camera
                previewSession()
            }
        }

        override fun onDisconnected(camera: CameraDevice?) {
            Log.d(TAG, "camera device closed")
            camera?.close()
        }

        override fun onError(camera: CameraDevice?, error: Int) {
            Log.d(TAG, "camera device error")
            this@PreviewFragment.activity?.finish()
        }

    }

    //Thread created and used so we can do this off the main UI thread
    private lateinit var backgroundThread: HandlerThread
    private lateinit var backgroundHandler: Handler

    private fun startBackgroundThread(){
        backgroundThread = HandlerThread("Camera 2 Kotlin").also { it.start() }
        backgroundHandler = Handler(backgroundThread.looper)
    }

    private fun stopBackgroundThread(){
        backgroundThread.quitSafely()
        try {
            backgroundThread.join()
        }catch (e: InterruptedException){
            Log.e(TAG, e.toString())
        }
    }

    //Connection to the camera device
    private fun connectCamera(){
        val deviceId = cameraId(CameraCharacteristics.LENS_FACING_BACK)
        Log.d(TAG, "deviceId: $deviceId")
        try {
            //It says error because of Android Studio
            // not being able to realize that we've actually set our permissions
            // using the Easy Permissions API
            cameraManager.openCamera(deviceId, deviceStateCallback, backgroundHandler)
        }catch (e: CameraAccessException){
            Log.e(TAG, e.toString())
        }catch (e: InterruptedException){
            //If a higher priority camera device or app interrupts the app, this error will be trown
            Log.e(TAG, "Open camera device interrupted while opening")
        }
    }


    //Hardcoding the width and height of the preview just for testing purposes
    private val MAX_PREVIEW_WIDTH = 1920
    private val MAX_PREVIEW_HEIGHT = 1080
    //Capture session is created first so we can then request a capture
    private lateinit var captureSession: CameraCaptureSession
    //Builder so we can extract the Builder objects first
    // before generating a Capture Request
    private lateinit var captureRequestBuilder: CaptureRequest.Builder


    private fun previewSession(){
        val surfaceTexture = previewTextureView.surfaceTexture
        surfaceTexture.setDefaultBufferSize(MAX_PREVIEW_WIDTH, MAX_PREVIEW_HEIGHT)
        val surface = Surface(surfaceTexture)

        captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        captureRequestBuilder.addTarget(surface)

        cameraDevice.createCaptureSession(Arrays.asList(surface),
                object : CameraCaptureSession.StateCallback(){
                    override fun onConfigureFailed(session: CameraCaptureSession?) {
                        Log.e(TAG, "creating capture session failed!")
                    }

                    override fun onConfigured(session: CameraCaptureSession?) {
                        if (session != null){
                            captureSession = session
                            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                            captureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null)
                        }
                    }

                }, backgroundHandler)
    }

    override fun onResume() {
        super.onResume()

        startBackgroundThread()
        if (previewTextureView.isAvailable){
            openCamera()
        }else{
            previewTextureView.surfaceTextureListener = surfaceListener
        }
    }

    override fun onPause() {
        closeCamera()
        stopBackgroundThread()
        super.onPause()
    }

    private fun closeCamera(){
        if (this::captureSession.isInitialized){
            captureSession.close()
        }
        if (this::cameraDevice.isInitialized){
            cameraDevice.close()
        }
    }

    //VIDEO PORTION

    private var isRecording = false
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        captureButton.setOnClickListener{
            Log.d(TAG, "capture button selected")
            if (isRecording){
                isRecording = false
                stopRecordSession()
            }else{
                isRecording = true
                startRecordSession()
            }
        }

        thumbButton.setOnClickListener{
            Log.d(TAG, "thumbnail button selected")
        }
    }

    private fun startChronometer(){
        chronometer.base = SystemClock.elapsedRealtime()
        chronometer.setTextColor(ContextCompat.getColor(context!!,android.R.color.holo_red_light))
        chronometer.start()
    }

    private fun stopChronometer(){
        chronometer.setTextColor(ContextCompat.getColor(context!!,android.R.color.white))
        chronometer.stop()
    }

    private val mediaRecorder by lazy {
        MediaRecorder()
    }
    private lateinit var currentVideoFilePath: String

    private fun createVideoFileName(): String{
        //Due to the filename being different every single time
        // we need the timestamp variable to differentiate the files
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        return "VIDEO_$timestamp.mp4"
    }

    private fun createVideoFile(): File{
        //We're setting the video files private to the app.
        // If we uninstall the app, the videos get removed as well
        val videoFile = File(context?.filesDir, createVideoFileName())
        currentVideoFilePath = videoFile.absolutePath
        return videoFile
    }

    private fun setupMediaRecorder(){
        //Actual orientation of the display screen
        val rotation = activity?.windowManager?.defaultDisplay?.rotation
        val sensorOrientation = cameraCharacteristics(
                cameraId(CameraCharacteristics.LENS_FACING_BACK),
                CameraCharacteristics.SENSOR_ORIENTATION
        )
        when(sensorOrientation){
            SENSOR_DEFAULT_ORIENTATION_DEGREES ->
                    mediaRecorder.setOrientationHint(DEFAULT_ORIENTATION.get(rotation!!))
            SENSOR_INVERSE_ORIENTATION_DEGREES ->
                    mediaRecorder.setOrientationHint(INVERSE_ORIENTATION.get(rotation!!))
        }
        //Allow us to call the object functions
        // and apply them back to the mediaRecorder instance itself
        mediaRecorder.apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                setOutputFile(createVideoFile())
            }else{
                createVideoFile()
                setOutputFile(currentVideoFilePath)
            }
            //If the bit rate is any less than 10000000, we'll get poor video quality
            setVideoEncodingBitRate(10000000)
            setVideoFrameRate(30)
            setVideoSize(1920, 1080)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            prepare()
        }
    }

    private fun stopMediaRecorder(){
        mediaRecorder.apply {
            try {
                stop()
                //Reset so it can be used again for recording
                reset()
            }catch (e: IllegalStateException){
                Log.e(TAG, e.toString())
            }
        }
    }

    private fun recordSession(){
        setupMediaRecorder()
        val surfaceTexture = previewTextureView.surfaceTexture
        surfaceTexture.setDefaultBufferSize(MAX_PREVIEW_WIDTH, MAX_PREVIEW_HEIGHT)
        val textureSurface = Surface(surfaceTexture)
        val recordSurface = mediaRecorder.surface

        captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
        captureRequestBuilder.addTarget(textureSurface)
        captureRequestBuilder.addTarget(recordSurface)
        val surfaces = ArrayList<Surface>().apply {
            add(textureSurface)
            add(recordSurface)
        }

        cameraDevice.createCaptureSession(surfaces,
                object : CameraCaptureSession.StateCallback(){
                    override fun onConfigureFailed(session: CameraCaptureSession?) {
                        Log.e(TAG, "creating record session failed!")
                    }

                    override fun onConfigured(session: CameraCaptureSession?) {
                        if (session != null){
                            captureSession = session
                            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                            captureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null)
                            isRecording = true
                            mediaRecorder.start()
                        }
                    }

                }, backgroundHandler)
    }

    private fun startRecordSession(){
        startChronometer()
        recordSession()
    }

    private fun stopRecordSession(){
        stopChronometer()
        stopMediaRecorder()
        previewSession()
        thumbButton.setImageDrawable(createRoundThumb())
    }

    private fun createVideoThumb() = ThumbnailUtils.createVideoThumbnail(
            currentVideoFilePath, MediaStore.Video.Thumbnails.MICRO_KIND
    )

    private fun createRoundThumb(): RoundedBitmapDrawable {
        val drawable = RoundedBitmapDrawableFactory.create(resources, createVideoThumb())
        drawable.isCircular = true
        return drawable
    }


}