package org.fedoraproject.javapackages.validator;

import java.util.Objects;

import org.apache.commons.text.StringEscapeUtils;
import org.fedoraproject.javapackages.validator.spi.Decorated;
import org.fedoraproject.javapackages.validator.spi.Decoration;

class HtmlDecorator implements TextDecorator {
    public static final HtmlDecorator INSTANCE = new HtmlDecorator();

    private HtmlDecorator() {
        super();
    }

    @Override
    public String decorate(Decorated decorated) {
        var result = new StringBuilder("<text class=\"");
        result.append(decorated.getDecoration().color().orElse(Decoration.Color.black));

        for (var modifier : decorated.getDecoration().modifiers()) {
            result.append(" ").append(modifier);
        }

        result.append("\">");
        result.append(StringEscapeUtils.escapeHtml4(Objects.toString(decorated.getObject())).replace(System.lineSeparator(), "<br>"));
        result.append("</text>");

        return result.toString();
    }
}
