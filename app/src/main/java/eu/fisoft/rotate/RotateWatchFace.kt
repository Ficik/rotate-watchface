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
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.WindowInsets
import android.widget.Toast

import java.lang.ref.WeakReference
import java.util.Calendar
import java.util.TimeZone

const val COMPLICATION_CENTER_FIRST_LINE = 10
const val COMPLICATION_CENTER_SECOND_LINE = 11
const val COMPLICATION_BORDER_CIRCLE = 12
const val COMPLICATION_BITE_LEFT = 13
const val COMPLICATION_BITE_RIGHT = 14

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
        private const val INTERACTIVE_UPDATE_RATE_MS = 10000

        /**
         * Handler message id for updating the time periodically in interactive mode.
         */
        private const val MSG_UPDATE_TIME = 0


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

        private var mComplicationDataCenterFirstLine: ComplicationData? = null
        private var mComplicationDataCenterSecondLine: ComplicationData? = null
        private var mComplicationDataBorderCircle: ComplicationData? = null
        private var mComplicationDataBiteLeft: ComplicationData? = null
        private var mComplicationDataBiteRight: ComplicationData? = null



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
        private var mBiteTextPaint = Paint()

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
            mInnerRingWidth = mSurfaceWidth / 8F
            mPaddingRingWidth = mSurfaceWidth / 64F



            mOuterRingHighlightTextPaint = Paint().apply {
                typeface = mFontHighlight
                textSize = mOuterRingWidth * 0.75F
                isAntiAlias = !mLowBitAmbient
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
            }

            mOuterRingTextPaint = Paint().apply {
                typeface = mFontHighlight
                textSize = mOuterRingWidth * 0.55F
                isAntiAlias = !mLowBitAmbient
                color = Color.GRAY
                textAlign = Paint.Align.CENTER
            }

            mInnerRingHighlightTextPaint = Paint().apply {
                typeface = mFontHighlight
                textSize = mOuterRingWidth * 0.45F
                isAntiAlias = !mLowBitAmbient
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
            }

            mInnerRingTextPaint = Paint().apply {
                typeface = mFont
                textSize = mOuterRingWidth * 0.35F
                isAntiAlias = !mLowBitAmbient
                color = Color.GRAY
                textAlign = Paint.Align.CENTER
            }

            mCenterTextPaint = Paint().apply {
                typeface = mFont
                textSize = mSurfaceWidth * 0.055F
                isAntiAlias = !mLowBitAmbient
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
            }

            mPaddingRingPaint = Paint().apply {
                color = Color.DKGRAY // ColorUtils.setAlphaComponent(mHighlightColor, (255 * 0.25).toInt())
                isAntiAlias = !mLowBitAmbient
                style = Paint.Style.STROKE
                strokeWidth = mPaddingRingWidth * 2
            }

            mBiteTextPaint = Paint().apply {
                typeface = mFontHighlight
                textSize = mOuterRingWidth * 0.40F
                textAlign = Paint.Align.CENTER
                color = Color.WHITE
                isAntiAlias = !mLowBitAmbient

            }

        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)

            setWatchFaceStyle(WatchFaceStyle.Builder(this@RotateWatchFace)
                    .setAcceptsTapEvents(true)
                    .setStatusBarGravity(Gravity.CENTER or Gravity.CENTER_HORIZONTAL)
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
                    COMPLICATION_CENTER_FIRST_LINE,
                    SystemProviders.DAY_OF_WEEK,
                    ComplicationData.TYPE_SHORT_TEXT
            )

            setDefaultSystemComplicationProvider(
                    COMPLICATION_CENTER_SECOND_LINE,
                    SystemProviders.DATE,
                    ComplicationData.TYPE_SHORT_TEXT
            )

            setDefaultSystemComplicationProvider(
                    COMPLICATION_BORDER_CIRCLE,
                    SystemProviders.WATCH_BATTERY,
                    ComplicationData.TYPE_RANGED_VALUE
            )

            setDefaultSystemComplicationProvider(
                    COMPLICATION_BITE_LEFT,
                    SystemProviders.STEP_COUNT,
                    ComplicationData.TYPE_SHORT_TEXT
            )

            setDefaultSystemComplicationProvider(
                    COMPLICATION_BITE_RIGHT,
                    SystemProviders.SUNRISE_SUNSET,
                    ComplicationData.TYPE_SHORT_TEXT
            )

            setActiveComplications(
                    COMPLICATION_CENTER_FIRST_LINE,
                    COMPLICATION_CENTER_SECOND_LINE,
                    COMPLICATION_BORDER_CIRCLE,
                    COMPLICATION_BITE_LEFT,
                    COMPLICATION_BITE_RIGHT
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
            if (data != null) {
                when (watchFaceComplicationId) {
                    COMPLICATION_CENTER_FIRST_LINE -> mComplicationDataCenterFirstLine = data
                    COMPLICATION_CENTER_SECOND_LINE -> mComplicationDataCenterSecondLine = data
                    COMPLICATION_BORDER_CIRCLE -> mComplicationDataBorderCircle = data
                    COMPLICATION_BITE_LEFT -> mComplicationDataBiteLeft = data
                    COMPLICATION_BITE_RIGHT -> mComplicationDataBiteRight = data
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
                    (mComplicationDataBorderCircle?.value ?: 0F) - ((mComplicationDataBorderCircle?.minValue ?: 0F)) / (mComplicationDataBorderCircle?.maxValue ?: 1F)
            )

            drawNumbers(canvas, hour, minuteFloat)
            drawWindow(canvas)

            drawBite(canvas, 320F, mComplicationDataBiteRight?.shortText?.getText(applicationContext, now)?.toString() ?: "")
            drawBite(canvas, 220F, mComplicationDataBiteLeft?.shortText?.getText(applicationContext, now)?.toString() ?: "")


            drawHour(canvas, hour, mOuterRingHighlightTextPaint)
            drawMinute(canvas, minute, mInnerRingHighlightTextPaint)

            val firstLine = mComplicationDataCenterFirstLine?.shortText?.getText(applicationContext, now)?.toString() ?:
                mComplicationDataCenterFirstLine?.longText?.getText(applicationContext, now)?.toString() ?: ""
            val secondLine = mComplicationDataCenterSecondLine?.shortText?.getText(applicationContext, now)?.toString() ?:
            mComplicationDataCenterSecondLine?.longText?.getText(applicationContext, now)?.toString() ?: ""

            val shouldOffsetText = firstLine.isNotEmpty() && secondLine.isNotEmpty()

            mCenterTextPaint.getTextBounds(firstLine, 0, firstLine.length, mTextBounds)
            canvas.drawText(
                    firstLine,
                    mSurfaceCenter,
                    mSurfaceCenter - mTextBounds.exactCenterY() - (if (shouldOffsetText) mTextBounds.height()*(5F/8) else 0F),
                    mCenterTextPaint
            )

            mCenterTextPaint.getTextBounds(secondLine, 0, secondLine.length, mTextBounds)
            canvas.drawText(secondLine,
                    mSurfaceCenter,
                    mSurfaceCenter - mTextBounds.exactCenterY() + (if (shouldOffsetText) mTextBounds.height()*(5F/8) else 0F),
                    mCenterTextPaint
            )




            // canvas.drawText(text, mXOffset, mYOffset, mTextPaint)
        }

        fun drawBite(canvas: Canvas, angle: Float, value: String) {
            if (value.isEmpty()) return
            val bgColor = if (mAmbient) {
                Color.DKGRAY
            } else {
                ColorUtils.setAlphaComponent(mHighlightColor, (0.65F * 255).toInt())
            }

            canvas.drawArc(
                    0F,
                    0F,
                    mSurfaceWidth,
                    mSurfaceWidth,
                    angle - 15F, 30F,
                    false,
                    Paint().apply {
                        color = bgColor
                        isAntiAlias = !mLowBitAmbient
                        style = Paint.Style.STROKE
                        strokeWidth = (mPaddingRingWidth * 2 + mOuterRingWidth) * 2
                        setShadowLayer(4F, 0F, 0F, Color.BLACK)
                    }
            )

            canvas.rotate(-(270F - angle), mSurfaceCenter, mSurfaceCenter)
            mBiteTextPaint.getTextBounds(value, 0, value.length, mTextBounds)
            canvas.drawText(
                    value,
                    mSurfaceCenter,
                    mPaddingRingWidth + mOuterRingWidth/2 - mTextBounds.exactCenterY(),
                    mBiteTextPaint
            )
            canvas.rotate(270F - angle, mSurfaceCenter, mSurfaceCenter)
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
            val bgColor = if (mAmbient) {
                Color.DKGRAY
            } else {
                mHighlightColor
            }


            // center
            canvas.drawCircle(
                    mSurfaceCenter,
                    mSurfaceCenter,
                    mSurfaceCenter - mPaddingRingWidth - mOuterRingWidth - mInnerRingWidth,
                    Paint().apply {
                        isAntiAlias = !mLowBitAmbient
                        color = Color.BLACK
                        style = Paint.Style.FILL
                    }
            )
                // window
            canvas.drawArc(
                    0F,
                    0F,
                    mSurfaceWidth,
                    mSurfaceWidth,
                    255F, 30F,
                    false,
                    Paint().apply {
                        color = bgColor
                        isAntiAlias = !mLowBitAmbient
                        style = Paint.Style.STROKE
                        strokeWidth = (mPaddingRingWidth * 2 + mOuterRingWidth + mInnerRingWidth) * 2
                        setShadowLayer(4F, 0F, 0F, Color.BLACK)
                    }
            )


        }

        fun drawRings(canvas: Canvas) {
            // outer ring
            canvas.drawCircle(
                    mSurfaceCenter,
                    mSurfaceCenter,
                    mSurfaceCenter - mPaddingRingWidth - mOuterRingWidth / 2,
                    Paint().apply {
                        isAntiAlias = !mLowBitAmbient
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
                        isAntiAlias = !mLowBitAmbient
                        style = Paint.Style.STROKE
                        strokeWidth = mInnerRingWidth
                    }
            )
        }

        fun drawNumbers(canvas: Canvas, hour: Int, minutef: Float) {
            val minute = minutef.toInt()

            canvas.rotate((minute / 60F) * -360F / 16, mSurfaceCenter, mSurfaceCenter)
            for (i in 1..16) {
                drawHour(canvas, (hour - i + 24) % 24, mOuterRingTextPaint)
                canvas.rotate(-360F / 16, mSurfaceCenter, mSurfaceCenter)
            }
            canvas.rotate((1 - (minute / 60F)) * -360F / 16, mSurfaceCenter, mSurfaceCenter)
            canvas.rotate(360F / 16, mSurfaceCenter, mSurfaceCenter)


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
                    mSurfaceCenter,
                    mPaddingRingWidth + mOuterRingWidth/2 - mTextBounds.exactCenterY(),
                    paint
            )
        }

        fun drawMinute(canvas: Canvas, minute: Int, paint: Paint) {
            val minutes = String.format("%02d", minute)
            paint.getTextBounds(minutes, 0, minutes.length, mTextBounds)

            canvas.drawText(
                    minutes,
                    mSurfaceCenter,
                    mPaddingRingWidth + mOuterRingWidth + mInnerRingWidth/2 - mTextBounds.exactCenterY(),
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
