package ru.hh.android.synthetic_plugin.model

sealed class IncludeData {
    data class Include(val includeId: String, val includingBindingClassName: String) : IncludeData()

    object NoInclude : IncludeData()
}