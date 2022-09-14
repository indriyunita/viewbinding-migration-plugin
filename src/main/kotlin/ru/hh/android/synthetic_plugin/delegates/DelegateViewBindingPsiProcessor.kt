package ru.hh.android.synthetic_plugin.delegates

import android.databinding.tool.ext.toCamelCase
import org.jetbrains.kotlin.lombok.utils.decapitalize
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.getOrCreateBody
import ru.hh.android.synthetic_plugin.extensions.getIncludedViewId
import ru.hh.android.synthetic_plugin.extensions.getMainDirPath
import ru.hh.android.synthetic_plugin.extensions.getViewIds
import ru.hh.android.synthetic_plugin.extensions.notifyError
import ru.hh.android.synthetic_plugin.extensions.toDelegatePropertyFormat
import ru.hh.android.synthetic_plugin.extensions.toViewDelegatePropertyFormat
import ru.hh.android.synthetic_plugin.model.IncludeData
import ru.hh.android.synthetic_plugin.model.ProjectInfo
import ru.hh.android.synthetic_plugin.utils.ClassParentsFinder
import ru.hh.android.synthetic_plugin.utils.Const
import java.io.File

class DelegateViewBindingPsiProcessor(
    projectInfo: ProjectInfo,
) : ViewBindingPsiProcessor(
    projectInfo,
) {
    private companion object {
        // https://github.com/yogacp/android-viewbinding/blob/master/android-viewbinding/src/main/java/android/viewbinding/library/fragment/FragmentBinding.kt
        const val IMPORT_FRAGMENT_DELEGATE_VIEW_BINDING =
            "android.viewbinding.library.fragment.viewBinding"

        // https://github.com/yogacp/android-viewbinding/blob/master/android-viewbinding/src/main/java/android/viewbinding/library/activity/ActivityBinding.kt
        const val IMPORT_ACTIVITY_DELEGATE_VIEW_BINDING =
            "android.viewbinding.library.activity.viewBinding"

        const val HH_IMPORT_INFLATE_AND_BIND_FUN =
            "ru.hh.shared.core.ui.design_system.utils.widget.inflateAndBindView"

        const val HH_IMPORT_CELLS_GET_VIEW_BINDING_FUN =
            "ru.hh.shared.core.ui.cells_framework.cells.getViewBinding"

        // Cells in hh.ru - wrapper for reducing boilerplate in delegates for RecyclerView
        const val HH_CELL_INTERFACE = "ru.hh.shared.core.ui.cells_framework.cells.interfaces.Cell"
    }

    /**
     * Left for single case - HH cell processing
     */
    private val bindingClassName = bindingImportDirectives.firstOrNull()

    private fun getIncludeData(
        mainDirPath: String,
        includedLayoutName: String,
        includingLayoutName: String,
        includingBindingClass: String
    ): IncludeData {
        val xmlFile = File(mainDirPath, Const.LAYOUT_DIR + includingLayoutName + ".xml")
        if (xmlFile.exists()) {
            val id = xmlFile.getIncludedViewId(includedLayoutName)
            if (id == Const.ERROR_INCLUDE_NO_ID) {
                projectInfo.project.notifyError("No include id for layout $includedLayoutName in $includingLayoutName")
            }
            return if (id == null) IncludeData.NoInclude else
                IncludeData.Include(id.toCamelCase().decapitalize(), includingBindingClass)
        }
        return IncludeData.NoInclude
    }

    /**
     * Return map of binding class name with its include data if any (includeId and includingBindingClass).
     * If there is more than one binding in a class, we try all the possible include configurations
     */
    private val bindingsWithIncludeMap: HashMap<String, IncludeData> by lazy {
        hashMapOf<String, IncludeData>().apply {
            // init
            layoutAndBindingMap.values.forEach { bindingClass ->
                this[bindingClass] = IncludeData.NoInclude
            }

            projectInfo.file.virtualFilePath.getMainDirPath()?.let { mainDirPath ->

                val keys = layoutAndBindingMap.keys
                for (i in keys.indices) {
                    val layoutName1 = keys.elementAt(i)
                    val bindingClass1 = layoutAndBindingMap[layoutName1].orEmpty()

                    for (j in i + 1..keys.indices.last) {
                        val layoutName2 = keys.elementAt(j)
                        val bindingClass2 = layoutAndBindingMap[layoutName2].orEmpty()

                        // only check include if there is no previous entry
                        if (this[bindingClass1] == IncludeData.NoInclude) {
                            val incl = getIncludeData(
                                mainDirPath = mainDirPath,
                                includedLayoutName = layoutName1,
                                includingLayoutName = layoutName2,
                                includingBindingClass = bindingClass2
                            ).also {
                                // don't override if already exists
                                this[bindingClass1] = it
                            }

                            // if layout A contains layout B, no need to check vice versa because
                            // it's an impossible scenario
                            if (incl is IncludeData.Include) {
                                continue
                            }
                        }

                        getIncludeData(
                            mainDirPath = mainDirPath,
                            includedLayoutName = layoutName2,
                            includingLayoutName = layoutName1,
                            includingBindingClass = bindingClass1
                        ).also {
                            if (this[bindingClass2] == IncludeData.NoInclude) {
                                this[bindingClass2] = it
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Pair of binding class name and its include data.
     * Sorted so that the binding class with NoInclude is last.
     * Because addBindingAtTopOfClassBody() will add the binding at the top of the class body,
     * and the last to be added will be at the top.
     *
     * For example:
     *
     * private val fragmentFirstBinding: FragmentFirstBinding by viewBinding()
     * private val toolbarBinding: ToolbarBinding = fragmentFirstBinding.toolbarId
     * private val contentMainBinding: ContentMainBinding = fragmentFirstBinding.conMain
     *
     * FragmentFirstBinding (with NoInclude) is the last to be added, so it will be at the top of the class body.
     */
    private val sortedBindingsWithInclude: List<Pair<String, IncludeData>> by lazy {
        bindingsWithIncludeMap.toList().sortedBy { it.second.order }
    }


    override fun processActivity(ktClass: KtClass) {
        val body = ktClass.getOrCreateBody()
        sortedBindingsWithInclude.forEach { (bindingClassName, include) ->
            val text = bindingClassName.toDelegatePropertyFormat(hasMultipleBindingsInFile, include)
            val viewBindingDeclaration = projectInfo.psiFactory.createProperty(text)

            addBindingAtTopOfClassBody(body, viewBindingDeclaration)
        }

        addImports(IMPORT_ACTIVITY_DELEGATE_VIEW_BINDING)
    }

    override fun processFragment(ktClass: KtClass) {
        val body = ktClass.getOrCreateBody()
        sortedBindingsWithInclude.forEach { (bindingClassName, include) ->
            val text = bindingClassName.toDelegatePropertyFormat(hasMultipleBindingsInFile, include)
            val viewBindingDeclaration = projectInfo.psiFactory.createProperty(text)

            addBindingAtTopOfClassBody(body, viewBindingDeclaration)
        }

        addImports(IMPORT_FRAGMENT_DELEGATE_VIEW_BINDING)
    }

    override fun processView(ktClass: KtClass) {
        val body = ktClass.getOrCreateBody()
        val inflateViewExpression = body.anonymousInitializers.getOrNull(0)
            ?.body?.children?.firstOrNull { it.text.contains("inflateView") }
        inflateViewExpression?.delete()

        bindingImportDirectives.forEach { bindingClassName ->
            val text = bindingClassName.toViewDelegatePropertyFormat(hasMultipleBindingsInFile)
            val viewBindingDeclaration = projectInfo.psiFactory.createProperty(text)

            addBindingAtTopOfClassBody(body, viewBindingDeclaration)

            addImports(
                HH_IMPORT_INFLATE_AND_BIND_FUN,
            )
        }
    }

    override fun canHandle(parents: ClassParentsFinder, ktClass: KtClass): Boolean {
        return when {
            parents.isChildOf(HH_CELL_INTERFACE) -> true
            else -> false
        }
    }

    override fun processCustomCases(parents: ClassParentsFinder, ktClass: KtClass) {
        when {
            parents.isChildOf(HH_CELL_INTERFACE) -> processCell(ktClass)
        }
    }

    private fun processCell(ktClass: KtClass) {
        val body = ktClass.getOrCreateBody()
        val bindFunc = body.functions.firstOrNull { it.name == "bind" }
        val withExpression = bindFunc
            ?.children?.firstOrNull { it.text.contains(Const.CELL_WITH_VIEW_HOLDER) }
        withExpression
            ?.children?.firstOrNull()?.children?.getOrNull(1)
            ?.children?.firstOrNull()?.let { withArg ->
                val newElement =
                    projectInfo.psiFactory.createArgument("viewHolder.getViewBinding(${bindingClassName}::bind)")
                withArg.replace(newElement)
            }
        addImports(
            HH_IMPORT_CELLS_GET_VIEW_BINDING_FUN,
        )
    }
}