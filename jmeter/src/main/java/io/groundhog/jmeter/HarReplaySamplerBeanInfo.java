package io.groundhog.jmeter;

import io.groundhog.base.URIScheme;

import org.apache.jmeter.testbeans.BeanInfoSupport;
import org.apache.jmeter.testbeans.gui.FileEditor;

import java.beans.PropertyDescriptor;

/**
 * @author Danny Thomas
 * @since 1.0
 */
public class HarReplaySamplerBeanInfo extends BeanInfoSupport {
  public HarReplaySamplerBeanInfo() {
    super(HarReplaySampler.class);
    createPropertyGroup("file", new String[]{"filename"});
    PropertyDescriptor p;
    p = property("filename");
    p.setValue(NOT_UNDEFINED, Boolean.TRUE);
    p.setValue(DEFAULT, "");
    p.setPropertyEditorClass(FileEditor.class);

    createPropertyGroup("server", new String[]{"scheme", "host", "port"});
    p = property("scheme", URIScheme.class);
    p.setValue(NOT_UNDEFINED, Boolean.TRUE);
    p.setValue(NOT_EXPRESSION, Boolean.TRUE);
    p.setValue(DEFAULT, URIScheme.HTTP);

    p = property("host");
    p.setValue(NOT_UNDEFINED, Boolean.TRUE);
    p.setValue(DEFAULT, "");

    p = property("port");
    p.setValue(NOT_UNDEFINED, Boolean.TRUE);
    p.setValue(DEFAULT, URIScheme.HTTP.defaultPort());

    createPropertyGroup("timeouts", new String[]{"connectionTimeout", "socketReadTimeout"});
    p = property("connectionTimeout");
    p.setValue(NOT_UNDEFINED, Boolean.TRUE);
    p.setValue(DEFAULT, 250);

    p = property("socketReadTimeout");
    p.setValue(NOT_UNDEFINED, Boolean.TRUE);
    p.setValue(DEFAULT, 250);
  }
}
