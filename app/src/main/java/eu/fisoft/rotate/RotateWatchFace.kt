package eu.fisoft.rotate

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.v4.content.ContextCompat
import android.support.v4.graphics.ColorUtils
import android.support.wearable.complications.ComplicationData
import android.support.wearable.complications.SystemProviders
import android.support.wearable.watchface.CanvasWatchFaceService
import android.support.wearable.watchface.WatchFaceService
import android.support.wearable.watchface.WatchFaceStyle
import android.text.TextPaint
import android.view.SurfaceHolder
import android.view.WindowInsets
import android.widget.Toast

import java.lang.ref.WeakReference
import java.util.Calendar
import java.util.TimeZone

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 *
 *
 * Important Note: Because watch face apps do not have a default Activity in
 * their project, you will need to set your Configurations to
 * "Do not launch Activity" for both the Wear and/or Application modules. If you
 * are unsure how to do this, please review the "Run Starter project" section
 * in the Google Watch Face Code Lab:
 * https://codelabs.developers.google.com/codelabs/watchface/index.html#0
 */
class RotateWatchFace : CanvasWatchFaceService() {

    companion object {
        private val NORMAL_TYPEFACE = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)

        /**
         * Updates rate in milliseconds for interactive mode. We update once a second since seconds
         * are displayed in interactive mode.
         */
        private const val INTERACTIVE_UPDATE_RATE_MS = 1000

        /**
         * Handler message id for updating the time periodically in interactive mode.
         */
        private const val MSG_UPDATE_TIME = 0

