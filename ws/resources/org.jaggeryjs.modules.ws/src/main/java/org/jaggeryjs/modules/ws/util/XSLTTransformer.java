package org.jaggeryjs.modules.ws.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Document;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;


public class XSLTTransformer {

    public static final String WSDL2SIG_XSL_LOCATION = "xslt/wsdl2sig.xslt";
    public static final String JSSTUB_XSL_LOCATION = "xslt/jsstub.xslt";
    public static final String WSDL10TO20_XSL_LOCATION = "xslt/wsdl11to20.xslt";

    private static Log log = LogFactory.getLog(XSLTTransformer.class);

    static {
        System.setProperty("javax.xml.transform.TransformerFactory", "net.sf.saxon.TransformerFactoryImpl");
    }

    public XSLTTransformer() {

    }

    public static DOMSource getSigStream(InputStream wsdlInStream, Map paramMap)
            throws TransformerFactoryConfigurationError, TransformerException,
            ParserConfigurationException {
        Source wsdlSource = new StreamSource(wsdlInStream);
        InputStream sigStream =
                XSLTTransformer.class.getClassLoader().getResourceAsStream(WSDL2SIG_XSL_LOCATION);

        Source wsdl2sigXSLTSource = new StreamSource(sigStream);
        DocumentBuilder docB = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document docSig = docB.newDocument();
        Result resultSig = new DOMResult(docSig);
        transform(wsdlSource, wsdl2sigXSLTSource, resultSig, paramMap, new URIResolver() {
            @Override
            public Source resolve(String href, String base) throws TransformerException {
                String xsd = href.substring(href.toLowerCase().indexOf("?xsd=") + 5);
                try {
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    return new StreamSource(new ByteArrayInputStream(outputStream.toByteArray()));
                } catch (Exception e) {
                    log.error("Error while printing xsd : " + xsd, e);
                    throw new TransformerException(e);
                }
            }
        });
        return new DOMSource(docSig);
    }

    public static void generateStub(Source xmlIn, Result result, Map paramMap)
            throws TransformerException {
        try (InputStream stubXSLTStream = XSLTTransformer.class.getClassLoader()
                .getResourceAsStream(JSSTUB_XSL_LOCATION);) {

            Source stubXSLSource = new StreamSource(stubXSLTStream);
            transform(xmlIn, stubXSLSource, result, paramMap, new URIResolver() {
                public Source resolve(String href, String base) {
                    InputStream is = XSLTTransformer.class.getResourceAsStream(href);
                    return new StreamSource(is);
                }
            });
        } catch (IOException e) {
            log.error("Error while handling inputstream: ", e);
            throw new TransformerException(e);
        }
    }

    public static InputStream getWSDL2(InputStream wsdl1InStream, Map paramMap) throws TransformerException, ParserConfigurationException {
        InputStream wsdl10to20xslt =
                XSLTTransformer.class.getClassLoader().getResourceAsStream(WSDL10TO20_XSL_LOCATION);
        InputStream wsdlIS;

        Source wsdl10Source = new StreamSource(wsdl1InStream);

        DocumentBuilder docB = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document docWSDL = docB.newDocument();
        Result resultWSDL20 = new DOMResult(docWSDL);

        Source wsdlXSLSource = new StreamSource(wsdl10to20xslt);
        try {
            transform(wsdl10Source, wsdlXSLSource, resultWSDL20, paramMap, new URIResolver() {
                public Source resolve(String href, String base) {
                    InputStream is = XSLTTransformer.class.getResourceAsStream(href);
                    Source isSource = new StreamSource(is);
                    try {
                        is.close();
                    } catch (IOException e) {
                        log.error(e.getMessage(), e);
                    }
                    return isSource;
                }
            });

            ByteArrayOutputStream wsdl20OutputStream = new ByteArrayOutputStream();
            Source xmlSource = new DOMSource(docWSDL);
            Result outputTarget = new StreamResult(wsdl20OutputStream);
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            Transformer transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(xmlSource, outputTarget);
            wsdlIS = new ByteArrayInputStream(wsdl20OutputStream.toByteArray());
        } catch (TransformerException e) {
            log.error(e.getMessage(), e);
            throw e;
        } finally {
            try {
                wsdl10to20xslt.close();
            } catch (IOException ignore) {
                log.error("Unable to close the XSLT used to transform WSDL 1.1 to 2.0", ignore);
            }
        }

        return wsdlIS;
    }

    public static void transform(Source xmlIn, Source xslIn, Result result, Map paramMap,
                                 URIResolver uriResolver) throws TransformerException {
        try {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            transformerFactory.setURIResolver(uriResolver);
            Transformer transformer = transformerFactory.newTransformer(xslIn);
            if (paramMap != null) {
                Set set = paramMap.keySet();
                for (Object aSet : set) {
                    String key = (String) aSet;
                    String value = (String) paramMap.get(key);
                    transformer.setParameter(key, value);
                }
            }
            transformer.transform(xmlIn, result);
        } catch (TransformerException e) {
            log.error(e.getMessage(), e);
            throw e;
        }
    }
}
