package de.rusty.server.template.extension;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.jruby.extension.spi.ExtensionRegistry;

/**
 * An AsciiDoctor extension that integrates the {@link DocumentDiffPreprocessor}
 * to highlight differences within a document.
 *
 * <p>The extension, when registered, enables the processing of documents to visually
 * emphasize modifications, aiding in the quick identification of new or altered content.</p>
 *
 * @author Vadim Golembo
 * @see DocumentDiffPreprocessor
 * @see ExtensionRegistry
 */
public class DocumentDiffExtension implements ExtensionRegistry {

    /**
     * Registers the {@link DocumentDiffPreprocessor} with the provided AsciiDoctor instance.
     *
     * @param asciidoctor The AsciiDoctor instance to which the preprocessor should be registered.
     */
    @Override
    public void register(Asciidoctor asciidoctor) {

        asciidoctor.javaExtensionRegistry().preprocessor(DocumentDiffPreprocessor.class);
    }
}