        private const val CENTER_COMPLICATION = 7
        private const val PADDING_CICLE_COMPLICATION = 2
    }

    override fun onCreateEngine(): Engine {
        return Engine()
    }

    private class EngineHandler(reference: RotateWatchFace.Engine) : Handler() {
        private val mWeakReference: WeakReference<RotateWatchFace.Engine> = WeakReference(reference)

        override fun handleMessage(msg: Message) {
            val engine = mWeakReference.get()
            if (engine != null) {
                when (msg.what) {
                    MSG_UPDATE_TIME -> engine.handleUpdateTimeMessage()
                }
            }
        }
    }

    inner class Engine : CanvasWatchFaceService.Engine() {

        private lateinit var mCalendar: Calendar

        private var mRegisteredTimeZoneReceiver = false

        private var mXOffset: Float = 0F
        private var mYOffset: Float = 0F

        private lateinit var mBackgroundPaint: Paint
        private lateinit var mTextPaint: Paint

        private var mTextBounds = Rect()

        private var mComplicationDataCenter: ComplicationData? = null
        private var mComplicationDataPadding: ComplicationData? = null


        private var mOuterRingWidth = 0F
        private var mInnerRingWidth = 0F
        private var mPaddingRingWidth = 0F
        private var mSurfaceWidth = 0F
        private var mSurfaceCenter = 0F
        private var mSurfaceHeight = 0F
        private var mFont = NORMAL_TYPEFACE
        private var mFontHighlight = NORMAL_TYPEFACE
        private var mHighlightColor = Color.parseColor("#B0FF740D")


        private var mOuterRingTextPaint: Paint = Paint()
        private var mOuterRingHighlightTextPaint: Paint = Paint()
        private var mInnerRingTextPaint: Paint = Paint()
        private var mInnerRingHighlightTextPaint: Paint = Paint()
        private var mCenterTextPaint = Paint()
        private var mPaddingRingPaint = Paint()

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        private var mLowBitAmbient: Boolean = false
        private var mBurnInProtection: Boolean = false
        private var mAmbient: Boolean = false



        private val mUpdateTimeHandler: Handler = EngineHandler(this)

        private val mTimeZoneReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                mCalendar.timeZone = TimeZone.getDefault()
                invalidate()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            mSurfaceWidth = width.toFloat()
            mSurfaceHeight = height.toFloat()
            mSurfaceCenter = width.toFloat() / 2F
            mOuterRingWidth = mSurfaceWidth / 7F
            mInnerRingWidth = mSurfaceWidth / 7F
            mPaddingRingWidth = mSurfaceWidth / 48F



            mOuterRingHighlightTextPaint = Paint().apply {
                typeface = mFontHighlight
                textSize = mOuterRingWidth * 0.75F
                isAntiAlias = true
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
            }

            mOuterRingTextPaint = Paint().apply {
                typeface = mFont
                textSize = mOuterRingWidth * 0.55F
                isAntiAlias = true
                color = Color.GRAY
                textAlign = Paint.Align.CENTER
            }

            mInnerRingHighlightTextPaint = Paint().apply {
                typeface = mFontHighlight
                textSize = mOuterRingWidth * 0.45F
                isAntiAlias = true
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
            }

            mInnerRingTextPaint = Paint().apply {
                typeface = mFont
                textSize = mOuterRingWidth * 0.35F
                isAntiAlias = true
                color = Color.GRAY
                textAlign = Paint.Align.CENTER
            }

            mCenterTextPaint = Paint().apply {
                typeface = mFont
                textSize = mSurfaceWidth * 0.05F
                isAntiAlias = true
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
            }

            mPaddingRingPaint = Paint().apply {
                color = Color.DKGRAY // ColorUtils.setAlphaComponent(mHighlightColor, (255 * 0.25).toInt())
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeWidth = mPaddingRingWidth * 2

            }
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)

            setWatchFaceStyle(WatchFaceStyle.Builder(this@RotateWatchFace)
                    .setAcceptsTapEvents(true)
                    .build())

            mCalendar = Calendar.getInstance()
            mFontHighlight = resources.getFont(R.font.oswald_medium)
            mFont = resources.getFont(R.font.oswald_regular)

            val resources = this@RotateWatchFace.resources
            mYOffset = resources.getDimension(R.dimen.digital_y_offset)

            // Initializes background.
            mBackgroundPaint = Paint().apply {
                //color = ContextCompat.getColor(applicationContext, R.color.background)
                color = Color.DKGRAY
            }

            // Initializes Watch Face.
            mTextPaint = Paint().apply {
                typeface = NORMAL_TYPEFACE
                isAntiAlias = true
                color = ContextCompat.getColor(applicationContext, R.color.digital_text)
                textAlign = Paint.Align.CENTER
            }

            setDefaultSystemComplicationProvider(
                    CENTER_COMPLICATION,
                    SystemProviders.DAY_AND_DATE,
                    ComplicationData.TYPE_SHORT_TEXT
            )

            setDefaultSystemComplicationProvider(
                    PADDING_CICLE_COMPLICATION,
                    SystemProviders.WATCH_BATTERY,
                    ComplicationData.TYPE_RANGED_VALUE
            )

            setActiveComplications(
                    CENTER_COMPLICATION,
                    PADDING_CICLE_COMPLICATION
            )
        }

        override fun onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            super.onDestroy()
        }

        override fun onPropertiesChanged(properties: Bundle) {
            super.onPropertiesChanged(properties)
            mLowBitAmbient = properties.getBoolean(
                    WatchFaceService.PROPERTY_LOW_BIT_AMBIENT, false)
            mBurnInProtection = properties.getBoolean(
                    WatchFaceService.PROPERTY_BURN_IN_PROTECTION, false)
        }

        override fun onTimeTick() {
            super.onTimeTick()
            invalidate()
        }

        override fun onAmbientModeChanged(inAmbientMode: Boolean) {
            super.onAmbientModeChanged(inAmbientMode)
            mAmbient = inAmbientMode

            if (mLowBitAmbient) {
                mTextPaint.isAntiAlias = !inAmbientMode
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer()
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        override fun onTapCommand(tapType: Int, x: Int, y: Int, eventTime: Long) {
            when (tapType) {
                WatchFaceService.TAP_TYPE_TOUCH -> {
                    // The user has started touching the screen.
                }
                WatchFaceService.TAP_TYPE_TOUCH_CANCEL -> {
                    // The user has started a different gesture or otherwise cancelled the tap.
                }
                WatchFaceService.TAP_TYPE_TAP ->
                    // The user has completed the tap gesture.
                    // TODO: Add code to handle the tap gesture.
                    Toast.makeText(applicationContext, R.string.message, Toast.LENGTH_SHORT)
                            .show()
            }
            invalidate()
        }

        override fun onComplicationDataUpdate(watchFaceComplicationId: Int, data: ComplicationData?) {
            super.onComplicationDataUpdate(watchFaceComplicationId, data)
            Toast.makeText(applicationContext, "onComplicationDataUpdate", Toast.LENGTH_SHORT)
                    .show()
            if (data != null) {
                if (watchFaceComplicationId == CENTER_COMPLICATION) {
                    mComplicationDataCenter = data
                }
                else if (watchFaceComplicationId == PADDING_CICLE_COMPLICATION) {
                    mComplicationDataPadding = data
                }
            }

        }

        override fun onDraw(canvas: Canvas, bounds: Rect) {
            // Draw the background.
            if (mAmbient) {
                canvas.drawColor(Color.BLACK)
            } else {
                canvas.drawColor(Color.BLACK)
            }

            if (!mAmbient) {
                drawRings(canvas)
            }

            val now = System.currentTimeMillis()
            mCalendar.timeInMillis = now
            val hour = mCalendar.get(Calendar.HOUR_OF_DAY)
            val minute = mCalendar.get(Calendar.MINUTE)
            val second = mCalendar.get(Calendar.SECOND)
            val minuteFloat = minute + (second/60F)

            drawPaddingRing(
                    canvas,
                    (mComplicationDataPadding?.value ?: 0F) - ((mComplicationDataPadding?.minValue ?: 0F)) / (mComplicationDataPadding?.maxValue ?: 1F)
            )

            drawNumbers(canvas, hour, minuteFloat)
            drawWindow(canvas)

            drawHour(canvas, hour, mOuterRingHighlightTextPaint)
            drawMinute(canvas, minute, mInnerRingHighlightTextPaint)

            val centerText = mComplicationDataCenter?.shortText?.getText(applicationContext, now)?.toString() ?:
                mComplicationDataCenter?.longText?.getText(applicationContext, now)?.toString() ?: ""
            mCenterTextPaint.getTextBounds(centerText, 0, centerText.length, mTextBounds);
            canvas.drawText(centerText, mSurfaceCenter, mSurfaceCenter - mTextBounds.exactCenterY(), mCenterTextPaint)




            // canvas.drawText(text, mXOffset, mYOffset, mTextPaint)
        }

        fun drawPaddingRing(canvas:Canvas, percent:Float) {
            canvas.drawArc(
                    0F, 0F,
                    mSurfaceWidth, mSurfaceWidth,
                    270F, 360F * percent,
                    false,
                    mPaddingRingPaint
            )
        }

        fun drawWindow(canvas: Canvas) {
            if (!mAmbient) {
                // window
                canvas.drawArc(
                        0F,
                        0F,
                        mSurfaceWidth,
                        mSurfaceWidth,
                        165F, 30F,
                        false,
                        Paint().apply {
                            color = mHighlightColor
                            style = Paint.Style.STROKE
                            strokeWidth = (mPaddingRingWidth + mOuterRingWidth + mInnerRingWidth) * 2
                            setShadowLayer(4F, 0F, 0F, Color.BLACK)
                        }
                )


                // center
                canvas.drawCircle(
                        mSurfaceCenter,
                        mSurfaceCenter,
                        mSurfaceCenter - mPaddingRingWidth - mOuterRingWidth - mInnerRingWidth,
                        Paint().apply {
                            color = Color.BLACK
                            style = Paint.Style.FILL
                        }
                )
            } else {
                // window
                canvas.drawArc(
                        0F,
                        0F,
                        mSurfaceWidth,
                        mSurfaceWidth,
                        165F, 30F,
                        false,
                        Paint().apply {
                            color = Color.DKGRAY
                            style = Paint.Style.STROKE
                            strokeWidth = (mPaddingRingWidth + mOuterRingWidth + mInnerRingWidth) * 2
                        }
                )
            }
        }

        fun drawRings(canvas: Canvas) {
            // outer ring
            canvas.drawCircle(
                    mSurfaceCenter,
                    mSurfaceCenter,
                    mSurfaceCenter - mPaddingRingWidth - mOuterRingWidth / 2,
                    Paint().apply {
                        color = Color.BLACK
                        style = Paint.Style.STROKE
                        strokeWidth = mOuterRingWidth
                    }
            )

            // inner ring
            canvas.drawCircle(
                    mSurfaceCenter,
                    mSurfaceCenter,
                    mSurfaceCenter - mPaddingRingWidth - mOuterRingWidth - mInnerRingWidth / 2,
                    Paint().apply {
                        color = Color.DKGRAY
                        style = Paint.Style.STROKE
                        strokeWidth = mInnerRingWidth
                    }
            )
        }

        fun drawNumbers(canvas: Canvas, hour: Int, minutef: Float) {
            val minute = minutef.toInt()

            canvas.rotate((minute / 60F) * -360F / 12, mSurfaceCenter, mSurfaceCenter)
            for (i in 1..12) {
                drawHour(canvas, (hour - i + 24) % 24, mOuterRingTextPaint)
                canvas.rotate(-360F / 12, mSurfaceCenter, mSurfaceCenter)
            }
            canvas.rotate((1 - (minute / 60F)) * -360F / 12, mSurfaceCenter, mSurfaceCenter)
            canvas.rotate(360F / 12, mSurfaceCenter, mSurfaceCenter)


            val startMinute = (minute / 5) * 5
            canvas.rotate(((minutef % 5F) / 5F) * -360F / 12, mSurfaceCenter, mSurfaceCenter)
            for (i in 0..11) {
                drawMinute(canvas, ((startMinute - (i * 5)) + 60) % 60, mInnerRingTextPaint)
                canvas.rotate(-360F / 12, mSurfaceCenter, mSurfaceCenter)
            }
            canvas.rotate((1 - ((minutef % 5F) / 5F)) * -360F / 12, mSurfaceCenter, mSurfaceCenter)
            canvas.rotate(360F / 12, mSurfaceCenter, mSurfaceCenter)
        }


        fun drawHour(canvas: Canvas, hour: Int, paint: Paint) {
            val hours = String.format("%s", hour)
            paint.getTextBounds(hours, 0, hours.length, mTextBounds)
            canvas.drawText(
                    hours,
                    mPaddingRingWidth + mOuterRingWidth/2,
                    mSurfaceCenter - mTextBounds.exactCenterY(),
                    paint
            )
        }

        fun drawMinute(canvas: Canvas, minute: Int, paint: Paint) {
            val minutes = String.format("%02d", minute)
            paint.getTextBounds(minutes, 0, minutes.length, mTextBounds)

            canvas.drawText(
                    minutes,
                    mPaddingRingWidth + mOuterRingWidth + mInnerRingWidth/2,
                    mSurfaceCenter - mTextBounds.exactCenterY(),
                    paint
            )
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)

            if (visible) {
                registerReceiver()

                // Update time zone in case it changed while we weren't visible.
                mCalendar.timeZone = TimeZone.getDefault()
                invalidate()
            } else {
                unregisterReceiver()
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer()
        }

        private fun registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = true
            val filter = IntentFilter(Intent.ACTION_TIMEZONE_CHANGED)
            this@RotateWatchFace.registerReceiver(mTimeZoneReceiver, filter)
        }

        private fun unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = false
            this@RotateWatchFace.unregisterReceiver(mTimeZoneReceiver)
        }

        override fun onApplyWindowInsets(insets: WindowInsets) {
            super.onApplyWindowInsets(insets)

            // Load resources that have alternate values for round watches.
            val resources = this@RotateWatchFace.resources
            val isRound = insets.isRound
            mXOffset = resources.getDimension(
                    if (isRound)
                        R.dimen.digital_x_offset_round
                    else
                        R.dimen.digital_x_offset
            )

            val textSize = resources.getDimension(
                    if (isRound)
                        R.dimen.digital_text_size_round
                    else
                        R.dimen.digital_text_size
            )

            mTextPaint.textSize = textSize
        }

        /**
         * Starts the [.mUpdateTimeHandler] timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private fun updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME)
            }
        }

        /**
         * Returns whether the [.mUpdateTimeHandler] timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private fun shouldTimerBeRunning(): Boolean {
            return isVisible && !isInAmbientMode
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        fun handleUpdateTimeMessage() {
            invalidate()
            if (shouldTimerBeRunning()) {
                val timeMs = System.currentTimeMillis()
                val delayMs = INTERACTIVE_UPDATE_RATE_MS - timeMs % INTERACTIVE_UPDATE_RATE_MS
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs)
            }
        }
    }
}
