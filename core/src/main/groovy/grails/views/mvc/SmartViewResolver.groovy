/*
 * Copyright 2015 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.views.mvc

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap
import grails.views.ResolvableGroovyTemplateEngine
import grails.views.resolve.TemplateResolverUtils
import grails.web.http.HttpHeaders
import grails.web.mime.MimeType
import groovy.text.Template
import groovy.transform.CompileStatic
import org.springframework.beans.BeanUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.servlet.LocaleResolver
import org.springframework.web.servlet.View

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
/**
 * Spring's default view resolving mechanism only accepts the view name and locale, this forces you to code around its limitations when you want to add intelligent features such as
 * version and mime type awareness.
 *
 * This aims to fix that whilst reducing complexity
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class SmartViewResolver  {

    public static final String OBJECT_TEMPLATE_NAME = "/object/_object"

    @Delegate(methodAnnotations = true) ResolvableGroovyTemplateEngine templateEngine

    Class<? extends GenericGroovyTemplateView> viewClass = GenericGroovyTemplateView
    String contentType
    String suffix = ""

    @Autowired
    LocaleResolver localeResolver

    final View objectView

    private Map<String, GenericGroovyTemplateView> viewCache = new ConcurrentLinkedHashMap.Builder<String, GenericGroovyTemplateView>()
                                                                                            .maximumWeightedCapacity(150)
                                                                                            .build().withDefault { String path ->
        GenericGroovyTemplateView view = BeanUtils.instantiateClass(viewClass)
        String contentType = getContentType()
        if (contentType != null) {
            view.contentType = contentType
        }

        view.url = path
        view.templateEngine = getTemplateEngine()
        view.localeResolver = getLocaleResolver()
        return view
    }

    SmartViewResolver(ResolvableGroovyTemplateEngine templateEngine) {
        this(templateEngine, "", null)
    }

    SmartViewResolver(ResolvableGroovyTemplateEngine templateEngine, String suffix, String contentType) {
        this.suffix = suffix
        this.contentType = contentType
        this.templateEngine = templateEngine
        this.objectView = resolveView(OBJECT_TEMPLATE_NAME, Locale.ENGLISH)
    }

    View resolveView(String viewName, Locale locale) {
        String url = "${viewName}${suffix}"
        View v = viewCache.containsKey(url) ? viewCache.get(url) : null
        if(v == null) {
            def template = resolveTemplate(url, locale)
            if(template != null) {
                return viewCache.get(url)
            }
        }
        return v
    }

    View resolveView(String viewName, HttpServletRequest request, HttpServletResponse response) {
        String url = "${viewName}${suffix}"
        View v = viewCache.containsKey(url) ? viewCache.get(url) : null
        if(v == null) {

            def locale = localeResolver?.resolveLocale(request) ?: request.locale
            List qualifiers = buildQualifiers(request, response)
            def template = resolveTemplate(url, locale, qualifiers as String[])
            if(template != null) {
                return viewCache.get(url)
            }
        }
        return v
    }

    View resolveView(Class type, HttpServletRequest request, HttpServletResponse response) {
        View v = resolveView(TemplateResolverUtils.fullTemplateNameForClass(type), request, response)
        if(v == null) {
            v = resolveView(TemplateResolverUtils.shortTemplateNameForClass(type), request, response)
        }
        return v != null ? v : objectView
    }

    View resolveView(Class type, Locale locale) {
        View v = resolveView(TemplateResolverUtils.fullTemplateNameForClass(type), locale)
        if(v == null) {
            v = resolveView(TemplateResolverUtils.shortTemplateNameForClass(type), locale)
        }
        return v != null ? v : objectView
    }

    protected List buildQualifiers(HttpServletRequest request, HttpServletResponse response) {
        def qualifiers = []
        def version = request.getHeader(HttpHeaders.ACCEPT_VERSION)
        MimeType mimeType = response.getMimeType()
        if (mimeType != null && mimeType != MimeType.ALL) {
            qualifiers.add(mimeType.extension)
        }
        if (version != null) {
            qualifiers.add(version)
        }
        qualifiers
    }


}
