package no.nav.pensjon.samhandling.fssproxy

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.type.MethodMetadata
import org.springframework.core.type.filter.AnnotationTypeFilter
import org.springframework.security.access.annotation.Secured
import org.springframework.security.access.prepost.PostAuthorize
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*


class EndpointAnnotationTest {

    @Test
    fun `should have securityAnnotation on all endpoints`() {
        val provider = ClassPathScanningCandidateComponentProvider(true)
        provider.addIncludeFilter(AnnotationTypeFilter(RequestMapping::class.java))

        val beanDefs = provider.findCandidateComponents("no.nav.pensjon.pensjonpenproxyfss")
        for (bd in beanDefs) {
            if (bd is AnnotatedBeanDefinition) {
                bd.relevanteMetoder().forEach { methodMetadata ->
                    if (methodMetadata.methodName != "ping" && methodMetadata.methodName != "deepPing") {
                        assertTrue(
                            methodMetadata.annotations.isPresent(PreAuthorize::class.java)
                                    || methodMetadata.annotations.isPresent(PostAuthorize::class.java)
                                    || methodMetadata.annotations.isPresent(Secured::class.java),
                            "All endpoints must have a security annotation. See ${methodMetadata.methodName} in ${methodMetadata.declaringClassName}"
                        )
                    }
                }
            }
        }
    }

    private fun AnnotatedBeanDefinition.relevanteMetoder(): MutableSet<MethodMetadata> {
        val methodsData = mutableSetOf<MethodMetadata>()
        methodsData.addAll(this.metadata.getAnnotatedMethods(GetMapping::class.java.name))
        methodsData.addAll(this.metadata.getAnnotatedMethods(PostMapping::class.java.name))
        methodsData.addAll(this.metadata.getAnnotatedMethods(PutMapping::class.java.name))
        methodsData.addAll(this.metadata.getAnnotatedMethods(DeleteMapping::class.java.name))
        return methodsData
    }
}