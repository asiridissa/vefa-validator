package no.difi.vefa.validator.declaration;

import no.difi.vefa.validator.api.DeclarationWithChildren;
import no.difi.vefa.validator.api.Expectation;
import no.difi.vefa.validator.api.ValidatorException;
import no.difi.vefa.validator.expectation.XmlExpectation;
import no.difi.vefa.validator.util.XmlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Iterator;

public class SbdhDeclaration implements DeclarationWithChildren {

    private static final String NAMESPACE = "http://www.unece.org/cefact/namespaces/StandardBusinessDocumentHeader";

    private static Logger logger = LoggerFactory.getLogger(SbdhDeclaration.class);

    private static XMLInputFactory xmlInputFactory = XMLInputFactory.newFactory();
    private static XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newFactory();

    @Override
    public boolean verify(byte[] content) throws ValidatorException {
        String namespace = XmlUtils.extractRootNamespace(new String(content));
        return NAMESPACE.equals(namespace);
    }

    @Override
    public String detect(byte[] content) throws ValidatorException {
        // Simple stupid
        return "SBDH:1.0";
    }

    @Override
    public Expectation expectations(byte[] content) throws ValidatorException {
        return new XmlExpectation(content);
    }

    @Override
    public Iterable<InputStream> children(InputStream inputStream) {
        return new SbdhIterator(inputStream);
    }

    private class SbdhIterator implements Iterable<InputStream>, Iterator<InputStream> {

        public InputStream inputStream;

        public SbdhIterator(InputStream inputStream) {
            this.inputStream = inputStream;
        }

        @Override
        public Iterator<InputStream> iterator() {
            return this;
        }

        @Override
        public boolean hasNext() {
            return inputStream != null;
        }

        @Override
        public InputStream next() {
            try {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

                XMLStreamReader source = xmlInputFactory.createXMLStreamReader(inputStream);
                XMLStreamWriter target = xmlOutputFactory.createXMLStreamWriter(outputStream, source.getEncoding());

                boolean payload = false;

                do {
                    switch (source.getEventType()) {
                        case XMLStreamReader.START_DOCUMENT:
                            logger.debug("START_DOCUMENT");
                            target.writeStartDocument(source.getEncoding(), source.getVersion());
                            break;

                        case XMLStreamConstants.END_DOCUMENT:
                            logger.debug("END_DOCUMENT");
                            target.writeEndDocument();
                            break;

                        case XMLStreamConstants.START_ELEMENT:
                            payload = !source.getNamespaceURI().equals("http://www.unece.org/cefact/namespaces/StandardBusinessDocumentHeader");

                            if (payload) {
                                logger.debug("START_ELEMENT");
                                target.writeStartElement(source.getPrefix(), source.getLocalName(), source.getNamespaceURI());

                                for (int i = 0; i < source.getAttributeCount(); i++)
                                    target.writeAttribute(source.getAttributeLocalName(i), source.getAttributeValue(i));
                                for (int i = 0; i < source.getNamespaceCount(); i++)
                                    target.writeNamespace(source.getNamespacePrefix(i), source.getNamespaceURI(i));
                            }
                            break;

                        case XMLStreamConstants.END_ELEMENT:
                            payload = !source.getNamespaceURI().equals("http://www.unece.org/cefact/namespaces/StandardBusinessDocumentHeader");

                            if (payload) {
                                logger.debug("END_ELEMENT");
                                target.writeEndElement();
                            }
                            break;

                        case XMLStreamConstants.CHARACTERS:
                            if (payload) {
                                logger.debug("CHARACTERS");
                                target.writeCharacters(source.getText());
                            }
                            break;

                        case XMLStreamConstants.CDATA:
                            if (payload) {
                                logger.debug("CDATA");
                                target.writeCData(source.getText());
                            }
                            break;
                    }

                    target.flush();
                } while (source.hasNext() && source.next() > 0);

                target.close();
                source.close();

                inputStream = null;

                return new ByteArrayInputStream(outputStream.toByteArray());
            } catch (Exception e) {
                logger.warn(e.getMessage(), e);
            }
            inputStream = null;
            return null;
        }

        @Override
        public void remove() {
            // No action
        }
    }
}
