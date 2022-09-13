package ru.hh.android.synthetic_plugin.extensions

import java.io.File
import kotlin.jvm.Throws
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import ru.hh.android.synthetic_plugin.utils.Const.ANDROID_VIEW_ID
import ru.hh.android.synthetic_plugin.utils.Const.ERROR_INCLUDE_NO_ID

/**
 * Read layout xml file and return id for included layout.
 * [includedLayout] is the name of the included layout file.
 *
 * For example:
 * <include android:id="@+id/toolbar_id"
 * layout="@layout/toolbar_main"/>
 *
 * [includedLayout] will be "toolbar_main"
 *
 * We want to return "toolbar_id" in this case
 */
fun File.getIncludedViewId(includedLayout: String): String? {
    if (this.exists()) {
        try {
            this.inputStream().use { inputStream ->
                val factory: XmlPullParserFactory = XmlPullParserFactory.newInstance().apply {
                    isNamespaceAware = true
                }
                val parser = factory.newPullParser().apply {
                    setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                    setInput(inputStream, null)
                }

                val id = getIncludedId(parser, includedLayout)?.also {
                    // strip "@+id/" prefix
                    if (it.startsWith(ANDROID_VIEW_ID)) {
                        return it.substring(ANDROID_VIEW_ID.length)
                    }
                }
                return if (id == ERROR_INCLUDE_NO_ID ) {
                    ERROR_INCLUDE_NO_ID
                } else null
            }
        } catch (e: XmlPullParserException) {
            e.printStackTrace()
            return null
        }
    }
    return null
}

/**
 * Look for include tag in xml file and return id for included layout.
 */
@Throws(XmlPullParserException::class)
private fun getIncludedId(parser: XmlPullParser, includedLayout: String): String? {

    while (parser.next() != XmlPullParser.END_DOCUMENT) {
        if (parser.eventType == XmlPullParser.START_TAG) {
            if (parser.name == "include") {
                val layout = parser.getAttributeValue(null, "layout")
                if (layout == "@layout/$includedLayout") {
                    return parser.getAttributeValue(null, "android:id") ?: ERROR_INCLUDE_NO_ID
                }
            }
        }
    }
    return null
}