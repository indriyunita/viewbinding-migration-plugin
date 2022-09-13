package ru.hh.android.synthetic_plugin.model

sealed class IncludeData(val order: Int) {


    /**
     * Example of [includeId]: "toolbarId"
     * Example of [includingBindingClassName]: "ToolbarBinding"
     */
    data class Include(val includeId: String, val includingBindingClassName: String) : IncludeData(0)

    object NoInclude : IncludeData(1)

}