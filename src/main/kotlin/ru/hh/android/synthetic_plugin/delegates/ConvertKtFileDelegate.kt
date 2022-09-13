package ru.hh.android.synthetic_plugin.delegates

import com.android.tools.build.jetifier.core.utils.Log
import com.intellij.util.IncorrectOperationException
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgument
import ru.hh.android.synthetic_plugin.extensions.notifyInfo
import ru.hh.android.synthetic_plugin.model.AndroidViewContainer
import ru.hh.android.synthetic_plugin.model.ProjectInfo
import ru.hh.android.synthetic_plugin.visitor.AndroidViewXmlSyntheticsRefsVisitor
import ru.hh.android.synthetic_plugin.visitor.SyntheticsImportsVisitor

object ConvertKtFileDelegate {

    /**
     * This function should be invoked inside [com.intellij.openapi.project.Project.executeWriteCommand]
     * because this method modify your codebase.
     */
    fun perform(
        projectInfo: ProjectInfo,
    ) {
        val xmlRefsVisitor = AndroidViewXmlSyntheticsRefsVisitor()
        val importsVisitor = SyntheticsImportsVisitor()
        projectInfo.file.accept(xmlRefsVisitor)
        projectInfo.file.accept(importsVisitor)
        val xmlViewRefs = xmlRefsVisitor.getResult()
        val syntheticImports = importsVisitor.getResult()
        val viewBindingPsiProcessor = getViewBindingPsiProcessor(projectInfo)

        val viewBindingDelegate = ViewBindingDelegate(
            projectInfo = projectInfo,
            viewBindingPsiProcessor = viewBindingPsiProcessor,
        )

        viewBindingDelegate.addViewBindingProperties()
        replaceSynthCallsToViews(
            psiFactory = projectInfo.psiFactory,
            xmlViewRefs = xmlViewRefs,
            viewBindingProperties = viewBindingPsiProcessor.syntheticImportDirectives,
            hasMultipleBindingsInFile = viewBindingPsiProcessor.hasMultipleBindingsInFile,
        )
        removeKotlinxSyntheticsImports(syntheticImports)

        projectInfo.project.notifyInfo("File ${projectInfo.file.name} converted successfully!")
    }

    private fun getViewBindingPsiProcessor(
        projectInfo: ProjectInfo,
    ) = DelegateViewBindingPsiProcessor(projectInfo)

    private fun replaceSynthCallsToViews(
        psiFactory: KtPsiFactory,
        xmlViewRefs: List<AndroidViewContainer>,
        viewBindingProperties: List<KtImportDirective>,
        hasMultipleBindingsInFile: Boolean,
    ) {
        xmlViewRefs.forEach { refContainer ->
            val newElement: KtValueArgument = psiFactory.createArgument(
                refContainer.getElementName(
                    viewBindingProperties = viewBindingProperties,
                    hasMultipleBindingsInFile = hasMultipleBindingsInFile,
                )
            )

            when (refContainer) {
                is AndroidViewContainer.KtRefExp -> {
                    refContainer.ref.replace(newElement)
                }
                is AndroidViewContainer.PsiRef -> {
                    refContainer.ref.element.replace(newElement)
                }
            }
        }
    }

    private fun removeKotlinxSyntheticsImports(syntheticImports: List<KtImportDirective>) {
        syntheticImports.forEach { import ->
            try {
                import.delete()
            } catch (e: IncorrectOperationException) {
                Log.e("removeKotlinxSyntheticsImports", e.message.orEmpty())
            }
        }
    }
}
