package eu.fisoft.rotate

import android.os.Bundle
import android.support.wearable.activity.WearableActivity
import android.support.wearable.complications.ComplicationData
import android.support.wearable.complications.ComplicationHelperActivity
import android.view.View
import android.content.ComponentName
import android.content.Intent


class ComplicationsSettings : WearableActivity() {

    private lateinit var mWatchFaceComponentName:ComponentName

    private val COMPLICATION_CONFIG_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_complications_settings)

        mWatchFaceComponentName = ComponentName(applicationContext, RotateWatchFace::class.java)
        // Enables Always-on
        setAmbientEnabled()

        findViewById<View>(R.id.border_complication_option).setOnClickListener(View.OnClickListener {
            startActivityForResult(
                    ComplicationHelperActivity.createProviderChooserHelperIntent(
                            applicationContext,
                            mWatchFaceComponentName,
                            COMPLICATION_BORDER_CIRCLE,
                            ComplicationData.TYPE_RANGED_VALUE
                    ),
                    COMPLICATION_CONFIG_REQUEST_CODE
            )
        })

        findViewById<View>(R.id.center_first_line_complication_option).setOnClickListener(View.OnClickListener {
            startActivityForResult(
                    ComplicationHelperActivity.createProviderChooserHelperIntent(
                            applicationContext,
                            mWatchFaceComponentName,
                            COMPLICATION_CENTER_FIRST_LINE,
                            ComplicationData.TYPE_SHORT_TEXT,
                            ComplicationData.TYPE_LONG_TEXT
                    ),
                    COMPLICATION_CONFIG_REQUEST_CODE
            )
        })

        findViewById<View>(R.id.center_second_line_complication_option).setOnClickListener(View.OnClickListener {
            startActivityForResult(
                    ComplicationHelperActivity.createProviderChooserHelperIntent(
                            applicationContext,
                            mWatchFaceComponentName,
                            COMPLICATION_CENTER_SECOND_LINE,
                            ComplicationData.TYPE_SHORT_TEXT,
                            ComplicationData.TYPE_LONG_TEXT
                    ),
                    COMPLICATION_CONFIG_REQUEST_CODE
            )
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == COMPLICATION_CONFIG_REQUEST_CODE && resultCode == RESULT_OK) {
            // update watch face somehow
        }
    }
}
