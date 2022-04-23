package com.chatopera.cc.freemarker;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;

import static freemarker.template.Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS;

public class FreeMarkerTplUtils {
    public static String getTemplate(String tpl, Map<String, Object> values) throws IOException, TemplateException {
        StringWriter writer = new StringWriter();
        if (tpl == null || !tpl.contains("$")) {
            return tpl;
        }

        Configuration cfg = new Configuration(DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
        MyTemplateLoader loader = new MyTemplateLoader(tpl);
        cfg.setTemplateLoader(loader);
        cfg.setDefaultEncoding("UTF-8");
        Template template = cfg.getTemplate("");
        template.process(values, writer);
        return writer.toString();
    }
}
