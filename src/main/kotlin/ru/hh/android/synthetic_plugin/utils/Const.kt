package ru.hh.android.synthetic_plugin.utils

object Const {
    const val ANDROID_VIEW_ID = "@+id/"
    const val ANDROID_VIEW_ID_DECLARATION = "android:id=\"@+id/"
    const val KOTLINX_SYNTHETIC = "kotlinx.android.synthetic.main."
    const val CELL_WITH_VIEW_HOLDER = "with(viewHolder.itemView)"
    const val SET_CONTENT_VIEW_PREFIX = "setContentView"

    const val MAIN_DIR_IDENTIFIER = "src/main"
    const val LAYOUT_DIR = "res/layout/"
    const val ERROR_INCLUDE_NO_ID = "Include tag must have id attribute"

}
