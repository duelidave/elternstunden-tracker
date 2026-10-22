package de.elternstunden.report.service;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.outputdevice.helper.ExternalResourceControlPriority;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Renders a Thymeleaf template to PDF (FS-PDF 7.8 rules 1 and 7). The template engine escapes every value (XML mode),
 * so free texts stay text. The renderer loads nothing from outside: fonts come from the classpath, and every other
 * resource (http, file, data) is refused before and after resolving its URI.
 */
@Component
public class PdfRenderer {

    private static final String[][] FONTS = {
        {"fonts/NotoSans-Regular.ttf", "Noto Sans", "400"},
        {"fonts/NotoSans-Bold.ttf", "Noto Sans", "700"},
        {"fonts/IndieFlower-Regular.ttf", "Indie Flower", "400"},
    };

    private final TemplateEngine templates = new TemplateEngine();

    public PdfRenderer() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".xhtml");
        resolver.setTemplateMode(TemplateMode.XML);
        resolver.setCharacterEncoding("UTF-8");
        templates.setTemplateResolver(resolver);
    }

    public byte[] render(String template, Map<String, Object> variables) {
        String html = templates.process(template, new Context(Locale.GERMAN, variables));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withHtmlContent(html, null);
        for (String[] font : FONTS) {
            builder.useFont(() -> stream(font[0]), font[1], Integer.valueOf(font[2]), FontStyle.NORMAL, true);
        }
        builder.useExternalResourceAccessControl(
                (uri, type) -> false, ExternalResourceControlPriority.RUN_BEFORE_RESOLVING_URI);
        builder.useExternalResourceAccessControl(
                (uri, type) -> false, ExternalResourceControlPriority.RUN_AFTER_RESOLVING_URI);
        builder.withProducer("Elternstunden");
        builder.toStream(out);
        try {
            builder.run();
        } catch (IOException ex) {
            throw new UncheckedIOException("PDF rendering failed", ex);
        }
        return out.toByteArray();
    }

    private static InputStream stream(String path) {
        try {
            return new ClassPathResource(path).getInputStream();
        } catch (IOException ex) {
            throw new UncheckedIOException("Font missing: " + path, ex);
        }
    }
}
