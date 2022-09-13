package ru.hh.android.synthetic_plugin.delegates

import android.databinding.tool.ext.toCamelCase
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiParserFacade
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.kotlin.lombok.utils.decapitalize
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.resolve.ImportPath
import ru.hh.android.synthetic_plugin.extensions.getIncludedViewId
import ru.hh.android.synthetic_plugin.extensions.getMainDirPath
import ru.hh.android.synthetic_plugin.extensions.getPackageName
import ru.hh.android.synthetic_plugin.extensions.isKotlinSynthetic
import ru.hh.android.synthetic_plugin.extensions.notifyError
import ru.hh.android.synthetic_plugin.extensions.toFormattedBindingName
import ru.hh.android.synthetic_plugin.extensions.toFormattedDirective
import ru.hh.android.synthetic_plugin.model.IncludeData
import ru.hh.android.synthetic_plugin.model.ProjectInfo
import ru.hh.android.synthetic_plugin.utils.ClassParentsFinder
import ru.hh.android.synthetic_plugin.utils.Const
import ru.hh.android.synthetic_plugin.utils.Const.ERROR_INCLUDE_NO_ID
import java.io.File

abstract class ViewBindingPsiProcessor(protected val projectInfo: ProjectInfo) {

    abstract fun processActivity(ktClass: KtClass)

    abstract fun processFragment(ktClass: KtClass)

    abstract fun processView(ktClass: KtClass)

    abstract fun canHandle(parents: ClassParentsFinder, ktClass: KtClass): Boolean

    abstract fun processCustomCases(parents: ClassParentsFinder, ktClass: KtClass)

    /**
     * Return non-formatted list of import synthetic directories
     */
    val syntheticImportDirectives =
        projectInfo.file.importDirectives.filter { it.importPath?.pathStr.isKotlinSynthetic() }

    /**
     * Return map of layout name (e.g. activity_main) to its binding class name (e.g. ActivityMainBinding)
     */
    private val layoutAndBindingMap: HashMap<String, String> =
        hashMapOf<String, String>().apply {
            syntheticImportDirectives.forEach { directive ->
                directive.text?.let { importPath ->
                    val layoutName = importPath.replace("import ", "")
                        .replace(Const.KOTLINX_SYNTHETIC, "")
                        .replace(".*", "")
                        .let {
                            if (it.contains(".")) {
                                // get only layout name, without view id
                                it.substringBefore(".")
                            } else {
                                it
                            }
                        }

                    val bindingName = directive.toFormattedDirective().toFormattedBindingName()

                    put(layoutName, bindingName)
                }
            }
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
    val sortedBindingsWithInclude: List<Pair<String, IncludeData>> by lazy {
        bindingsWithIncludeMap.toList().sortedBy { it.second.order }
    }

    private fun getIncludeData(
        mainDirPath: String,
        includedLayoutName: String,
        includingLayoutName: String,
        includingBindingClass: String
    ): IncludeData {
        val xmlFile = File(mainDirPath, Const.LAYOUT_DIR + includingLayoutName + ".xml")
        val id = xmlFile.getIncludedViewId(includedLayoutName)
        if (id == ERROR_INCLUDE_NO_ID) {
            projectInfo.project.notifyError("No include id for layout $includedLayoutName in $includingLayoutName")
        }
        return if (id == null) IncludeData.NoInclude else
            IncludeData.Include(id.toCamelCase().decapitalize(), includingBindingClass)
    }


    /**
     * Support for multiple binding in single .kt file
     */
    protected val bindingImportDirectives = syntheticImportDirectives.map { directive ->
        directive.toFormattedDirective().toFormattedBindingName()
    }.toSet()

    val hasMultipleBindingsInFile = bindingImportDirectives.size > 1

    private val bindingQualifiedClassNames = run {
        bindingImportDirectives.map { bindingClassName ->
            "${projectInfo.androidFacet?.getPackageName().orEmpty()}.databinding.$bindingClassName"
        }
    }

    /**
     * Add binding declarations at the top of class body (after lBrace)
     */
    fun addBindingAtTopOfClassBody(
        body: KtClassBody,
        vararg properties: KtProperty,
    ) {
        properties.forEach {
            body.addAfter(it, body.lBrace)
        }
    }

    fun addImports(vararg imports: String) {
        projectInfo.file.importList?.let { importList ->
            imports.forEach { import ->
                val importPath = ImportPath.fromString(import)
                val importDirective = projectInfo.psiFactory.createImportDirective(importPath)
                importList.add(getNewLine())
                importList.add(importDirective)
            }
        }
    }

    /**
     * New line for imports because psiFactory.createNewLine()
     * generates leading whitespace
     */
    fun getNewLine(): PsiElement {
        val parserFacade = PsiParserFacade.SERVICE.getInstance(projectInfo.project)
        return parserFacade.createWhiteSpaceFromText("\n")
    }

    fun addViewBindingImports(ktClass: KtClass) {
        addImports(*bindingQualifiedClassNames.toTypedArray())
    }

    /**
     * Format code after changes
     */
    fun formatCode(ktClass: KtClass) {
        val codeStyleManager: CodeStyleManager = CodeStyleManager.getInstance(projectInfo.project)
        codeStyleManager.reformat(ktClass)
    }

    /**
     * Returns proper binding name for setContentView() in Activities
     * or returns default "binding" if no name was found or there is
     * only one import exists in file
     */
    fun getContentViewBindingForActivity(layoutName: String): String {
        return if (hasMultipleBindingsInFile) {
            syntheticImportDirectives.find { it.importPath?.pathStr?.contains(layoutName) == true }
                ?.toFormattedDirective()
                ?.toFormattedBindingName()
                ?.decapitalize() ?: "binding"
        } else {
            "binding"
        }
    }

    /**
     * Trick to add binding elements properly formatted
     */
    private fun findNextNonWhitespaceElement(
        objectDeclaration: KtObjectDeclaration? = null,
        expression: PsiElement? = null,
    ): PsiElement? {
        var nextPsiElement: PsiElement? = objectDeclaration?.nextSibling ?: expression?.nextSibling
        do {
            if (nextPsiElement is PsiWhiteSpace) {
                nextPsiElement = nextPsiElement.nextSibling
                continue
            }
            return nextPsiElement
        } while (true)
    }
}
