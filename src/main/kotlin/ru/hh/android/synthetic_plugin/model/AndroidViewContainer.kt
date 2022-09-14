package ru.hh.android.synthetic_plugin.model

import android.databinding.tool.ext.toCamelCase
import com.intellij.psi.PsiReference
import com.intellij.psi.xml.XmlAttributeValue
import org.jetbrains.kotlin.lombok.utils.decapitalize
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtReferenceExpression
import ru.hh.android.synthetic_plugin.extensions.toShortFormattedBindingName
import ru.hh.android.synthetic_plugin.utils.Const

/**
 * Model for holding information about XML view.
 */
sealed class AndroidViewContainer {

    abstract val xml: XmlAttributeValue
    abstract val isNeedBindingPrefix: Boolean

    data class PsiRef(
        val ref: PsiReference,
        override val xml: XmlAttributeValue,
        override val isNeedBindingPrefix: Boolean,
    ) : AndroidViewContainer()

    data class KtRefExp(
        val ref: KtReferenceExpression,
        override val xml: XmlAttributeValue,
        override val isNeedBindingPrefix: Boolean,
    ) : AndroidViewContainer()


    /**
     * With asterisk import, we won't get the view IDs, so we can't use import directive
     * Instead, we use a hashmap containing layout ID -> view ID list
     */
    fun getElementName(
        layoutAndIds: Map<String, List<String>>,
        hasMultipleBindingsInFile: Boolean,
    ): String {

        // e.g. "button_first"
        val idSnakeCase = xml.text
            .removeSurrounding("\"")
            .removePrefix(Const.ANDROID_VIEW_ID)

        val idCamelCase = idSnakeCase.toCamelCase().decapitalize()

        val prefix = if (!hasMultipleBindingsInFile) {
            when {
                isNeedBindingPrefix -> "binding."
                else -> ""
            }
        } else {
            var containingLayout = ""
            for ((layout, ids) in layoutAndIds) {
                if (ids.contains(idSnakeCase)) {
                    containingLayout = layout
                    break
                }
            }
            val newPrefix = containingLayout.toShortFormattedBindingName()

            when {
                isNeedBindingPrefix -> "${newPrefix}Binding."
                else -> ""
            }
        }

        return "$prefix$idCamelCase"
    }
}
